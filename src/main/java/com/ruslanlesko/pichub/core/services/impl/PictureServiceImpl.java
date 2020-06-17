package com.ruslanlesko.pichub.core.services.impl;

import com.ruslanlesko.pichub.core.dao.AlbumDao;
import com.ruslanlesko.pichub.core.dao.PictureDataDao;
import com.ruslanlesko.pichub.core.dao.PictureMetaDao;
import com.ruslanlesko.pichub.core.entity.PictureMeta;
import com.ruslanlesko.pichub.core.entity.PictureResponse;
import com.ruslanlesko.pichub.core.exception.AuthorizationException;
import com.ruslanlesko.pichub.core.exception.MissingItemException;
import com.ruslanlesko.pichub.core.meta.MetaParser;
import com.ruslanlesko.pichub.core.security.JWTParser;
import com.ruslanlesko.pichub.core.services.PictureService;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
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

public class PictureServiceImpl implements PictureService {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private static final int TARGET_WIDTH = 1792;
    private static final int TARGET_HEIGHT = 1120;

    private final PictureMetaDao pictureMetaDao;
    private final PictureDataDao pictureDataDao;
    private final AlbumDao albumDao;
    private final JWTParser jwtParser;

    public PictureServiceImpl(PictureMetaDao pictureMetaDao,
                              PictureDataDao pictureDataDao,
                              AlbumDao albumDao,
                              JWTParser jwtParser) {
        this.pictureMetaDao = pictureMetaDao;
        this.pictureDataDao = pictureDataDao;
        this.albumDao = albumDao;
        this.jwtParser = jwtParser;
    }

    @Override
    public Future<PictureResponse> getPictureData(String token, String clientHash, long userId, long pictureId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return Future.failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<PictureResponse> resultPromise = Promise.promise();

        pictureMetaDao.find(pictureId).setHandler(result -> {
            if (result.failed()) {
                resultPromise.fail(result.cause());
                return;
            }

            var metaOptional = result.result();
            if (metaOptional.isEmpty()) {
                resultPromise.fail(new MissingItemException());
                return;
            }

            var meta = metaOptional.get();

            isAlbumNotAccessible(meta.getAlbumId(), userId).setHandler(albumAccessResult -> {
                if (albumAccessResult.failed()) {
                    resultPromise.fail(albumAccessResult.cause());
                    return;
                }

                var albumNotAccessible = albumAccessResult.result();
                if (meta.getUserId() != userId && albumNotAccessible) {
                    resultPromise.fail(new AuthorizationException("Wrong user id"));
                }

                final String hash = calculateHash(meta);
                if (hash.equals(clientHash)) {
                    resultPromise.complete(new PictureResponse(null, true, hash));
                    return;
                }

                String optimizedPath = meta.getPathOptimized();
                String originalPath = meta.getPath();

                String pathToFind = optimizedPath == null || optimizedPath.isBlank() ? originalPath : optimizedPath;

                pictureDataDao.find(pathToFind).setHandler(dataResult -> {
                    if (dataResult.failed()) {
                        resultPromise.fail(dataResult.cause());
                        return;
                    }

                    resultPromise.complete(new PictureResponse(dataResult.result(), false, hash));
                });
            });
        });

        return resultPromise.future();
    }

    private String calculateHash(PictureMeta meta) {
        LocalDateTime dateModified = meta.getDateModified();
        if (dateModified == null) {
            dateModified = meta.getDateUploaded();
        }
        return "W/\"" + dateModified.toEpochSecond(ZoneOffset.UTC) + "\"";
    }

    private Future<Boolean> isAlbumNotAccessible(long albumId, long userId) {
        Promise<Boolean> resultPromise = Promise.promise();

        if (albumId <= 0) {
            return Future.succeededFuture(true);
        }

        albumDao.findById(albumId).setHandler(albumResult -> {
            if (albumResult.failed()) {
                resultPromise.fail(albumResult.cause());
                return;
            }

            var album = albumResult.result();

            if (album.isEmpty()) {
                resultPromise.complete(true);
                return;
            }

            resultPromise.complete(album.get().getUserId() != userId && !album.get().getSharedUsers().contains(userId));
        });

        return resultPromise.future();
    }

    @Override
    public Future<Long> insertNewPicture(String token, long userId, Optional<Long> albumId, byte[] data) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return Future.failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<Long> resultPromise = Promise.promise();

        final LocalDateTime dateCaptured = extractDateCaptured(data);
        data = getProperlyRotatedData(data);
        byte[] optimizedPictureData = convertToOptimized(data);

        Future<String> optimizedPathFuture;
        if (optimizedPictureData == null) {
            logger.warn("Optimized version was not created");
            optimizedPathFuture = Future.failedFuture("Optimized version was not created");
        } else {
            optimizedPathFuture = pictureDataDao.save(optimizedPictureData);
        }

        Future<String> originalPathFuture = pictureDataDao.save(data);

        CompositeFuture.all(optimizedPathFuture, originalPathFuture).setHandler(pathResults -> {
            if (pathResults.failed()) {
                resultPromise.fail(pathResults.cause());
                return;
            }

            String optimizedPath = pathResults.result().resultAt(0);
            String originalPath = pathResults.result().resultAt(1);

            PictureMeta meta = new PictureMeta(-1, userId, albumId.orElse(-1L), originalPath,
                    optimizedPath,
                    LocalDateTime.now(), dateCaptured, LocalDateTime.now()
            );

            pictureMetaDao.save(meta).setHandler(metaResult -> {
                if (metaResult.failed()) {
                    resultPromise.fail(metaResult.cause());
                    return;
                }

                var id = metaResult.result();

                logger.info("Inserted new picture with id {} for user id {}", id, userId);
                resultPromise.complete(id);
            });
        });

        return resultPromise.future();
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

    @Override
    public Future<Void> rotatePicture(String token, long userId, long pictureId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return Future.failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<Void> resultPromise = Promise.promise();

        pictureMetaDao.find(pictureId).setHandler(metaResult -> {
            if (metaResult.failed()) {
                resultPromise.fail(metaResult.cause());
                return;
            }

            var optMeta = metaResult.result();
            if (optMeta.isEmpty()) {
                resultPromise.fail(new MissingItemException());
                return;
            }

            isAlbumNotAccessible(optMeta.get().getAlbumId(), userId).setHandler(albumAccessResult -> {
                if (albumAccessResult.failed()) {
                    resultPromise.fail(albumAccessResult.cause());
                    return;
                }

                var albumNotAccessible = albumAccessResult.result();
                if (optMeta.get().getUserId() != userId && albumNotAccessible) {
                    resultPromise.fail(new AuthorizationException("Wrong user id"));
                    return;
                }

                String path = optMeta.get().getPath();
                String pathOptimized = optMeta.get().getPathOptimized();

                doRotate(pathOptimized);
                doRotate(path).setHandler(rotateResult -> {
                    if (rotateResult.failed()) {
                        resultPromise.fail(rotateResult.cause());
                        return;
                    }

                    pictureMetaDao.setLastModified(pictureId, LocalDateTime.now()).setHandler(modificationResult -> {
                        if (modificationResult.failed()) {
                            logger.warn("Cannot set last modified");
                        }
                    });
                    resultPromise.complete();
                });
            });
        });

        return resultPromise.future();
    }

    private Future<Void> doRotate(String path) {
        if (path == null) {
            return Future.failedFuture(new MissingItemException());
        }

        Promise<Void> resultPromise = Promise.promise();

        pictureDataDao.find(path).setHandler(dataResult -> {
            if (dataResult.failed()) {
                resultPromise.fail(dataResult.cause());
                return;
            }

            byte[] rotated = rotateImage(dataResult.result(), 90);
            if (rotated == null) {
                resultPromise.fail("Cannot rotate image");
                return;
            }

            pictureDataDao.replace(path, rotated).setHandler(resultPromise);
        });

        return resultPromise.future();
    }

    @Override
    public Future<Void> deletePicture(String token, long userId, long pictureId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return Future.failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<Void> resultPromise = Promise.promise();

        pictureMetaDao.find(pictureId).setHandler(metaResult -> {
            if (metaResult.failed()) {
                resultPromise.fail(metaResult.cause());
                return;
            }

            var meta = metaResult.result();

            if (meta.isEmpty()) {
                logger.error("Picture with id {} is missing in database", pictureId);
                resultPromise.fail(new MissingItemException());
                return;
            }

            pictureMetaDao.deleteById(pictureId).setHandler(deleteDbResult -> {
                if (deleteDbResult.failed()) {
                    resultPromise.fail(deleteDbResult.cause());
                    return;
                }

                pictureDataDao.delete(meta.get().getPath()).setHandler(deleteOriginalResult -> {
                    if (deleteOriginalResult.failed()) {
                        logger.error("Cannot delete picture id {} data", pictureId);
                        resultPromise.fail(deleteOriginalResult.cause());
                        return;
                    }

                    logger.info("Successfully deleted picture id {}", pictureId);

                    String optimized = meta.get().getPathOptimized();
                    if (optimized != null) {
                        pictureDataDao.delete(optimized).setHandler(resultPromise);
                        return;
                    }
                    resultPromise.complete();
                });
            });
        });

        return resultPromise.future();
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
