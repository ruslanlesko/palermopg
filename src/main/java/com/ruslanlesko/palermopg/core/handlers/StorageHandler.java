package com.ruslanlesko.palermopg.core.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import static com.ruslanlesko.palermopg.core.util.ApiUtils.cors;

import com.ruslanlesko.palermopg.core.services.StorageService;

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
            JsonObject jsonReponse = new JsonObject()
                    .put("size", storageConsumption.getSize())
                    .put("limit", storageConsumption.getLimit());

            cors(routingContext.response()).end(jsonReponse.encode());
        });
    }
}