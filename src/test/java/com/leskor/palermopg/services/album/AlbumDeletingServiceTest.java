package com.leskor.palermopg.services.album;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.dao.PictureMetaDao;
import com.leskor.palermopg.entity.Album;
import com.leskor.palermopg.entity.PictureMeta;
import com.leskor.palermopg.exception.AuthorizationException;
import com.leskor.palermopg.security.JWTParser;
import com.leskor.palermopg.services.PictureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static io.vertx.core.Future.succeededFuture;
import static java.time.LocalDateTime.now;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlbumDeletingServiceTest {
    private static final String
            NAME = "Birthday Party",
            PATH = "/pic1.jpg",
            PATH_2 = "/pic2.jpg",
            TOKEN = "Bearer abcxyz";

    private static final long
            USER_ID = 25,
            ALBUM_ID = 42,
            ALBUM_ID_3 = 43,
            PICTURE_ID = 256;

    private static final Album
            ALBUM = Album.create(ALBUM_ID, USER_ID, NAME, List.of(), true),
            ALBUM_FOR_SHARED_USER = Album.create(ALBUM_ID, USER_ID - 1, NAME, List.of(USER_ID), true),
            ALBUM_2 = Album.create(ALBUM_ID, USER_ID - 1, NAME, List.of(), false),
            ALBUM_3 = Album.create(ALBUM_ID_3, USER_ID - 1, NAME, List.of(), false);

    private static final PictureMeta
            PICTURE_META = new PictureMeta(PICTURE_ID, USER_ID, ALBUM_ID, -1, PATH, "", now(), now(), now()),
            PICTURE_META_2 = new PictureMeta(PICTURE_ID + 1, USER_ID, ALBUM_ID, -1, PATH_2, "", now().plusDays(2), now(), now());

    private JWTParser jwtParser;
    private PictureService pictureService;
    private AlbumDao albumDao;
    private PictureMetaDao pictureMetaDao;
    private AlbumFetchingService albumFetchingService;
    private AlbumDeletingService albumDeletingService;

    @BeforeEach
    void setUp() {
        jwtParser = mock(JWTParser.class);
        pictureService = mock(PictureService.class);
        albumDao = mock(AlbumDao.class);
        pictureMetaDao = mock(PictureMetaDao.class);
        albumFetchingService = mock(AlbumFetchingService.class);
        albumDeletingService = new AlbumDeletingService(jwtParser, pictureService, albumDao, pictureMetaDao, albumFetchingService);
    }

    @Test
    void delete() {
        when(albumDao.findById(ALBUM_ID)).thenReturn(succeededFuture(Optional.of(ALBUM)));
        when(albumDao.delete(ALBUM_ID)).thenReturn(succeededFuture());
        when(pictureMetaDao.findForAlbumId(ALBUM_ID)).thenReturn(succeededFuture(List.of(PICTURE_META, PICTURE_META_2)));
        when(pictureService.deletePicture(USER_ID, PICTURE_ID)).thenReturn(succeededFuture());
        when(pictureService.deletePicture(USER_ID, PICTURE_ID + 1)).thenReturn(succeededFuture());

        albumDeletingService.delete(USER_ID, ALBUM_ID)
                .onComplete(res -> assertTrue(res.succeeded()));
    }

    @Test
    void deleteAll() {
        when(jwtParser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(false);
        when(jwtParser.isAdmin(TOKEN)).thenReturn(true);
        when(albumFetchingService.getAlbumsForUserId(USER_ID)).thenReturn(succeededFuture(List.of(ALBUM, ALBUM_FOR_SHARED_USER, ALBUM_3)));
        when(albumDao.delete(ALBUM_ID)).thenReturn(succeededFuture());
        when(albumDao.delete(ALBUM_ID_3)).thenReturn(succeededFuture());
        when(pictureMetaDao.findForAlbumId(ALBUM_ID)).thenReturn(succeededFuture(List.of(PICTURE_META, PICTURE_META_2)));
        when(pictureMetaDao.findForAlbumId(ALBUM_ID)).thenReturn(succeededFuture(List.of()));
        when(pictureService.deletePicture(USER_ID, PICTURE_ID)).thenReturn(succeededFuture());
        when(pictureService.deletePicture(USER_ID, PICTURE_ID + 1)).thenReturn(succeededFuture());

        albumDeletingService.deleteAll(TOKEN, USER_ID)
                .onComplete(res -> assertTrue(res.succeeded()));
    }

    @ParameterizedTest
    @MethodSource("albumsNotOwnedByUser")
    void returnsErrorForNotOwnedAlbums(Album album) {
        when(albumDao.findById(ALBUM_ID)).thenReturn(succeededFuture(Optional.of(album)));

        albumDeletingService.delete(USER_ID, ALBUM_ID)
                .onComplete(resp -> {
                    assertFalse(resp.succeeded());
                    assertTrue(resp.failed());
                    assertEquals(AuthorizationException.class, resp.cause().getClass());
                });
    }

    private static Stream<Arguments> albumsNotOwnedByUser() {
        return Stream.of(
                Arguments.of(ALBUM_FOR_SHARED_USER),
                Arguments.of(ALBUM_2)
        );
    }
}