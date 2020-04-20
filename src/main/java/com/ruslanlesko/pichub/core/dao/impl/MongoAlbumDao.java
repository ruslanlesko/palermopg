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
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.*;

public class MongoAlbumDao implements AlbumDao {
    private final static String DB = "pichubdb";
    private final static String COLLECTION = "albums";

    private MongoClient mongoClient;

    public MongoAlbumDao(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    @Override
    public long save(Album album) {
        long id = getNextId();

        Document document = new Document()
                .append("id", id)
                .append("userId", album.getUserId())
                .append("name", album.getName());
        getCollection().insertOne(document);
        return id;
    }

    @Override
    public Optional<Album> findById(long id) {
        Document result = getCollection().find(eq("id", id)).first();

        if (result == null) {
            return Optional.empty();
        }

        return Optional.of(new Album(id, result.getLong("userId"), result.getString("name")));
    }

    @Override
    public List<Album> findAlbumsForUserId(long userId) {
        List<Album> result = new ArrayList<>();
        getCollection().find(eq("userId", userId))
                .forEach((Consumer<Document>) document -> result.add(new Album(
                        document.getLong("id"),
                        document.getLong("userId"),
                        document.getString("name")
                )));
        return result;
    }

    @Override
    public boolean renameAlbum(long id, String name) {
        BasicDBObject query = new BasicDBObject();
        query.put("id", id);

        BasicDBObject newDoc = new BasicDBObject();
        newDoc.put("name", name);

        BasicDBObject updateDoc = new BasicDBObject();
        updateDoc.put("$set", newDoc);

        UpdateResult result = getCollection().updateOne(query, updateDoc);
        return result.getModifiedCount() == 1 && result.wasAcknowledged();
    }

    @Override
    public boolean delete(long id) {
        DeleteResult deleteResult = getCollection().deleteOne(eq("id", id));
        return deleteResult.getDeletedCount() == 1 && deleteResult.wasAcknowledged();
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
