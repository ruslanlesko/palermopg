package com.leskor.palermopg.services;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.dao.PictureDataDao;
import com.leskor.palermopg.dao.PictureMetaDao;
import com.leskor.palermopg.entity.Album;
import com.leskor.palermopg.entity.PictureMeta;
import com.leskor.palermopg.exception.AuthorizationException;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AlbumServiceTest {
    private static final String TOKEN = "abc";
    private static final String ALBUM_NAME = "great pics";
    private static final long USER_ID = 42;
    private static final long USER_ID_2 = 97;
    private static final long ALBUM_ID = 69;
    private static final String DOWNLOAD_CODE = "jkh57f";
    private static final Album SAMPLE_ALBUM = new Album(ALBUM_ID, USER_ID, ALBUM_NAME, List.of(), DOWNLOAD_CODE, false);
    private static final PictureMeta PICTURE_META = new PictureMeta(25, USER_ID, ALBUM_ID, 2,
            "pic.jpg", "pic_0.jpg", LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), "98sdv9");
    private static final byte[] DATA = new byte[]{99, 2};
    
    @Test
    void testGetAlbumsForUser() {
        PictureMetaDao pictureMetaDao = mock(PictureMetaDao.class);
        PictureDataDao pictureDataDao = mock(PictureDataDao.class);
        AlbumDao albumDao = mock(AlbumDao.class);
        PictureService pictureService = mock(PictureService.class);

        when(albumDao.findAlbumsForUserId(USER_ID))
                .thenReturn(Future.succeededFuture(List.of(SAMPLE_ALBUM)));

        AlbumService service = new AlbumService(pictureMetaDao, pictureDataDao, albumDao, pictureService);

        List<Album> expected = List.of(SAMPLE_ALBUM);

        service.getAlbumsForUserId(USER_ID)
                .onComplete(response -> assertEquals(expected, response.result()));
    }

    @Test
    void testAddAlbum() {
        PictureMetaDao pictureMetaDao = mock(PictureMetaDao.class);
        PictureDataDao pictureDataDao = mock(PictureDataDao.class);
        AlbumDao albumDao = mock(AlbumDao.class);
        PictureService pictureService = mock(PictureService.class);

        when(albumDao.save(argThat(a ->
                        a.getId() == -1
                        && a.getName().equals(ALBUM_NAME)
                        && a.getUserId() == USER_ID
                        && a.getSharedUsers().isEmpty()
                        && a.getDownloadCode().length() == 128)))
                .thenReturn(Future.succeededFuture(ALBUM_ID));

        AlbumService albumService = new AlbumService(pictureMetaDao, pictureDataDao, albumDao, pictureService);

        albumService.addNewAlbum(USER_ID, ALBUM_NAME)
                .onComplete(response -> assertEquals(ALBUM_ID, response.result()));
    }

    @Test
    void testDeleteAlbumNotAccessible() {
        PictureMetaDao pictureMetaDao = mock(PictureMetaDao.class);
        PictureDataDao pictureDataDao = mock(PictureDataDao.class);
        AlbumDao albumDao = mock(AlbumDao.class);
        PictureService pictureService = mock(PictureService.class);

        when(albumDao.findById(ALBUM_ID))
                .thenReturn(Future.succeededFuture(Optional.of(new Album(ALBUM_ID, USER_ID_2, ALBUM_NAME, List.of(), DOWNLOAD_CODE, false))));

        AlbumService albumService = new AlbumService(pictureMetaDao, pictureDataDao, albumDao, pictureService);

        AuthorizationException expected = new AuthorizationException("Album is not available to user");

        albumService.delete(TOKEN, USER_ID, ALBUM_ID)
                .onComplete(response -> {
                    assertTrue(response.failed());
                    assertEquals(expected, response.cause());
                });
    }

    @Test
    void testAlbumDownload() {
        PictureMetaDao pictureMetaDao = mock(PictureMetaDao.class);
        PictureDataDao pictureDataDao = mock(PictureDataDao.class);
        AlbumDao albumDao = mock(AlbumDao.class);
        PictureService pictureService = mock(PictureService.class);

        when(albumDao.findById(ALBUM_ID)).thenReturn(Future.succeededFuture(Optional.of(SAMPLE_ALBUM)));
        when(pictureMetaDao.findForAlbumId(ALBUM_ID)).thenReturn(Future.succeededFuture(List.of(PICTURE_META)));
        when(pictureDataDao.find(PICTURE_META.getPath())).thenReturn(Future.succeededFuture(DATA));

        AlbumService albumService = new AlbumService(pictureMetaDao, pictureDataDao, albumDao, pictureService);

        albumService.download(USER_ID, ALBUM_ID, DOWNLOAD_CODE)
                .onComplete(response -> assertTrue(response.result().length > 0));

    }
}