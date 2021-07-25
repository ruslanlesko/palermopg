package com.leskor.palermopg.services.album;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.dao.PictureMetaDao;
import com.leskor.palermopg.entity.Album;
import com.leskor.palermopg.exception.AuthorizationException;
import com.leskor.palermopg.exception.MissingItemException;
import com.leskor.palermopg.security.JWTParser;
import com.leskor.palermopg.services.PictureService;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;

public class AlbumDeletingService {
    private final JWTParser jwtParser;
    private final PictureService pictureService;
    private final AlbumDao albumDao;
    private final PictureMetaDao pictureMetaDao;
    private final AlbumFetchingService albumFetchingService;

    public AlbumDeletingService(
            JWTParser jwtParser,
            PictureService pictureService,
            AlbumDao albumDao,
            PictureMetaDao pictureMetaDao,
            AlbumFetchingService albumFetchingService
    ) {
        this.jwtParser = jwtParser;
        this.pictureService = pictureService;
        this.albumDao = albumDao;
        this.pictureMetaDao = pictureMetaDao;
        this.albumFetchingService = albumFetchingService;
    }

    public Future<Void> deleteAll(String token, long userId) {
        if (!jwtParser.validateTokenForUserId(token, userId) && !jwtParser.isAdmin(token)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        return albumFetchingService.getAlbumsForUserId(userId)
                .compose(albums -> {
                    var deleteFutures = albums.stream()
                            .filter(a -> a.getUserId() == userId)
                            .map(album -> deleteAlbum(userId, album))
                            .map(fut -> {
                                Promise promise = Promise.promise();

                                fut.onSuccess(success -> promise.complete())
                                        .onFailure(cause -> promise.fail("Not deleted: " + cause.getMessage()));

                                return promise.future();
                            })
                            .collect(toList());

                    return CompositeFuture.all(deleteFutures)
                            .compose(success -> succeededFuture());
                });
    }

    public Future<Void> delete(long userId, long albumId) {
        return albumDao.findById(albumId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(album -> deleteAlbum(userId, album));
    }

    private Future<Void> deleteAlbum(long userId, Album album) {
        if (album.getUserId() != userId) {
            return failedFuture(new AuthorizationException("Album is not available to user"));
        }
        return pictureMetaDao.findForAlbumId(album.getId())
                .compose(metas -> {
                    var deleteFutures = metas.stream()
                            .map(meta -> pictureService.deletePicture(userId, meta.getId()))
                            .map(fut -> {
                                Promise promise = Promise.promise();

                                fut.onSuccess(success -> promise.complete())
                                        .onFailure(cause -> promise.fail("Not deleted: " + cause.getMessage()));

                                return promise.future();
                            })
                            .collect(toList());

                    return CompositeFuture.all(deleteFutures)
                            .compose(success -> succeededFuture());
                })
                .compose(deleted ->  albumDao.delete(album.getId()));
    }
}
