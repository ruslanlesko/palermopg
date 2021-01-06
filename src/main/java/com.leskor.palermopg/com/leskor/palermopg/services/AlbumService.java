package com.leskor.palermopg.services;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.dao.PictureDataDao;
import com.leskor.palermopg.dao.PictureMetaDao;
import com.leskor.palermopg.exception.AuthorizationException;
import com.leskor.palermopg.exception.MissingItemException;
import com.leskor.palermopg.util.CodeGenerator;
import com.leskor.palermopg.entity.Album;
import com.leskor.palermopg.entity.PictureMeta;
import com.leskor.palermopg.security.JWTParser;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;

public class AlbumService {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private final PictureMetaDao pictureMetaDao;
    private final PictureDataDao pictureDataDao;
    private final AlbumDao albumDao;
    private final PictureService pictureService;
    private final JWTParser jwtParser;

    public AlbumService(
            PictureMetaDao pictureMetaDao,
            PictureDataDao pictureDataDao, AlbumDao albumDao,
            PictureService pictureService, JWTParser jwtParser
    ) {
        this.pictureMetaDao = pictureMetaDao;
        this.pictureDataDao = pictureDataDao;
        this.albumDao = albumDao;
        this.pictureService = pictureService;
        this.jwtParser = jwtParser;
    }

    public Future<Long> addNewAlbum(String token, long userId, String albumName) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return Future.failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Album newAlbum = new Album(-1, userId, albumName, List.of(), CodeGenerator.generateDownloadCode(), false);

        return albumDao.save(newAlbum)
                .map(id -> {
                    logger.info("Album with id {} was created for user id {}", id, userId);
                    return id;
                });
    }

    public Future<List<Album>> getAlbumsForUserId(String token, long userId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        return albumDao.findAlbumsForUserId(userId)
                .compose(albums -> {
                    var albumsWithoutDownloadCode = albums.stream()
                            .filter(a -> a.getDownloadCode() == null || a.getDownloadCode().isEmpty())
                            .collect(toList());
                    if (albumsWithoutDownloadCode.isEmpty()) {
                        List<Album> sortedAlbums = albums.stream()
                                .sorted(Comparator.comparingLong(Album::getId).reversed())
                                .collect(toList());
                        return succeededFuture(sortedAlbums);
                    }

                    var futures = albumsWithoutDownloadCode.stream()
                            .map(a -> albumDao.setDownloadCode(a.getId(), CodeGenerator.generateDownloadCode()))
                            .collect(toList());

                    return CompositeFuture.all(new ArrayList<Future>(futures))
                            .compose(success -> getAlbumsForUserId(token, userId));
                });
    }

    public Future<List<PictureMeta>> getPictureMetaForAlbum(String token, long userId, long albumId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        return albumDao.findById(albumId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> Future.failedFuture(new MissingItemException())))
                .compose(album -> album.getUserId() != userId && !album.getSharedUsers().contains(userId) ?
                        failedFuture(new AuthorizationException("Album is missing or not available to user")) : succeededFuture(album))
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

                                return CompositeFuture.all(new ArrayList<Future>(futures))
                                        .compose(success -> getPictureMetaForAlbum(token, userId, albumId));
                            }
                            return succeededFuture(results);
                        })
                );
    }

    public Future<Void> rename(String token, long userId, long albumId, String newName) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        return albumDao.findById(albumId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(album -> album.getUserId() != userId ?
                        failedFuture(new AuthorizationException("Album is not available to user"))
                            : albumDao.renameAlbum(albumId, newName));
    }

    public Future<Void> delete(String token, long userId, long albumId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        return albumDao.findById(albumId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(album -> album.getUserId() != userId ?
                        failedFuture(new AuthorizationException("Album is not available to user")) : albumDao.delete(albumId))
                .compose(dbRecordDeleted -> pictureMetaDao.findForAlbumId(albumId))
                .compose(metas -> {
                    var deleteFutures = metas.stream()
                            .map(meta -> pictureService.deletePicture(token, userId, meta.getId()))
                            .map(fut -> {
                                Promise promise = Promise.promise();

                                fut.onSuccess(success -> promise.complete())
                                        .onFailure(cause -> promise.fail("Not deleted"));

                                return promise.future();
                            })
                            .collect(toList());

                    return CompositeFuture.all(deleteFutures)
                            .compose(success -> succeededFuture());
                });
    }

    public Future<Void> shareAlbum(String token, long userId, long albumId, List<Long> sharedUsers) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        return albumDao.findById(albumId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(album -> album.getUserId() != userId ?
                        failedFuture(new AuthorizationException("Album is not available to user")) : succeededFuture(album))
                .compose(album -> albumDao.updateSharedUsers(albumId, combineSharedUsers(album.getSharedUsers(), sharedUsers)));
    }

    public Future<byte[]> download(long userId, long albumId, String code) {
        return albumDao.findById(albumId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(album -> album.getUserId() != userId && !album.getSharedUsers().contains(userId)
                        || !album.getDownloadCode().equals(code) ?
                            failedFuture(new AuthorizationException("Album is missing or not available to user"))
                                : pictureMetaDao.findForAlbumId(albumId))
                .compose(metas -> {
                    var pics = metas.stream()
                            .sorted(this::sortPictureMeta)
                            .collect(toList());

                    var futures = pics.stream()
                            .map(p -> pictureDataDao.find(p.getPath()))
                            .collect(toList());

                    return CompositeFuture.all(new ArrayList<Future>(futures))
                            .compose(dataResults -> {
                                try (
                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        ZipOutputStream zos = new ZipOutputStream(baos);
                                ) {
                                    for (int i = 0; i < dataResults.size(); i++) {
                                        var data = (byte[]) dataResults.resultAt(i);
                                        var meta = pics.get(i);
                                        ZipEntry entry = new ZipEntry(meta.getId() + ".jpg");
                                        zos.putNextEntry(entry);
                                        zos.write(data);
                                        zos.closeEntry();
                                    }
                                    zos.finish();
                                    zos.flush();
                                    return succeededFuture(baos.toByteArray());
                                } catch (IOException e) {
                                    logger.error(e.getMessage());
                                    return failedFuture(e);
                                }
                            });
                });
    }

    public Future<Void> setChronologicalOrder(String token, long userId, long albumId, boolean isChronologicalOrder) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        return albumDao.findById(albumId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(album -> album.getUserId() != userId ?
                        failedFuture(new AuthorizationException("Album is not available to user"))
                            : albumDao.setChronologicalOrder(albumId, isChronologicalOrder));
    }

    private int sortPictureMeta(PictureMeta a, PictureMeta b) {
        LocalDateTime uploadedA = a.getDateUploaded();
        LocalDateTime uploadedB = b.getDateUploaded();
        LocalDateTime capturedA = a.getDateCaptured();
        LocalDateTime capturedB = b.getDateCaptured();

        if (uploadedA.getYear() == uploadedB.getYear()
                && uploadedA.getDayOfYear() == uploadedB.getDayOfYear()) {
            return capturedB.compareTo(capturedA);
        }

        return uploadedB.compareTo(uploadedA);
    }

    List<Long> combineSharedUsers(List<Long> current, List<Long> toAdd) {
        List<Long> result = new ArrayList<>(current);
        result.addAll(toAdd);
        return result.stream().distinct().collect(toList());
    }
}
