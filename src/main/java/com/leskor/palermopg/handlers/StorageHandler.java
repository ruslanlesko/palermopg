package com.leskor.palermopg.handlers;

import com.leskor.palermopg.services.StorageService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.Arrays;
import java.util.List;

import static com.leskor.palermopg.util.ApiUtils.cors;
import static com.leskor.palermopg.util.ApiUtils.handleFailure;
import static java.util.stream.Collectors.toList;

public class StorageHandler {
    private final StorageService storageService;

    public StorageHandler(StorageService storageService) {
        this.storageService = storageService;
    }

    public void storageByUser(RoutingContext routingContext) {
        long userId = Long.parseLong(routingContext.request().getParam("userId"));
        String token = routingContext.request().getHeader("Authorization");

        storageService.findForUser(token, userId)
                .onSuccess(result -> {
                    JsonObject jsonResponse = new JsonObject()
                            .put("size", result.getSize())
                            .put("limit", result.getLimit());
                    cors(routingContext.response()).end(jsonResponse.encode());
                }).onFailure(cause -> handleFailure(cause, routingContext.response()));
    }

    public void storageByUsers(RoutingContext routingContext) {
        List<Long> ids = extractUserIds(routingContext.request().getParam("users"));
        String token = routingContext.request().getHeader("Authorization");

        if (ids.isEmpty()) {
            cors(routingContext.response()).setStatusCode(400).end("'users' param is required");
            return;
        }

        storageService.findForUsers(token, ids)
                .onSuccess(result -> {
                    JsonArray jsonArray = new JsonArray(result);
                    cors(routingContext.response()).end(jsonArray.encode());
                }).onFailure(cause -> handleFailure(cause, routingContext.response()));
    }

    private List<Long> extractUserIds(String raw) {
        return (raw == null || raw.isBlank()) ? List.of()
                : Arrays.stream(raw.split(",")).map(Long::valueOf).collect(toList());
    }

    public void setUserLimit(RoutingContext routingContext) {
        long userId = Long.parseLong(routingContext.request().getParam("userId"));
        String token = routingContext.request().getHeader("Authorization");
        JsonObject body = routingContext.getBodyAsJson();
        if (body == null || !body.containsKey("limit")) {
            cors(routingContext.response().setStatusCode(400)).end();
            return;
        }
        long limit = body.getLong("limit");

        storageService.setLimitForUser(token, userId, limit)
                .onSuccess(result -> cors(routingContext.response()).end())
                .onFailure(cause -> handleFailure(cause, routingContext.response()));
    }
}