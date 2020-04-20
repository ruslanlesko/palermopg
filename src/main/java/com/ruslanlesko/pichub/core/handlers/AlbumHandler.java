package com.ruslanlesko.pichub.core.handlers;

import com.ruslanlesko.pichub.core.dao.AlbumDao;
import com.ruslanlesko.pichub.core.dao.PictureMetaDao;
import com.ruslanlesko.pichub.core.entity.Album;
import com.ruslanlesko.pichub.core.entity.PictureMeta;
import com.ruslanlesko.pichub.core.exception.AuthorizationException;
import com.ruslanlesko.pichub.core.security.JWTParser;
import com.ruslanlesko.pichub.core.services.AlbumService;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

        routingContext.vertx().executeBlocking(future -> {
            try {
                Optional<Long> id = albumService.addNewAlbum(token, userId, name);
                if (id.isEmpty()) {
                    withCORSHeaders(routingContext.response().setStatusCode(500)).end();
                    return;
                }
                withCORSHeaders(routingContext.response()).end(String.valueOf(id.get()));
            } catch (AuthorizationException ex) {
                withCORSHeaders(routingContext.response().setStatusCode(401)).end();
            } finally {
                future.complete();
            }
        });
    }

    public void getAlbumsForUser(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        String token = request.getHeader("Authorization");

        logger.debug("Getting albums for userId " + userId);

        routingContext.vertx().executeBlocking(future -> {
            try {
                List<Album> albums = albumService.getAlbumsForUserId(token, userId);
                JsonArray result = new JsonArray(albums);
                withCORSHeaders(routingContext.response()).end(result.encode());
            } catch (AuthorizationException ex) {
                withCORSHeaders(routingContext.response().setStatusCode(401)).end();
            } finally {
                future.complete();
            }
        });
    }

    public void getAlbumContents(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long albumId = Long.parseLong(request.getParam("albumId"));
        long userId = Long.parseLong(request.getParam("userId"));
        String token = request.getHeader("Authorization");

        routingContext.vertx().executeBlocking(future -> {
            try {
                List<JsonObject> data = albumService.getPictureMetaForAlbum(token, userId, albumId).stream()
                        .map(p -> new JsonObject().put("userId", p.getUserId()).put("pictureId", p.getId()))
                        .collect(Collectors.toList());
                withCORSHeaders(routingContext.response()).end(new JsonArray(data).encode());
            } catch (AuthorizationException ex) {
                withCORSHeaders(routingContext.response().setStatusCode(401)).end();
            } finally {
                future.complete();
            }
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

        routingContext.vertx().executeBlocking(future -> {
            try {
                if (albumService.rename(token, userId, albumId, name)) {
                    withCORSHeaders(routingContext.response()).end();
                    return;
                }
                withCORSHeaders(routingContext.response().setStatusCode(404)).end();
            } catch (AuthorizationException ex) {
                withCORSHeaders(routingContext.response().setStatusCode(401)).end();
            } finally {
                future.complete();
            }
        });
    }

    public void deleteAlbum(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long albumId = Long.parseLong(request.getParam("albumId"));
        String token = request.getHeader("Authorization");

        routingContext.vertx().executeBlocking(future -> {
            try {
                boolean notExist = albumService.getAlbumsForUserId(token, userId).stream()
                        .map(Album::getId).noneMatch(id -> id == albumId);
                if (notExist) {
                    withCORSHeaders(routingContext.response().setStatusCode(404)).end();
                    return;
                }
                if (albumService.delete(token, userId, albumId)) {
                    withCORSHeaders(routingContext.response()).end("{id:" + albumId + "}");
                    return;
                }
                withCORSHeaders(routingContext.response().setStatusCode(500)).end();
            } catch (AuthorizationException ex) {
                withCORSHeaders(routingContext.response().setStatusCode(401)).end();
            } finally {
                future.complete();
            }
        });
    }

    private HttpServerResponse withCORSHeaders(HttpServerResponse response) {
        return response.putHeader("Access-Control-Allow-Headers", "content-type, authorization")
                .putHeader("Access-Control-Allow-Origin", "*")
                .putHeader("Access-Control-Request-Methods", "GET, POST, DELETE, PATCH, OPTIONS");
    }
}
