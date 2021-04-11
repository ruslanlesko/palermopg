package com.leskor.palermopg.services.album;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.dao.PictureMetaDao;
import com.leskor.palermopg.entity.Album;
import com.leskor.palermopg.entity.PictureMeta;
import com.leskor.palermopg.exception.AuthorizationException;
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
            DOWNLOAD_CODE = "132",
            PATH = "/pic1.jpg",
            PATH_2 = "/pic2.jpg";

    private static final long
            USER_ID = 25,
            ALBUM_ID = 42,
            PICTURE_ID = 256;

    private static final Album
            ALBUM = new Album(ALBUM_ID, USER_ID, NAME, List.of(), DOWNLOAD_CODE, true),
            ALBUM_FOR_SHARED_USER = new Album(ALBUM_ID, USER_ID - 1, NAME, List.of(USER_ID), DOWNLOAD_CODE, true),
            ALBUM_2 = new Album(ALBUM_ID, USER_ID - 1, NAME, List.of(), DOWNLOAD_CODE, false);

    private static final PictureMeta
            PICTURE_META = new PictureMeta(PICTURE_ID, USER_ID, ALBUM_ID, -1, PATH, "", now(), now(), now(), DOWNLOAD_CODE),
            PICTURE_META_2 = new PictureMeta(PICTURE_ID + 1, USER_ID, ALBUM_ID, -1, PATH_2, "", now().plusDays(2), now(), now(), DOWNLOAD_CODE);

    private PictureService pictureService;
    private AlbumDao albumDao;
    private PictureMetaDao pictureMetaDao;
    private AlbumDeletingService albumDeletingService;

    @BeforeEach
    void setUp() {
        pictureService = mock(PictureService.class);
        albumDao = mock(AlbumDao.class);
        pictureMetaDao = mock(PictureMetaDao.class);
        albumDeletingService = new AlbumDeletingService(pictureService, albumDao, pictureMetaDao);
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