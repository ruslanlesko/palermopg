package com.ruslanlesko.palermopg.core.services.impl;

import com.ruslanlesko.palermopg.core.dao.AlbumDao;
import com.ruslanlesko.palermopg.core.dao.PictureDataDao;
import com.ruslanlesko.palermopg.core.dao.PictureMetaDao;
import com.ruslanlesko.palermopg.core.entity.Album;
import com.ruslanlesko.palermopg.core.entity.PictureMeta;
import com.ruslanlesko.palermopg.core.entity.PictureResponse;
import com.ruslanlesko.palermopg.core.entity.StorageConsumption;
import com.ruslanlesko.palermopg.core.exception.AuthorizationException;
import com.ruslanlesko.palermopg.core.exception.StorageLimitException;
import com.ruslanlesko.palermopg.core.security.JWTParser;
import com.ruslanlesko.palermopg.core.services.PictureService;
import com.ruslanlesko.palermopg.core.services.StorageService;
import io.vertx.core.Future;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PictureServiceTest {
    private static final String TOKEN = "abc";
    private static final long USER_ID = 42;
    private static final long USER_ID_2 = 43;
    private static final long USER_ID_3 = 44;
    private static final long PICTURE_ID = 69;
    private static final long ALBUM_ID = 2;
    private static final String PATH = "path";
    private static final String DATA_PATH = "sample_picture.jpg";
    private static final String DOWNLOAD_CODE = "a9z1c8";
    private static final String ALBUM_DOWNLOAD_CODE = "jkh57f";
    private static final LocalDateTime TIME = LocalDateTime.now();
    private static final StorageConsumption STORAGE_CONSUMPTION = new StorageConsumption(USER_ID, 8, 1024 * 1024 * 1024);
    private static final StorageConsumption STORAGE_CONSUMPTION_LIMITED = new StorageConsumption(USER_ID, 8, 1024 * 1024);

    private static byte[] data;

    @BeforeAll
    static void setup() throws URISyntaxException, IOException {
        data = Files.readAllBytes(Path.of(PictureServiceTest.class.getClassLoader().getResource(DATA_PATH).toURI()));
    }

    @Test
    void testGetPictureData() {
        JWTParser parser = mock(JWTParser.class);
        PictureMetaDao metaDao = mock(PictureMetaDao.class);
        PictureDataDao dataDao = mock(PictureDataDao.class);
        AlbumDao albumDao = mock(AlbumDao.class);
        StorageService storageService = mock(StorageService.class);

        PictureMeta meta = new PictureMeta(PICTURE_ID, USER_ID, ALBUM_ID, -1L, PATH, null, TIME, TIME, TIME, DOWNLOAD_CODE);
        Album album = new Album(ALBUM_ID, USER_ID, "album", List.of(), ALBUM_DOWNLOAD_CODE, false);

        when(parser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(metaDao.find(PICTURE_ID)).thenReturn(Future.succeededFuture(Optional.of(meta)));
        when(dataDao.find(PATH)).thenReturn(Future.succeededFuture(data));
        when(albumDao.findById(ALBUM_ID)).thenReturn(Future.succeededFuture(Optional.of(album)));

        PictureService service = new PictureService(metaDao, dataDao, albumDao, parser, storageService);

        String expectedHash = "W/\"" + TIME.toEpochSecond(ZoneOffset.UTC) + "\"";

        PictureResponse expected = new PictureResponse(data, false, expectedHash);
        service.getPictureData(TOKEN, null, USER_ID, PICTURE_ID, false)
                .onComplete(response -> assertEquals(expected, response.result()));
    }

    @Test
    void testGetPictureDataByDownloadCode() {
        JWTParser parser = mock(JWTParser.class);
        PictureMetaDao metaDao = mock(PictureMetaDao.class);
        PictureDataDao dataDao = mock(PictureDataDao.class);
        AlbumDao albumDao = mock(AlbumDao.class);
        StorageService storageService = mock(StorageService.class);

        PictureMeta meta = new PictureMeta(PICTURE_ID, USER_ID, ALBUM_ID, -1L, PATH, null, TIME, TIME, TIME, DOWNLOAD_CODE);
        Album album = new Album(ALBUM_ID, USER_ID, "album", List.of(), ALBUM_DOWNLOAD_CODE, false);

        when(metaDao.find(PICTURE_ID)).thenReturn(Future.succeededFuture(Optional.of(meta)));
        when(dataDao.find(PATH)).thenReturn(Future.succeededFuture(data));
        when(albumDao.findById(ALBUM_ID)).thenReturn(Future.succeededFuture(Optional.of(album)));

        PictureService service = new PictureService(metaDao, dataDao, albumDao, parser, storageService);

        byte[] expected = data;
        service.downloadPicture(USER_ID, PICTURE_ID, DOWNLOAD_CODE)
                .onComplete(response -> assertEquals(expected, response.result()));
    }

    @Test
    void testGetPictureDataAlbumNotAccessible() {
        JWTParser parser = mock(JWTParser.class);
        PictureMetaDao metaDao = mock(PictureMetaDao.class);
        PictureDataDao dataDao = mock(PictureDataDao.class);
        AlbumDao albumDao = mock(AlbumDao.class);
        StorageService storageService = mock(StorageService.class);

        PictureMeta meta = new PictureMeta(PICTURE_ID, USER_ID_2, ALBUM_ID, -1L, PATH, null, TIME, TIME, TIME, DOWNLOAD_CODE);
        Album album = new Album(ALBUM_ID, USER_ID_2, "album", List.of(USER_ID_3), ALBUM_DOWNLOAD_CODE, false);

        when(parser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(metaDao.find(PICTURE_ID)).thenReturn(Future.succeededFuture(Optional.of(meta)));
        when(dataDao.find(PATH)).thenReturn(Future.succeededFuture(data));
        when(albumDao.findById(ALBUM_ID)).thenReturn(Future.succeededFuture(Optional.of(album)));

        PictureService service = new PictureService(metaDao, dataDao, albumDao, parser, storageService);
        
        service.getPictureData(TOKEN, null, USER_ID, PICTURE_ID, false).onComplete(response -> {
            assertTrue(response.failed());
            assertEquals(new AuthorizationException("Wrong user id"), response.cause());
        });
    }

    @Test
    void testInsertingNewPicture() {
        JWTParser parser = mock(JWTParser.class);
        PictureMetaDao metaDao = mock(PictureMetaDao.class);
        PictureDataDao dataDao = mock(PictureDataDao.class);
        StorageService storageService = mock(StorageService.class);

        when(parser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(dataDao.save(data)).thenReturn(Future.succeededFuture(PATH));
        when(dataDao.save(any())).thenReturn(Future.succeededFuture(PATH + "_optimized"));
        when(metaDao.save(any())).thenReturn(Future.succeededFuture(PICTURE_ID));
        when(storageService.findForUser(TOKEN, USER_ID)).thenReturn(Future.succeededFuture(STORAGE_CONSUMPTION));

        PictureService service = new PictureService(metaDao, dataDao, null, parser, storageService);

        Long expected = PICTURE_ID;
        service.insertNewPicture(TOKEN, USER_ID, Optional.empty(), data)
                .onComplete(response -> assertEquals(expected, response.result()));
    }

    @Test
    void testInsertingNewPictureExceedingLimit() {
        JWTParser parser = mock(JWTParser.class);
        PictureMetaDao metaDao = mock(PictureMetaDao.class);
        PictureDataDao dataDao = mock(PictureDataDao.class);
        StorageService storageService = mock(StorageService.class);

        when(parser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(storageService.findForUser(TOKEN, USER_ID)).thenReturn(Future.succeededFuture(STORAGE_CONSUMPTION_LIMITED));

        PictureService service = new PictureService(metaDao, dataDao, null, parser, storageService);

        service.insertNewPicture(TOKEN, USER_ID, Optional.empty(), data)
                .onComplete(response -> {
                    assertTrue(response.failed());
                    assertEquals(StorageLimitException.class, response.cause().getClass());
                });
    }
}
