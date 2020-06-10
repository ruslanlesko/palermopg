package com.ruslanlesko.pichub.core.dao.impl;

import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.ruslanlesko.pichub.core.dao.PictureMetaDao;
import com.ruslanlesko.pichub.core.entity.PictureMeta;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.eq;

public class MongoPictureMetaDao implements PictureMetaDao {
    private static Logger logger = LoggerFactory.getLogger("Application");

    private final static String DB = "pichubdb";
    private final static String COLLECTION = "pictures";

    private MongoClient mongoClient;

    public MongoPictureMetaDao(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    @Override
    public Future<Long> save(PictureMeta pictureMeta) {
        Promise<Long> resultPromise = Promise.promise();

        Vertx.factory.context().executeBlocking(call -> {
            long id = getNextId();

            Document document = new Document()
                    .append("id", id)
                    .append("path", pictureMeta.getPath())
                    .append("pathOptimized", pictureMeta.getPathOptimized())
                    .append("userId", pictureMeta.getUserId())
                    .append("dateUploaded", pictureMeta.getDateUploaded())
                    .append("dateCaptured", pictureMeta.getDateCaptured())
                    .append("dateModified", pictureMeta.getDateModified());

            if (pictureMeta.getAlbumId() > 0) {
                document.append("albumId", pictureMeta.getAlbumId());
            }
            getCollection().insertOne(document);
            resultPromise.complete(id);
        });

        return resultPromise.future();
    }

    @Override
    public Future<Optional<PictureMeta>> find(long id) {
        Promise<Optional<PictureMeta>> resultPromise = Promise.promise();

        Vertx.factory.context().executeBlocking(call -> {
           try {
               Document result = getCollection().find(eq("id", id)).first();

               if (result == null) {
                   resultPromise.complete(Optional.empty());
                   return;
               }

               resultPromise.complete(Optional.of(mapToPicture(result)));
           } finally {
               call.complete();
           }
        });

        return resultPromise.future();
    }

    @Override
    public Future<List<PictureMeta>> findPictureMetasForAlbumId(long albumId) {
        logger.debug("Finding pictures for album id " + albumId);

        Promise<List<PictureMeta>> resultPromise = Promise.promise();

        Vertx.factory.context().executeBlocking(call -> {
            try {
                List<PictureMeta> result = new ArrayList<>();
                getCollection().find(eq("albumId", albumId))
                        .forEach((Consumer<Document>) document -> result.add(mapToPicture(document)));
                resultPromise.complete(result);
            } finally {
                call.complete();
            }
        });

        return resultPromise.future();
    }

    @Override
    public Future<Boolean> setLastModified(long id, LocalDateTime lastModified) {
        Promise<Boolean> resultPromise = Promise.promise();

        Vertx.factory.context().executeBlocking(call -> {
            try {
                BasicDBObject query = new BasicDBObject();
                query.put("id", id);

                BasicDBObject newDoc = new BasicDBObject();
                newDoc.put("dateModified", lastModified);

                BasicDBObject updateDoc = new BasicDBObject();
                updateDoc.put("$set", newDoc);

                UpdateResult result = getCollection().updateOne(query, updateDoc);
                resultPromise.complete(result.getModifiedCount() == 1 && result.wasAcknowledged());
            } finally {
                call.complete();
            }
        });

        return resultPromise.future();
    }

    @Override
    public Future<Boolean> deleteById(long id) {
        Promise<Boolean> resultPromise = Promise.promise();

        Vertx.factory.context().executeBlocking(call -> {
            try {
                DeleteResult deleteResult = getCollection().deleteOne(eq("id", id));
                if (deleteResult.getDeletedCount() == 0) {
                    logger.error("Delete failed due to the absence of document to delete");
                    resultPromise.complete(false);
                    return;
                }
                resultPromise.complete(deleteResult.wasAcknowledged());
            } finally {
                call.complete();
            }
        });

        return resultPromise.future();
    }

    private PictureMeta mapToPicture(Document document) {
        Long albumId = document.getLong("albumId");

        return new PictureMeta(
                document.getLong("id"),
                document.getLong("userId"),
                albumId == null ? -1 : albumId,
                document.getString("path"),
                document.getString("pathOptimized"),
                document.getDate("dateUploaded").toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                document.getDate("dateCaptured").toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                document.get("dateModified") == null ? document.getDate("dateUploaded").toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : document.getDate("dateModified").toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
        );
    }

    private long getNextId() {
        Document result = getCollection()
                .aggregate(List.of(Aggregates.group(null, Accumulators.max("maxId", "$id"))))
        .first();

        if (result == null) {
            return 1;
        } else {
            return result.getLong("maxId") + 1;
        }
    }

    private MongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB).getCollection(COLLECTION);
    }
}
