package com.ruslanlesko.pichub.core.services;

import com.ruslanlesko.pichub.core.entity.Album;
import com.ruslanlesko.pichub.core.entity.PictureMeta;
import io.vertx.core.Future;

import java.util.List;
import java.util.Optional;

public interface AlbumService {
    Future<Optional<Long>> addNewAlbum(String token, long userId, String albumName);
    Future<List<Album>> getAlbumsForUserId(String token, long userId);
    Future<List<PictureMeta>> getPictureMetaForAlbum(String token, long userId, long albumId);
    Future<Boolean> rename(String token, long userId, long albumId, String newName);
    Future<Boolean> delete(String token, long userId, long albumId);
    Future<Boolean> shareAlbum(String token, long userId, long albumId, List<Long> sharedUsers);
}
