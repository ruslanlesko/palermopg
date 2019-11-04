package com.ruslanlesko.pichub.core.entity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

public class Picture {
    private final long id;
    private final byte[] data;
    private final LocalDateTime dateUploaded;
    private final LocalDateTime dateCaptured;

    public Picture(long id, byte[] data, LocalDateTime dateUploaded, LocalDateTime dateCaptured) {
        this.id = id;
        this.data = data;
        this.dateUploaded = dateUploaded;
        this.dateCaptured = dateCaptured;
    }

    public long getId() {
        return id;
    }

    public byte[] getData() {
        return data;
    }

    public LocalDateTime getDateUploaded() {
        return dateUploaded;
    }

    public LocalDateTime getDateCaptured() {
        return dateCaptured;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Picture picture = (Picture) o;
        return id == picture.id &&
               Arrays.equals(data, picture.data) &&
               Objects.equals(dateUploaded, picture.dateUploaded) &&
               Objects.equals(dateCaptured, picture.dateCaptured);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, dateUploaded, dateCaptured);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return "Picture{" +
               "id=" + id +
               ", dateUploaded=" + dateUploaded +
               ", dateCaptured=" + dateCaptured +
               '}';
    }
}
