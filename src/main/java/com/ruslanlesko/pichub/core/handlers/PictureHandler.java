package com.ruslanlesko.pichub.core.handlers;

import com.ruslanlesko.pichub.core.exception.AuthorizationException;
import com.ruslanlesko.pichub.core.services.PictureService;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

import java.util.Optional;

public class PictureHandler {
    private final PictureService pictureService;

    public PictureHandler(PictureService pictureService) {
        this.pictureService = pictureService;
    }

    public void getById(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long id = Long.parseLong(request.getParam("pictureId"));
        String token = request.getHeader("Authorization");
        String clientHash = request.getHeader("If-None-Match");

        pictureService.getPictureData(token, clientHash, userId, id).setHandler(result -> {
            if (result.failed()) {
                handleFailure(result.cause(), routingContext.response());
                return;
            }

            var response = result.result();
            if (response.isNotModified()) {
                withCORSHeaders(routingContext.response().setStatusCode(304))
                        .putHeader("ETag", response.getHash())
                        .putHeader("Cache-Control", "no-cache")
                        .end();
                return;
            }
            if (!response.isNotModified() && response.getData().isEmpty()) {
                withCORSHeaders(routingContext.response().setStatusCode(404)).end();
                return;
            }

            withCORSHeaders(routingContext.response())
                    .putHeader("ETag", response.getHash())
                    .putHeader("Cache-Control", "no-cache")
                    .end(Buffer.buffer(response.getData().get()));
        });
    }

    public void add(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        Optional<Long> albumId = Optional.ofNullable(request.getParam("albumId")).map(Long::parseLong);
        String token = request.getHeader("Authorization");
        byte[] data = routingContext.getBody().getBytes();

        routingContext.vertx().executeBlocking(future -> {
            try {
                Optional<Long> id = pictureService.insertNewPicture(token, userId, albumId, data);
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

    public void rotate(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long id = Long.parseLong(request.getParam("pictureId"));
        String token = request.getHeader("Authorization");

        pictureService.rotatePicture(token, userId, id).setHandler(result -> {
            if (result.failed()) {
                handleFailure(result.cause(), routingContext.response());
                return;
            }

            if (result.result()) {
                withCORSHeaders(routingContext.response()).end();
                return;
            }

            withCORSHeaders(routingContext.response().setStatusCode(500)).end();
        });
    }

    public void deleteById(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long id = Long.parseLong(request.getParam("pictureId"));
        String token = request.getHeader("Authorization");

        pictureService.getPictureData(token, null, userId, id).setHandler(result -> {
            if (result.failed()) {
                handleFailure(result.cause(), routingContext.response());
                return;
            }

            var data = result.result().getData();
            routingContext.vertx().executeBlocking(future -> {
                try {
                    if (data.isEmpty()) {
                        withCORSHeaders(routingContext.response().setStatusCode(404)).end();
                        return;
                    }
                    if (pictureService.deletePicture(token, userId, id)) {
                        withCORSHeaders(routingContext.response()).end("{\"id\":" + id + "}");
                        return;
                    }
                    withCORSHeaders(routingContext.response().setStatusCode(500)).end();
                } finally {
                    future.complete();
                }
            });
        });
    }

    private void handleFailure(Throwable cause, HttpServerResponse response) {
        if (cause instanceof AuthorizationException) {
            withCORSHeaders(response.setStatusCode(401)).end();
            return;
        }
        withCORSHeaders(response.setStatusCode(500)).end();
    }

    private HttpServerResponse withCORSHeaders(HttpServerResponse response) {
        return response.putHeader("Access-Control-Allow-Headers", "content-type, authorization")
                .putHeader("Access-Control-Allow-Origin", "*")
                .putHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
                .putHeader("Access-Control-Max-Age", "-1");
    }
}
