package com.ruslanlesko.pichub.core.entity;

import java.util.Objects;
import java.util.Optional;

public class PictureResponse {
    private final Optional<byte[]> data;
    private final boolean notModified;
    private final String hash;

    public PictureResponse(Optional<byte[]> data, boolean notModified, String hash) {
        this.data = data;
        this.notModified = notModified;
        this.hash = hash;
    }

    public Optional<byte[]> getData() {
        return data;
    }

    public boolean isNotModified() {
        return notModified;
    }

    public String getHash() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PictureResponse that = (PictureResponse) o;
        return notModified == that.notModified &&
               Objects.equals(data, that.data) &&
               Objects.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data, notModified, hash);
    }

    @Override
    public String toString() {
        return "PictureResponse{" +
               "data=" + data +
               ", notModified=" + notModified +
               ", hash='" + hash + '\'' +
               '}';
    }
}
