package com.leskor.palermopg.services;

import com.leskor.palermopg.dao.LimitsDao;
import com.leskor.palermopg.dao.PictureDataDao;
import com.leskor.palermopg.dao.PictureMetaDao;
import com.leskor.palermopg.entity.PictureMeta;
import com.leskor.palermopg.entity.StorageConsumption;
import com.leskor.palermopg.security.JWTParser;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

public class StorageServiceTest {
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
    private static final long NEW_LIMIT = 4L * 1024L * 1024L * 1024L;

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
        LimitsDao limitsDao = mock(LimitsDao.class);

        when(jwtParser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(pictureMetaDao.findPictureMetasForUserId(USER_ID)).thenReturn(Future.succeededFuture(PICTURE_METAS));
        when(pictureDataDao.find(PATH_1)).thenReturn(Future.succeededFuture(new byte[]{0, 1, 2}));
        when(pictureDataDao.find(PATH_2)).thenReturn(Future.succeededFuture(new byte[]{0}));
        when(pictureDataDao.find(PATH_3)).thenReturn(Future.succeededFuture(new byte[]{0, 1, 2, 3, 4}));
        when(limitsDao.getLimitForUser(USER_ID)).thenReturn(Future.succeededFuture(Optional.empty()));

        StorageService service = new StorageService(pictureMetaDao, pictureDataDao, limitsDao, jwtParser);

        var expectedConsumption = new StorageConsumption(USER_ID, CONSUMPTION_SIZE, CONSUMPTION_LIMIT);

        service.findForUser(TOKEN, USER_ID)
            .onComplete(response -> assertEquals(expectedConsumption, response.result()));
    }

    @Test
    void testSettingNewLimitForUser() {
        JWTParser jwtParser = mock(JWTParser.class);
        PictureMetaDao pictureMetaDao = mock(PictureMetaDao.class);
        PictureDataDao pictureDataDao = mock(PictureDataDao.class);
        LimitsDao limitsDao = mock(LimitsDao.class);

        when(jwtParser.isAdmin(TOKEN)).thenReturn(true);
        when(limitsDao.setLimitForUser(USER_ID, NEW_LIMIT)).thenReturn(Future.succeededFuture());

        StorageService service = new StorageService(pictureMetaDao, pictureDataDao, limitsDao, jwtParser);

        service.setLimitForUser(TOKEN, USER_ID, NEW_LIMIT)
                .onComplete(response -> {
                    assertFalse(response.failed());
                    verify(limitsDao, times(1)).setLimitForUser(USER_ID, NEW_LIMIT);
                });
    }
}