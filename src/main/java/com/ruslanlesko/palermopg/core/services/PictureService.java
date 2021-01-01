package com.ruslanlesko.palermopg.core.services;

import com.ruslanlesko.palermopg.core.dao.AlbumDao;
import com.ruslanlesko.palermopg.core.dao.PictureDataDao;
import com.ruslanlesko.palermopg.core.dao.PictureMetaDao;
import com.ruslanlesko.palermopg.core.entity.PictureMeta;
import com.ruslanlesko.palermopg.core.entity.PictureResponse;
import com.ruslanlesko.palermopg.core.exception.AuthorizationException;
import com.ruslanlesko.palermopg.core.exception.MissingItemException;
import com.ruslanlesko.palermopg.core.exception.StorageLimitException;
import com.ruslanlesko.palermopg.core.meta.MetaParser;
import com.ruslanlesko.palermopg.core.security.JWTParser;
import com.ruslanlesko.palermopg.core.util.CodeGenerator;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

public class PictureService {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private static final int TARGET_WIDTH = 1792;
    private static final int TARGET_HEIGHT = 1120;

    private final PictureMetaDao pictureMetaDao;
    private final PictureDataDao pictureDataDao;
    private final AlbumDao albumDao;
    private final JWTParser jwtParser;
    private final StorageService storageService;

    public PictureService(PictureMetaDao pictureMetaDao,
                          PictureDataDao pictureDataDao,
                          AlbumDao albumDao,
                          JWTParser jwtParser,
                          StorageService storageService) {
        this.pictureMetaDao = pictureMetaDao;
        this.pictureDataDao = pictureDataDao;
        this.albumDao = albumDao;
        this.jwtParser = jwtParser;
        this.storageService = storageService;
    }

    public Future<PictureResponse> getPictureData(String token, String clientHash, long userId, long pictureId, boolean fullSize) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        return pictureMetaDao.find(pictureId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(meta ->
                    isAlbumNotAccessible(meta.getAlbumId(), userId)
                            .compose(albumNotAccessible -> (albumNotAccessible && meta.getUserId() != userId) ?
                                    failedFuture(new AuthorizationException("Wrong user id")) : succeededFuture(meta))
                ).compose(meta -> {
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
                .compose(meta ->
                        isAlbumNotAccessible(meta.getAlbumId(), userId)
                                .compose(albumNotAccessible -> (albumNotAccessible && meta.getUserId() != userId) ?
                                        failedFuture(new MissingItemException()) : succeededFuture(meta)))
                .compose(meta -> pictureDataDao.find(meta.getPath()));
    }

    private String calculateHash(PictureMeta meta, boolean fullSize) {
        LocalDateTime dateModified = meta.getDateModified();
        if (dateModified == null) {
            dateModified = meta.getDateUploaded();
        }
        String fullSizeSuffix = fullSize ? "1" : "";
        return "W/\"" + dateModified.toEpochSecond(ZoneOffset.UTC) + fullSizeSuffix + "\"";
    }

    private Future<Boolean> isAlbumNotAccessible(long albumId, long userId) {
        return albumId <= 0 ? succeededFuture(true)
                : albumDao.findById(albumId)
                    .map(opt -> opt.isEmpty() || opt.get().getUserId() != userId && !opt.get().getSharedUsers().contains(userId));
    }

    public Future<Long> insertNewPicture(String token, long userId, Optional<Long> albumId, byte[] data) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        return storageService.findForUser(token, userId)
                .compose(storage -> {
                    final LocalDateTime dateCaptured = extractDateCaptured(data);
                    final byte[] rotatedData = getProperlyRotatedData(data);
                    final byte[] optimizedPictureData = convertToOptimized(rotatedData);

                    long size = rotatedData.length + (optimizedPictureData == null ? 0 : optimizedPictureData.length);
                    if (storage.getSize() + size > storage.getLimit()) return failedFuture(new StorageLimitException());

                    if (optimizedPictureData == null) {
                        logger.warn("Optimized version was not created");
                        return failedFuture("Optimized version was not created");
                    }

                    Future<String> optimizedPathFuture = pictureDataDao.save(optimizedPictureData);
                    Future<String> originalPathFuture = pictureDataDao.save(rotatedData);
                    return CompositeFuture.all(optimizedPathFuture, originalPathFuture)
                            .compose(pathResults -> {
                                String optimizedPath = pathResults.resultAt(0);
                                String originalPath = pathResults.resultAt(1);

                                PictureMeta meta = new PictureMeta(-1, userId, albumId.orElse(-1L), size, originalPath,
                                        optimizedPath,
                                        LocalDateTime.now(), dateCaptured, LocalDateTime.now(),
                                        CodeGenerator.generateDownloadCode());

                                return pictureMetaDao.save(meta)
                                        .map(id -> {
                                            logger.info("Inserted new picture with id {} for user id {}", id, userId);
                                            return id;
                                        });
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

    private byte[] getProperlyRotatedData(byte[] data) {
        try {
            MetaParser metaParser = new MetaParser(data);
            int degrees = metaParser.getRotation();
            return rotateImage(data, degrees);
        } catch (Exception ex) {
            return data;
        }
    }

    public Future<Void> rotatePicture(String token, long userId, long pictureId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        return pictureMetaDao.find(pictureId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(meta ->
                        isAlbumNotAccessible(meta.getAlbumId(), userId)
                            .compose(albumNotAccessible -> (albumNotAccessible && meta.getUserId() != userId) ?
                                    failedFuture(new AuthorizationException("Wrong user id")) : succeededFuture(meta)))
                .compose(meta -> {
                    doRotate(meta.getPathOptimized());
                    return doRotate(meta.getPath());
                }).compose(rotated ->
                    pictureMetaDao.setLastModified(pictureId, LocalDateTime.now())
                            .onFailure(cause -> logger.warn("Cannot set last modified: " + cause.getMessage()))
                );
    }

    private Future<Void> doRotate(String path) {
        if (path == null) {
            return failedFuture(new MissingItemException());
        }

        return pictureDataDao.find(path)
                .map(data -> rotateImage(data, 90))
                .compose(rotatedData -> rotatedData == null ?
                        failedFuture("Cannot rotate image") : pictureDataDao.replace(path, rotatedData));
    }

    public Future<Void> deletePicture(String token, long userId, long pictureId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        return pictureMetaDao.find(pictureId)
                .compose(opt -> opt.map(Future::succeededFuture).orElseGet(() -> failedFuture(new MissingItemException())))
                .compose(meta -> pictureMetaDao.deleteById(pictureId)
                        .compose(dbItemDeleted -> pictureDataDao.delete(meta.getPath()))
                        .compose(originalDeleted -> {
                            logger.info("Successfully deleted picture id {}", pictureId);
                            return meta.getPathOptimized() == null ?
                                    succeededFuture() : pictureDataDao.delete(meta.getPathOptimized());
                        })
                );
    }

    private byte[] rotateImage(byte[] bytes, int degrees) {
        if (degrees <= 0) {
            return bytes;
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            BufferedImage image = ImageIO.read(bais);

            final double rads = Math.toRadians(degrees);
            final double sin = Math.abs(Math.sin(rads));
            final double cos = Math.abs(Math.cos(rads));
            final int w = (int) Math.floor(image.getWidth() * cos + image.getHeight() * sin);
            final int h = (int) Math.floor(image.getHeight() * cos + image.getWidth() * sin);
            final BufferedImage rotatedImage = new BufferedImage(w, h, image.getType());
            final AffineTransform at = new AffineTransform();
            at.translate(w / 2.0, h / 2.0);
            at.rotate(rads, 0, 0);
            at.translate(-image.getWidth() / 2.0, -image.getHeight() / 2.0);
            final AffineTransformOp rotateOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BICUBIC);
            rotateOp.filter(image, rotatedImage);

            ImageIO.write(rotatedImage, "JPEG", baos);
            baos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            logger.error(e.getMessage());
            return null;
        } finally {
            try {
                bais.close();
                baos.close();
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }
    }

    private byte[] convertToOptimized(byte[] bytes) {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            BufferedImage image = ImageIO.read(bais);
            int originalHeight = image.getHeight();
            int originalWidth = image.getWidth();

            if (originalHeight <= TARGET_HEIGHT && originalWidth <= TARGET_WIDTH) {
                return bytes;
            }

            double percent = originalHeight > originalWidth ?
                             (double) TARGET_HEIGHT / (double) originalHeight
                                                            : (double) TARGET_WIDTH / (double) originalWidth;

            AffineTransform resize = AffineTransform.getScaleInstance(percent, percent);
            AffineTransformOp op = new AffineTransformOp(resize, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
            BufferedImage resultImage = op.filter(image, null);

            ImageIO.write(resultImage, "JPEG", baos);
            baos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            logger.error(e.getMessage());
            return null;
        } finally {
            try {
                bais.close();
                baos.close();
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }
    }
}
