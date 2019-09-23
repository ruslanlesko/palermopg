package com.ruslanlesko.pichub.core.dao.impl;

import com.ruslanlesko.pichub.core.dao.PictureDao;
import com.ruslanlesko.pichub.core.entity.Picture;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FilePictureDao implements PictureDao {
    private String folderPath = "/Users/ruslan_lesko/Projects/pichub/data";

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
            result = new Picture(pictureId, data);
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return Optional.empty();
        }

        return Optional.of(result);
    }
}
