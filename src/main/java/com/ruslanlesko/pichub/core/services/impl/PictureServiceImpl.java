package com.ruslanlesko.pichub.core.services.impl;

import com.ruslanlesko.pichub.core.dao.AlbumDao;
import com.ruslanlesko.pichub.core.dao.PictureDataDao;
import com.ruslanlesko.pichub.core.dao.PictureMetaDao;
import com.ruslanlesko.pichub.core.entity.Album;
import com.ruslanlesko.pichub.core.entity.PictureMeta;
import com.ruslanlesko.pichub.core.exception.AuthorizationException;
import com.ruslanlesko.pichub.core.meta.MetaParser;
import com.ruslanlesko.pichub.core.security.JWTParser;
import com.ruslanlesko.pichub.core.services.PictureService;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PictureServiceImpl implements PictureService {
    private static final Logger logger = LoggerFactory.getLogger("Application");

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
    public Optional<byte[]> getPictureData(String token, long userId, long pictureId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            throw new AuthorizationException("Invalid token for userId: " + userId);
        }

        Optional<PictureMeta> meta = pictureMetaDao.find(pictureId);
        if (meta.isEmpty()) {
            return Optional.empty();
        }

        if (meta.get().getUserId() != userId && !checkAlbum(meta.get().getAlbumId(), userId)) {
            throw new AuthorizationException("Wrong user id");
        }

        return pictureDataDao.find(meta.get().getPath());
    }

    private boolean checkAlbum(long albumId, long userId) {
        if (albumId <= 0) {
            return false;
        }

        Optional<Album> album = albumDao.findById(albumId);
        if (album.isEmpty()) {
            return false;
        }

        return album.get().getUserId() == userId || album.get().getSharedUsers().contains(userId);
    }

    @Override
    public List<Long> getPictureIdsForUserId(String token, long userId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            throw new AuthorizationException("Invalid token for userId: " + userId);
        }

        return pictureMetaDao.findPictureMetasForUser(userId).stream()
                .filter(p -> p.getAlbumId() <= 0)
                .sorted((picA, picB) -> {
                    LocalDateTime uploadedA = picA.getDateUploaded();
                    LocalDateTime uploadedB = picB.getDateUploaded();
                    LocalDateTime capturedA = picA.getDateCaptured();
                    LocalDateTime capturedB = picB.getDateCaptured();

                    if (uploadedA.getYear() == uploadedB.getYear()
                            && uploadedA.getDayOfYear() == uploadedB.getDayOfYear()) {
                        return capturedB.compareTo(capturedA);
                    }

                    return uploadedB.compareTo(uploadedA);
                })
                .map(PictureMeta::getId)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Long> insertNewPicture(String token, long userId, Optional<Long> albumId, byte[] data) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            throw new AuthorizationException("Invalid token for userId: " + userId);
        }

        MetaParser metaParser = new MetaParser(data);
        LocalDateTime dateCaptured = metaParser.getDateCaptured();
        int degrees = metaParser.getRotation();

        data = rotateImage(data, degrees);

        String path = pictureDataDao.save(data);
        PictureMeta meta = new PictureMeta(-1, userId, albumId.orElseGet(() -> -1L), path, LocalDateTime.now(), dateCaptured);
        long id = pictureMetaDao.save(meta);
        logger.info("Inserted new picture with id {} for user id {}", id, userId);
        return id > 0 ? Optional.of(id) : Optional.empty();
    }

    @Override
    public boolean deletePicture(String token, long userId, long pictureId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            throw new AuthorizationException("Invalid token for userId: " + userId);
        }

        Optional<PictureMeta> meta = pictureMetaDao.find(pictureId);
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
            at.translate(w / 2, h / 2);
            at.rotate(rads,0, 0);
            at.translate(-image.getWidth() / 2, -image.getHeight() / 2);
            final AffineTransformOp rotateOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BICUBIC);
            rotateOp.filter(image, rotatedImage);

            ImageIO.write(rotatedImage, "JPEG", baos);
            baos.flush();
            byte[] resultBytes = baos.toByteArray();
            return resultBytes;
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
