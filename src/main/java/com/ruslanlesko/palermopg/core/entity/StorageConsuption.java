package com.ruslanlesko.palermopg.core.entity;

import java.util.Objects;

public class StorageConsuption {
    private final long size;
    private final long limit;
    
    public StorageConsuption(long size, long limit) {
        this.size = size;
        this.limit = limit;
    }

    public long getSize() {
        return size;
    }

    public long getLimit() {
        return limit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        StorageConsuption that = (StorageConsuption) o;
        return size == that.size && limit == that.limit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(size, limit);
    }
}