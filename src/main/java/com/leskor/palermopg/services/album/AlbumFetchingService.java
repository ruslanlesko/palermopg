package com.leskor.palermopg.services.album;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.dao.PictureDataDao;
import com.leskor.palermopg.dao.PictureMetaDao;
import com.leskor.palermopg.entity.Album;
import com.leskor.palermopg.entity.PictureMeta;
import com.leskor.palermopg.exception.AuthorizationException;
import com.leskor.palermopg.exception.MissingItemException;
import com.leskor.palermopg.util.CodeGenerator;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Comparator.comparingLong;
import static java.util.stream.Collectors.toList;

public class AlbumFetchingService {
    private final AlbumDao albumDao;
    private final PictureMetaDao pictureMetaDao;
    private final PictureDataDao pictureDataDao;

    public AlbumFetchingService(AlbumDao albumDao, PictureMetaDao pictureMetaDao, PictureDataDao pictureDataDao) {
        this.albumDao = albumDao;
        this.pictureMetaDao = pictureMetaDao;
        this.pictureDataDao = pictureDataDao;
    }

    public Future<List<Album>> getAlbumsForUserId(long userId) {
        if (userId < 1) return failedFuture(new IllegalArgumentException("User ID is invalid for fetching albums"));

        return albumDao.findAlbumsForUserId(userId)
                .compose(albums -> {
                    List<Album> albumsWithoutDownloadCode = albumsWithoutDownloadCode(albums);

                    if (albumsWithoutDownloadCode.isEmpty()) {
                        return futureOfSortedAlbums(albums);
                    }

                    return setDownloadCodesForAlbums(albumsWithoutDownloadCode)
                            .compose(success -> getAlbumsForUserId(userId));
                });
    }

    public Future<List<PictureMeta>> getPictureMetaForAlbum(long userId, long albumId) {
        return albumDao.findById(albumId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> Future.failedFuture(new MissingItemException())))
                .compose(album -> checkAccess(album, userId))
                .compose(album -> pictureMetaDao.findForAlbumId(albumId)
                        .compose(metas -> {
                            boolean isChronologicalOrder = album.isChronologicalOrder();
                            var results = metas.stream()
                                    .sorted((a, b) -> isChronologicalOrder ? -1 * sortPictureMeta(a, b) : sortPictureMeta(a, b))
                                    .collect(toList());

                            var metasWithoutDownloadCode = results.stream()
                                    .filter(p -> p.getDownloadCode() == null || p.getDownloadCode().isEmpty())
                                    .collect(toList());

                            if (metasWithoutDownloadCode.size() > 0) {
                                var futures = metasWithoutDownloadCode.stream()
                                        .map(p -> pictureMetaDao.setDownloadCode(p.getId(), CodeGenerator.generateDownloadCode()))
                                        .collect(toList());

                                return CompositeFuture.all(new ArrayList<>(futures))
                                        .compose(success -> getPictureMetaForAlbum(userId, albumId));
                            }
                            return succeededFuture(results);
                        })
                );
    }

    public Future<byte[]> download(long userId, long albumId, String code) {
        return albumDao.findById(albumId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(album -> checkAccess(album, userId))
                .compose(album -> album.getDownloadCode().equals(code) ? pictureMetaDao.findForAlbumId(albumId)
                        : failedFuture(new AuthorizationException("Album is missing or not available to user")))
                .compose(metas -> {
                    var pics = metas.stream()
                            .sorted(this::sortPictureMeta)
                            .collect(toList());

                    var futures = pics.stream()
                            .map(p -> pictureDataDao.find(p.getPath()))
                            .collect(toList());

                    return CompositeFuture.all(new ArrayList<>(futures))
                            .compose(dataResults -> createArchive(dataResults, pics));
                });
    }

    private List<Album> albumsWithoutDownloadCode(List<Album> albums) {
        return albums.stream()
                .filter(a -> a.getDownloadCode() == null || a.getDownloadCode().isEmpty())
                .collect(toList());
    }

    private Future<List<Album>> futureOfSortedAlbums(List<Album> albums) {
        List<Album> sortedAlbums = albums.stream()
                .sorted(comparingLong(Album::getId).reversed())
                .collect(toList());
        return succeededFuture(sortedAlbums);
    }

    private CompositeFuture setDownloadCodesForAlbums(List<Album> albums) {
        List<Future<Void>> futures = albums.stream()
                .map(a -> albumDao.setDownloadCode(a.getId(), CodeGenerator.generateDownloadCode()))
                .collect(toList());
        return CompositeFuture.all(new ArrayList<>(futures));
    }

    private Future<Album> checkAccess(Album album, long userId) {
        return album.getUserId() != userId && (album.getSharedUsers() == null || !album.getSharedUsers().contains(userId)) ?
                failedFuture(new AuthorizationException("Album is missing or not available to user")) : succeededFuture(album);
    }

    private Future<byte[]> createArchive(CompositeFuture pictures, List<PictureMeta> metas) {
        try (
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ZipOutputStream zos = new ZipOutputStream(baos)
        ) {
            for (int i = 0; i < pictures.size(); i++) {
                var data = (byte[]) pictures.resultAt(i);
                var meta = metas.get(i);
                ZipEntry entry = new ZipEntry(meta.getId() + ".jpg");
                zos.putNextEntry(entry);
                zos.write(data);
                zos.closeEntry();
            }
            zos.finish();
            zos.flush();
            return succeededFuture(baos.toByteArray());
        } catch (IOException e) {
            return failedFuture(e);
        }
    }

    private int sortPictureMeta(PictureMeta a, PictureMeta b) {
        LocalDateTime uploadedA = a.getDateUploaded();
        LocalDateTime uploadedB = b.getDateUploaded();
        LocalDateTime capturedA = a.getDateCaptured();
        LocalDateTime capturedB = b.getDateCaptured();

        if (uploadedA.getYear() == uploadedB.getYear() && uploadedA.getDayOfYear() == uploadedB.getDayOfYear()) {
            return capturedB.compareTo(capturedA);
        }

        return uploadedB.compareTo(uploadedA);
    }
}
