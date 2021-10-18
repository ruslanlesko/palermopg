package com.leskor.palermopg.meta;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static com.drew.metadata.exif.ExifIFD0Directory.TAG_ORIENTATION;
import static com.drew.metadata.exif.ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL;

public class MetaParser {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private final Metadata metadata;

    public MetaParser(byte[] data) {
        try {
            this.metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(data));
        } catch (ImageProcessingException | IOException e) {
            logger.error(e.getMessage());
            throw new IllegalArgumentException(e);
        }
    }

    public LocalDateTime getDateCaptured() {
        ExifSubIFDDirectory exifDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        Date date = exifDirectory.getDate(TAG_DATETIME_ORIGINAL);
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public int getRotation() {
        try {
            var exifDirectory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (!exifDirectory.containsTag(TAG_ORIENTATION)) {
                return -1;
            }
            int orientation = exifDirectory.getInt(TAG_ORIENTATION);
            return switch (orientation) {
                case 3 -> 180;
                case 6 -> 90;
                case 8 -> 270;
                default -> 0;
            };
        } catch (MetadataException e) {
            logger.error(e.getMessage());
            return -1;
        }
    }
}
