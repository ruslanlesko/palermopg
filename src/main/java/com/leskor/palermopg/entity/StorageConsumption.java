package com.leskor.palermopg.entity;

import java.util.Objects;

public class StorageConsumption {
    private final long userId;
    private final long size;
    private final long limit;
    
    public StorageConsumption(long userId, long size, long limit) {
        this.userId = userId;
        this.size = size;
        this.limit = limit;
    }

    public long getSize() {
        return size;
    }

    public long getUserId() {
        return userId;
    }

    public long getLimit() {
        return limit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StorageConsumption that = (StorageConsumption) o;
        return userId == that.userId &&
                size == that.size &&
                limit == that.limit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, size, limit);
    }
}