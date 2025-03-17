package com.leskor.palermopg.services.album;

import static io.vertx.core.Future.succeededFuture;
import static java.time.LocalDateTime.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.dao.PictureDataDao;
import com.leskor.palermopg.dao.PictureMetaDao;
import com.leskor.palermopg.entity.Album;
import com.leskor.palermopg.entity.PictureMeta;
import com.leskor.palermopg.exception.AuthorizationException;
import com.leskor.palermopg.security.JWTParser;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AlbumFetchingServiceTest {
    private static final String
            TOKEN = "abc",
            NAME = "Birthday Party",
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
            ALBUM = Album.create(ALBUM_ID, USER_ID, NAME, List.of(), true),
            ALBUM_FOR_SHARED_USER =
                    Album.create(ALBUM_ID, USER_ID - 1, NAME, List.of(USER_ID), true),
            ALBUM_WITH_SHARED_USERS_NULL = Album.create(ALBUM_ID, USER_ID - 1, NAME, null, true),
            ALBUM_2 = Album.create(ALBUM_ID + 1, USER_ID, NAME, List.of(), false);

    private static final PictureMeta
            PICTURE_META =
            new PictureMeta(PICTURE_ID, USER_ID, ALBUM_ID, -1, PATH, "", now(), now(), now()),
            PICTURE_META_2 = new PictureMeta(PICTURE_ID + 1, USER_ID, ALBUM_ID, -1, PATH_2, "",
                    now().plusDays(2), now(), now()),
            PICTURE_META_3 = new PictureMeta(PICTURE_ID + 2, USER_ID, ALBUM_ID, -1, "", "", now(),
                    now().plusDays(2), now());

    private JWTParser jwtParser;
    private AlbumDao albumDao;
    private PictureMetaDao pictureMetaDao;
    private PictureDataDao pictureDataDao;
    private AlbumFetchingService albumFetchingService;

    @BeforeEach
    void setUp() {
        jwtParser = mock(JWTParser.class);
        albumDao = mock(AlbumDao.class);
        pictureMetaDao = mock(PictureMetaDao.class);
        pictureDataDao = mock(PictureDataDao.class);
        albumFetchingService =
                new AlbumFetchingService(albumDao, pictureMetaDao, pictureDataDao, jwtParser);
    }

    @Test
    void getAlbumsForUserId() {
        when(albumDao.findAlbumsForUserId(USER_ID)).thenReturn(
                succeededFuture(List.of(ALBUM, ALBUM_2)));

        when(pictureMetaDao.findForAlbumId(ALBUM_ID))
                .thenReturn(succeededFuture(List.of(PICTURE_META, PICTURE_META_2)));
        when(pictureMetaDao.findForAlbumId(ALBUM_ID + 1))
                .thenReturn(succeededFuture(List.of()));

        albumFetchingService.getAlbumsForUserId(USER_ID)
                .onComplete(res -> assertEquals(
                        List.of(
                                ALBUM_2,
                                ALBUM.withCoverPicture(new Album.CoverPicture(PICTURE_META.userId(),
                                                PICTURE_META.id()))
                                        .withDateCreated(PICTURE_META.dateUploaded())),
                                res.result()));
    }

    @ParameterizedTest
    @MethodSource("albumsInDifferentOrder")
    void getAlbumsForUserIdSortedProperly(List<Album> albums) {
        when(albumDao.findAlbumsForUserId(USER_ID)).thenReturn(succeededFuture(albums));

        albumFetchingService.getAlbumsForUserId(USER_ID)
                .onComplete(res -> assertEquals(List.of(ALBUM_2, ALBUM), res.result()));
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

    @Test
    void getAlbumForUserId() {
        when(albumDao.findById(ALBUM_ID)).thenReturn(succeededFuture(Optional.of(ALBUM)));
        when(pictureMetaDao.findForAlbumId(ALBUM_ID))
                .thenReturn(succeededFuture(List.of(PICTURE_META, PICTURE_META_2)));

        albumFetchingService.getAlbumForUserId(USER_ID, ALBUM_ID)
                .onComplete(res -> assertEquals(
                        ALBUM.withCoverPicture(new Album.CoverPicture(PICTURE_META.userId(),
                                        PICTURE_META.id()))
                                .withDateCreated(PICTURE_META.dateUploaded()),
                        res.result()));
    }

    @ParameterizedTest
    @MethodSource("albumsWithUserAccess")
    void getPictureMetaForAlbum(Album album) {
        when(albumDao.findById(ALBUM_ID)).thenReturn(succeededFuture(Optional.of(album)));

        when(pictureMetaDao.findForAlbumId(ALBUM_ID))
                .thenReturn(succeededFuture(List.of(PICTURE_META, PICTURE_META_2, PICTURE_META_3)));

        albumFetchingService.getPictureMetaForAlbum(USER_ID, ALBUM_ID)
                .onComplete(
                        res -> assertEquals(List.of(PICTURE_META, PICTURE_META_3, PICTURE_META_2),
                                res.result()));
    }

    @ParameterizedTest
    @MethodSource("albumsWithoutUserAccess")
    void returnsErrorWhenAlbumIsNotAccessible(Album album) {
        when(jwtParser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(albumDao.findById(ALBUM_ID)).thenReturn(succeededFuture(Optional.of(album)));

        albumFetchingService.getPictureMetaForAlbum(USER_ID + 1, ALBUM_ID)
                .onComplete(resp -> {
                    assertNull(resp.result());
                    assertFalse(resp.succeeded());
                    assertTrue(resp.failed());
                    assertEquals(AuthorizationException.class, resp.cause().getClass());
                });

        albumFetchingService.download(TOKEN, USER_ID + 1, ALBUM_ID)
                .onComplete(resp -> {
                    assertNull(resp.result());
                    assertFalse(resp.succeeded());
                    assertTrue(resp.failed());
                    assertEquals(AuthorizationException.class, resp.cause().getClass());
                });
    }

    @Test
    void download() {
        when(jwtParser.validateTokenForUserId(TOKEN, USER_ID)).thenReturn(true);
        when(albumDao.findById(ALBUM_ID)).thenReturn(succeededFuture(Optional.of(ALBUM)));
        when(pictureMetaDao.findForAlbumId(ALBUM_ID)).thenReturn(
                succeededFuture(List.of(PICTURE_META, PICTURE_META_2)));
        when(pictureDataDao.find(PATH)).thenReturn(succeededFuture(DATA));
        when(pictureDataDao.find(PATH_2)).thenReturn(succeededFuture(DATA_2));

        albumFetchingService.download(TOKEN, USER_ID, ALBUM_ID)
                .onComplete(response -> assertTrue(response.result().length > 0));
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