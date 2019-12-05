package com.ruslanlesko.pichub.core.dao;

import com.ruslanlesko.pichub.core.entity.Album;

import java.util.List;
import java.util.Optional;

public interface AlbumDao {
    long save(Album album);
    Optional<Album> findById(long id);
    List<Album> findAlbumsForUserId(long userId);
}
