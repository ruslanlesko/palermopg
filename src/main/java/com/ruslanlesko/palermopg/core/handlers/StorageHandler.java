package com.ruslanlesko.palermopg.core.handlers;

import com.ruslanlesko.palermopg.core.services.StorageService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static com.ruslanlesko.palermopg.core.util.ApiUtils.cors;
import static com.ruslanlesko.palermopg.core.util.ApiUtils.handleFailure;
import static java.util.stream.Collectors.toList;

public class StorageHandler {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private final StorageService storageService;

    public StorageHandler(StorageService storageService) {
        this.storageService = storageService;
    }

    public void storageByUser(RoutingContext routingContext) {
        long userId = Long.parseLong(routingContext.request().getParam("userId"));
        String token = routingContext.request().getHeader("Authorization");
        logger.debug("Computing storage consumed by user {}", userId);

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
        logger.debug("Computing storage consumed by users");

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
            logger.debug("Body is invalid, returning 400");
            cors(routingContext.response().setStatusCode(400)).end();
            return;
        }
        long limit = body.getLong("limit");
        logger.debug("Setting storage limit for user {}", userId);

        storageService.setLimitForUser(token, userId, limit)
                .onSuccess(result -> cors(routingContext.response()).end())
                .onFailure(cause -> handleFailure(cause, routingContext.response()));
    }
}