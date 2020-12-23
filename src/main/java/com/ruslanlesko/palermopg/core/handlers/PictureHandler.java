package com.ruslanlesko.palermopg.core.handlers;

import com.ruslanlesko.palermopg.core.services.PictureService;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.Optional;

import static com.ruslanlesko.palermopg.core.util.ApiUtils.cors;
import static com.ruslanlesko.palermopg.core.util.ApiUtils.handleFailure;

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
        String downloadCode = request.getParam("downloadCode");

        if (downloadCode != null && !downloadCode.isEmpty()) {
            pictureService.downloadPicture(userId, id, downloadCode).setHandler(result -> {
                if (result.failed()) {
                    handleFailure(result.cause(), routingContext.response());
                    return;
                }

                var data = result.result();
                cors(routingContext.response())
                        .putHeader("Content-Disposition", "attachment; filename=\"" + id + ".jpg\"")
                        .end(Buffer.buffer(data));
            });
            return;
        }

        pictureService.getPictureData(token, clientHash, userId, id).setHandler(result -> {
            if (result.failed()) {
                handleFailure(result.cause(), routingContext.response());
                return;
            }

            var response = result.result();
            if (response.isNotModified()) {
                cors(routingContext.response().setStatusCode(304))
                        .putHeader("ETag", response.getHash())
                        .putHeader("Cache-Control", "no-cache")
                        .end();
                return;
            }
            cors(routingContext.response())
                    .putHeader("ETag", response.getHash())
                    .putHeader("Cache-Control", "no-cache")
                    .end(Buffer.buffer(response.getData()));
        });
    }

    public void add(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        Optional<Long> albumId = Optional.ofNullable(request.getParam("albumId")).map(Long::parseLong);
        String token = request.getHeader("Authorization");
        byte[] data = routingContext.getBody().getBytes();

        pictureService.insertNewPicture(token, userId, albumId, data).setHandler(insertResult -> {
            if (insertResult.failed()) {
                handleFailure(insertResult.cause(), routingContext.response());
                return;
            }
            JsonObject response = new JsonObject().put("id", insertResult.result());
            cors(routingContext.response()).end(response.encode());
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
            cors(routingContext.response()).end();
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

            pictureService.deletePicture(token, userId, id).setHandler(deleteResult -> {
                if (deleteResult.failed()) {
                    handleFailure(deleteResult.cause(), routingContext.response());
                    return;
                }
                JsonObject response = new JsonObject().put("id", id);
                cors(routingContext.response()).end(response.encode());
            });
        });
    }
}
