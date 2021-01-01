package com.ruslanlesko.palermopg.core.services;

import com.ruslanlesko.palermopg.core.dao.LimitsDao;
import com.ruslanlesko.palermopg.core.dao.PictureDataDao;
import com.ruslanlesko.palermopg.core.dao.PictureMetaDao;
import com.ruslanlesko.palermopg.core.entity.PictureMeta;
import com.ruslanlesko.palermopg.core.entity.StorageConsumption;
import com.ruslanlesko.palermopg.core.exception.AuthorizationException;
import com.ruslanlesko.palermopg.core.security.JWTParser;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.vertx.core.Future.succeededFuture;

public class StorageService {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private static final long LIMIT = 2L * 1024L * 1024L * 1024L;

    private final PictureMetaDao pictureMetaDao;
    private final PictureDataDao pictureDataDao;
    private final LimitsDao limitsDao;
    private final JWTParser jwtParser;

    public StorageService(
            PictureMetaDao pictureMetaDao,
            PictureDataDao pictureDataDao,
            LimitsDao limitsDao, JWTParser jwtParser
    ) {
        this.pictureMetaDao = pictureMetaDao;
        this.pictureDataDao = pictureDataDao;
        this.limitsDao = limitsDao;
        this.jwtParser = jwtParser;
    }

    public Future<StorageConsumption> findForUser(String token, long userId) {
        if (!jwtParser.validateTokenForUserId(token, userId) && !jwtParser.isAdmin(token)) {
            return Future.failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<StorageConsumption> resultPromise = Promise.promise();

        pictureMetaDao.findPictureMetasForUserId(userId)
                .onSuccess(metas -> {
                    long knownSize = metas.stream()
                        .mapToLong(PictureMeta::getSize)
                        .filter(size -> size > 0)
                        .sum();

                    List<Future<Long>> unknownMetasSizes = metas.stream()
                        .filter(i -> i.getSize() <= 0)
                        .map(this::calculateSize)
                        .collect(Collectors.toList());

                    if (unknownMetasSizes.isEmpty()) {
                        getLimitAndReturnStorageConsumption(resultPromise, userId, knownSize);
                        return;
                    }

                    logger.info("Unknown sizes of {} pictures for user id {}, calculating...", unknownMetasSizes.size(), userId);

                    CompositeFuture.all(new ArrayList<Future>(unknownMetasSizes))
                            .onSuccess(results -> {
                                long size = 0;
                                for (int i = 0; i < results.size(); i++) {
                                    size += (Long) results.resultAt(i);
                                }
                                getLimitAndReturnStorageConsumption(resultPromise, userId, knownSize + size);
                            }).onFailure(resultPromise::fail);
                }).onFailure(resultPromise::fail);
        
        return resultPromise.future();
    }

    public Future<List<StorageConsumption>> findForUsers(String token, List<Long> ids) {
        Promise<List<StorageConsumption>> resultPromise = Promise.promise();

        List<Future<StorageConsumption>> futures = ids.stream()
                .map(id -> findForUser(token, id))
                .collect(Collectors.toList());

        CompositeFuture.all(new ArrayList<Future>(futures))
                .onSuccess(results -> {
                    List<StorageConsumption> storageConsumptions = new ArrayList<>();
                    for (int i = 0; i < results.result().size(); i++) {
                        storageConsumptions.add((StorageConsumption) results.result().resultAt(i));
                    }
                    resultPromise.complete(storageConsumptions);
                }).onFailure(resultPromise::fail);

        return resultPromise.future();
    }

    private void getLimitAndReturnStorageConsumption(Promise<StorageConsumption> resultPromise, long userId, long size) {
        limitsDao.getLimitForUser(userId)
                .onSuccess(result -> {
                    long limit = result.orElse(LIMIT);
                    resultPromise.complete(new StorageConsumption(userId, size, limit));
                }).onFailure(resultPromise::fail);
    }

    public Future<Void> setLimitForUser(String token, long userId, long limit) {
        if (!jwtParser.isAdmin(token)) {
            return Future.failedFuture(new AuthorizationException("User is not an admin"));
        }

        return limitsDao.setLimitForUser(userId, limit);
    }

    private Future<Long> calculateSize(PictureMeta meta) {
        return pictureDataDao.find(meta.getPath())
                .compose(originalData -> {
                    final long size = originalData.length;
                    final String pathOptimized = meta.getPathOptimized();
                    return pathOptimized == null ? succeededFuture(size)
                            : pictureDataDao.find(pathOptimized)
                                .map(optimizedData -> optimizedData.length + size);
                });
    }
}