package com.leskor.palermopg.services.album;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.dao.PictureMetaDao;
import com.leskor.palermopg.exception.AuthorizationException;
import com.leskor.palermopg.exception.MissingItemException;
import com.leskor.palermopg.services.PictureService;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;

public class AlbumDeletingService {
    private final PictureService pictureService;
    private final AlbumDao albumDao;
    private final PictureMetaDao pictureMetaDao;

    public AlbumDeletingService(PictureService pictureService, AlbumDao albumDao, PictureMetaDao pictureMetaDao) {
        this.pictureService = pictureService;
        this.albumDao = albumDao;
        this.pictureMetaDao = pictureMetaDao;
    }

    public Future<Void> delete(long userId, long albumId) {
        return albumDao.findById(albumId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(album -> album.getUserId() == userId ? albumDao.delete(albumId)
                        : failedFuture(new AuthorizationException("Album is not available to user")))
                .compose(dbRecordDeleted -> pictureMetaDao.findForAlbumId(albumId))
                .compose(metas -> {
                    var deleteFutures = metas.stream()
                            .map(meta -> pictureService.deletePicture(userId, meta.getId()))
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
}
