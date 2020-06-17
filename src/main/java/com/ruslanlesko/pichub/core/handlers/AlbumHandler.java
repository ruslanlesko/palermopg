package com.ruslanlesko.pichub.core.handlers;

import com.ruslanlesko.pichub.core.entity.Album;
import com.ruslanlesko.pichub.core.exception.AuthorizationException;
import com.ruslanlesko.pichub.core.exception.MissingItemException;
import com.ruslanlesko.pichub.core.services.AlbumService;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AlbumHandler {
    private static Logger logger = LoggerFactory.getLogger("Application");

    private final AlbumService albumService;

    public AlbumHandler(AlbumService albumService) {
        this.albumService = albumService;
    }

    public void add(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        String token = request.getHeader("Authorization");
        JsonObject body = routingContext.getBodyAsJson();
        if (body == null || !body.containsKey("name")) {
            logger.debug("Body is invalid, returning 404");
            withCORSHeaders(routingContext.response().setStatusCode(404)).end();
            return;
        }
        String name = body.getString("name");

        logger.debug("Creating new album for userId " + userId);

        albumService.addNewAlbum(token, userId, name).setHandler(addResult -> {
            if (addResult.failed()) {
                handleFailure(addResult.cause(), routingContext.response());
                return;
            }

            var id = addResult.result();
            withCORSHeaders(routingContext.response()).end(String.valueOf(id));
        });
    }

    public void getAlbumsForUser(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        String token = request.getHeader("Authorization");

        logger.debug("Getting albums for userId " + userId);

        albumService.getAlbumsForUserId(token, userId).setHandler(getResult -> {
            if (getResult.failed()) {
                handleFailure(getResult.cause(), routingContext.response());
                return;
            }

            var albums = getResult.result();
            JsonArray result = new JsonArray(albums);
            withCORSHeaders(routingContext.response()).end(result.encode());
        });
    }

    public void getAlbumContents(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long albumId = Long.parseLong(request.getParam("albumId"));
        long userId = Long.parseLong(request.getParam("userId"));
        String token = request.getHeader("Authorization");

        albumService.getPictureMetaForAlbum(token, userId, albumId).setHandler(getResult -> {
            if (getResult.failed()) {
                handleFailure(getResult.cause(), routingContext.response());
                return;
            }

            List<JsonObject> data = getResult.result().stream()
                    .map(p -> new JsonObject().put("userId", p.getUserId()).put("pictureId", p.getId()))
                    .collect(Collectors.toList());
            withCORSHeaders(routingContext.response()).end(new JsonArray(data).encode());
        });
    }

    public void renameAlbum(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long albumId = Long.parseLong(request.getParam("albumId"));
        String token = request.getHeader("Authorization");
        JsonObject body = routingContext.getBodyAsJson();
        if (body == null || !body.containsKey("name")) {
            logger.debug("Body is invalid, returning 400");
            withCORSHeaders(routingContext.response().setStatusCode(400)).end();
            return;
        }
        String name = body.getString("name");

        albumService.rename(token, userId, albumId, name).setHandler(renameResult -> {
            if (renameResult.failed()) {
                handleFailure(renameResult.cause(), routingContext.response());
                return;
            }

            withCORSHeaders(routingContext.response()).end();
        });
    }

    public void deleteAlbum(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long albumId = Long.parseLong(request.getParam("albumId"));
        String token = request.getHeader("Authorization");

        albumService.getAlbumsForUserId(token, userId).setHandler(getResult -> {
            if (getResult.failed()) {
                handleFailure(getResult.cause(), routingContext.response());
                return;
            }

            boolean notExist = getResult.result().stream()
                    .map(Album::getId).noneMatch(id -> id == albumId);
            if (notExist) {
                withCORSHeaders(routingContext.response().setStatusCode(404)).end();
                return;
            }
            albumService.delete(token, userId, albumId).setHandler(deleteResult -> {
                if (deleteResult.failed()) {
                    handleFailure(deleteResult.cause(), routingContext.response());
                    return;
                }

                withCORSHeaders(routingContext.response()).end("{id:" + albumId + "}");
            });
        });
    }

    public void shareAlbum(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long albumId = Long.parseLong(request.getParam("albumId"));
        String token = request.getHeader("Authorization");
        JsonObject body = routingContext.getBodyAsJson();
        List<Long> sharedUsers = extractLongArr(body.getJsonArray("sharedUsers", new JsonArray()));

        albumService.shareAlbum(token, userId, albumId, sharedUsers).setHandler(shareResult -> {
            if (shareResult.failed()) {
                handleFailure(shareResult.cause(), routingContext.response());
                return;
            }

            withCORSHeaders(routingContext.response()).end();
        });
    }

    private List<Long> extractLongArr(JsonArray arr) {
        List<Long> result = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            if (arr.getLong(i) != null) {
                result.add(arr.getLong(i));
            }
        }
        return result;
    }

    private void handleFailure(Throwable cause, HttpServerResponse response) {
        if (cause instanceof AuthorizationException) {
            withCORSHeaders(response.setStatusCode(401)).end();
            return;
        }
        if (cause instanceof MissingItemException) {
            withCORSHeaders(response.setStatusCode(404)).end();
        }
        withCORSHeaders(response.setStatusCode(500)).end();
    }

    private HttpServerResponse withCORSHeaders(HttpServerResponse response) {
        return response.putHeader("Access-Control-Allow-Headers", "content-type, authorization")
                .putHeader("Access-Control-Allow-Origin", "*")
                .putHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PATCH, OPTIONS")
                .putHeader("Access-Control-Max-Age", "-1");
    }
}
