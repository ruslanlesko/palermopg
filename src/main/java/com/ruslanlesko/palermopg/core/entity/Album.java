package com.ruslanlesko.palermopg.core.entity;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Album {
    private final long id;
    private final long userId;
    private final String name;
    private final List<Long> sharedUsers;

    public Album(long id, long userId, String name, List<Long> sharedUsers) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.sharedUsers = sharedUsers == null ? List.of() : Collections.unmodifiableList(sharedUsers);
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

    public List<Long> getSharedUsers() {
        return sharedUsers;
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
               Objects.equals(name, album.name) &&
               Objects.equals(sharedUsers, album.sharedUsers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId, name, sharedUsers);
    }
}
