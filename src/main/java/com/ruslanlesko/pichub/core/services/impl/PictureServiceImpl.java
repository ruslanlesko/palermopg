package com.ruslanlesko.pichub.core.services.impl;

import com.ruslanlesko.pichub.core.dao.PictureDataDao;
import com.ruslanlesko.pichub.core.dao.PictureMetaDao;
import com.ruslanlesko.pichub.core.entity.PictureMeta;
import com.ruslanlesko.pichub.core.exception.AuthorizationException;
import com.ruslanlesko.pichub.core.meta.MetaParser;
import com.ruslanlesko.pichub.core.security.JWTParser;
import com.ruslanlesko.pichub.core.services.PictureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PictureServiceImpl implements PictureService {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private final PictureMetaDao pictureMetaDao;
    private final PictureDataDao pictureDataDao;
    private final JWTParser jwtParser;

    public PictureServiceImpl(PictureMetaDao pictureMetaDao,
                              PictureDataDao pictureDataDao,
                              JWTParser jwtParser) {
        this.pictureMetaDao = pictureMetaDao;
        this.pictureDataDao = pictureDataDao;
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

        if (meta.get().getUserId() != userId) {
            throw new AuthorizationException("Wrong user id");
        }

        return pictureDataDao.find(meta.get().getPath());
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

        String path = pictureDataDao.save(data);
        PictureMeta meta = new PictureMeta(-1, userId, albumId.orElseGet(() -> -1L), path, LocalDateTime.now(), dateCaptured);
        long id = pictureMetaDao.save(meta);
        logger.info("Inserted new picture with id {} for user id {}", id, userId);
        return id > 0 ? Optional.of(id) : Optional.empty();
    }
}
