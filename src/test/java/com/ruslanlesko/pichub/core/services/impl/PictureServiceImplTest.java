package com.ruslanlesko.pichub.core.services.impl;

import com.ruslanlesko.pichub.core.dao.PictureDataDao;
import com.ruslanlesko.pichub.core.dao.PictureMetaDao;
import com.ruslanlesko.pichub.core.entity.PictureMeta;
import com.ruslanlesko.pichub.core.security.JWTParser;
import com.ruslanlesko.pichub.core.services.PictureService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

class PictureServiceImplTest {

    @Test
    void testGetPictureData() {
        final String token = "abc";
        final long userId = 42;
        final long pictureId = 69;
        final String path = "path";
        final byte[] data = new byte[]{98, -2, 44, 25, 27};
        final PictureMeta meta = new PictureMeta(pictureId, userId, -1, path, null, null);

        JWTParser parser = mock(JWTParser.class);
        PictureMetaDao metaDao = mock(PictureMetaDao.class);
        PictureDataDao dataDao = mock(PictureDataDao.class);

        when(parser.validateTokenForUserId(token, userId)).thenReturn(true);
        when(metaDao.find(pictureId)).thenReturn(Optional.of(meta));
        when(dataDao.find(path)).thenReturn(Optional.of(data));

        PictureService service = new PictureServiceImpl(metaDao, dataDao, null, parser);

        Optional<byte[]> expected = Optional.of(data);
        Optional<byte[]> actual = service.getPictureData(token, userId, pictureId);

        assertEquals(expected, actual);
    }

    @Test
    void testPictureIdsForUserId() {
        final String token = "abc";
        final long userId = 42;
        final PictureMeta metaA = new PictureMeta(69, userId, -1, null,
                    LocalDateTime.of(2019, 10, 4, 12, 4),
                    LocalDateTime.of(2019, 10, 4, 10, 22)
                ),
                metaB = new PictureMeta(27, userId, -1, null,
                    LocalDateTime.of(2019, 10, 5, 12, 4),
                    LocalDateTime.of(2019, 10, 4, 10, 26)
                ),
                metaC = new PictureMeta(25, userId, -1, null,
                    LocalDateTime.of(2019, 10, 5, 12, 4),
                    LocalDateTime.of(2019, 10, 4, 10, 22)
                );

        JWTParser parser = mock(JWTParser.class);
        PictureMetaDao metaDao = mock(PictureMetaDao.class);
        PictureDataDao dataDao = mock(PictureDataDao.class);

        when(parser.validateTokenForUserId(token, userId)).thenReturn(true);
        when(metaDao.findPictureMetasForUser(userId)).thenReturn(List.of(metaA, metaB, metaC));

        PictureService service = new PictureServiceImpl(metaDao, dataDao, null, parser);

        List<Long> expected = List.of(27L, 25L, 69L);
        List<Long> actual = service.getPictureIdsForUserId(token, userId);

        assertEquals(expected, actual);
    }

    void testInsertingNewPicture() {
        final String token = "abc";
        final long userId = 42;
        final long pictureId = 69;
        final String path = "path";
        final byte[] data = new byte[]{21, 5};

        JWTParser parser = mock(JWTParser.class);
        PictureMetaDao metaDao = mock(PictureMetaDao.class);
        PictureDataDao dataDao = mock(PictureDataDao.class);

        when(parser.validateTokenForUserId(token, userId)).thenReturn(true);
        when(dataDao.save(data)).thenReturn(path);
        when(metaDao.save(any())).thenReturn(pictureId);

        PictureService service = new PictureServiceImpl(metaDao, dataDao, null, parser);

        Optional<Long> expected = Optional.of(pictureId);
        Optional<Long> actual = service.insertNewPicture(token, userId, Optional.empty(), data);

        assertEquals(expected, actual);
    }
}
