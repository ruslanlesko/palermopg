package com.ruslanlesko.pichub.core.entity;

import java.util.Objects;

public class Album {
    private final long id;
    private final long userId;
    private final String name;

    public Album(long id, long userId, String name) {
        this.id = id;
        this.userId = userId;
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Album album = (Album) o;
        return id == album.id &&
               userId == album.userId &&
               Objects.equals(name, album.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId, name);
    }
}
