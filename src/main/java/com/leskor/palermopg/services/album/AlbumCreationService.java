package com.leskor.palermopg.services.album;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.entity.Album;
import io.vertx.core.Future;

import java.util.List;

import static io.vertx.core.Future.failedFuture;

public class AlbumCreationService {
    private final AlbumDao dao;

    public AlbumCreationService(AlbumDao dao) {
        this.dao = dao;
    }

    public Future<Long> addNewAlbum(Album album) {
        if (album == null || album.getUserId() < 1 || album.getName() == null || album.getName().isEmpty()) {
            return failedFuture(new IllegalArgumentException("Album data is invalid for creation"));
        }

        Album albumToPersist = prepareAlbumForPersisting(album);
        return dao.save(albumToPersist);
    }

    private Album prepareAlbumForPersisting(Album album) {
        List<Long> sharedUsers = album.getSharedUsers() == null ? List.of() : album.getSharedUsers();
        boolean isChronologicalOrder = album.isChronologicalOrder() != null && album.isChronologicalOrder();

        return new Album(-1, album.getUserId(), album.getName(), sharedUsers, isChronologicalOrder);
    }
}
