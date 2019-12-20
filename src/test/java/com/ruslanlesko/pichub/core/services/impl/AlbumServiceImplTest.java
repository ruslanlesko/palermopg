package com.ruslanlesko.pichub.core.services.impl;

import com.ruslanlesko.pichub.core.dao.AlbumDao;
import com.ruslanlesko.pichub.core.dao.PictureMetaDao;
import com.ruslanlesko.pichub.core.entity.Album;
import com.ruslanlesko.pichub.core.entity.PictureMeta;
import com.ruslanlesko.pichub.core.security.JWTParser;
import com.ruslanlesko.pichub.core.services.AlbumService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlbumServiceImplTest {

    @Test
    void testAddNewAlbum() {
        final String token = "abc";
        final long userId = 42;
        final long albumId = 69;
        final String albumName = "albumName";

        PictureMetaDao metDao = mock(PictureMetaDao.class);
        AlbumDao albumDao = mock(AlbumDao.class);
        JWTParser parser = mock(JWTParser.class);

        when(parser.validateTokenForUserId(token, userId)).thenReturn(true);
        when(albumDao.save(any())).thenReturn(albumId);

        AlbumService service = new AlbumServiceImpl(metDao, albumDao, parser);

        Optional<Long> expected = Optional.of(albumId);
        Optional<Long> actual = service.addNewAlbum(token, userId, albumName);

        assertEquals(expected, actual);
    }

    @Test
    void testGetAlbumsForId() {
        final String token = "abc";
        final long userId = 42;
        final List<Album> albums = IntStream.range(1, 3)
                .mapToObj(i -> new Album(i, userId, null))
                .collect(Collectors.toList());

        PictureMetaDao metaDao = mock(PictureMetaDao.class);
        AlbumDao albumDao = mock(AlbumDao.class);
        JWTParser parser = mock(JWTParser.class);

        when(parser.validateTokenForUserId(token, userId)).thenReturn(true);
        when(albumDao.findAlbumsForUserId(userId)).thenReturn(albums);

        AlbumService service = new AlbumServiceImpl(metaDao, albumDao, parser);

        assertEquals(albums, service.getAlbumsForUserId(token, userId));
    }

    @Test
    void testGetPictureMetaForAlbum() {
        final String token = "abc";
        final long userId = 42;
        final long albumId = 69;
        final Album album = new Album(albumId, userId, null);
        final PictureMeta metaA = new PictureMeta(69, userId, -1, null,
                LocalDateTime.of(2019, 10, 4, 12, 4),
                LocalDateTime.of(2019, 10, 4, 10, 22)
            ),
            metaB = new PictureMeta(27, userId, -1, null,
                LocalDateTime.of(2019, 10, 5, 12, 4),
                LocalDateTime.of(2019, 10, 4, 10, 26)
            ),
            metaC = new PictureMeta(25, userId, -1, null,
                LocalDateTime.of(2019, 10, 5, 12, 4),
                LocalDateTime.of(2019, 10, 4, 10, 22)
            );

        PictureMetaDao metaDao = mock(PictureMetaDao.class);
        AlbumDao albumDao = mock(AlbumDao.class);
        JWTParser parser = mock(JWTParser.class);

        when(parser.validateTokenForUserId(token, userId)).thenReturn(true);
        when(albumDao.findById(albumId)).thenReturn(Optional.of(album));
        when(metaDao.findPictureMetasForAlbumId(albumId)).thenReturn(List.of(metaA, metaB, metaC));

        AlbumService service = new AlbumServiceImpl(metaDao, albumDao, parser);

        List<PictureMeta> expected = List.of(metaB, metaC, metaA);
        List<PictureMeta> actual = service.getPictureMetaForAlbum(token, userId, albumId);

        assertEquals(expected, actual);
    }
}
