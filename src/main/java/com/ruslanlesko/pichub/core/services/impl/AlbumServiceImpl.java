package com.ruslanlesko.pichub.core.services.impl;

import com.ruslanlesko.pichub.core.dao.AlbumDao;
import com.ruslanlesko.pichub.core.dao.PictureDataDao;
import com.ruslanlesko.pichub.core.dao.PictureMetaDao;
import com.ruslanlesko.pichub.core.entity.Album;
import com.ruslanlesko.pichub.core.entity.PictureMeta;
import com.ruslanlesko.pichub.core.exception.AuthorizationException;
import com.ruslanlesko.pichub.core.security.JWTParser;
import com.ruslanlesko.pichub.core.services.AlbumService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AlbumServiceImpl implements AlbumService {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private final PictureMetaDao pictureMetaDao;
    private final PictureDataDao pictureDataDao;
    private final AlbumDao albumDao;
    private final JWTParser jwtParser;

    public AlbumServiceImpl(PictureMetaDao pictureMetaDao,
                            PictureDataDao pictureDataDao,
                            AlbumDao albumDao,
                            JWTParser jwtParser) {
        this.pictureMetaDao = pictureMetaDao;
        this.pictureDataDao = pictureDataDao;
        this.albumDao = albumDao;
        this.jwtParser = jwtParser;
    }

    @Override
    public Optional<Long> addNewAlbum(String token, long userId, String albumName) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            throw new AuthorizationException("Invalid token for userId: " + userId);
        }
        long id = albumDao.save(new Album(-1, userId, albumName));
        logger.info("Album with id {} was created for user id {}", id, userId);
        return id > 0 ? Optional.of(id) : Optional.empty();
    }

    @Override
    public List<Album> getAlbumsForUserId(String token, long userId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            throw new AuthorizationException("Invalid token for userId: " + userId);
        }
        return albumDao.findAlbumsForUserId(userId);
    }

    @Override
    public List<PictureMeta> getPictureMetaForAlbum(String token, long userId, long albumId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            throw new AuthorizationException("Invalid token for userId: " + userId);
        }
        Optional<Album> albumOptional = albumDao.findById(albumId);
        if (albumOptional.isEmpty() || albumOptional.get().getUserId() != userId) {
            throw new AuthorizationException("Album is missing or not available to user");
        }

        return pictureMetaDao.findPictureMetasForAlbumId(albumId).stream()
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
                }).collect(Collectors.toList());
    }

    @Override
    public boolean rename(String token, long userId, long albumId, String newName) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            throw new AuthorizationException("Invalid token for userId: " + userId);
        }
        Optional<Album> albumOptional = albumDao.findById(albumId);
        if (albumOptional.isEmpty() || albumOptional.get().getUserId() != userId) {
            throw new AuthorizationException("Album is missing or not available to user");
        }

        return albumDao.renameAlbum(albumId, newName);
    }

    @Override
    public boolean delete(String token, long userId, long albumId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            throw new AuthorizationException("Invalid token for userId: " + userId);
        }

        Optional<Album> albumOptional = albumDao.findById(albumId);
        if (albumOptional.isEmpty() || albumOptional.get().getUserId() != userId) {
            throw new AuthorizationException("Album is missing or not available to user");
        }

        if (!albumDao.delete(albumId)) {
            return false;
        }

        return pictureMetaDao.findPictureMetasForAlbumId(albumId).stream()
            .allMatch(meta -> pictureMetaDao.deleteById(meta.getId()) && pictureDataDao.delete(meta.getPath()));
    }
}
