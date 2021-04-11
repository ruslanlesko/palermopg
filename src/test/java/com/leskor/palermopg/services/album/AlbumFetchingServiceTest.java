package com.leskor.palermopg.services.album;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.dao.PictureDataDao;
import com.leskor.palermopg.dao.PictureMetaDao;
import com.leskor.palermopg.entity.Album;
import com.leskor.palermopg.entity.PictureMeta;
import com.leskor.palermopg.exception.AuthorizationException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlbumFetchingServiceTest {
    private static final String
            NAME = "Birthday Party",
            DOWNLOAD_CODE = "132",
            PATH = "/pic1.jpg",
            PATH_2 = "/pic2.jpg";

    private static final long
            USER_ID = 25,
            ALBUM_ID = 42,
            PICTURE_ID = 256;

    private static final byte[]
            DATA = new byte[] {0, 9, 25},
            DATA_2 = new byte[] {16, 101};

    private static final Album
            ALBUM = new Album(ALBUM_ID, USER_ID, NAME, List.of(), DOWNLOAD_CODE, true),
            ALBUM_FOR_SHARED_USER = new Album(ALBUM_ID, USER_ID - 1, NAME, List.of(USER_ID), DOWNLOAD_CODE, true),
            ALBUM_WITH_SHARED_USERS_NULL = new Album(ALBUM_ID, USER_ID - 1, NAME, null, DOWNLOAD_CODE, true),
            ALBUM_NO_DOWNLOAD_CODE = new Album(ALBUM_ID, USER_ID, NAME, List.of(), null, true),
            ALBUM_2 = new Album(ALBUM_ID + 1, USER_ID, NAME, List.of(), DOWNLOAD_CODE, false),
            ALBUM_2_NO_DOWNLOAD_CODE = new Album(ALBUM_ID + 1, USER_ID, NAME, List.of(), null, false),
            ALBUM_3 = new Album(ALBUM_ID + 2, USER_ID, NAME, List.of(), DOWNLOAD_CODE, false);

    private static final PictureMeta
            PICTURE_META = new PictureMeta(PICTURE_ID, USER_ID, ALBUM_ID, -1, PATH, "", now(), now(), now(), DOWNLOAD_CODE),
            PICTURE_META_2 = new PictureMeta(PICTURE_ID + 1, USER_ID, ALBUM_ID, -1, PATH_2, "", now().plusDays(2), now(), now(), DOWNLOAD_CODE),
            PICTURE_META_2_NO_DOWNLOAD_CODE = new PictureMeta(PICTURE_ID + 1, USER_ID, ALBUM_ID, -1, "", "", now().plusDays(2), now(), now(), ""),
            PICTURE_META_3 = new PictureMeta(PICTURE_ID + 2, USER_ID, ALBUM_ID, -1, "", "", now(), now().plusDays(2), now(), DOWNLOAD_CODE),
            PICTURE_META_3_NO_DOWNLOAD_CODE = new PictureMeta(PICTURE_ID + 2, USER_ID, ALBUM_ID, -1, "", "", now(), now().plusDays(2), now(), "");

    private AlbumDao albumDao;
    private PictureMetaDao pictureMetaDao;
    private PictureDataDao pictureDataDao;
    private AlbumFetchingService albumFetchingService;

    @BeforeEach
    void setUp() {
        albumDao = mock(AlbumDao.class);
        pictureMetaDao = mock(PictureMetaDao.class);
        pictureDataDao = mock(PictureDataDao.class);
        albumFetchingService = new AlbumFetchingService(albumDao, pictureMetaDao, pictureDataDao);
    }

    @Test
    void getAlbumsForUserId() {
        when(albumDao.findAlbumsForUserId(USER_ID)).thenReturn(succeededFuture(List.of(ALBUM)));

        albumFetchingService.getAlbumsForUserId(USER_ID)
                .onComplete(res -> assertEquals(List.of(ALBUM), res.result()));
    }

    @ParameterizedTest
    @MethodSource("albumsInDifferentOrder")
    void getAlbumsForUserIdSortedProperly(List<Album> albums) {
        when(albumDao.findAlbumsForUserId(USER_ID)).thenReturn(succeededFuture(albums));

        albumFetchingService.getAlbumsForUserId(USER_ID)
                .onComplete(res -> assertEquals(List.of(ALBUM_2, ALBUM), res.result()));
    }

    @Test
    void getAlbumsForUserIdWithoutDownloadCode() {
        when(albumDao.findAlbumsForUserId(USER_ID))
                .thenReturn(succeededFuture(List.of(ALBUM_NO_DOWNLOAD_CODE, ALBUM_2_NO_DOWNLOAD_CODE, ALBUM_3)))
                .thenReturn(succeededFuture(List.of(ALBUM, ALBUM_2, ALBUM_3)));

        when(albumDao.setDownloadCode(eq(ALBUM_ID), anyString())).thenReturn(succeededFuture());
        when(albumDao.setDownloadCode(eq(ALBUM_ID + 1), anyString())).thenReturn(succeededFuture());

        albumFetchingService.getAlbumsForUserId(USER_ID)
                .onComplete(res -> assertEquals(List.of(ALBUM_3, ALBUM_2, ALBUM), res.result()));
    }

    @Test
    void returnsErrorWhenInvalidUserIdProvided() {
        albumFetchingService.getAlbumsForUserId(-42)
                .onComplete(resp -> {
                    assertNull(resp.result());
                    assertFalse(resp.succeeded());
                    assertTrue(resp.failed());
                    assertEquals(IllegalArgumentException.class, resp.cause().getClass());
                });
    }

    @ParameterizedTest
    @MethodSource("albumsWithUserAccess")
    void getPictureMetaForAlbum(Album album) {
        when(albumDao.findById(ALBUM_ID)).thenReturn(succeededFuture(Optional.of(album)));

        when(pictureMetaDao.findForAlbumId(ALBUM_ID))
                .thenReturn(succeededFuture(List.of(PICTURE_META, PICTURE_META_2_NO_DOWNLOAD_CODE, PICTURE_META_3_NO_DOWNLOAD_CODE)))
                .thenReturn(succeededFuture(List.of(PICTURE_META, PICTURE_META_2, PICTURE_META_3)));

        when(pictureMetaDao.setDownloadCode(eq(PICTURE_ID + 1), anyString())).thenReturn(succeededFuture());
        when(pictureMetaDao.setDownloadCode(eq(PICTURE_ID + 2), anyString())).thenReturn(succeededFuture());

        albumFetchingService.getPictureMetaForAlbum(USER_ID, ALBUM_ID)
                .onComplete(res -> assertEquals(List.of(PICTURE_META, PICTURE_META_3, PICTURE_META_2), res.result()));
    }

    @ParameterizedTest
    @MethodSource("albumsWithoutUserAccess")
    void returnsErrorWhenAlbumIsNotAccessible(Album album) {
        when(albumDao.findById(ALBUM_ID)).thenReturn(succeededFuture(Optional.of(album)));

        albumFetchingService.getPictureMetaForAlbum(USER_ID + 1, ALBUM_ID)
                .onComplete(resp -> {
                    assertNull(resp.result());
                    assertFalse(resp.succeeded());
                    assertTrue(resp.failed());
                    assertEquals(AuthorizationException.class, resp.cause().getClass());
                });

        albumFetchingService.download(USER_ID + 1, ALBUM_ID, DOWNLOAD_CODE)
                .onComplete(resp -> {
                    assertNull(resp.result());
                    assertFalse(resp.succeeded());
                    assertTrue(resp.failed());
                    assertEquals(AuthorizationException.class, resp.cause().getClass());
                });
    }

    @Test
    void download() {
        when(albumDao.findById(ALBUM_ID)).thenReturn(succeededFuture(Optional.of(ALBUM)));
        when(pictureMetaDao.findForAlbumId(ALBUM_ID)).thenReturn(succeededFuture(List.of(PICTURE_META, PICTURE_META_2)));
        when(pictureDataDao.find(PATH)).thenReturn(succeededFuture(DATA));
        when(pictureDataDao.find(PATH_2)).thenReturn(succeededFuture(DATA_2));

        albumFetchingService.download(USER_ID, ALBUM_ID, DOWNLOAD_CODE)
                .onComplete(response -> assertTrue(response.result().length > 0));
    }

    @Test
    void returnsErrorWhenDownloadCodeIsNotValid() {
        when(albumDao.findById(ALBUM_ID)).thenReturn(succeededFuture(Optional.of(ALBUM)));

        albumFetchingService.download(USER_ID, ALBUM_ID, DOWNLOAD_CODE + "z")
                .onComplete(resp -> {
                    assertNull(resp.result());
                    assertFalse(resp.succeeded());
                    assertTrue(resp.failed());
                    assertEquals(AuthorizationException.class, resp.cause().getClass());
                });
    }

    private static Stream<Arguments> albumsInDifferentOrder() {
        return Stream.of(
                Arguments.of(List.of(ALBUM, ALBUM_2)),
                Arguments.of(List.of(ALBUM_2, ALBUM))
        );
    }

    private static Stream<Arguments> albumsWithUserAccess() {
        return Stream.of(Arguments.of(ALBUM), Arguments.of(ALBUM_FOR_SHARED_USER));
    }

    private static Stream<Arguments> albumsWithoutUserAccess() {
        return Stream.of(Arguments.of(ALBUM), Arguments.of(ALBUM_WITH_SHARED_USERS_NULL));
    }
}