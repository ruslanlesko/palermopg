package com.ruslanlesko.palermopg.core.services.impl;

import com.ruslanlesko.palermopg.core.dao.AlbumDao;
import com.ruslanlesko.palermopg.core.dao.PictureDataDao;
import com.ruslanlesko.palermopg.core.dao.PictureMetaDao;
import com.ruslanlesko.palermopg.core.entity.Album;
import com.ruslanlesko.palermopg.core.entity.PictureMeta;
import com.ruslanlesko.palermopg.core.entity.PictureResponse;
import com.ruslanlesko.palermopg.core.exception.AuthorizationException;
import com.ruslanlesko.palermopg.core.security.JWTParser;
import com.ruslanlesko.palermopg.core.services.PictureService;
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

class PictureServiceImplTest {
    private static final String TOKEN = "abc";
    private static final long USER_ID = 42;
    private static final long USER_ID_2 = 43;
    private static final long USER_ID_3 = 44;
    private static final long PICTURE_ID = 69;
    private static final long ALBUM_ID = 2;
    private static final String PATH = "path";
    private static final String DATA_PATH = "sample_picture.jpg";
    private static final LocalDateTime TIME = LocalDateTime.now();

    private static byte[] data;

    @BeforeAll
    static void setup() throws URISyntaxException, IOException {
        data = Files.readAllBytes(Path.of(PictureServiceImplTest.class.getClassLoader().getResource(DATA_PATH).toURI()));
    }

    @Test
    void testGetPictureData() {
        JWTParser parser = mock(JWTParser.class);
        PictureMetaDao metaDao = mock(PictureMetaDao.class);
        PictureDataDao dataDao = mock(PictureDataDao.class);
        AlbumDao albumDao = mock(AlbumDao.class);

        PictureMeta meta = new PictureMeta(PICTURE_ID, USER_ID, ALBUM_ID, PATH, null, TIME, TIME, TIME);
        Album album = new Album(ALBUM_ID, USER_ID, "album", List.of());

        when(parser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(metaDao.find(PICTURE_ID)).thenReturn(Future.succeededFuture(Optional.of(meta)));
        when(dataDao.find(PATH)).thenReturn(Future.succeededFuture(data));
        when(albumDao.findById(ALBUM_ID)).thenReturn(Future.succeededFuture(Optional.of(album)));

        PictureService service = new PictureServiceImpl(metaDao, dataDao, albumDao, parser);

        String expectedHash = "W/\"" + TIME.toEpochSecond(ZoneOffset.UTC) + "\"";

        PictureResponse expected = new PictureResponse(data, false, expectedHash);
        service.getPictureData(TOKEN, null, USER_ID, PICTURE_ID)
                .setHandler(response -> assertEquals(expected, response.result()));
    }

    @Test
    void testGetPictureDataAlbumNotAccessible() {
        JWTParser parser = mock(JWTParser.class);
        PictureMetaDao metaDao = mock(PictureMetaDao.class);
        PictureDataDao dataDao = mock(PictureDataDao.class);
        AlbumDao albumDao = mock(AlbumDao.class);

        PictureMeta meta = new PictureMeta(PICTURE_ID, USER_ID_2, ALBUM_ID, PATH, null, TIME, TIME, TIME);
        Album album = new Album(ALBUM_ID, USER_ID_2, "album", List.of(USER_ID_3));

        when(parser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(metaDao.find(PICTURE_ID)).thenReturn(Future.succeededFuture(Optional.of(meta)));
        when(dataDao.find(PATH)).thenReturn(Future.succeededFuture(data));
        when(albumDao.findById(ALBUM_ID)).thenReturn(Future.succeededFuture(Optional.of(album)));

        PictureService service = new PictureServiceImpl(metaDao, dataDao, albumDao, parser);
        
        service.getPictureData(TOKEN, null, USER_ID, PICTURE_ID).setHandler(response -> {
            assertTrue(response.failed());
            assertEquals(new AuthorizationException("Wrong user id"), response.cause());
        });
    }

    @Test
    void testInsertingNewPicture() {
        JWTParser parser = mock(JWTParser.class);
        PictureMetaDao metaDao = mock(PictureMetaDao.class);
        PictureDataDao dataDao = mock(PictureDataDao.class);

        when(parser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(dataDao.save(data)).thenReturn(Future.succeededFuture(PATH));
        when(dataDao.save(any())).thenReturn(Future.succeededFuture(PATH + "_optimized"));
        when(metaDao.save(any())).thenReturn(Future.succeededFuture(PICTURE_ID));

        PictureService service = new PictureServiceImpl(metaDao, dataDao, null, parser);

        Long expected = PICTURE_ID;
        service.insertNewPicture(TOKEN, USER_ID, Optional.empty(), data)
                .setHandler(response -> assertEquals(expected, response.result()));
    }
}
