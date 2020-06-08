package com.ruslanlesko.pichub.core.dao;

import io.vertx.core.Future;

import java.util.Optional;

public interface PictureDataDao {
    String save(byte[] data);
    Future<Optional<byte[]>> find(String path);
    Future<Boolean> replace(String path, byte[] data);
    boolean delete(String path);
}
