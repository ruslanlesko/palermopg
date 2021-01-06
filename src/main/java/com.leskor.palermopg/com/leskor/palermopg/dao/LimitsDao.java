package com.leskor.palermopg.dao;

import io.vertx.core.Future;

import java.util.Optional;

public interface LimitsDao {
    Future<Void> setLimitForUser(long userId, long limit);
    Future<Optional<Long>> getLimitForUser(long userId);
}
