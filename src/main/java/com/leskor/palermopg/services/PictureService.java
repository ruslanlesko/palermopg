package com.leskor.palermopg.services;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.dao.PictureDataDao;
import com.leskor.palermopg.dao.PictureMetaDao;
import com.leskor.palermopg.entity.PictureMeta;
import com.leskor.palermopg.entity.PictureResponse;
import com.leskor.palermopg.entity.StorageConsumption;
import com.leskor.palermopg.exception.AuthorizationException;
import com.leskor.palermopg.exception.MissingItemException;
import com.leskor.palermopg.exception.StorageLimitException;
import com.leskor.palermopg.meta.MetaParser;
import com.leskor.palermopg.security.JWTParser;
import com.leskor.palermopg.util.CodeGenerator;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.time.ZoneOffset.UTC;

public class PictureService {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private final PictureMetaDao pictureMetaDao;
    private final PictureDataDao pictureDataDao;
    private final AlbumDao albumDao;
    private final JWTParser jwtParser;
    private final StorageService storageService;
    private final PictureManipulationService pictureManipulationService;

    public PictureService(PictureMetaDao pictureMetaDao,
                          PictureDataDao pictureDataDao,
                          AlbumDao albumDao,
                          JWTParser jwtParser,
                          StorageService storageService,
                          PictureManipulationService pictureManipulationService) {
        this.pictureMetaDao = pictureMetaDao;
        this.pictureDataDao = pictureDataDao;
        this.albumDao = albumDao;
        this.jwtParser = jwtParser;
        this.storageService = storageService;
        this.pictureManipulationService = pictureManipulationService;
    }

    public Future<PictureResponse> getPictureData(String token, String clientHash, long userId, long pictureId, boolean fullSize) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return Future.failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        return pictureMetaDao.find(pictureId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> Future.failedFuture(new MissingItemException())))
                .compose(meta -> checkPictureAccess(userId, meta))
                .compose(meta -> {
                    final String hash = calculateHash(meta, fullSize);
                    if (hash.equals(clientHash)) return succeededFuture(new PictureResponse(null, true, hash));

                    final String optimizedPath = meta.getPathOptimized();
                    final String originalPath = meta.getPath();
                    final String pathToFind = fullSize || optimizedPath == null || optimizedPath.isBlank() ?
                            originalPath : optimizedPath;

                    return pictureDataDao.find(pathToFind)
                            .map(data -> new PictureResponse(data, false, hash));
                });
    }

    public Future<byte[]> downloadPicture(long userId, long pictureId, String code) {
        return pictureMetaDao.find(pictureId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(meta -> meta.getDownloadCode().equals(code) ? succeededFuture(meta) : failedFuture(new MissingItemException()))
                .compose(meta -> checkPictureAccess(userId, meta))
                .compose(meta -> pictureDataDao.find(meta.getPath()));
    }

    private String calculateHash(PictureMeta meta, boolean fullSize) {
        LocalDateTime dateModified = meta.getDateModified();
        if (dateModified == null) {
            dateModified = meta.getDateUploaded();
        }
        String fullSizeSuffix = fullSize ? "1" : "";
        return String.format("W/\"%d%d%s\"", meta.getId(), dateModified.toEpochSecond(UTC), fullSizeSuffix);
    }

    public Future<Long> insertNewPicture(String token, long userId, long albumId, byte[] data) {
        final LocalDateTime dateCaptured = extractDateCaptured(data);
        return storageService.findForUser(token, userId).compose(storage ->
                pictureManipulationService.rotateToCorrectOrientation(data).compose(rotatedData ->
                        pictureManipulationService.convertToOptimized(rotatedData).compose(optimizedPictureData ->
                                doInsertPicture(userId, albumId, storage, dateCaptured, rotatedData, optimizedPictureData)
                        )
                )
        );
    }

    private Future<Long> doInsertPicture(
            long userId,
            long albumId,
            StorageConsumption storage,
            LocalDateTime dateCaptured,
            byte[] rotatedData,
            byte[] optimizedPictureData
    ) {
        long size = rotatedData.length + (optimizedPictureData == null ? 0 : optimizedPictureData.length);
        if (storage.getSize() + size > storage.getLimit()) return Future.failedFuture(new StorageLimitException());

        if (optimizedPictureData == null) {
            logger.warn("Optimized version was not created");
            return failedFuture("Optimized version was not created");
        }

        Future<String> optimizedPathFuture = pictureDataDao.save(optimizedPictureData);
        Future<String> originalPathFuture = pictureDataDao.save(rotatedData);
        return CompositeFuture.all(optimizedPathFuture, originalPathFuture).compose(pathResults -> {
            String optimizedPath = pathResults.resultAt(0);
            String originalPath = pathResults.resultAt(1);

            PictureMeta meta = new PictureMeta(-1, userId, albumId, size, originalPath,
                    optimizedPath,
                    LocalDateTime.now(), dateCaptured, LocalDateTime.now(),
                    CodeGenerator.generateDownloadCode());

            return pictureMetaDao.save(meta)
                    .map(id -> {
                        logger.info("Inserted new picture with id {} for user id {}", id, userId);
                        return id;
                    });
        });
    }

    private LocalDateTime extractDateCaptured(byte[] data) {
        try {
            MetaParser metaParser = new MetaParser(data);
            return metaParser.getDateCaptured();
        } catch (Exception ex) {
            logger.info("No meta");
            return LocalDateTime.now();
        }
    }

    public Future<Void> rotatePicture(long userId, long pictureId) {
        return pictureMetaDao.find(pictureId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(meta -> checkPictureAccess(userId, meta))
                .compose(meta -> {
                    doRotate(meta.getPathOptimized());
                    return doRotate(meta.getPath());
                }).compose(rotated ->
                    pictureMetaDao.setLastModified(pictureId, LocalDateTime.now())
                            .onFailure(cause -> logger.warn("Cannot set last modified: " + cause.getMessage()))
                );
    }

    private Future<Void> doRotate(String path) {
        if (path == null) return failedFuture(new MissingItemException());
        else return pictureDataDao.find(path)
                .compose(pictureManipulationService::rotate90)
                .compose(rotatedData -> pictureDataDao.replace(path, rotatedData));
    }

    public Future<Void> deletePicture(long userId, long pictureId) {
        return pictureMetaDao.find(pictureId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(meta -> checkPictureAccess(userId, meta))
                .compose(meta -> pictureMetaDao.deleteById(pictureId)
                        .compose(dbItemDeleted -> pictureDataDao.delete(meta.getPath()))
                        .compose(originalDeleted -> meta.getPathOptimized() == null ? succeededFuture()
                                : pictureDataDao.delete(meta.getPathOptimized()))
                );
    }

    private Future<PictureMeta> checkPictureAccess(long userId, PictureMeta meta) {
        return isAlbumNotAccessible(meta.getAlbumId(), userId)
                .compose(albumNotAccessible -> (albumNotAccessible && meta.getUserId() != userId) ?
                        failedFuture(new AuthorizationException("Wrong user id")) : succeededFuture(meta));
    }

    private Future<Boolean> isAlbumNotAccessible(long albumId, long userId) {
        return albumId <= 0 ? succeededFuture(true)
                : albumDao.findById(albumId)
                .map(opt -> opt.isEmpty() || opt.get().getUserId() != userId && (opt.get().getSharedUsers() == null || !opt.get().getSharedUsers().contains(userId)));
    }
}
