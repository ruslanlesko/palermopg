package com.ruslanlesko.pichub.core.dao;

import com.ruslanlesko.pichub.core.entity.Album;
import io.vertx.core.Future;

import java.util.List;
import java.util.Optional;

public interface AlbumDao {
    Future<Long> save(Album album);
    Future<Optional<Album>> findById(long id);
    Future<List<Album>> findAlbumsForUserId(long userId);
    Future<Boolean> renameAlbum(long id, String name);
    Future<Boolean> delete(long id);
    Future<Boolean> updateSharedUsers(long id, List<Long> sharedIds);
}
