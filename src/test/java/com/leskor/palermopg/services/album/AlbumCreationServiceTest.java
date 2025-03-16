package com.leskor.palermopg.services.album;

import static io.vertx.core.Future.succeededFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.entity.Album;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AlbumCreationServiceTest {
    private static final long
            CREATED_ALBUM_ID = 42,
            USER_ID = 21,
            USER_ID_2 = 14,
            USER_ID_3 = 99;

    private static final String ALBUM_NAME = "Vacation 2020";
    private static final List<Long> SHARED_USERS = List.of(USER_ID_2, USER_ID_3);

    private AlbumDao dao;
    private AlbumCreationService albumCreationService;

    @BeforeEach
    void setUp() {
        dao = mock(AlbumDao.class);
        albumCreationService = new AlbumCreationService(dao);
    }

    @Test
    void addNewAlbum() {
        when(dao.save(albumMatcher(SHARED_USERS, true))).thenReturn(
                succeededFuture(CREATED_ALBUM_ID));

        Album album = Album.create(-1, USER_ID, ALBUM_NAME, SHARED_USERS, true);
        albumCreationService.addNewAlbum(album)
                .onComplete(resp -> assertEquals(CREATED_ALBUM_ID, resp.result()));
    }

    @Test
    void addNewAlbumWithOnlyRequiredFieldsSet() {
        when(dao.save(albumMatcher(List.of(), false))).thenReturn(
                succeededFuture(CREATED_ALBUM_ID));

        Album album = Album.create(-1, USER_ID, ALBUM_NAME, null, null);
        albumCreationService.addNewAlbum(album)
                .onComplete(resp -> assertEquals(CREATED_ALBUM_ID, resp.result()));
    }

    @ParameterizedTest
    @MethodSource("invalidAlbums")
    void returnsErrorWhenInvalidAlbumProvided(Album album) {
        albumCreationService.addNewAlbum(album)
                .onComplete(resp -> {
                    assertNull(resp.result());
                    assertFalse(resp.succeeded());
                    assertTrue(resp.failed());
                    assertEquals(IllegalArgumentException.class, resp.cause().getClass());
                });
    }

    private static Album albumMatcher(List<Long> sharedUsers, boolean isChronologicalOrder) {
        return argThat(a -> a.id() == -1
                && a.userId() == USER_ID
                && a.name().equals(ALBUM_NAME)
                && a.sharedUsers().equals(sharedUsers)
                && a.isChronologicalOrder().equals(isChronologicalOrder)
        );
    }

    private static Stream<Arguments> invalidAlbums() {
        return Stream.of(
                Arguments.of(Album.create(-1, -1, ALBUM_NAME, SHARED_USERS, true)),
                Arguments.of(Album.create(-1, USER_ID, "", SHARED_USERS, true)),
                Arguments.of(Album.create(-1, USER_ID, null, SHARED_USERS, true)),
                Arguments.of((Album) null)
        );
    }
}