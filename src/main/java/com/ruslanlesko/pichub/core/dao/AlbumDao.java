package com.ruslanlesko.pichub.core.dao;

import com.ruslanlesko.pichub.core.entity.Album;
import io.vertx.core.Future;

import java.util.List;
import java.util.Optional;

public interface AlbumDao {
    Future<Long> save(Album album);
    Future<Optional<Album>> findById(long id);
    Future<List<Album>> findAlbumsForUserId(long userId);
    Future<Void> renameAlbum(long id, String name);
    Future<Void> delete(long id);
    Future<Void> updateSharedUsers(long id, List<Long> sharedIds);
}
