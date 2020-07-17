package com.ruslanlesko.palermopg.core.dao.impl;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.ruslanlesko.palermopg.core.dao.AlbumDao;
import com.ruslanlesko.palermopg.core.entity.Album;
import com.ruslanlesko.palermopg.core.exception.MissingItemException;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;
import static com.ruslanlesko.palermopg.core.util.ReactiveListSubscriber.forPromise;
import static com.ruslanlesko.palermopg.core.util.ReactiveSubscriber.forSinglePromise;
import static com.ruslanlesko.palermopg.core.util.ReactiveSubscriber.forVoidPromise;

public class MongoAlbumDao implements AlbumDao {
    private final static String DB = System.getenv("PIC_DB_NAME");
    private final static String COLLECTION = "albums";

    private final MongoClient mongoClient;

    public MongoAlbumDao(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    @Override
    public Future<Long> save(Album album) {
        Promise<Long> resultPromise = Promise.promise();

        getNextId().setHandler(nextIdResult -> {
            if (nextIdResult.failed()) {
                resultPromise.fail(nextIdResult.cause());
                return;
            }

            var nextId = nextIdResult.result();

            Document document = new Document()
                    .append("id", nextId)
                    .append("userId", album.getUserId())
                    .append("name", album.getName());

            getCollection().insertOne(document)
                    .subscribe(forSinglePromise(resultPromise, success -> nextId));
        });

        return resultPromise.future();
    }

    @Override
    public Future<Optional<Album>> findById(long id) {
        Promise<Optional<Album>> resultPromise = Promise.promise();

        getCollection()
                .find(eq("id", id))
                .first()
                .subscribe(forSinglePromise(resultPromise, doc -> Optional.of(new Album(
                        id,
                        doc.getLong("userId"),
                        doc.getString("name"),
                        doc.getList("sharedUsers", Long.class)

                )), Optional.empty()));

        return resultPromise.future();
    }

    @Override
    public Future<List<Album>> findAlbumsForUserId(long userId) {
        Promise<List<Album>> resultPromise = Promise.promise();

        getCollection().find(or(eq("userId", userId), eq("sharedUsers", userId)))
                .subscribe(forPromise(resultPromise, document -> new Album(
                        document.getLong("id"),
                        document.getLong("userId"),
                        document.getString("name"),
                        document.getList("sharedUsers", Long.class)
                )));

        return resultPromise.future();
    }

    @Override
    public Future<Void> renameAlbum(long id, String name) {
        Promise<Void> resultPromise = Promise.promise();

        BasicDBObject query = new BasicDBObject();
        query.put("id", id);

        BasicDBObject newDoc = new BasicDBObject();
        newDoc.put("name", name);

        BasicDBObject updateDoc = new BasicDBObject();
        updateDoc.put("$set", newDoc);

        getCollection().updateOne(query, updateDoc)
                .subscribe(forVoidPromise(
                        resultPromise,
                        result ->  result.getModifiedCount() == 1 && result.wasAcknowledged(),
                        new MissingItemException()));

        return resultPromise.future();
    }

    @Override
    public Future<Void> delete(long id) {
        Promise<Void> resultPromise = Promise.promise();

        getCollection().deleteOne(eq("id", id))
                .subscribe(forVoidPromise(
                        resultPromise,
                        deleteResult -> deleteResult.getDeletedCount() == 1 && deleteResult.wasAcknowledged(),
                        new MissingItemException()));

        return resultPromise.future();
    }

    @Override
    public Future<Void> updateSharedUsers(long id, List<Long> sharedIds) {
        Promise<Void> resultPromise = Promise.promise();

        BasicDBObject query = new BasicDBObject();
        query.put("id", id);

        BasicDBObject newDoc = new BasicDBObject();
        newDoc.put("sharedUsers", sharedIds);

        BasicDBObject updateDoc = new BasicDBObject();
        updateDoc.put("$set", newDoc);

        getCollection().updateOne(query, updateDoc)
                .subscribe(forVoidPromise(
                        resultPromise,
                        result -> result.getModifiedCount() == 1 && result.wasAcknowledged(),
                        new MissingItemException()));
        return resultPromise.future();
    }

    private Future<Long> getNextId() {
        Promise<Long> resultPromise = Promise.promise();

        List<Bson> aggregation = List.of(Aggregates.group(null, Accumulators.max("maxId", "$id")));

        getCollection()
                .aggregate(aggregation)
                .first()
                .subscribe(forSinglePromise(resultPromise, doc -> doc.getLong("maxId") + 1, 1L));

        return resultPromise.future();
    }

    private MongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB).getCollection(COLLECTION);
    }
}
