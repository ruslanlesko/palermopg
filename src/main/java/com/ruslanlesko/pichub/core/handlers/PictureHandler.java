package com.ruslanlesko.pichub.core.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruslanlesko.pichub.core.exception.AuthorizationException;
import com.ruslanlesko.pichub.core.services.PictureService;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class PictureHandler {
    private static Logger logger = LoggerFactory.getLogger("Application");

    private final PictureService pictureService;

    public PictureHandler(PictureService pictureService) {
        this.pictureService = pictureService;
    }

    public void getById(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long id = Long.parseLong(request.getParam("pictureId"));
        String token = request.getHeader("Authorization");

        routingContext.vertx().executeBlocking(future -> {
            try {
                Optional<byte[]> data = pictureService.getPictureData(token, userId, id);
                if (data.isEmpty()) {
                    withCORSHeaders(routingContext.response().setStatusCode(404)).end();
                    return;
                }
                withCORSHeaders(routingContext.response()).end(Buffer.buffer(data.get()));
            } catch (AuthorizationException ex) {
                withCORSHeaders(routingContext.response().setStatusCode(401)).end();
            } finally {
                future.complete();
            }
        });
    }

    public void getIdsForUser(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        String token = request.getHeader("Authorization");

        routingContext.vertx().executeBlocking(future -> {
            try {
                List<Long> ids = pictureService.getPictureIdsForUserId(token, userId);
                logger.info("Returning {} ids for user id {}", ids.size(), userId);
                ObjectMapper mapper = new ObjectMapper();
                withCORSHeaders(routingContext.response()).end(mapper.writeValueAsString(ids));
            } catch (AuthorizationException ex) {
                withCORSHeaders(routingContext.response().setStatusCode(401)).end();
            } catch (JsonProcessingException ex) {
                withCORSHeaders(routingContext.response()).end("");
            } finally {
                future.complete();
            }
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

    public void deleteById(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long id = Long.parseLong(request.getParam("pictureId"));
        String token = request.getHeader("Authorization");

        routingContext.vertx().executeBlocking(future -> {
            try {
                Optional<byte[]> data = pictureService.getPictureData(token, userId, id);
                if (data.isEmpty()) {
                    withCORSHeaders(routingContext.response().setStatusCode(404)).end();
                    return;
                }
                if (pictureService.deletePicture(token, userId, id)) {
                    withCORSHeaders(routingContext.response()).end("{\"id\":" + id + "}");
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
                .putHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
                .putHeader("Access-Control-Max-Age", "-1");
    }
}
