package com.ruslanlesko.pichub.core.dao.impl;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.ruslanlesko.pichub.core.dao.PictureMetaDao;
import com.ruslanlesko.pichub.core.entity.PictureMeta;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.impl.SimpleLoggerFactory;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.*;

public class MongoPictureMetaDao implements PictureMetaDao {
    private static Logger logger = new SimpleLoggerFactory().getLogger("PictureMetaDao");

    private final static String DB = "pichubdb";
    private final static String COLLECTION = "pictures";

    private MongoClient mongoClient;

    public MongoPictureMetaDao(String url) {
        mongoClient = MongoClients.create(url);
    }

    @Override
    public long save(PictureMeta pictureMeta) {
        long id = getNextId();

        Document document = new Document()
                .append("id", id)
                .append("path", pictureMeta.getPath())
                .append("userId", pictureMeta.getUserId())
                .append("dateUploaded", pictureMeta.getDateUploaded())
                .append("dateCaptured", pictureMeta.getDateCaptured());
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

    private PictureMeta mapToPicture(Document document) {
        return new PictureMeta(
                document.getLong("id"),
                document.getLong("userId"),
                document.getString("path"),
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
