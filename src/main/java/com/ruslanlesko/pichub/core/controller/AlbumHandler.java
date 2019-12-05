package com.ruslanlesko.pichub.core.controller;

import com.ruslanlesko.pichub.core.dao.AlbumDao;
import com.ruslanlesko.pichub.core.dao.PictureMetaDao;
import com.ruslanlesko.pichub.core.entity.Album;
import com.ruslanlesko.pichub.core.exception.AuthorizationException;
import com.ruslanlesko.pichub.core.security.JWTParser;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.impl.SimpleLoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AlbumHandler {
    private static Logger logger = new SimpleLoggerFactory().getLogger("AlbumHandler");

    private final AlbumDao albumDao;
    private final PictureMetaDao pictureMetaDao;
    private final JWTParser jwtParser;

    public AlbumHandler(AlbumDao albumDao, PictureMetaDao pictureMetaDao, JWTParser jwtParser) {
        this.albumDao = albumDao;
        this.pictureMetaDao = pictureMetaDao;
        this.jwtParser = jwtParser;
    }

    public void add(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        String token = request.getHeader("Authorization");

        logger.debug("Creating new album for userId " + userId);

        try {
            checkAuthorization(token, userId);
        } catch (AuthorizationException ex) {
            withCORSHeaders(routingContext.response().setStatusCode(401)).end();
            return;
        }

        JsonObject body = routingContext.getBodyAsJson();
        if (body == null || !body.containsKey("name")) {
            logger.debug("Body is invalid, returning 404");
            withCORSHeaders(routingContext.response().setStatusCode(404)).end();
            return;
        }

        Album newAlbum = new Album(-1, userId, body.getString("name"));

        routingContext.vertx().executeBlocking(future -> {
            long id = albumDao.save(newAlbum);
            withCORSHeaders(routingContext.response()).end(String.valueOf(id));

            future.complete();
        });
    }

    public void getAlbumsForUser(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        String token = request.getHeader("Authorization");

        logger.debug("Getting albums for userId " + userId);

        try {
            checkAuthorization(token, userId);
        } catch (AuthorizationException ex) {
            withCORSHeaders(routingContext.response().setStatusCode(401)).end();
            return;
        }

        routingContext.vertx().executeBlocking(future -> {
            List<Album> albums = albumDao.findAlbumsForUserId(userId);
            JsonArray result = new JsonArray(albums);
            withCORSHeaders(routingContext.response()).end(result.encode());

            future.complete();
        });
    }

    public void getAlbumContents(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long albumId = Long.parseLong(request.getParam("albumId"));
        long userId = Long.parseLong(request.getParam("userId"));
        String token = request.getHeader("Authorization");

        try {
            checkAuthorization(token, userId);
        } catch (AuthorizationException ex) {
            withCORSHeaders(routingContext.response().setStatusCode(401)).end();
            return;
        }

        routingContext.vertx().executeBlocking(future -> {
            Optional<Album> albumOptional = albumDao.findById(albumId);
            if (albumOptional.isEmpty() || albumOptional.get().getUserId() != userId) {
                withCORSHeaders(routingContext.response().setStatusCode(404)).end();
                return;
            }

            List<JsonObject> data = pictureMetaDao.findPictureMetasForAlbumId(albumId).stream()
                    .sorted((picA, picB) -> {
                        LocalDateTime uploadedA = picA.getDateUploaded();
                        LocalDateTime uploadedB = picB.getDateUploaded();
                        LocalDateTime capturedA = picA.getDateCaptured();
                        LocalDateTime capturedB = picB.getDateCaptured();

                        if (uploadedA.getYear() == uploadedB.getYear()
                            && uploadedA.getDayOfYear() == uploadedB.getDayOfYear()) {
                            return capturedB.compareTo(capturedA);
                        }

                        return uploadedB.compareTo(uploadedA);
                    })
                    .map(p -> new JsonObject().put("userId", p.getUserId()).put("pictureId", p.getId()))
                    .collect(Collectors.toList());

            withCORSHeaders(routingContext.response()).end(new JsonArray(data).encode());

            future.complete();
        });
    }

    private void checkAuthorization(String token, long userId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            throw new AuthorizationException();
        }
    }

    private HttpServerResponse withCORSHeaders(HttpServerResponse response) {
        return response.putHeader("Access-Control-Allow-Headers", "content-type, authorization")
                .putHeader("Access-Control-Allow-Origin", "*")
                .putHeader("Access-Control-Request-Methods", "GET, POST, OPTIONS");
    }
}
