package com.ruslanlesko.palermopg.core.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.ruslanlesko.palermopg.core.dao.PictureDataDao;
import com.ruslanlesko.palermopg.core.dao.PictureMetaDao;
import com.ruslanlesko.palermopg.core.entity.StorageConsuption;
import com.ruslanlesko.palermopg.core.entity.PictureMeta;
import com.ruslanlesko.palermopg.core.security.JWTParser;
import com.ruslanlesko.palermopg.core.services.StorageService;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

public class StorageServiceImplTest {
    private static final String 
        TOKEN = "Bearer abcxyz",
        PATH_1 = "path/1",
        PATH_2 = "path/2",
        PATH_3 = "path/3";
    private static final long USER_ID = 42;
    private static final long PICTURE_ID = 16;
    private static final long PICTURE_ID_2 = 17;
    private static final long CONSUMPTION_SIZE = 1024;
    private static final long CONSUMPTION_LIMIT = 2L * 1024L * 1024L * 1024L;

    private static final List<PictureMeta> PICTURE_METAS = List.of(
        new PictureMeta(15, USER_ID, 21, 1015L, "", "", null, null, null),
        new PictureMeta(PICTURE_ID, USER_ID, 21, -1L, PATH_1, PATH_2, null, null, null),
        new PictureMeta(PICTURE_ID_2, USER_ID, 21, -1L, PATH_3, null, null, null, null)
    );

    @Test
    void testSimpleUserConsumption() {
        JWTParser jwtParser = mock(JWTParser.class);
        PictureMetaDao pictureMetaDao = mock(PictureMetaDao.class);
        PictureDataDao pictureDataDao = mock(PictureDataDao.class);

        when(jwtParser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(pictureMetaDao.findPictureMetasForUserId(USER_ID)).thenReturn(Future.succeededFuture(PICTURE_METAS));
        when(pictureDataDao.find(PATH_1)).thenReturn(Future.succeededFuture(new byte[]{0, 1, 2}));
        when(pictureDataDao.find(PATH_2)).thenReturn(Future.succeededFuture(new byte[]{0}));
        when(pictureDataDao.find(PATH_3)).thenReturn(Future.succeededFuture(new byte[]{0, 1, 2, 3, 4}));
        when(pictureMetaDao.setSize(PICTURE_ID, 4)).thenReturn(Future.succeededFuture());
        when(pictureMetaDao.setSize(PICTURE_ID_2, 5)).thenReturn(Future.succeededFuture());

        StorageService service = new StorageServiceImpl(pictureMetaDao, pictureDataDao, jwtParser);

        var expectedConsumption = new StorageConsuption(CONSUMPTION_SIZE, CONSUMPTION_LIMIT);

        service.findForUser(TOKEN, USER_ID)
            .setHandler(response -> assertEquals(expectedConsumption, response.result()));
    }
}