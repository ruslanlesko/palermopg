package com.ruslanlesko.palermopg.core.dao.impl;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.ruslanlesko.palermopg.core.dao.PictureMetaDao;
import com.ruslanlesko.palermopg.core.entity.PictureMeta;
import com.ruslanlesko.palermopg.core.exception.MissingItemException;
import com.ruslanlesko.palermopg.core.util.ReactiveSubscriber;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;
import static com.ruslanlesko.palermopg.core.util.ReactiveListSubscriber.forPromise;
import static com.ruslanlesko.palermopg.core.util.ReactiveSubscriber.forSinglePromise;
import static com.ruslanlesko.palermopg.core.util.ReactiveSubscriber.forVoidPromise;

public class MongoPictureMetaDao implements PictureMetaDao {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private final static String DB = System.getenv("PIC_DB_NAME");
    private final static String COLLECTION = "pictures";

    private final MongoClient mongoClient;

    public MongoPictureMetaDao(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    @Override
    public Future<Long> save(PictureMeta pictureMeta) {
        Promise<Long> resultPromise = Promise.promise();

        getNextIdAsync().setHandler(id -> {
            Document document = new Document()
                    .append("id", id.result())
                    .append("path", pictureMeta.getPath())
                    .append("pathOptimized", pictureMeta.getPathOptimized())
                    .append("userId", pictureMeta.getUserId())
                    .append("dateUploaded", pictureMeta.getDateUploaded())
                    .append("dateCaptured", pictureMeta.getDateCaptured())
                    .append("dateModified", pictureMeta.getDateModified());

            if (pictureMeta.getAlbumId() > 0) {
                document.append("albumId", pictureMeta.getAlbumId());
            }

            getCollection().insertOne(document).subscribe(ReactiveSubscriber.forSinglePromise(resultPromise, success -> id.result()));
        });

        return resultPromise.future();
    }

    @Override
    public Future<Optional<PictureMeta>> find(long id) {
        Promise<Optional<PictureMeta>> resultPromise = Promise.promise();

        getCollection()
                .find(eq("id", id))
                .first()
                .subscribe(forSinglePromise(resultPromise, doc -> Optional.of(mapToPicture(doc)), Optional.empty()));

        return resultPromise.future();
    }

    @Override
    public Future<List<PictureMeta>> findPictureMetasForAlbumId(long albumId) {
        logger.debug("Finding pictures for album id " + albumId);

        Promise<List<PictureMeta>> resultPromise = Promise.promise();

        getCollection()
                .find(eq("albumId", albumId))
                .subscribe(forPromise(resultPromise, this::mapToPicture));

        return resultPromise.future();
    }

    @Override
    public Future<Void> setLastModified(long id, LocalDateTime lastModified) {
        Promise<Void> resultPromise = Promise.promise();

        BasicDBObject query = new BasicDBObject();
        query.put("id", id);

        BasicDBObject newDoc = new BasicDBObject();
        newDoc.put("dateModified", lastModified);

        BasicDBObject updateDoc = new BasicDBObject();
        updateDoc.put("$set", newDoc);

        getCollection()
                .updateOne(query, updateDoc)
                .subscribe(forVoidPromise(resultPromise, res -> res.getModifiedCount() == 1 && res.wasAcknowledged(), new MissingItemException()));

        return resultPromise.future();
    }

    @Override
    public Future<Void> deleteById(long id) {
        Promise<Void> resultPromise = Promise.promise();

        getCollection()
                .deleteOne(eq("id", id))
                .subscribe(forVoidPromise(resultPromise, DeleteResult::wasAcknowledged, new MissingItemException()));

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

    private Future<Long> getNextIdAsync() {
        Promise<Long> resultPromise = Promise.promise();

        var aggregation = List.of(Aggregates.group(null, Accumulators.max("maxId", "$id")));

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
