package com.ruslanlesko.pichub.core.dao;

import com.ruslanlesko.pichub.core.entity.PictureMeta;
import io.vertx.core.Future;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PictureMetaDao {
    Future<Long> save(PictureMeta pictureMeta);
    Future<Optional<PictureMeta>> find(long id);
    Future<List<PictureMeta>> findPictureMetasForAlbumId(long albumId);
    Future<Void> setLastModified(long id, LocalDateTime lastModified);
    Future<Void> deleteById(long id);
}
