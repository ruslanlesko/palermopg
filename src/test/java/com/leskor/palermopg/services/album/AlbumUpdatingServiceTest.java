package com.leskor.palermopg.services.album;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.entity.Album;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlbumUpdatingServiceTest {
    private static final String
            NAME = "Birthday Party",
            DOWNLOAD_CODE = "132";

    private static final long
            USER_ID = 25,
            ALBUM_ID = 42;

    private static final List<Long>
            INITIAL_SHARED_USERS = List.of(42L, 25L),
            SHARED_USERS_TO_SET = List.of(42L, 25L, 99L, 5L);

    private static final Album
            ALBUM = new Album(ALBUM_ID, USER_ID, NAME, INITIAL_SHARED_USERS, DOWNLOAD_CODE, true),
            ALBUM_UPDATED =  new Album(ALBUM_ID, USER_ID, null, SHARED_USERS_TO_SET, null, null),
            ALBUM_FOR_SHARED_USER = new Album(ALBUM_ID, USER_ID - 1, NAME, List.of(USER_ID), DOWNLOAD_CODE, true),
            ALBUM_2 = new Album(ALBUM_ID, USER_ID - 1, NAME, List.of(), DOWNLOAD_CODE, true);

    private AlbumDao dao;
    private AlbumUpdatingService albumUpdatingService;

    @BeforeEach
    void setUp() {
        dao = mock(AlbumDao.class);
        albumUpdatingService = new AlbumUpdatingService(dao);
    }

    @Test
    void update() {
        when(dao.findById(ALBUM_ID)).thenReturn(succeededFuture(Optional.of(ALBUM)));
        when(dao.updateAlbum(ALBUM_UPDATED)).thenReturn(succeededFuture());

        albumUpdatingService.update(ALBUM_UPDATED)
                .onComplete(res -> assertTrue(res.succeeded()));
    }

    @ParameterizedTest
    @MethodSource("albumsNotOwnedByUser")
    void returnsErrorForNotOwnedAlbums(Album album) {
        when(dao.findById(ALBUM_ID)).thenReturn(succeededFuture(Optional.of(album)));

        albumUpdatingService.update(ALBUM_UPDATED)
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