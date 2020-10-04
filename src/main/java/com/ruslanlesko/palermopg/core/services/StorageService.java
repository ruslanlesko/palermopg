package com.ruslanlesko.palermopg.core.services;

import com.ruslanlesko.palermopg.core.entity.StorageConsuption;

import io.vertx.core.Future;

public interface StorageService {
    Future<StorageConsuption> findForUser(String token, long userId);
}