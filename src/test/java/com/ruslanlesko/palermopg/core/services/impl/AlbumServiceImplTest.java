package com.ruslanlesko.palermopg.core.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.ruslanlesko.palermopg.core.dao.AlbumDao;
import com.ruslanlesko.palermopg.core.dao.PictureMetaDao;
import com.ruslanlesko.palermopg.core.entity.Album;
import com.ruslanlesko.palermopg.core.exception.AuthorizationException;
import com.ruslanlesko.palermopg.core.security.JWTParser;
import com.ruslanlesko.palermopg.core.services.AlbumService;
import com.ruslanlesko.palermopg.core.services.PictureService;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

public class AlbumServiceImplTest {
    private static final String TOKEN = "abc";
    private static final String ALBUM_NAME = "great pics";
    private static final long USER_ID = 42;
    private static final long USER_ID_2 = 97;
    private static final long ALBUM_ID = 69;
    private static final Album SAMPLE_ALBUM = new Album(ALBUM_ID, USER_ID, ALBUM_NAME, List.of());
    
    @Test
    void testGetAlbumsForUser() {
        JWTParser parser = mock(JWTParser.class);
        PictureMetaDao pictureMetaDao = mock(PictureMetaDao.class);
        AlbumDao albumDao = mock(AlbumDao.class);
        PictureService pictureService = mock(PictureService.class);

        when(parser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(albumDao.findAlbumsForUserId(USER_ID))
                .thenReturn(Future.succeededFuture(List.of(SAMPLE_ALBUM)));

        AlbumService service = new AlbumServiceImpl(pictureMetaDao, albumDao, pictureService, parser);

        List<Album> expected = List.of(SAMPLE_ALBUM);

        service.getAlbumsForUserId(TOKEN, USER_ID)
                .setHandler(response -> assertEquals(expected, response.result()));
    }

    @Test
    void testAddAlbum() {
        JWTParser parser = mock(JWTParser.class);
        PictureMetaDao pictureMetaDao = mock(PictureMetaDao.class);
        AlbumDao albumDao = mock(AlbumDao.class);
        PictureService pictureService = mock(PictureService.class);

        when(parser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(albumDao.save(new Album(-1L, USER_ID, ALBUM_NAME, List.of())))
                .thenReturn(Future.succeededFuture(ALBUM_ID));

        AlbumService albumService = new AlbumServiceImpl(pictureMetaDao, albumDao, pictureService, parser);
        
        long expected = ALBUM_ID;

        albumService.addNewAlbum(TOKEN, USER_ID, ALBUM_NAME)
                .setHandler(response -> assertEquals(expected, response.result()));
    }

    @Test
    void testDeleteAlbumNotAccessible() {
        JWTParser parser = mock(JWTParser.class);
        PictureMetaDao pictureMetaDao = mock(PictureMetaDao.class);
        AlbumDao albumDao = mock(AlbumDao.class);
        PictureService pictureService = mock(PictureService.class);

        when(parser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(albumDao.findById(ALBUM_ID))
                .thenReturn(Future.succeededFuture(Optional.of(new Album(ALBUM_ID, USER_ID_2, ALBUM_NAME, List.of()))));

        AlbumService albumService = new AlbumServiceImpl(pictureMetaDao, albumDao, pictureService, parser);

        AuthorizationException expected = new AuthorizationException("Album is not available to user");

        albumService.delete(TOKEN, USER_ID, ALBUM_ID)
                .setHandler(response -> {
                    assertTrue(response.failed());
                    assertEquals(expected, response.cause());
                });
    }
}