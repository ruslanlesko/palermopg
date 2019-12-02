package com.ruslanlesko.pichub.core.dao;

import com.ruslanlesko.pichub.core.entity.PictureMeta;

import java.util.List;
import java.util.Optional;

public interface PictureMetaDao {
    long save(PictureMeta pictureMeta);
    Optional<PictureMeta> find(long id);
    List<PictureMeta> findPictureMetasForUser(long userId);
}
