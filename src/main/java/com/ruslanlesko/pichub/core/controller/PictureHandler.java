package com.ruslanlesko.pichub.core.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruslanlesko.pichub.core.dao.PictureDataDao;
import com.ruslanlesko.pichub.core.dao.PictureMetaDao;
import com.ruslanlesko.pichub.core.entity.PictureMeta;
import com.ruslanlesko.pichub.core.exception.AuthorizationException;
import com.ruslanlesko.pichub.core.meta.MetaParser;
import com.ruslanlesko.pichub.core.security.JWTParser;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
//import org.slf4j.impl.SimpleLoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PictureHandler {
//    private static Logger logger = new SimpleLoggerFactory().getLogger("PictureHandler");

    private PictureDataDao pictureDataDao;
    private PictureMetaDao pictureMetaDao;
    private JWTParser jwtParser;

    public PictureHandler(PictureDataDao pictureDataDao, PictureMetaDao pictureMetaDao, JWTParser jwtParser) {
        this.pictureDataDao = pictureDataDao;
        this.pictureMetaDao = pictureMetaDao;
        this.jwtParser = jwtParser;
    }

    public void getById(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long id = Long.parseLong(request.getParam("pictureId"));
        String token = request.getHeader("Authorization");

        try {
            checkAuthorization(token, userId);
        } catch (AuthorizationException ex) {
            withCORSHeaders(routingContext.response().setStatusCode(401)).end();
            return;
        }

        routingContext.vertx().executeBlocking(future -> {
            Optional<PictureMeta> meta = pictureMetaDao.find(id);
            if (meta.isEmpty()) {
                withCORSHeaders(routingContext.response().setStatusCode(404)).end();
                return;
            }

            if (meta.get().getUserId() != userId) {
                withCORSHeaders(routingContext.response().setStatusCode(401)).end();
                return;
            }

            Optional<byte[]> data = pictureDataDao.find(meta.get().getPath());

            if (data.isEmpty()) {
                withCORSHeaders(routingContext.response().setStatusCode(404)).end();
                return;
            }

            withCORSHeaders(routingContext.response()).end(Buffer.buffer(data.get()));

            future.complete();
        });
    }

    public void getIdsForUser(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        String token = request.getHeader("Authorization");

        try {
            checkAuthorization(token, userId);
        } catch (AuthorizationException ex) {
            withCORSHeaders(routingContext.response().setStatusCode(401)).end();
            return;
        }

        routingContext.vertx().executeBlocking(future -> {
            List<Long> result = pictureMetaDao.findPictureMetasForUser(userId).stream()
                    .filter(p -> p.getAlbumId() <= 0)
                    .sorted((picA, picB) -> {
                        LocalDateTime uploadedA = picA.getDateUploaded();
                        LocalDateTime uploadedB = picB.getDateUploaded();
                        LocalDateTime capturedA = picA.getDateCaptured();
                        LocalDateTime capturedB = picB.getDateCaptured();

                        if (uploadedA.getYear() == uploadedB.getYear()
                            && uploadedA.getDayOfYear() == uploadedB.getDayOfYear()) {
                            return capturedB.compareTo(capturedA);
                        }

                        return uploadedB.compareTo(uploadedA);
                    })
                    .map(PictureMeta::getId)
                    .collect(Collectors.toList());

//            logger.info("Extracted " + result.size() + " pictures");

            ObjectMapper mapper = new ObjectMapper();
            try {
                withCORSHeaders(routingContext.response()).end(mapper.writeValueAsString(result));
            } catch (JsonProcessingException e) {
                withCORSHeaders(routingContext.response()).end("");
            }

            future.complete();
        });
    }

    public void add(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        long userId = Long.parseLong(request.getParam("userId"));
        long albumId = Long.parseLong(Optional.ofNullable(request.getParam("albumId")).orElse("-1"));
        String token = request.getHeader("Authorization");

        try {
            checkAuthorization(token, userId);
        } catch (AuthorizationException ex) {
            withCORSHeaders(routingContext.response().setStatusCode(401)).end();
            return;
        }

        byte[] data = routingContext.getBody().getBytes();

        MetaParser metaParser = new MetaParser(data);
        LocalDateTime dateCaptured = metaParser.getDateCaptured();

        routingContext.vertx().executeBlocking(future -> {
            String path = pictureDataDao.save(data);
            long id = pictureMetaDao.save(new PictureMeta(-1, userId, albumId, path, LocalDateTime.now(), dateCaptured));

            withCORSHeaders(routingContext.response()).end(String.valueOf(id));

            future.complete();
        });
    }

    private void checkAuthorization(String token, long userId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            throw new AuthorizationException();
        }
    }

    private HttpServerResponse withCORSHeaders(HttpServerResponse response) {
        return response.putHeader("Access-Control-Allow-Headers", "content-type, authorization")
                .putHeader("Access-Control-Allow-Origin", "*")
                .putHeader("Access-Control-Request-Methods", "GET, POST, OPTIONS");
    }
}
