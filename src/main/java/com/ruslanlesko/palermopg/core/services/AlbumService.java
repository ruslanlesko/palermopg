package com.ruslanlesko.palermopg.core.services;

import com.ruslanlesko.palermopg.core.entity.Album;
import com.ruslanlesko.palermopg.core.entity.PictureMeta;
import io.vertx.core.Future;

import java.util.List;

public interface AlbumService {
    Future<Long> addNewAlbum(String token, long userId, String albumName);
    Future<List<Album>> getAlbumsForUserId(String token, long userId);
    Future<List<PictureMeta>> getPictureMetaForAlbum(String token, long userId, long albumId);
    Future<Void> rename(String token, long userId, long albumId, String newName);
    Future<Void> delete(String token, long userId, long albumId);
    Future<Void> shareAlbum(String token, long userId, long albumId, List<Long> sharedUsers);
}
