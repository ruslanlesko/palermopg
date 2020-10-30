package com.ruslanlesko.palermopg.core.handlers;

import com.ruslanlesko.palermopg.core.services.StorageService;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.ruslanlesko.palermopg.core.util.ApiUtils.cors;
import static com.ruslanlesko.palermopg.core.util.ApiUtils.handleFailure;

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

        storageService.findForUser(token, userId).setHandler(result -> {
            if (result.failed()) {
                handleFailure(result.cause(), routingContext.response());
                return;
            }

            var storageConsumption = result.result();
            JsonObject jsonResponse = new JsonObject()
                    .put("size", storageConsumption.getSize())
                    .put("limit", storageConsumption.getLimit());

            cors(routingContext.response()).end(jsonResponse.encode());
        });
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

        storageService.setLimitForUser(token, userId, limit).setHandler(result -> {
            if (result.failed()) {
                handleFailure(result.cause(), routingContext.response());
                return;
            }
            cors(routingContext.response()).end();
        });
    }
}