package com.ruslanlesko.pichub.core.entity;

import java.time.LocalDateTime;
import java.util.Objects;

public class PictureMeta {
    private final long id;
    private final long userId;
    private final String path;
    private final LocalDateTime dateUploaded;
    private final LocalDateTime dateCaptured;

    public PictureMeta(long id, long userId, String path, LocalDateTime dateUploaded, LocalDateTime dateCaptured) {
        this.id = id;
        this.userId = userId;
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
        PictureMeta pictureMeta = (PictureMeta) o;
        return id == pictureMeta.id &&
               userId == pictureMeta.userId &&
               Objects.equals(path, pictureMeta.path) &&
               Objects.equals(dateUploaded, pictureMeta.dateUploaded) &&
               Objects.equals(dateCaptured, pictureMeta.dateCaptured);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId, path, dateUploaded, dateCaptured);
    }

    @Override
    public String toString() {
        return "Picture{" +
               "id=" + id +
               ", userId=" + userId +
               ", path='" + path + '\'' +
               ", dateUploaded=" + dateUploaded +
               ", dateCaptured=" + dateCaptured +
               '}';
    }
}
