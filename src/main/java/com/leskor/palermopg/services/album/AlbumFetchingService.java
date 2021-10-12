package com.leskor.palermopg.services.album;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.dao.PictureDataDao;
import com.leskor.palermopg.dao.PictureMetaDao;
import com.leskor.palermopg.entity.Album;
import com.leskor.palermopg.entity.PictureMeta;
import com.leskor.palermopg.exception.AuthorizationException;
import com.leskor.palermopg.exception.MissingItemException;
import com.leskor.palermopg.security.JWTParser;
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
    private final JWTParser jwtParser;

    public AlbumFetchingService(AlbumDao albumDao, PictureMetaDao pictureMetaDao, PictureDataDao pictureDataDao, JWTParser jwtParser) {
        this.albumDao = albumDao;
        this.pictureMetaDao = pictureMetaDao;
        this.pictureDataDao = pictureDataDao;
        this.jwtParser = jwtParser;
    }

    public Future<List<Album>> getAlbumsForUserId(long userId) {
        if (userId < 1) return failedFuture(new IllegalArgumentException("User ID is invalid for fetching albums"));

        return albumDao.findAlbumsForUserId(userId)
            .map(albums -> albums.stream()
                .sorted(comparingLong(Album::id).reversed())
                .collect(toList())
            );
    }

    public Future<List<PictureMeta>> getPictureMetaForAlbum(long userId, long albumId) {
        return albumDao.findById(albumId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> Future.failedFuture(new MissingItemException())))
                .compose(album -> checkAccess(album, userId))
                .compose(album -> pictureMetaDao.findForAlbumId(albumId)
                        .map(metas -> metas.stream()
                            .sorted((a, b) -> album.isChronologicalOrder() ? -1 * sortPictureMeta(a, b) : sortPictureMeta(a, b))
                            .collect(toList())
                        )
                );
    }

    public Future<byte[]> download(String token, long userId, long albumId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        return albumDao.findById(albumId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(album -> checkAccess(album, userId))
                .compose(album -> pictureMetaDao.findForAlbumId(albumId))
                .compose(metas -> {
                    var pics = metas.stream()
                            .sorted(this::sortPictureMeta)
                            .collect(toList());

                    var futures = pics.stream()
                            .map(p -> pictureDataDao.find(p.path()))
                            .collect(toList());

                    return CompositeFuture.all(new ArrayList<>(futures))
                            .compose(dataResults -> createArchive(dataResults, pics));
                });
    }

    private Future<Album> checkAccess(Album album, long userId) {
        return album.userId() != userId && (album.sharedUsers() == null || !album.sharedUsers().contains(userId)) ?
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
                ZipEntry entry = new ZipEntry(meta.id() + ".jpg");
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
        LocalDateTime uploadedA = a.dateUploaded();
        LocalDateTime uploadedB = b.dateUploaded();
        LocalDateTime capturedA = a.dateCaptured();
        LocalDateTime capturedB = b.dateCaptured();

        if (uploadedA.getYear() == uploadedB.getYear() && uploadedA.getDayOfYear() == uploadedB.getDayOfYear()) {
            return capturedB.compareTo(capturedA);
        }

        return uploadedB.compareTo(uploadedA);
    }
}
