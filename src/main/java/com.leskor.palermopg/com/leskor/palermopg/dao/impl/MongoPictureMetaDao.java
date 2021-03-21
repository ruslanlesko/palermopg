package com.leskor.palermopg.dao.impl;

import com.leskor.palermopg.dao.PictureMetaDao;
import com.leskor.palermopg.entity.PictureMeta;
import com.leskor.palermopg.exception.MissingItemException;
import com.leskor.palermopg.util.ReactiveListSubscriber;
import com.leskor.palermopg.util.ReactiveSubscriber;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.bson.Document;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static com.leskor.palermopg.util.MongoUtils.setField;
import static com.mongodb.client.model.Filters.eq;

public class MongoPictureMetaDao implements PictureMetaDao {
    private final static String DB = System.getenv("PIC_DB_NAME");
    private final static String COLLECTION = "pictures";

    private final MongoClient mongoClient;

    public MongoPictureMetaDao(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    @Override
    public Future<Long> save(PictureMeta pictureMeta) {
        Promise<Long> resultPromise = Promise.promise();

        getNextIdAsync()
                .onSuccess(id -> {
                    Document document = new Document()
                            .append("id", id)
                            .append("size", pictureMeta.getSize())
                            .append("path", pictureMeta.getPath())
                            .append("pathOptimized", pictureMeta.getPathOptimized())
                            .append("userId", pictureMeta.getUserId())
                            .append("dateUploaded", pictureMeta.getDateUploaded())
                            .append("dateCaptured", pictureMeta.getDateCaptured())
                            .append("dateModified", pictureMeta.getDateModified());

                    if (pictureMeta.getAlbumId() > 0) {
                        document.append("albumId", pictureMeta.getAlbumId());
                    }

                    getCollection().insertOne(document).subscribe(ReactiveSubscriber.forSinglePromise(resultPromise, success -> id));
                }).onFailure(resultPromise::fail);

        return resultPromise.future();
    }

    @Override
    public Future<Optional<PictureMeta>> find(long id) {
        Promise<Optional<PictureMeta>> resultPromise = Promise.promise();

        getCollection()
                .find(eq("id", id))
                .first()
                .subscribe(ReactiveSubscriber.forSinglePromise(resultPromise, doc -> Optional.of(mapToPicture(doc)), Optional.empty()));

        return resultPromise.future();
    }

    @Override
    public Future<List<PictureMeta>> findForAlbumId(long albumId) {
        Promise<List<PictureMeta>> resultPromise = Promise.promise();

        getCollection()
                .find(eq("albumId", albumId))
                .subscribe(ReactiveListSubscriber.forPromise(resultPromise, this::mapToPicture));

        return resultPromise.future();
    }

    @Override
    public Future<Void> setLastModified(long id, LocalDateTime lastModified) {
        return setField(getCollection(), id, "dateModified", lastModified);
    }

    @Override
    public Future<Void> deleteById(long id) {
        Promise<Void> resultPromise = Promise.promise();

        getCollection()
                .deleteOne(eq("id", id))
                .subscribe(ReactiveSubscriber.forVoidPromise(resultPromise, DeleteResult::wasAcknowledged, new MissingItemException()));

        return resultPromise.future();
    }

    @Override
    public Future<List<PictureMeta>> findPictureMetasForUserId(long userId) {
        Promise<List<PictureMeta>> resultPromise = Promise.promise();

        getCollection()
                .find(eq("userId", userId))
                .subscribe(ReactiveListSubscriber.forPromise(resultPromise, this::mapToPicture));

        return resultPromise.future();
    }

    @Override
    public Future<Void> setDownloadCode(long id, String code) {
        return setField(getCollection(), id, "downloadCode", code);
    }

    private PictureMeta mapToPicture(Document document) {
        Integer albumId = document.getInteger("albumId");

        return new PictureMeta(
                document.getInteger("id"),
                document.getInteger("userId"),
                albumId == null ? -1 : albumId,
                document.getInteger("size") == null ? -1L : document.getInteger("size"),
                document.getString("path"),
                document.getString("pathOptimized"),
                document.getDate("dateUploaded").toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                document.getDate("dateCaptured").toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                document.get("dateModified") == null ? document.getDate("dateUploaded").toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : document.getDate("dateModified").toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                document.getString("downloadCode") == null ? "" : document.getString("downloadCode"));
    }

    private Future<Long> getNextIdAsync() {
        Promise<Long> resultPromise = Promise.promise();

        var aggregation = List.of(Aggregates.group(null, Accumulators.max("maxId", "$id")));

        getCollection()
                .aggregate(aggregation)
                .first()
                .subscribe(ReactiveSubscriber.forSinglePromise(resultPromise, doc -> doc.getInteger("maxId").longValue() + 1, 1L));

        return resultPromise.future();
    }

    private MongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB).getCollection(COLLECTION);
    }
}
