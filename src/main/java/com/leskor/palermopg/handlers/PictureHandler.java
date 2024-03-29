package com.leskor.palermopg.handlers;

import com.leskor.palermopg.services.PictureService;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.Optional;

import static com.leskor.palermopg.util.ApiUtils.cors;
import static com.leskor.palermopg.util.ApiUtils.handleFailure;
import static io.vertx.core.buffer.Buffer.buffer;

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
        boolean fullSize = Boolean.parseBoolean(request.getParam("fullSize"));
        String tokenCookie = request.getCookie("token") == null ? null : request.getCookie("token").getValue();

        if (tokenCookie != null && !tokenCookie.isEmpty()) {
            pictureService.downloadPicture("Bearer " + tokenCookie, userId, id)
                    .onSuccess(result -> cors(routingContext.response())
                            .putHeader("Content-Disposition", "attachment; filename=\"" + id + ".jpg\"")
                            .end(buffer(result)))
                    .onFailure(cause -> handleFailure(cause, routingContext.response()));
            return;
        }

        pictureService.getPictureData(token, clientHash, userId, id, fullSize)
                .onSuccess(result -> {
                    if (result.notModified()) {
                        cors(routingContext.response().setStatusCode(304))
                                .putHeader("ETag", result.hash())
                                .putHeader("Cache-Control", "max-age=10800, public")
                                .end();
                        return;
                    }
                    cors(routingContext.response())
                            .putHeader("ETag", result.hash())
                            .putHeader("Cache-Control", "max-age=10800, public")
                            .end(buffer(result.data()));
                }).onFailure(cause -> handleFailure(cause, routingContext.response()));
    }

    public void add(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        Optional<Long> albumId = Optional.ofNullable(request.getParam("albumId")).map(Long::parseLong);
        String token = request.getHeader("Authorization");
        byte[] data = routingContext.getBody().getBytes();

        pictureService.insertNewPicture(token, userId, albumId.orElse(-1L), data)
                .onSuccess(insertResult -> {
                    JsonObject response = new JsonObject().put("id", insertResult);
                    cors(routingContext.response()).end(response.encode());
                }).onFailure(cause -> handleFailure(cause, routingContext.response()));
    }

    public void rotate(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long id = Long.parseLong(request.getParam("pictureId"));

        pictureService.rotatePicture(userId, id)
                .onSuccess(result -> cors(routingContext.response()).end())
                .onFailure(cause -> handleFailure(cause, routingContext.response()));
    }

    public void deleteById(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long id = Long.parseLong(request.getParam("pictureId"));
        String token = request.getHeader("Authorization");

        pictureService.getPictureData(token, null, userId, id, false)
                .onSuccess(result -> pictureService.deletePicture(userId, id)
                        .onSuccess(deleteResult -> {
                            JsonObject response = new JsonObject().put("id", id);
                            cors(routingContext.response()).end(response.encode());
                        }).onFailure(cause -> handleFailure(cause, routingContext.response())))
                .onFailure(cause -> handleFailure(cause, routingContext.response()));
    }
}
