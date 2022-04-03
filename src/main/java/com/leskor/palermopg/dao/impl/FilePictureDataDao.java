package com.leskor.palermopg.dao.impl;

import com.leskor.palermopg.dao.PictureDataDao;
import com.leskor.palermopg.exception.MissingItemException;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FilePictureDataDao implements PictureDataDao {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private static final String folderPath = System.getenv("PIC_DATA");

    private final Context context;

    public FilePictureDataDao(Context context) {
        this.context = context;
    }

    @Override
    public Future<String> save(byte[] data, long albumId) {
        Promise<String> resultPromise = Promise.promise();

        context.executeBlocking(call -> {
            try {
                Path path = folderPathForAlbum(albumId);
                long largestId = Files.walk(path, 2)
                        .map(this::extractId)
                        .map(Number::longValue)
                        .filter(n -> n > 0).reduce(0L, (a, b) -> a > b ? a : b);
                if (largestId < 0) {
                    logger.error("Cannot create an id");
                    resultPromise.fail("Cannot create an id");
                    return;
                }

                long newId = largestId + 1;

                Path target = Paths.get(path.toString() + "/" + newId + ".jpg");
                Files.write(target, data);
                resultPromise.complete(target.toString());
            } catch (IOException e) {
                logger.error(e.getMessage());
                resultPromise.fail(e);
            } finally {
                call.complete();
            }
        });

        return resultPromise.future();
    }

    private Path folderPathForAlbum(long albumId) throws IOException {
        Path target = albumId < 0 ? Paths.get(folderPath) : Paths.get(folderPath + "/a" + albumId);
        return Files.exists(target) && Files.isDirectory(target) ? target : Files.createDirectory(target);
    }

    @Override
    public Future<byte[]> find(String path) {
        Promise<byte[]> resultPromise = Promise.promise();

        context.executeBlocking(call -> {
           try {
               Path fullPath = Path.of(path);

               if (Files.notExists(fullPath)) {
                   resultPromise.fail(new MissingItemException());
                   return;
               }

               try {
                   resultPromise.complete(Files.readAllBytes(fullPath));
               } catch (IOException e) {
                   logger.error(e.getMessage());
                   resultPromise.fail(e);
               }
           } finally {
               call.complete();
           }
        });

        return resultPromise.future();
    }

    @Override
    public Future<Void> replace(String path, byte[] data) {
        Path fullPath = Path.of(path);

        if (Files.notExists(fullPath)) {
            return Future.failedFuture(new MissingItemException());
        }

        Promise<Void> resultPromise = Promise.promise();

        context.executeBlocking(call -> {
            try {
                Files.write(fullPath, data);
                resultPromise.complete();
            } catch (IOException e) {
                logger.error(e.getMessage());
                resultPromise.fail(e);
            } finally {
                call.complete();
            }
        });

        return resultPromise.future();
    }

    @Override
    public Future<Void> delete(String path) {
        Promise<Void> resultPromise = Promise.promise();

        Path fullPath = Path.of(path);

        context.executeBlocking(call -> {
            try {
                if (Files.deleteIfExists(fullPath)) {
                    resultPromise.complete();
                } else {
                    resultPromise.fail(new MissingItemException());
                }
            } catch (IOException e) {
                logger.error(e.getMessage());
                resultPromise.fail(e);
            } finally {
                call.complete();
            }
        });

        return resultPromise.future();
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
