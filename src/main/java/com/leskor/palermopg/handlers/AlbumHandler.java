package com.leskor.palermopg.handlers;

import com.leskor.palermopg.entity.Album;
import com.leskor.palermopg.entity.PictureMeta;
import com.leskor.palermopg.services.AlbumService;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.leskor.palermopg.util.ApiUtils.cors;
import static com.leskor.palermopg.util.ApiUtils.handleFailure;
import static io.vertx.core.buffer.Buffer.buffer;

public class AlbumHandler {
    private final AlbumService albumService;

    public AlbumHandler(AlbumService albumService) {
        this.albumService = albumService;
    }

    public void add(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        JsonObject body = routingContext.getBodyAsJson();
        if (body == null || !body.containsKey("name")) {
            cors(routingContext.response().setStatusCode(404)).end();
            return;
        }
        Album newAlbum = jsonToAlbum(userId, body);

        albumService.addNewAlbum(newAlbum)
                .onSuccess(id -> {
                    JsonObject response = new JsonObject().put("id", id);
                    cors(routingContext.response()).end(response.encode());
                }).onFailure(cause -> handleFailure(cause, routingContext.response()));
    }

    public void getAlbumsForUser(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));

        albumService.getAlbumsForUserId(userId)
                .onSuccess(albums -> {
                    JsonArray result = new JsonArray(albums);
                    cors(routingContext.response()).end(result.encode());
                }).onFailure(cause -> handleFailure(cause, routingContext.response()));
    }

    public void getAlbumContents(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long albumId = Long.parseLong(request.getParam("albumId"));
        long userId = Long.parseLong(request.getParam("userId"));

        albumService.getPictureMetaForAlbum(userId, albumId)
                .onSuccess(result -> {
                    List<JsonObject> data = result.stream()
                            .map(this::pictureDataToJson)
                            .collect(Collectors.toList());
                    cors(routingContext.response()).end(new JsonArray(data).encode());
                }).onFailure(cause -> handleFailure(cause, routingContext.response()));
    }

    public void downloadAlbum(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long albumId = Long.parseLong(request.getParam("albumId"));
        long userId = Long.parseLong(request.getParam("userId"));
        String code = request.getParam("code");

        albumService.download(userId, albumId, code)
                .onSuccess(data -> cors(routingContext.response())
                        .putHeader("Content-Disposition", "attachment; filename=\"" + albumId + ".zip\"")
                        .end(buffer(data)))
                .onFailure(cause -> handleFailure(cause, routingContext.response()));
    }

    public void updateAlbum(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long albumId = Long.parseLong(request.getParam("albumId"));
        JsonObject body = routingContext.getBodyAsJson();
        if (body == null || !body.containsKey("name") && !body.containsKey("isChronologicalOrder")) {
            cors(routingContext.response().setStatusCode(400)).end();
            return;
        }
        String name = body.getString("name");
        String orderStr = body.getString("isChronologicalOrder");

        var resultFuture = orderStr == null ? albumService.rename(userId, albumId, name)
                : albumService.setChronologicalOrder(userId, albumId, Boolean.parseBoolean(orderStr));

        resultFuture
                .onSuccess(result -> cors(routingContext.response()).end())
                .onFailure(cause -> handleFailure(cause, routingContext.response()));
    }

    public void deleteAlbum(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long albumId = Long.parseLong(request.getParam("albumId"));

        albumService.getAlbumsForUserId(userId)
                .onSuccess(result -> {
                    boolean notExist = result.stream().map(Album::getId).noneMatch(id -> id == albumId);
                    if (notExist) {
                        cors(routingContext.response().setStatusCode(404)).end();
                        return;
                    }
                    albumService.delete(userId, albumId)
                            .onSuccess(deleteResult -> {
                                JsonObject response = new JsonObject().put("id", albumId);
                                cors(routingContext.response()).end(response.encode());
                            }).onFailure(cause -> handleFailure(cause, routingContext.response()));
                }).onFailure(cause -> handleFailure(cause, routingContext.response()));
    }

    public void shareAlbum(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long albumId = Long.parseLong(request.getParam("albumId"));
        JsonObject body = routingContext.getBodyAsJson();
        List<Long> sharedUsers = extractLongArr(body.getJsonArray("sharedUsers", new JsonArray()));

        albumService.shareAlbum(userId, albumId, sharedUsers)
                .onSuccess(result -> cors(routingContext.response()).end())
                .onFailure(cause -> handleFailure(cause, routingContext.response()));
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

    private JsonObject pictureDataToJson(PictureMeta p) {
        return new JsonObject()
                .put("userId", p.getUserId())
                .put("pictureId", p.getId())
                .put("downloadCode", p.getDownloadCode());
    }

    private Album jsonToAlbum(long userId, JsonObject json) {
        String name = json.getString("name");
        List<Long> sharedUsers = null;
        if (json.containsKey("sharedUsers")) {
            sharedUsers = extractLongArr(json.getJsonArray("sharedUsers"));
        }
        Boolean isChrono = null;
        if (json.containsKey("isChronologicalOrder")) {
            isChrono = Boolean.parseBoolean(json.getString("isChronologicalOrder"));
        }
        return new Album(-1, userId, name, sharedUsers, null, isChrono);
    }
}
