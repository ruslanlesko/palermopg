package com.leskor.palermopg.core.dao;

import com.leskor.palermopg.core.entity.PictureMeta;
import io.vertx.core.Future;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PictureMetaDao {
    Future<Long> save(PictureMeta pictureMeta);
    Future<Optional<PictureMeta>> find(long id);
    Future<List<PictureMeta>> findForAlbumId(long albumId);
    Future<Void> setLastModified(long id, LocalDateTime lastModified);
    Future<Void> deleteById(long id);
    Future<List<PictureMeta>> findPictureMetasForUserId(long userId);
    Future<Void> setSize(long id, long size);
    Future<Void> setDownloadCode(long id, String code);
}
