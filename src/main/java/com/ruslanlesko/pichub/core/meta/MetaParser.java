package com.ruslanlesko.pichub.core.meta;

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
        Date date = exifDirectory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public int getRotation() {
        try {
            ExifIFD0Directory exifDirectory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            int orientation = exifDirectory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            int degrees;
            switch (orientation) {
                case 3: {
                    degrees = 180;
                    break;
                }
                case 6: {
                    degrees = 90;
                    break;
                }
                case 8: {
                    degrees = 270;
                    break;
                }
                default: {
                    degrees = 0;
                    break;
                }
            }
            logger.info("Orientation: " + orientation);
            logger.info("Rotating image to " + degrees + " degrees");
            return degrees;
        } catch (MetadataException e) {
            logger.error(e.getMessage());
            return -1;
        }
    }
}
