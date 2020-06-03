package com.ruslanlesko.pichub.core.dao;

import com.ruslanlesko.pichub.core.entity.PictureMeta;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PictureMetaDao {
    long save(PictureMeta pictureMeta);
    Optional<PictureMeta> find(long id);
    List<PictureMeta> findPictureMetasForUser(long userId);
    List<PictureMeta> findPictureMetasForAlbumId(long albumId);
    boolean setLastModified(long id, LocalDateTime lastModified);
    boolean deleteById(long id);
}
