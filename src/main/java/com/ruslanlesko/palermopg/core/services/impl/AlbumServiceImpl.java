package com.ruslanlesko.palermopg.core.services.impl;

import com.ruslanlesko.palermopg.core.dao.AlbumDao;
import com.ruslanlesko.palermopg.core.dao.PictureMetaDao;
import com.ruslanlesko.palermopg.core.entity.Album;
import com.ruslanlesko.palermopg.core.entity.PictureMeta;
import com.ruslanlesko.palermopg.core.exception.AuthorizationException;
import com.ruslanlesko.palermopg.core.exception.MissingItemException;
import com.ruslanlesko.palermopg.core.security.JWTParser;
import com.ruslanlesko.palermopg.core.services.AlbumService;
import com.ruslanlesko.palermopg.core.services.PictureService;
import com.ruslanlesko.palermopg.core.util.CodeGenerator;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AlbumServiceImpl implements AlbumService {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private final PictureMetaDao pictureMetaDao;
    private final AlbumDao albumDao;
    private final PictureService pictureService;
    private final JWTParser jwtParser;

    public AlbumServiceImpl(
            PictureMetaDao pictureMetaDao,
            AlbumDao albumDao,
            PictureService pictureService, JWTParser jwtParser
    ) {
        this.pictureMetaDao = pictureMetaDao;
        this.albumDao = albumDao;
        this.pictureService = pictureService;
        this.jwtParser = jwtParser;
    }

    @Override
    public Future<Long> addNewAlbum(String token, long userId, String albumName) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return Future.failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<Long> resultPromise = Promise.promise();

        albumDao.save(new Album(-1, userId, albumName, List.of())).setHandler(saveResult -> {
            if (saveResult.failed()) {
                resultPromise.fail(saveResult.cause());
                return;
            }

            var id = saveResult.result();

            logger.info("Album with id {} was created for user id {}", id, userId);
            resultPromise.complete(id);
        });

        return resultPromise.future();
    }

    @Override
    public Future<List<Album>> getAlbumsForUserId(String token, long userId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return Future.failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }
        return albumDao.findAlbumsForUserId(userId);
    }

    @Override
    public Future<List<PictureMeta>> getPictureMetaForAlbum(String token, long userId, long albumId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return Future.failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<List<PictureMeta>> resultPromise = Promise.promise();

        albumDao.findById(albumId).setHandler(albumResult -> {
            if (albumResult.failed()) {
                resultPromise.fail(albumResult.cause());
                return;
            }

            var albumOptional = albumResult.result();
            if (albumOptional.isEmpty()) {
                resultPromise.fail(new MissingItemException());
                return;
            }
            if (albumOptional.get().getUserId() != userId && !albumOptional.get().getSharedUsers().contains(userId)) {
                resultPromise.fail(new AuthorizationException("Album is missing or not available to user"));
                return;
            }

            pictureMetaDao.findPictureMetasForAlbumId(albumId).setHandler(metaResult -> {
                if (metaResult.failed()) {
                    resultPromise.fail(metaResult.cause());
                    return;
                }

                var results = metaResult.result().stream()
                        .sorted((picA, picB) -> {
                            LocalDateTime uploadedA = picA.getDateUploaded();
                            LocalDateTime uploadedB = picB.getDateUploaded();
                            LocalDateTime capturedA = picA.getDateCaptured();
                            LocalDateTime capturedB = picB.getDateCaptured();

                            if (uploadedA.getYear() == uploadedB.getYear()
                                && uploadedA.getDayOfYear() == uploadedB.getDayOfYear()) {
                                return capturedB.compareTo(capturedA);
                            }

                            return uploadedB.compareTo(uploadedA);
                        }).collect(Collectors.toList());

                var metasWithoutDownloadCode = results.stream()
                        .filter(p -> p.getDownloadCode() == null || p.getDownloadCode().isEmpty())
                        .collect(Collectors.toList());

                if (metasWithoutDownloadCode.size() > 0) {
                    var futures = metasWithoutDownloadCode.stream()
                            .map(p -> pictureMetaDao.setDownloadCode(p.getId(), CodeGenerator.generateDownloadCode()))
                            .collect(Collectors.toList());

                    CompositeFuture.all(new ArrayList<Future>(futures)).setHandler(codeUpdateResults -> {
                        if (codeUpdateResults.failed()) {
                            resultPromise.fail(codeUpdateResults.cause());
                            return;
                        }
                        getPictureMetaForAlbum(token, userId, albumId).setHandler(resultPromise);
                    });
                    return;
                }

                resultPromise.complete(results);
            });
        });

        return resultPromise.future();
    }

    @Override
    public Future<Void> rename(String token, long userId, long albumId, String newName) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return Future.failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<Void> resultPromise = Promise.promise();

        albumDao.findById(albumId).setHandler(findResult -> {
            if (findResult.failed()) {
                resultPromise.fail(findResult.cause());
                return;
            }

            var albumOptional = findResult.result();
            if (albumOptional.isEmpty()) {
                resultPromise.fail(new MissingItemException());
                return;
            }
            if (albumOptional.get().getUserId() != userId) {
                resultPromise.fail(new AuthorizationException("Album is not available to user"));
                return;
            }

            albumDao.renameAlbum(albumId, newName).setHandler(resultPromise);
        });

        return resultPromise.future();
    }

    @Override
    public Future<Void> delete(String token, long userId, long albumId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return Future.failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<Void> resultPromise = Promise.promise();

        albumDao.findById(albumId).setHandler(albumResult -> {
            if (albumResult.failed()) {
                resultPromise.fail(albumResult.cause());
                return;
            }

            var albumOptional = albumResult.result();
            if (albumOptional.isEmpty()) {
                resultPromise.fail(new MissingItemException());
                return;
            }
            if (albumOptional.get().getUserId() != userId) {
                resultPromise.fail(new AuthorizationException("Album is not available to user"));
                return;
            }

            albumDao.delete(albumId).setHandler(deleteResult -> {
                if (deleteResult.failed()) {
                    resultPromise.fail(deleteResult.cause());
                    return;
                }

                pictureMetaDao.findPictureMetasForAlbumId(albumId).setHandler(metaResult -> {
                    if (metaResult.failed()) {
                        resultPromise.fail(metaResult.cause());
                        return;
                    }

                    var deleteFutures = metaResult.result().stream().map(meta -> pictureService.deletePicture(token, userId, meta.getId()))
                            .map(fut -> {
                                Promise promise = Promise.promise();

                                fut.setHandler(futRes -> {
                                    if (futRes.succeeded()) {
                                        promise.complete();
                                    } else {
                                        promise.fail("Not deleted");
                                    }
                                });

                                return promise.future();
                            })
                            .collect(Collectors.toList());

                    CompositeFuture.all(deleteFutures).setHandler(compositeResults -> {
                        if (compositeResults.failed()) {
                            resultPromise.fail(compositeResults.cause());
                            return;
                        }
                        resultPromise.complete();
                    });
                });
            });
        });

        return resultPromise.future();
    }

    @Override
    public Future<Void> shareAlbum(String token, long userId, long albumId, List<Long> sharedUsers) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return Future.failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<Void> resultPromise = Promise.promise();

        albumDao.findById(albumId).setHandler(albumResult -> {
            if (albumResult.failed()) {
                resultPromise.fail(albumResult.cause());
                return;
            }

            var albumOptional = albumResult.result();
            if (albumOptional.isEmpty()) {
                resultPromise.fail(new MissingItemException());
                return;
            }
            if (albumOptional.get().getUserId() != userId) {
                resultPromise.fail(new AuthorizationException("Album is not available to user"));
                return;
            }

            Album album = albumOptional.get();
            List<Long> newSharedIds = combineSharedUsers(album.getSharedUsers(), sharedUsers);

            albumDao.updateSharedUsers(albumId, newSharedIds).setHandler(resultPromise);
        });

        return resultPromise.future();
    }

    List<Long> combineSharedUsers(List<Long> current, List<Long> toAdd) {
        List<Long> result = new ArrayList<>(current);
        result.addAll(toAdd);
        return result.stream().distinct().collect(Collectors.toList());
    }
}
