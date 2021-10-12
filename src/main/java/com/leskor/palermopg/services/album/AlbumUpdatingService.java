package com.leskor.palermopg.services.album;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.entity.Album;
import com.leskor.palermopg.exception.AuthorizationException;
import com.leskor.palermopg.exception.MissingItemException;
import io.vertx.core.Future;

import static io.vertx.core.Future.failedFuture;

public class AlbumUpdatingService {
    private final AlbumDao dao;

    public AlbumUpdatingService(AlbumDao dao) {
        this.dao = dao;
    }

    public Future<Void> update(Album album) {
        return dao.findById(album.id())
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(a -> a.userId() == album.userId() ? dao.updateAlbum(album)
                        : failedFuture(new AuthorizationException("Album is not available to user")));
    }
}
