package com.leskor.palermopg.util;

import com.leskor.palermopg.exception.MissingItemException;
import com.mongodb.BasicDBObject;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.bson.Document;

public class MongoUtils {
    public static <T> Future<Void> setField(MongoCollection<Document> collection, long id, String field, T value) {
        Promise<Void> resultPromise = Promise.promise();

        BasicDBObject query = new BasicDBObject();
        query.put("id", id);

        BasicDBObject newDoc = new BasicDBObject();
        newDoc.put(field, value);

        BasicDBObject updateDoc = new BasicDBObject();
        updateDoc.put("$set", newDoc);

        collection
                .updateOne(query, updateDoc)
                .subscribe(
                        ReactiveSubscriber.forVoidPromise(
                                resultPromise, res -> res.getModifiedCount() == 1 && res.wasAcknowledged(),
                                new MissingItemException()
                        )
                );

        return resultPromise.future();
    }
}
