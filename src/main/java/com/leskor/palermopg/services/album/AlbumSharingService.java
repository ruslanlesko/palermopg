package com.leskor.palermopg.services.album;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.exception.AuthorizationException;
import com.leskor.palermopg.exception.MissingItemException;
import io.vertx.core.Future;

import java.util.ArrayList;
import java.util.List;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;

public class AlbumSharingService {
    private final AlbumDao dao;

    public AlbumSharingService(AlbumDao dao) {
        this.dao = dao;
    }

    public Future<Void> shareAlbum(long userId, long albumId, List<Long> sharedUsers) {
        return dao.findById(albumId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(album -> album.userId() != userId ?
                        failedFuture(new AuthorizationException("Album is not available to user")) : succeededFuture(album))
                .compose(album -> dao.updateSharedUsers(albumId, combineSharedUsers(album.sharedUsers(), sharedUsers)));
    }

    private List<Long> combineSharedUsers(List<Long> current, List<Long> toAdd) {
        if (current == null) {
            return toAdd.stream().distinct().collect(toList());
        }
        List<Long> result = new ArrayList<>(current);
        result.addAll(toAdd);
        return result.stream().distinct().collect(toList());
    }
}
