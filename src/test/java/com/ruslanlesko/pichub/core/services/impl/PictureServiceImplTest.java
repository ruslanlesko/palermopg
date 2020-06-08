package com.ruslanlesko.pichub.core.services.impl;

import com.ruslanlesko.pichub.core.dao.PictureDataDao;
import com.ruslanlesko.pichub.core.dao.PictureMetaDao;
import com.ruslanlesko.pichub.core.entity.PictureMeta;
import com.ruslanlesko.pichub.core.entity.PictureResponse;
import com.ruslanlesko.pichub.core.security.JWTParser;
import com.ruslanlesko.pichub.core.services.PictureService;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PictureServiceImplTest {

    @Test
    void testGetPictureData() {
        final String token = "abc";
        final long userId = 42;
        final long pictureId = 69;
        final String path = "path";
        final byte[] data = new byte[]{98, -2, 44, 25, 27};
        final PictureMeta meta = new PictureMeta(pictureId, userId, -1, path, "", null, null, null);

        JWTParser parser = mock(JWTParser.class);
        PictureMetaDao metaDao = mock(PictureMetaDao.class);
        PictureDataDao dataDao = mock(PictureDataDao.class);

        when(parser.validateTokenForUserId(token, userId)).thenReturn(true);
        when(metaDao.find(pictureId)).thenReturn(Future.succeededFuture(Optional.of(meta)));
        when(dataDao.find(path)).thenReturn(Future.succeededFuture(Optional.of(data)));

        PictureService service = new PictureServiceImpl(metaDao, dataDao, null, parser);

        PictureResponse expected = new PictureResponse(Optional.of(data), false, null);
        PictureResponse actual = service.getPictureData(token, null, userId, pictureId).result();

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
        when(dataDao.save(data)).thenReturn(Future.succeededFuture(path));
        when(metaDao.save(any())).thenReturn(Future.succeededFuture(pictureId));

        PictureService service = new PictureServiceImpl(metaDao, dataDao, null, parser);

        Optional<Long> expected = Optional.of(pictureId);
        Optional<Long> actual = service.insertNewPicture(token, userId, Optional.empty(), data).result();

        assertEquals(expected, actual);
    }
}
