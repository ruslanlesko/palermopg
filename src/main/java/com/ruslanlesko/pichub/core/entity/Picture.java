package com.ruslanlesko.pichub.core.entity;

import java.util.Arrays;
import java.util.Objects;

public class Picture {
    private final long id;
    private final byte[] data;

    public Picture(long id, byte[] data) {
        this.id = id;
        this.data = data;
    }

    public long getId() {
        return id;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Picture picture = (Picture) o;
        return id == picture.id &&
                Arrays.equals(data, picture.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }
}
