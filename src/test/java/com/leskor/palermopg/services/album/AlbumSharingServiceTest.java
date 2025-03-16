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

public class AlbumSharingServiceTest {
    private static final String NAME = "Birthday Party";

    private static final long
            USER_ID = 25,
            ALBUM_ID = 42;

    private static final List<Long>
            INITIAL_SHARED_USERS = List.of(42L, 25L),
            ADDED_SHARED_USERS = List.of(42L, 99L, 99L, 5L),
            SHARED_USERS_TO_SET = List.of(42L, 25L, 99L, 5L);

    private static final Album
            ALBUM = Album.create(ALBUM_ID, USER_ID, NAME, INITIAL_SHARED_USERS, true),
            ALBUM_FOR_SHARED_USER = Album.create(ALBUM_ID, USER_ID - 1, NAME, List.of(USER_ID), true),
            ALBUM_2 = Album.create(ALBUM_ID, USER_ID - 1, NAME, List.of(), true);


    private AlbumDao dao;
    private AlbumSharingService albumSharingService;

    @BeforeEach
    void setUp() {
        dao = mock(AlbumDao.class);
        albumSharingService = new AlbumSharingService(dao);
    }

    @Test
    void shareAlbum() {
        when(dao.findById(ALBUM_ID)).thenReturn(succeededFuture(Optional.of(ALBUM)));
        when(dao.updateSharedUsers(ALBUM_ID, SHARED_USERS_TO_SET)).thenReturn(succeededFuture());

        albumSharingService.shareAlbum(USER_ID, ALBUM_ID, ADDED_SHARED_USERS)
                .onComplete(res -> assertTrue(res.succeeded()));
    }

    @ParameterizedTest
    @MethodSource("albumsNotOwnedByUser")
    void returnsErrorForNotOwnedAlbums(Album album) {
        when(dao.findById(ALBUM_ID)).thenReturn(succeededFuture(Optional.of(album)));

        albumSharingService.shareAlbum(USER_ID, ALBUM_ID, ADDED_SHARED_USERS)
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
