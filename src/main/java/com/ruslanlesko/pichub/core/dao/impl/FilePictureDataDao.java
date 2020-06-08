package com.ruslanlesko.pichub.core.dao.impl;

import com.ruslanlesko.pichub.core.dao.PictureDataDao;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class FilePictureDataDao implements PictureDataDao {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private static final String folderPath = System.getenv("PIC_DATA");

    @Override
    public Future<String> save(byte[] data) {
        Promise<String> resultPromise = Promise.promise();

        Vertx.factory.context().executeBlocking(call -> {
            try {
                long largestId = Files.walk(Paths.get(folderPath), 2)
                        .map(this::extractId)
                        .map(Number::longValue)
                        .filter(n -> n > 0).reduce(0L, (a, b) -> a > b ? a : b);
                if (largestId < 0) {
                    logger.error("Cannot create an id");
                    resultPromise.complete(null);
                    return;
                }

                long newId = largestId + 1;

                Path target = Paths.get(folderPath + "/" + newId + ".jpg");
                Files.write(target, data);
                resultPromise.complete(target.toString());
            } catch (IOException e) {
                logger.error(e.getMessage());
                resultPromise.complete(null);
            } finally {
                call.complete();
            }
        });

        return resultPromise.future();
    }

    @Override
    public Future<Optional<byte[]>> find(String path) {
        Promise<Optional<byte[]>> resultPromise = Promise.promise();

        Vertx.factory.context().executeBlocking(call -> {
           try {
               Path fullPath = Path.of(path);

               if (Files.notExists(fullPath)) {
                   resultPromise.complete(Optional.empty());
                   return;
               }

               try {
                   resultPromise.complete(Optional.of(Files.readAllBytes(fullPath)));
               } catch (IOException e) {
                   logger.error(e.getMessage());
                   resultPromise.complete(Optional.empty());
               }
           } finally {
               call.complete();
           }
        });

        return resultPromise.future();
    }

    @Override
    public Future<Boolean> replace(String path, byte[] data) {
        Path fullPath = Path.of(path);

        if (Files.notExists(fullPath)) {
            return Future.succeededFuture(false);
        }

        Promise<Boolean> resultPromise = Promise.promise();

        Vertx.factory.context().executeBlocking(call -> {
            try {
                Files.write(fullPath, data);
                resultPromise.complete(true);
            } catch (IOException e) {
                logger.error(e.getMessage());
                resultPromise.complete(false);
            } finally {
                call.complete();
            }
        });

        return resultPromise.future();
    }

    @Override
    public Future<Boolean> delete(String path) {
        Promise<Boolean> resultPromise = Promise.promise();

        Path fullPath = Path.of(path);

        Vertx.factory.context().executeBlocking(call -> {
            try {
                resultPromise.complete(Files.deleteIfExists(fullPath));
            } catch (IOException e) {
                logger.error(e.getMessage());
                resultPromise.complete(false);
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
