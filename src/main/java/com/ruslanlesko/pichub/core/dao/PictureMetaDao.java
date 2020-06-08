package com.ruslanlesko.pichub.core.dao;

import com.ruslanlesko.pichub.core.entity.PictureMeta;
import io.vertx.core.Future;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PictureMetaDao {
    long save(PictureMeta pictureMeta);
    Future<Optional<PictureMeta>> find(long id);
    List<PictureMeta> findPictureMetasForAlbumId(long albumId);
    Future<Boolean> setLastModified(long id, LocalDateTime lastModified);
    Future<Boolean> deleteById(long id);
}
