package com.ruslanlesko.palermopg.core.entity;

import java.time.LocalDateTime;
import java.util.Objects;

public class PictureMeta {
    private final long id;
    private final long userId;
    private final long albumId;
    private final long size;
    private final String path;
    private final String pathOptimized;
    private final LocalDateTime dateUploaded;
    private final LocalDateTime dateCaptured;
    private final LocalDateTime dateModified;
    private final String downloadCode;

    public PictureMeta(
            long id,
            long userId,
            long albumId,
            long size,
            String path,
            String pathOptimized,
            LocalDateTime dateUploaded,
            LocalDateTime dateCaptured,
            LocalDateTime dateModified,
            String downloadCode) {
        this.id = id;
        this.userId = userId;
        this.albumId = albumId;
        this.size = size;
        this.path = path;
        this.pathOptimized = pathOptimized;
        this.dateUploaded = dateUploaded;
        this.dateCaptured = dateCaptured;
        this.dateModified = dateModified;
        this.downloadCode = downloadCode;
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

    public long getSize() {
        return size;
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

    public String getPathOptimized() {
        return pathOptimized;
    }

    public LocalDateTime getDateModified() {
        return dateModified;
    }

    public String getDownloadCode() {
        return downloadCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PictureMeta that = (PictureMeta) o;
        return id == that.id && userId == that.userId && albumId == that.albumId && size == that.size && Objects.equals(path, that.path) && Objects.equals(pathOptimized, that.pathOptimized) && Objects.equals(dateUploaded, that.dateUploaded) && Objects.equals(dateCaptured, that.dateCaptured) && Objects.equals(dateModified, that.dateModified) && Objects.equals(downloadCode, that.downloadCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId, albumId, size, path, pathOptimized, dateUploaded, dateCaptured, dateModified, downloadCode);
    }
}
