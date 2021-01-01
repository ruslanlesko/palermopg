package com.ruslanlesko.palermopg.core.services;

import com.ruslanlesko.palermopg.core.dao.AlbumDao;
import com.ruslanlesko.palermopg.core.dao.PictureDataDao;
import com.ruslanlesko.palermopg.core.dao.PictureMetaDao;
import com.ruslanlesko.palermopg.core.entity.Album;
import com.ruslanlesko.palermopg.core.entity.PictureMeta;
import com.ruslanlesko.palermopg.core.exception.AuthorizationException;
import com.ruslanlesko.palermopg.core.exception.MissingItemException;
import com.ruslanlesko.palermopg.core.security.JWTParser;
import com.ruslanlesko.palermopg.core.util.CodeGenerator;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.vertx.core.Future.failedFuture;
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
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<Long> resultPromise = Promise.promise();

        albumDao.save(new Album(-1, userId, albumName, List.of(), CodeGenerator.generateDownloadCode()))
                .onSuccess(id -> {
                    logger.info("Album with id {} was created for user id {}", id, userId);
                    resultPromise.complete(id);
                }).onFailure(resultPromise::fail);

        return resultPromise.future();
    }

    public Future<List<Album>> getAlbumsForUserId(String token, long userId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<List<Album>> resultPromise = Promise.promise();

        albumDao.findAlbumsForUserId(userId)
                .onSuccess(albums -> {
                    var albumsWithoutDownloadCode = albums.stream()
                            .filter(a -> a.getDownloadCode() == null || a.getDownloadCode().isEmpty())
                            .collect(toList());
                    if (albumsWithoutDownloadCode.isEmpty()) {
                        resultPromise.complete(albums);
                        return;
                    }

                    var futures = albumsWithoutDownloadCode.stream()
                            .map(a -> albumDao.setDownloadCode(a.getId(), CodeGenerator.generateDownloadCode()))
                            .collect(toList());

                    CompositeFuture.all(new ArrayList<Future>(futures))
                            .onSuccess(success -> getAlbumsForUserId(token, userId).onComplete(resultPromise))
                            .onFailure(resultPromise::fail);
                }).onFailure(resultPromise::fail);

        return resultPromise.future();
    }

    public Future<List<PictureMeta>> getPictureMetaForAlbum(String token, long userId, long albumId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<List<PictureMeta>> resultPromise = Promise.promise();

        albumDao.findById(albumId)
                .onSuccess(albumResult -> {
                    if (albumResult.isEmpty()) {
                        resultPromise.fail(new MissingItemException());
                        return;
                    }
                    if (albumResult.get().getUserId() != userId && !albumResult.get().getSharedUsers().contains(userId)) {
                        resultPromise.fail(new AuthorizationException("Album is missing or not available to user"));
                        return;
                    }

                    pictureMetaDao.findForAlbumId(albumId)
                            .onSuccess(metas -> {
                                var results = metas.stream()
                                        .sorted(this::sortPictureMeta)
                                        .collect(toList());

                                var metasWithoutDownloadCode = results.stream()
                                        .filter(p -> p.getDownloadCode() == null || p.getDownloadCode().isEmpty())
                                        .collect(toList());

                                if (metasWithoutDownloadCode.size() > 0) {
                                    var futures = metasWithoutDownloadCode.stream()
                                            .map(p -> pictureMetaDao.setDownloadCode(p.getId(), CodeGenerator.generateDownloadCode()))
                                            .collect(toList());

                                    CompositeFuture.all(new ArrayList<Future>(futures))
                                            .onSuccess(success -> getPictureMetaForAlbum(token, userId, albumId).onComplete(resultPromise))
                                            .onFailure(resultPromise::fail);
                                    return;
                                }

                                resultPromise.complete(results);
                            }).onFailure(resultPromise::fail);
                }).onFailure(resultPromise::fail);

        return resultPromise.future();
    }

    public Future<Void> rename(String token, long userId, long albumId, String newName) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<Void> resultPromise = Promise.promise();

        albumDao.findById(albumId)
                .onSuccess(findResult -> {
                    if (findResult.isEmpty()) {
                        resultPromise.fail(new MissingItemException());
                        return;
                    }
                    if (findResult.get().getUserId() != userId) {
                        resultPromise.fail(new AuthorizationException("Album is not available to user"));
                        return;
                    }

                    albumDao.renameAlbum(albumId, newName).onComplete(resultPromise);
                }).onFailure(resultPromise::fail);

        return resultPromise.future();
    }

    public Future<Void> delete(String token, long userId, long albumId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<Void> resultPromise = Promise.promise();

        albumDao.findById(albumId)
                .onSuccess(albumResult -> {
                    if (albumResult.isEmpty()) {
                        resultPromise.fail(new MissingItemException());
                        return;
                    }
                    if (albumResult.get().getUserId() != userId) {
                        resultPromise.fail(new AuthorizationException("Album is not available to user"));
                        return;
                    }

                    albumDao.delete(albumId)
                            .onSuccess(deleteResult -> {
                                pictureMetaDao.findForAlbumId(albumId)
                                        .onSuccess(metas -> {
                                            var deleteFutures = metas.stream()
                                                    .map(meta -> pictureService.deletePicture(token, userId, meta.getId()))
                                                    .map(fut -> {
                                                        Promise promise = Promise.promise();

                                                        fut.onSuccess(success -> promise.complete())
                                                                .onFailure(cause -> promise.fail("Not deleted"));

                                                        return promise.future();
                                                    })
                                                    .collect(toList());

                                            CompositeFuture.all(deleteFutures)
                                                    .onSuccess(success -> resultPromise.complete())
                                                    .onFailure(resultPromise::fail);
                                        }).onFailure(resultPromise::fail);
                            }).onFailure(resultPromise::fail);
                }).onFailure(resultPromise::fail);

        return resultPromise.future();
    }

    public Future<Void> shareAlbum(String token, long userId, long albumId, List<Long> sharedUsers) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<Void> resultPromise = Promise.promise();

        albumDao.findById(albumId)
                .onSuccess(albumResult -> {
                    if (albumResult.isEmpty()) {
                        resultPromise.fail(new MissingItemException());
                        return;
                    }
                    Album album = albumResult.get();

                    if (album.getUserId() != userId) {
                        resultPromise.fail(new AuthorizationException("Album is not available to user"));
                        return;
                    }

                    List<Long> newSharedIds = combineSharedUsers(album.getSharedUsers(), sharedUsers);

                    albumDao.updateSharedUsers(albumId, newSharedIds).onComplete(resultPromise);
                }).onFailure(resultPromise::fail);

        return resultPromise.future();
    }

    public Future<byte[]> download(long userId, long albumId, String code) {
        Promise<byte[]> resultPromise = Promise.promise();

        albumDao.findById(albumId)
                .onSuccess(albumResult -> {
                    Album album = albumResult.get();
                    if (album.getUserId() != userId && !album.getSharedUsers().contains(userId)
                            || !album.getDownloadCode().equals(code)) {
                        resultPromise.fail(new AuthorizationException("Album is missing or not available to user"));
                        return;
                    }

                    pictureMetaDao.findForAlbumId(albumId)
                            .onSuccess(metas -> {
                                var pics = metas.stream()
                                        .sorted(this::sortPictureMeta)
                                        .collect(toList());

                                var futures = pics.stream()
                                        .map(p -> pictureDataDao.find(p.getPath()))
                                        .collect(toList());

                                CompositeFuture.all(new ArrayList<Future>(futures))
                                        .onSuccess(dataResults -> {
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
                                                resultPromise.complete(baos.toByteArray());
                                            } catch (IOException e) {
                                                logger.error(e.getMessage());
                                                resultPromise.fail(e);
                                            }
                                        }).onFailure(resultPromise::fail);
                            }).onFailure(resultPromise::fail);
                }).onFailure(resultPromise::fail);

        return resultPromise.future();
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
