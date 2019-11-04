package com.ruslanlesko.pichub.core.dao;

import com.ruslanlesko.pichub.core.entity.Picture;

import java.util.List;
import java.util.Optional;

public interface PictureDao {
    long save(long userId, Picture picture);
    Optional<Picture> find(long userId, long id);
    List<Long> findIdsForUser(long userId);
    List<Picture> findPicturesForUser(long userId);
}
