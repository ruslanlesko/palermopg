package com.leskor.palermopg.core.dao.impl;

import com.leskor.palermopg.core.dao.LimitsDao;
import com.leskor.palermopg.core.exception.MissingItemException;
import com.leskor.palermopg.core.util.ReactiveSubscriber;
import com.mongodb.BasicDBObject;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;
import static com.leskor.palermopg.core.util.ReactiveSubscriber.forSinglePromise;

public class MongoLimitsDao implements LimitsDao {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private static final String DB = System.getenv("PIC_DB_NAME");
    private static final String COLLECTION = "limits";

    private final MongoClient client;

    public MongoLimitsDao(MongoClient client) {
        this.client = client;
    }

    @Override
    public Future<Void> setLimitForUser(long userId, long limit) {
        Promise<Void> resultPromise = Promise.promise();
        getLimitForUser(userId)
                .onSuccess(result -> {
                    if (result.isEmpty()) {
                        logger.info("No limit for user id {}, setting...", userId);
                        insertLimit(resultPromise, userId, limit);
                    } else {
                        logger.info("Updating limit for user id {}" ,userId);
                        updateLimit(resultPromise, userId, limit);
                    }
                }).onFailure(resultPromise::fail);
        return resultPromise.future();
    }

    private void insertLimit(Promise<Void> resultPromise, long userId, long limit) {
        Document document = new Document()
                .append("userId", userId)
                .append("limit", limit);
        getCollection().insertOne(document)
                .subscribe(ReactiveSubscriber.forVoidPromise(resultPromise, success -> true, new RuntimeException("Cannot insert limit")));
    }

    private void updateLimit(Promise<Void> resultPromise, long userId, long limit) {
        BasicDBObject query = new BasicDBObject();
        query.put("userId", userId);

        BasicDBObject newDoc = new BasicDBObject();
        newDoc.put("limit", limit);

        BasicDBObject updateDoc = new BasicDBObject();
        updateDoc.put("$set", newDoc);

        getCollection().updateOne(query, updateDoc)
                .subscribe(ReactiveSubscriber.forVoidPromise(
                        resultPromise,
                        result -> result.getModifiedCount() == 1 && result.wasAcknowledged(),
                        new MissingItemException()
                ));
    }

    @Override
    public Future<Optional<Long>> getLimitForUser(long userId) {
        Promise<Optional<Long>> resultPromise = Promise.promise();
        getCollection()
                .find(eq("userId", userId))
                .first()
                .subscribe(ReactiveSubscriber.forSinglePromise(resultPromise, doc -> Optional.of(doc.getLong("limit")), Optional.empty()));
        return resultPromise.future();
    }

    private MongoCollection<Document> getCollection() {
        return client.getDatabase(DB).getCollection(COLLECTION);
    }
}
