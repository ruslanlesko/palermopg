package com.leskor.palermopg.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public record Album(
        long id,
        long userId,
        String name,
        List<Long> sharedUsers,
        @JsonProperty("isChronologicalOrder") Boolean isChronologicalOrder,
        CoverPicture coverPicture
) {
    public static Album create(long id, long userId, String name, List<Long> sharedUsers, Boolean isChronologicalOrder) {
        return new Album(id, userId, name, sharedUsers, isChronologicalOrder, null);
    }

    public Album withCoverPicture(CoverPicture coverPicture) {
        return new Album(id, userId, name, sharedUsers, isChronologicalOrder, coverPicture);
    }

    public record CoverPicture(long userId, long pictureId) {}
}
