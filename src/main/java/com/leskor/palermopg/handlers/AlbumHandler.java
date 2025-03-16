package com.leskor.palermopg.handlers;

import com.leskor.palermopg.entity.Album;
import com.leskor.palermopg.entity.PictureMeta;
import com.leskor.palermopg.services.album.*;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;

import static com.leskor.palermopg.util.ApiUtils.cors;
import static com.leskor.palermopg.util.ApiUtils.handleFailure;
import static io.vertx.core.buffer.Buffer.buffer;

public class AlbumHandler {
    private final AlbumCreationService albumCreationService;
    private final AlbumFetchingService albumFetchingService;
    private final AlbumSharingService albumSharingService;
    private final AlbumUpdatingService albumUpdatingService;
    private final AlbumDeletingService albumDeletingService;

    public AlbumHandler(
            AlbumCreationService albumCreationService,
            AlbumFetchingService albumFetchingService,
            AlbumSharingService albumSharingService,
            AlbumUpdatingService albumUpdatingService, AlbumDeletingService albumDeletingService) {
        this.albumCreationService = albumCreationService;
        this.albumFetchingService = albumFetchingService;
        this.albumSharingService = albumSharingService;
        this.albumUpdatingService = albumUpdatingService;
        this.albumDeletingService = albumDeletingService;
    }

    public void add(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        JsonObject body = routingContext.getBodyAsJson();
        if (body == null || !body.containsKey("name")) {
            cors(routingContext.response().setStatusCode(404)).end();
            return;
        }
        Album newAlbum = jsonToAlbum(userId, -1, body);

        albumCreationService.addNewAlbum(newAlbum)
                .onSuccess(id -> {
                    JsonObject response = new JsonObject().put("id", id);
                    cors(routingContext.response()).end(response.encode());
                }).onFailure(cause -> handleFailure(cause, routingContext.response()));
    }

    public void getAlbumsForUser(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));

        albumFetchingService.getAlbumsForUserId(userId)
                .onSuccess(albums -> {
                    JsonArray result = new JsonArray(albums);
                    cors(routingContext.response()).end(result.encode());
                }).onFailure(cause -> handleFailure(cause, routingContext.response()));
    }

    public void getAlbumContents(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long albumId = Long.parseLong(request.getParam("albumId"));
        long userId = Long.parseLong(request.getParam("userId"));

        albumFetchingService.getPictureMetaForAlbum(userId, albumId)
                .onSuccess(result -> {
                    List<JsonObject> data = result.stream()
                            .map(this::pictureDataToJson)
                            .toList();
                    cors(routingContext.response()).end(new JsonArray(data).encode());
                }).onFailure(cause -> handleFailure(cause, routingContext.response()));
    }

    public void downloadAlbum(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long albumId = Long.parseLong(request.getParam("albumId"));
        long userId = Long.parseLong(request.getParam("userId"));
        String tokenCookie = request.getCookie("token") == null ? null : request.getCookie("token").getValue();

        albumFetchingService.download("Bearer " + tokenCookie, userId, albumId)
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
        if (body == null) {
            cors(routingContext.response().setStatusCode(400)).end();
            return;
        }

        Album album = jsonToAlbum(userId, albumId, body);

        albumUpdatingService.update(album)
                .onSuccess(result -> cors(routingContext.response()).end())
                .onFailure(cause -> handleFailure(cause, routingContext.response()));
    }

    public void deleteAlbum(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long albumId = Long.parseLong(request.getParam("albumId"));

        albumFetchingService.getAlbumsForUserId(userId)
                .onSuccess(result -> {
                    boolean notExist = result.stream().map(Album::id).noneMatch(id -> id == albumId);
                    if (notExist) {
                        cors(routingContext.response().setStatusCode(404)).end();
                        return;
                    }
                    albumDeletingService.delete(userId, albumId)
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

        albumSharingService.shareAlbum(userId, albumId, sharedUsers)
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
                .put("userId", p.userId())
                .put("pictureId", p.id());
    }

    private Album jsonToAlbum(long userId, long albumId, JsonObject json) {
        String name = json.getString("name");
        List<Long> sharedUsers = null;
        if (json.containsKey("sharedUsers")) {
            sharedUsers = extractLongArr(json.getJsonArray("sharedUsers"));
        }
        Boolean isChrono = null;
        if (json.containsKey("isChronologicalOrder")) {
            isChrono = Boolean.parseBoolean(json.getString("isChronologicalOrder"));
        }
        return new Album(albumId, userId, name, sharedUsers, isChrono, null);
    }

    public void deleteAllAlbumsForUser(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        String token = routingContext.request().getHeader("Authorization");

        albumDeletingService.deleteAll(token, userId)
                .onSuccess(deleteResult -> {
                    JsonObject response = new JsonObject().put("id", userId);
                    cors(routingContext.response()).end(response.encode());
                }).onFailure(cause -> handleFailure(cause, routingContext.response()));
    }
}
