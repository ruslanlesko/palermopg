package com.ruslanlesko.pichub.core.dao.impl;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.ruslanlesko.pichub.core.dao.PictureDao;
import com.ruslanlesko.pichub.core.entity.Picture;
import org.bson.Document;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class MongoPictureDao implements PictureDao {
    private final static String DB = "pichubdb";
    private final static String COLLECTION = "pictures";

//    @Inject
    MongoClient mongoClient;

    public long save(long userId, Picture picture) {
        Document document = new Document()
                .append("id", picture.getId())
                .append("path", picture.getPath())
                .append("userId", userId)
                .append("dateUploaded", picture.getDateUploaded())
                .append("dateCaptured", picture.getDateCaptured());
        getCollection().insertOne(document);
        return picture.getId();
    }

    public Optional<Picture> find(long userId, long id) {
        return Optional.empty();
    }

    public List<Long> findIdsForUser(long userId) {
        return null;
    }

    public List<Picture> findPicturesForUser(long userId) {
        return null;
    }

    private long getNextId() {
        return 42;
    }

    private MongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB).getCollection(COLLECTION);
    }
}
