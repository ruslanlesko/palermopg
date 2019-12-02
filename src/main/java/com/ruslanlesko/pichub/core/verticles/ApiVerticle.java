package com.ruslanlesko.pichub.core.verticles;

import com.ruslanlesko.pichub.core.controller.PictureHandler;
import com.ruslanlesko.pichub.core.dao.impl.FilePictureDataDao;
import com.ruslanlesko.pichub.core.dao.impl.MongoPictureMetaDao;
import com.ruslanlesko.pichub.core.security.JWTParser;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.impl.SimpleLoggerFactory;

public class ApiVerticle extends AbstractVerticle {
    private static Logger logger = new SimpleLoggerFactory().getLogger("ApiVerticle");

    @Override
    public void start(Promise<Void> startPromise) {
        MongoPictureMetaDao mongoPictureMetaDao = new MongoPictureMetaDao("mongodb://pcusr:pcpwd@localhost/pichubdb");

        PictureHandler pictureHandler = new PictureHandler(new FilePictureDataDao(), mongoPictureMetaDao, new JWTParser());

        Router router = Router.router(vertx);
        router.route("/pic/:userId*").handler(BodyHandler.create());
        router.options().handler(r ->r.response()
                .putHeader("Access-Control-Allow-Headers", "content-type, authorization")
                .putHeader("Access-Control-Allow-Origin", "*")
                .putHeader("Access-Control-Request-Methods", "GET, POST, OPTIONS")
                .end()
        );
        router.get("/pic/:userId").produces("application/json").handler(pictureHandler::getIdsForUser);
        router.get("/pic/:userId/:pictureId").produces("image/jpeg").handler(pictureHandler::getById);
        router.post("/pic/:userId").consumes("image/jpeg").handler(pictureHandler::add);

        logger.debug("Creating HTTP server on 8081 port");
        vertx.createHttpServer().requestHandler(router)
                .listen(8081, result -> {
            if (result.succeeded()) {
                logger.debug("HTTP server was created");
                startPromise.complete();
            } else {
                startPromise.fail(result.cause());
            }
        });
    }
}
