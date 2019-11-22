package com.ruslanlesko.pichub.core.dao.impl;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.ruslanlesko.pichub.core.dao.PictureDao;
import com.ruslanlesko.pichub.core.entity.Picture;

import javax.enterprise.context.ApplicationScoped;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class FilePictureDao implements PictureDao {
    private String folderPath = System.getenv("PIC_DATA");

    @Override
    public List<Long> findIdsForUser(long userId) {
        List<Long> result = new ArrayList<>();

        Path userDir = Paths.get(folderPath + "/" + userId);

        if (Files.notExists(userDir)) {
            return result;
        }

        try {
            Files.list(userDir)
                    .map(this::extractId)
                    .map(Number::longValue)
                    .filter(n -> n > 0)
                    .forEach(result::add);

        } catch (IOException e) {
            return null;
        }

        return result;
    }

    @Override
    public List<Picture> findPicturesForUser(long userId) {
        Path userDir = Paths.get(folderPath + "/" + userId);

        if (Files.notExists(userDir)) {
            return List.of();
        }

        try {
            return Files.list(userDir)
                    .map(this::pathToPicture)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return null;
        }
    }

    private Picture pathToPicture(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            FileTime fileTime = attributes.lastModifiedTime();
            long fileTimeMills = fileTime.toMillis();
            LocalDateTime uploadedDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(fileTimeMills), ZoneId.systemDefault());
            Metadata metadata = ImageMetadataReader.readMetadata(new FileInputStream(path.toString()));
            ExifSubIFDDirectory exifDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            Date date = exifDirectory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
            LocalDateTime dateCaptured = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            return new Picture(extractId(path), Files.readAllBytes(path), path.toString(), uploadedDate, dateCaptured);
        } catch (IOException | ImageProcessingException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    @Override
    public long save(long userId, Picture picture) {
        Path userDir = Paths.get(folderPath + "/" + userId);

        try {
            if (Files.notExists(userDir)) {
                Files.createDirectory(userDir);
            }

            long largestIf = Files.walk(Paths.get(folderPath), 2)
                    .map(this::extractId)
                    .map(Number::longValue)
                    .filter(n -> n > 0).reduce(0L, (a, b) -> a > b ? a : b);

            long id = largestIf + 1;
            Path target = Paths.get(userDir.toString() + "/" + id + ".jpg");
            Files.write(target, picture.getData());
            return id;
        } catch (IOException e) {
            return -1;
        }
    }

    private long extractId(Path p) {
        String path = p.getFileName().toString();
        int idx = path.indexOf('.');
        if (idx < 0) {
            return -1;
        }
        try {
            return Long.valueOf(path.substring(0, idx));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    @Override
    public Optional<Picture> find(long userId, long pictureId) {
        Path fullPath = Paths.get(folderPath + "/" + userId + "/" + pictureId + ".jpg");

        if (Files.notExists(fullPath)) {
            return Optional.empty();
        }

        Picture result;

        try {
            byte[] data = Files.readAllBytes(fullPath);
            result = new Picture(pictureId, data, fullPath.toString(), null, null);
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return Optional.empty();
        }

        return Optional.of(result);
    }
}
