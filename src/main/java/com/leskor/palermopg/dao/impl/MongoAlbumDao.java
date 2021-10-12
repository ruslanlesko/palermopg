package com.leskor.palermopg.dao.impl;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.entity.Album;
import com.leskor.palermopg.exception.MissingItemException;
import com.leskor.palermopg.util.ReactiveSubscriber;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.Optional;

import static com.leskor.palermopg.util.MongoUtils.setField;
import static com.leskor.palermopg.util.ReactiveListSubscriber.forPromise;
import static com.leskor.palermopg.util.ReactiveSubscriber.forSinglePromise;
import static com.leskor.palermopg.util.ReactiveSubscriber.forVoidPromise;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;

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

        getNextId()
                .onSuccess(nextId -> {
                    Document document = new Document()
                            .append("id", nextId)
                            .append("userId", album.userId())
                            .append("name", album.name())
                            .append("isChronologicalOrder", album.isChronologicalOrder())
                            .append("sharedUsers", album.sharedUsers());

                    getCollection().insertOne(document)
                            .subscribe(forSinglePromise(resultPromise, success -> nextId));
                }).onFailure(resultPromise::fail);

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
                        doc.getList("sharedUsers", Long.class),
                        doc.get("isChronologicalOrder") != null && doc.getBoolean("isChronologicalOrder"))), Optional.empty()));

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
                        document.getList("sharedUsers", Long.class),
                        document.get("isChronologicalOrder") != null && document.getBoolean("isChronologicalOrder"))));

        return resultPromise.future();
    }

    @Override
    public Future<Void> renameAlbum(long id, String name) {
        return setField(getCollection(), id, "name", name);
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
        return setField(getCollection(), id, "sharedUsers", sharedIds);
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

    @Override
    public Future<Void> setChronologicalOrder(long id, boolean isChronologicalOrder) {
        return setField(getCollection(), id, "isChronologicalOrder", isChronologicalOrder);
    }

    @Override
    public Future<Void> updateAlbum(Album album) {
        Promise<Void> resultPromise = Promise.promise();

        BasicDBObject query = new BasicDBObject();
        query.put("id", album.id());

        BasicDBObject newDoc = new BasicDBObject();

        if (album.name() != null) newDoc.put("name", album.name());
        if (album.isChronologicalOrder() != null) newDoc.put("isChronologicalOrder", album.isChronologicalOrder());
        if (album.sharedUsers() != null) newDoc.put("sharedUsers", album.sharedUsers());

        BasicDBObject updateDoc = new BasicDBObject();
        updateDoc.put("$set", newDoc);

        getCollection()
                .updateOne(query, updateDoc)
                .subscribe(
                        ReactiveSubscriber.forVoidPromise(
                                resultPromise, res -> res.getModifiedCount() == 1 && res.wasAcknowledged(),
                                new MissingItemException()
                        )
                );

        return resultPromise.future();
    }

    private MongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB).getCollection(COLLECTION);
    }
}
