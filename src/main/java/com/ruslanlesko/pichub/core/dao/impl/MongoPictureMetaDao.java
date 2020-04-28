package com.ruslanlesko.pichub.core.dao.impl;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.result.DeleteResult;
import com.ruslanlesko.pichub.core.dao.PictureMetaDao;
import com.ruslanlesko.pichub.core.entity.PictureMeta;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.*;

public class MongoPictureMetaDao implements PictureMetaDao {
    private static Logger logger = LoggerFactory.getLogger("Application");

    private final static String DB = "pichubdb";
    private final static String COLLECTION = "pictures";

    private MongoClient mongoClient;

    public MongoPictureMetaDao(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    @Override
    public long save(PictureMeta pictureMeta) {
        long id = getNextId();

        Document document = new Document()
                .append("id", id)
                .append("path", pictureMeta.getPath())
                .append("pathOptimized", pictureMeta.getPathOptimized())
                .append("userId", pictureMeta.getUserId())
                .append("dateUploaded", pictureMeta.getDateUploaded())
                .append("dateCaptured", pictureMeta.getDateCaptured());

        if (pictureMeta.getAlbumId() > 0) {
            document.append("albumId", pictureMeta.getAlbumId());
        }
        getCollection().insertOne(document);
        return id;
    }

    @Override
    public Optional<PictureMeta> find(long id) {
        Document result = getCollection().find(eq("id", id)).first();

        if (result == null) {
            return Optional.empty();
        }

        return Optional.of(mapToPicture(result));
    }

    @Override
    public List<PictureMeta> findPictureMetasForUser(long userId) {
        logger.debug("Finding pictures for user id " + userId);
        List<PictureMeta> result = new ArrayList<>();
        getCollection().find(eq("userId", userId))
                .forEach((Consumer<Document>) document -> result.add(mapToPicture(document)));
        return result;
    }

    @Override
    public List<PictureMeta> findPictureMetasForAlbumId(long albumId) {
        logger.debug("Finding pictures for album id " + albumId);
        List<PictureMeta> result = new ArrayList<>();
        getCollection().find(eq("albumId", albumId))
                .forEach((Consumer<Document>) document -> result.add(mapToPicture(document)));
        return result;
    }

    @Override
    public boolean deleteById(long id) {
        DeleteResult deleteResult = getCollection().deleteOne(eq("id", id));
        if (deleteResult.getDeletedCount() == 0) {
            logger.error("Delete failed due to the absence of document to delete");
            return false;
        }
        return deleteResult.wasAcknowledged();
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
                document.getDate("dateCaptured").toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
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
