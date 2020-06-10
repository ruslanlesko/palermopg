package com.ruslanlesko.pichub.core.dao.impl;

import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.ruslanlesko.pichub.core.dao.AlbumDao;
import com.ruslanlesko.pichub.core.entity.Album;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;

public class MongoAlbumDao implements AlbumDao {
    private final static String DB = "pichubdb";
    private final static String COLLECTION = "albums";

    private final MongoClient mongoClient;

    public MongoAlbumDao(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    @Override
    public Future<Long> save(Album album) {
        Promise<Long> resultPromise = Promise.promise();

        getNextId().setHandler(idResult -> Vertx.factory.context().executeBlocking(call -> {
            if (idResult.failed()) {
                resultPromise.fail(idResult.cause());
                return;
            }

            long id = idResult.result();

            Document document = new Document()
                    .append("id", id)
                    .append("userId", album.getUserId())
                    .append("name", album.getName());
            getCollection().insertOne(document);
            resultPromise.complete(id);
        }));

        return resultPromise.future();
    }

    @Override
    public Future<Optional<Album>> findById(long id) {
        Promise<Optional<Album>> resultPromise = Promise.promise();

        Vertx.factory.context().executeBlocking(call -> {
            Document result = getCollection().find(eq("id", id)).first();

            if (result == null) {
                resultPromise.complete(Optional.empty());
                return;
            }

            resultPromise.complete(Optional.of(new Album(
                    id,
                    result.getLong("userId"),
                    result.getString("name"),
                    result.getList("sharedUsers", Long.class)
            )));
        });

        return resultPromise.future();
    }

    @Override
    public Future<List<Album>> findAlbumsForUserId(long userId) {
        Promise<List<Album>> resultPromise = Promise.promise();

        Vertx.factory.context().executeBlocking(call -> {
            List<Album> result = new ArrayList<>();
            getCollection().find(or(eq("userId", userId), eq("sharedUsers", userId)))
                    .forEach((Consumer<Document>) document -> result.add(new Album(
                            document.getLong("id"),
                            document.getLong("userId"),
                            document.getString("name"),
                            document.getList("sharedUsers", Long.class)
                    )));
            resultPromise.complete(result);
        });

        return resultPromise.future();
    }

    @Override
    public Future<Boolean> renameAlbum(long id, String name) {
        Promise<Boolean> resultPromise = Promise.promise();

        Vertx.factory.context().executeBlocking(call -> {
            BasicDBObject query = new BasicDBObject();
            query.put("id", id);

            BasicDBObject newDoc = new BasicDBObject();
            newDoc.put("name", name);

            BasicDBObject updateDoc = new BasicDBObject();
            updateDoc.put("$set", newDoc);

            UpdateResult result = getCollection().updateOne(query, updateDoc);
            resultPromise.complete(result.getModifiedCount() == 1 && result.wasAcknowledged());
        });

        return resultPromise.future();
    }

    @Override
    public Future<Boolean> delete(long id) {
        Promise<Boolean> resultPromise = Promise.promise();

        Vertx.factory.context().executeBlocking(call -> {
            DeleteResult deleteResult = getCollection().deleteOne(eq("id", id));
            resultPromise.complete(deleteResult.getDeletedCount() == 1 && deleteResult.wasAcknowledged());
        });

        return resultPromise.future();
    }

    @Override
    public Future<Boolean> updateSharedUsers(long id, List<Long> sharedIds) {
        Promise<Boolean> resultPromise = Promise.promise();

        Vertx.factory.context().executeBlocking(call -> {
            BasicDBObject query = new BasicDBObject();
            query.put("id", id);

            BasicDBObject newDoc = new BasicDBObject();
            newDoc.put("sharedUsers", sharedIds);

            BasicDBObject updateDoc = new BasicDBObject();
            updateDoc.put("$set", newDoc);

            UpdateResult result = getCollection().updateOne(query, updateDoc);
            resultPromise.complete(result.getModifiedCount() == 1 && result.wasAcknowledged());
        });

        return resultPromise.future();
    }

    private Future<Long> getNextId() {
        Promise<Long> resultPromise = Promise.promise();

        Vertx.factory.context().executeBlocking(call -> {
            Document result = getCollection()
                    .aggregate(List.of(Aggregates.group(null, Accumulators.max("maxId", "$id"))))
                    .first();

            if (result == null) {
                resultPromise.complete(1L);
            } else {
                resultPromise.complete(result.getLong("maxId") + 1);
            }
        });

        return resultPromise.future();
    }

    private MongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB).getCollection(COLLECTION);
    }
}
