package com.leskor.palermopg.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public record Album(
        long id,
        long userId,
        String name,
        List<Long> sharedUsers,
        @JsonProperty("isChronologicalOrder") Boolean isChronologicalOrder
) { }
