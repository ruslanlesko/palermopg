package com.ruslanlesko.palermopg.core.services.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.ruslanlesko.palermopg.core.dao.PictureDataDao;
import com.ruslanlesko.palermopg.core.dao.PictureMetaDao;
import com.ruslanlesko.palermopg.core.entity.PictureMeta;
import com.ruslanlesko.palermopg.core.entity.StorageConsuption;
import com.ruslanlesko.palermopg.core.exception.AuthorizationException;
import com.ruslanlesko.palermopg.core.security.JWTParser;
import com.ruslanlesko.palermopg.core.services.StorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class StorageServiceImpl implements StorageService {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private static final long LIMIT = 2L * 1024L * 1024L * 1024L;

    private final PictureMetaDao pictureMetaDao;
    private final PictureDataDao pictureDataDao;
    private final JWTParser jwtParser;

    public StorageServiceImpl(
        PictureMetaDao pictureMetaDao,
        PictureDataDao pictureDataDao,
        JWTParser jwtParser
    ) {
        this.pictureMetaDao = pictureMetaDao;
        this.pictureDataDao = pictureDataDao;
        this.jwtParser = jwtParser;
    }

    @Override
    public Future<StorageConsuption> findForUser(String token, long userId) {
        if (!jwtParser.validateTokenForUserId(token, userId) && !jwtParser.isAdmin(token)) {
            return Future.failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<StorageConsuption> resultPromise = Promise.promise();

        pictureMetaDao.findPictureMetasForUserId(userId).setHandler(metasResult -> {
            if (metasResult.failed()) {
                resultPromise.fail(metasResult.cause());
                return;
            }

            List<PictureMeta> metas = metasResult.result();
            long knownSize = metas.stream()
                .mapToLong(PictureMeta::getSize)
                .filter(size -> size > 0)
                .sum();

            List<Future<Long>> unknownMetasSizes = metas.stream()
                .filter(i -> i.getSize() <= 0)
                .map(this::calculateSize)
                .collect(Collectors.toList());

            if (unknownMetasSizes.isEmpty()) {
                resultPromise.complete(new StorageConsuption(knownSize, LIMIT));
                return;
            }

            logger.info("Unknown sizes of {} pictures for user id {}, calculating...", unknownMetasSizes.size(), userId);

            CompositeFuture.all(new ArrayList<Future>(unknownMetasSizes)).setHandler(results -> {
                if (results.failed()) {
                    resultPromise.fail(results.cause());
                    return;
                }
                long size = 0;
                for (int i = 0; i < results.result().size(); i++) {
                    size += (Long) results.result().resultAt(i);
                }
                resultPromise.complete(new StorageConsuption(knownSize + size, LIMIT));
            });
        });
        
        return resultPromise.future();
    }

    private Future<Long> calculateSize(PictureMeta meta) {
        Promise<Long> resultPromise = Promise.promise();

        pictureDataDao.find(meta.getPath()).setHandler(originalData -> {
            if (originalData.failed()) {
                resultPromise.fail(originalData.cause());
                return;
            }

            final long size = originalData.result().length;
            if (meta.getPathOptimized() == null) {
                pictureMetaDao.setSize(meta.getId(), size).setHandler(persisted -> {
                    if (persisted.failed()) {
                        resultPromise.fail(persisted.cause());
                        return;
                    }
                    resultPromise.complete(size);
                });
                return;
            }

            pictureDataDao.find(meta.getPathOptimized()).setHandler(optimizedData -> {
                if (optimizedData.failed()) {
                    resultPromise.fail(optimizedData.cause());
                    return;
                }

                final long result = size + optimizedData.result().length;
                pictureMetaDao.setSize(meta.getId(), result).setHandler(persisted -> {
                    if (persisted.failed()) {
                        resultPromise.fail(persisted.cause());
                        return;
                    }
                    resultPromise.complete(result);
                });
            });
        });

        return resultPromise.future();
    }
}