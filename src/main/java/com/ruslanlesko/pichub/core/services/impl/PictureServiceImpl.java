package com.ruslanlesko.pichub.core.services.impl;

import com.ruslanlesko.pichub.core.dao.AlbumDao;
import com.ruslanlesko.pichub.core.dao.PictureDataDao;
import com.ruslanlesko.pichub.core.dao.PictureMetaDao;
import com.ruslanlesko.pichub.core.entity.Album;
import com.ruslanlesko.pichub.core.entity.PictureMeta;
import com.ruslanlesko.pichub.core.entity.PictureResponse;
import com.ruslanlesko.pichub.core.exception.AuthorizationException;
import com.ruslanlesko.pichub.core.meta.MetaParser;
import com.ruslanlesko.pichub.core.security.JWTParser;
import com.ruslanlesko.pichub.core.services.PictureService;
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
                resultPromise.complete(new PictureResponse(Optional.empty(), false, null));
                return;
            }

            var meta = metaOptional.get();
            if (meta.getUserId() != userId && isAlbumNotAccessible(meta.getAlbumId(), userId)) {
                resultPromise.fail(new AuthorizationException("Wrong user id"));
            }

            final String hash = calculateHash(meta);
            if (hash.equals(clientHash)) {
                resultPromise.complete(new PictureResponse(Optional.empty(), true, hash));
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

                var dataOptional = dataResult.result();
                resultPromise.complete(new PictureResponse(dataOptional, false, hash));
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

    private boolean isAlbumNotAccessible(long albumId, long userId) {
        if (albumId <= 0) {
            return true;
        }

        Optional<Album> album = albumDao.findById(albumId);
        if (album.isEmpty()) {
            return true;
        }

        return album.get().getUserId() != userId && !album.get().getSharedUsers().contains(userId);
    }

    @Override
    public Optional<Long> insertNewPicture(String token, long userId, Optional<Long> albumId, byte[] data) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            throw new AuthorizationException("Invalid token for userId: " + userId);
        }

        LocalDateTime dateCaptured;

        try {
            MetaParser metaParser = new MetaParser(data);
            dateCaptured = metaParser.getDateCaptured();
            int degrees = metaParser.getRotation();

            data = rotateImage(data, degrees);

        } catch (Exception ex) {
            dateCaptured = LocalDateTime.now();
            logger.info("No meta");
        }

        byte[] optimizedPictureData = convertToOptimized(data);

        String optimizedPath = null;
        if (optimizedPictureData == null) {
            logger.warn("Optimized version was not created");
        } else {
            optimizedPath = pictureDataDao.save(optimizedPictureData);
        }

        String path = pictureDataDao.save(data);

        PictureMeta meta = new PictureMeta(-1, userId, albumId.orElse(-1L), path,
                optimizedPath,
                LocalDateTime.now(), dateCaptured, LocalDateTime.now()
        );

        long id = pictureMetaDao.save(meta);
        logger.info("Inserted new picture with id {} for user id {}", id, userId);
        return id > 0 ? Optional.of(id) : Optional.empty();
    }

    @Override
    public Future<Boolean> rotatePicture(String token, long userId, long pictureId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            return Future.failedFuture(new AuthorizationException("Invalid token for userId: " + userId));
        }

        Promise<Boolean> resultPromise = Promise.promise();

        pictureMetaDao.find(pictureId).setHandler(metaResult -> {
           if (metaResult.failed()) {
               resultPromise.fail(metaResult.cause());
               return;
           }

           var optMeta = metaResult.result();
            if (optMeta.isEmpty()) {
                resultPromise.complete(false);
                return;
            }

            if (optMeta.get().getUserId() != userId && isAlbumNotAccessible(optMeta.get().getAlbumId(), userId)) {
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

                var wasRotated = rotateResult.result();
                if (wasRotated) {
                    pictureMetaDao.setLastModified(pictureId, LocalDateTime.now());
                }
                resultPromise.complete(wasRotated);
            });
        });

        return resultPromise.future();
    }

    private Future<Boolean> doRotate(String path) {
        if (path == null) {
            return Future.succeededFuture(false);
        }

        Promise<Boolean> resultPromise = Promise.promise();

        pictureDataDao.find(path).setHandler(dataResult -> {
            if (dataResult.failed()) {
                resultPromise.fail(dataResult.cause());
                return;
            }

            var data = dataResult.result();
            if (data.isEmpty()) {
                resultPromise.complete(false);
                return;
            }

            byte[] rotated = rotateImage(data.get(), 90);
            if (rotated == null) {
                resultPromise.complete(false);
                return;
            }

            pictureDataDao.replace(path, rotated).setHandler(resultPromise);
        });

        return resultPromise.future();
    }

    @Override
    public boolean deletePicture(String token, long userId, long pictureId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            throw new AuthorizationException("Invalid token for userId: " + userId);
        }

        Optional<PictureMeta> meta = pictureMetaDao.find(pictureId).result();
        if (meta.isEmpty()) {
            logger.error("Picture with id {} is missing in database", pictureId);
            return false;
        }

        boolean wasDeleted = pictureMetaDao.deleteById(pictureId);
        if (!wasDeleted) {
            logger.error("Cannot delete picture with id {} for user id {}", pictureId, userId);
            return false;
        }

        if (!pictureDataDao.delete(meta.get().getPath())) {
            logger.error("Cannot delete picture id {} data", pictureId);
            return false;
        }

        logger.info("Successfully deleted picture id {}", pictureId);
        return true;
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
            at.rotate(rads,0, 0);
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
            AffineTransformOp op = new AffineTransformOp (resize, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
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
