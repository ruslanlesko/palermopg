package com.ruslanlesko.pichub.core.dao;

import java.util.Optional;

public interface PictureDataDao {
    String save(byte[] data);
    Optional<byte[]> find(String path);
    boolean delete(String path);
}
