package com.leskor.palermopg.dao;

import io.vertx.core.Future;

public interface PictureDataDao {
    Future<String> save(byte[] data, long albumId);
    Future<byte[]> find(String path);
    Future<Void> replace(String path, byte[] data);
    Future<Void> delete(String path);
}
