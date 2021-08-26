package com.leskor.palermopg.services;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.dao.PictureDataDao;
import com.leskor.palermopg.dao.PictureMetaDao;
import com.leskor.palermopg.entity.Album;
import com.leskor.palermopg.entity.PictureMeta;
import com.leskor.palermopg.entity.PictureResponse;
import com.leskor.palermopg.entity.StorageConsumption;
import com.leskor.palermopg.exception.AuthorizationException;
import com.leskor.palermopg.exception.StorageLimitException;
import com.leskor.palermopg.security.JWTParser;
import io.vertx.core.Future;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
    private static final LocalDateTime TIME = LocalDateTime.now();
    private static final StorageConsumption STORAGE_CONSUMPTION = new StorageConsumption(USER_ID, 8, 1024 * 1024 * 1024);
    private static final StorageConsumption STORAGE_CONSUMPTION_LIMITED = new StorageConsumption(USER_ID, 8, 1024 * 1024);
    public static final byte[] OPTIMIZED_DATA = {42, 69};

    private static byte[] data;

    private JWTParser parser;
    private PictureMetaDao metaDao;
    private PictureDataDao dataDao;
    private AlbumDao albumDao;
    private StorageService storageService;
    private PictureManipulationService pmService;

    @BeforeAll
    static void setup() throws URISyntaxException, IOException {
        data = Files.readAllBytes(Path.of(PictureServiceTest.class.getClassLoader().getResource(DATA_PATH).toURI()));
    }

    @BeforeEach
    void prepareMocks() {
        this.parser = mock(JWTParser.class);
        this.metaDao = mock(PictureMetaDao.class);
        this.dataDao = mock(PictureDataDao.class);
        this.albumDao = mock(AlbumDao.class);
        this.storageService = mock(StorageService.class);
        this.pmService = mock(PictureManipulationService.class);
    }

    @Test
    void testGetPictureData() {
        PictureMeta meta = new PictureMeta(PICTURE_ID, USER_ID, ALBUM_ID, -1L, PATH, null, TIME, TIME, TIME);
        Album album = new Album(ALBUM_ID, USER_ID, "album", List.of(), false);

        when(parser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(metaDao.find(PICTURE_ID)).thenReturn(Future.succeededFuture(Optional.of(meta)));
        when(dataDao.find(PATH)).thenReturn(Future.succeededFuture(data));
        when(albumDao.findById(ALBUM_ID)).thenReturn(Future.succeededFuture(Optional.of(album)));

        PictureService service = new PictureService(metaDao, dataDao, albumDao, parser, storageService, pmService);

        String expectedHash = "W/\"" + PICTURE_ID + TIME.toEpochSecond(ZoneOffset.UTC) + "\"";

        PictureResponse expected = new PictureResponse(data, false, expectedHash);
        service.getPictureData(TOKEN, null, USER_ID, PICTURE_ID, false)
                .onComplete(response -> assertEquals(expected, response.result()));
    }

    @Test
    void testDownloadPictureData() {
        PictureMeta meta = new PictureMeta(PICTURE_ID, USER_ID, ALBUM_ID, -1L, PATH, null, TIME, TIME, TIME);
        Album album = new Album(ALBUM_ID, USER_ID, "album", List.of(), false);

        when(parser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(metaDao.find(PICTURE_ID)).thenReturn(Future.succeededFuture(Optional.of(meta)));
        when(dataDao.find(PATH)).thenReturn(Future.succeededFuture(data));
        when(albumDao.findById(ALBUM_ID)).thenReturn(Future.succeededFuture(Optional.of(album)));

        PictureService service = new PictureService(metaDao, dataDao, albumDao, parser, storageService, pmService);

        byte[] expected = data;
        service.downloadPicture(TOKEN, USER_ID, PICTURE_ID)
                .onComplete(response -> assertEquals(expected, response.result()));
    }

    @Test
    void testGetPictureDataAlbumNotAccessible() {
        PictureMeta meta = new PictureMeta(PICTURE_ID, USER_ID_2, ALBUM_ID, -1L, PATH, null, TIME, TIME, TIME);
        Album album = new Album(ALBUM_ID, USER_ID_2, "album", List.of(USER_ID_3), false);

        when(parser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(metaDao.find(PICTURE_ID)).thenReturn(Future.succeededFuture(Optional.of(meta)));
        when(dataDao.find(PATH)).thenReturn(Future.succeededFuture(data));
        when(albumDao.findById(ALBUM_ID)).thenReturn(Future.succeededFuture(Optional.of(album)));

        PictureService service = new PictureService(metaDao, dataDao, albumDao, parser, storageService, pmService);
        
        service.getPictureData(TOKEN, null, USER_ID, PICTURE_ID, false).onComplete(response -> {
            assertTrue(response.failed());
            assertEquals(new AuthorizationException("Wrong user id"), response.cause());
        });
    }

    @Test
    void testInsertingNewPicture() {
        when(dataDao.save(data)).thenReturn(Future.succeededFuture(PATH));
        when(dataDao.save(OPTIMIZED_DATA)).thenReturn(Future.succeededFuture(PATH + "_optimized"));
        when(metaDao.save(any())).thenReturn(Future.succeededFuture(PICTURE_ID));
        when(storageService.findForUser(TOKEN, USER_ID)).thenReturn(Future.succeededFuture(STORAGE_CONSUMPTION));
        when(pmService.rotateToCorrectOrientation(data)).thenReturn(Future.succeededFuture(data));
        when(pmService.convertToOptimized(data)).thenReturn(Future.succeededFuture(OPTIMIZED_DATA));

        PictureService service = new PictureService(metaDao, dataDao, null, parser, storageService, pmService);

        Long expected = PICTURE_ID;
        service.insertNewPicture(TOKEN, USER_ID, -1L, data)
                .onComplete(response -> assertEquals(expected, response.result()));
    }

    @Test
    void testInsertingNewPictureExceedingLimit() {
        when(storageService.findForUser(TOKEN, USER_ID)).thenReturn(Future.succeededFuture(STORAGE_CONSUMPTION_LIMITED));
        when(pmService.rotateToCorrectOrientation(data)).thenReturn(Future.succeededFuture(data));
        when(pmService.convertToOptimized(data)).thenReturn(Future.succeededFuture(OPTIMIZED_DATA));

        PictureService service = new PictureService(metaDao, dataDao, null, parser, storageService, pmService);

        service.insertNewPicture(TOKEN, USER_ID, -1L, data)
                .onComplete(response -> {
                    assertTrue(response.failed());
                    assertEquals(StorageLimitException.class, response.cause().getClass());
                });
    }
}
