package com.ruslanlesko.pichub.core.meta;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public class MetaParser {
    private static Logger logger = LoggerFactory.getLogger("Application");
    private final byte[] data;

    public MetaParser(byte[] data) {
        this.data = data;
    }

    public LocalDateTime getDateCaptured() {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(data));
            ExifSubIFDDirectory exifDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            Date date = exifDirectory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (ImageProcessingException | IOException e) {
            logger.error("Cannot extract capturing date: " + e.getMessage());
            return null;
        }
    }
}
