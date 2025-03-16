package com.leskor.palermopg.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record Album(
        long id,
        long userId,
        String name,
        List<Long> sharedUsers,
        @JsonProperty("isChronologicalOrder") Boolean isChronologicalOrder,
        CoverPicture coverPicture,
        String dateCreated
) {
    public static Album create(long id, long userId, String name, List<Long> sharedUsers,
                               Boolean isChronologicalOrder) {
        return new Album(id, userId, name, sharedUsers, isChronologicalOrder, null, null);
    }

    public Album withCoverPicture(CoverPicture coverPicture) {
        return new Album(id, userId, name, sharedUsers, isChronologicalOrder, coverPicture,
                dateCreated);
    }

    public Album withDateCreated(LocalDateTime dateCreated) {
        return new Album(id, userId, name, sharedUsers, isChronologicalOrder, coverPicture,
                dateCreated.format(
                        DateTimeFormatter.ISO_LOCAL_DATE));
    }

    public record CoverPicture(long userId, long pictureId) {
    }
}
