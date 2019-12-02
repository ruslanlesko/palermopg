package com.ruslanlesko.pichub.core.dao.impl;

import com.ruslanlesko.pichub.core.dao.PictureDataDao;
import org.slf4j.Logger;
import org.slf4j.impl.SimpleLoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class FilePictureDataDao implements PictureDataDao {
    private static Logger logger = new SimpleLoggerFactory().getLogger("PictureDataDao");

    private String folderPath = System.getenv("PIC_DATA");

    @Override
    public String save(byte[] data) {
        try {
            long largestId = Files.walk(Paths.get(folderPath), 2)
                    .map(this::extractId)
                    .map(Number::longValue)
                    .filter(n -> n > 0).reduce(0L, (a, b) -> a > b ? a : b);
            if (largestId < 0) {
                logger.error("Cannot create an id");
                return null;
            }

            long newId = largestId + 1;

            Path target = Paths.get(folderPath + "/" + newId + ".jpg");
            Files.write(target, data);

            return target.toString();
        } catch (IOException e) {
            logger.error(e.getMessage());
            return null;
        }
    }

    @Override
    public Optional<byte[]> find(String path) {
        logger.debug("Loading picture from file: " + path);
        Path fullPath = Path.of(path);

        if (Files.notExists(fullPath)) {
            return Optional.empty();
        }

        try {
            return Optional.of(Files.readAllBytes(fullPath));
        } catch (IOException e) {
            logger.error(e.getMessage());
            return Optional.empty();
        }
    }

    private long extractId(Path p) {
        String path = p.getFileName().toString();
        int idx = path.indexOf('.');
        if (idx < 0) {
            return -1;
        }
        try {
            return Long.parseLong(path.substring(0, idx));
        } catch (NumberFormatException ex) {
            logger.error(ex.getMessage());
            return -1;
        }
    }
}
