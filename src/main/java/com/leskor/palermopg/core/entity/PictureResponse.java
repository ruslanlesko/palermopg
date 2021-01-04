package com.leskor.palermopg.core.entity;

import java.util.Arrays;
import java.util.Objects;

public class PictureResponse {
    private final byte[] data;
    private final boolean notModified;
    private final String hash;

    public PictureResponse(byte[] data, boolean notModified, String hash) {
        this.data = data;
        this.notModified = notModified;
        this.hash = hash;
    }

    public byte[] getData() {
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
               Arrays.equals(data, that.data) &&
               Objects.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data, notModified, hash);
    }
}
