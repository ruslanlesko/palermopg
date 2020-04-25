package com.ruslanlesko.pichub.core.services;

import com.ruslanlesko.pichub.core.entity.Album;
import com.ruslanlesko.pichub.core.entity.PictureMeta;

import java.util.List;
import java.util.Optional;

public interface AlbumService {
    Optional<Long> addNewAlbum(String token, long userId, String albumName);
    List<Album> getAlbumsForUserId(String token, long userId);
    List<PictureMeta> getPictureMetaForAlbum(String token, long userId, long albumId);
    boolean rename(String token, long userId, long albumId, String newName);
    boolean delete(String token, long userId, long albumId);
    boolean shareAlbum(String token, long userId, long albumId, List<Long> sharedUsers);
}
