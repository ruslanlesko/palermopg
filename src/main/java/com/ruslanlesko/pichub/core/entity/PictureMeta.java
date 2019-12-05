package com.ruslanlesko.pichub.core.entity;

import java.time.LocalDateTime;
import java.util.Objects;

public class PictureMeta {
    private final long id;
    private final long userId;
    private final long albumId;
    private final String path;
    private final LocalDateTime dateUploaded;
    private final LocalDateTime dateCaptured;

    public PictureMeta(long id, long userId, long albumId, String path, LocalDateTime dateUploaded, LocalDateTime dateCaptured) {
        this.id = id;
        this.userId = userId;
        this.albumId = albumId;
        this.path = path;
        this.dateUploaded = dateUploaded;
        this.dateCaptured = dateCaptured;
    }

    public long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public long getAlbumId() {
        return albumId;
    }

    public String getPath() {
        return path;
    }

    public LocalDateTime getDateUploaded() {
        return dateUploaded;
    }

    public LocalDateTime getDateCaptured() {
        return dateCaptured;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PictureMeta that = (PictureMeta) o;
        return id == that.id &&
               userId == that.userId &&
               albumId == that.albumId &&
               Objects.equals(path, that.path) &&
               Objects.equals(dateUploaded, that.dateUploaded) &&
               Objects.equals(dateCaptured, that.dateCaptured);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId, albumId, path, dateUploaded, dateCaptured);
    }

    @Override
    public String toString() {
        return "PictureMeta{" +
               "id=" + id +
               ", userId=" + userId +
               ", albumId=" + albumId +
               ", path='" + path + '\'' +
               ", dateUploaded=" + dateUploaded +
               ", dateCaptured=" + dateCaptured +
               '}';
    }
}
