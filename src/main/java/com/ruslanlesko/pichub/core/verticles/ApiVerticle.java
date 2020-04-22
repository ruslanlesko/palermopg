package com.ruslanlesko.pichub.core.verticles;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.ruslanlesko.pichub.core.dao.AlbumDao;
import com.ruslanlesko.pichub.core.dao.PictureDataDao;
import com.ruslanlesko.pichub.core.dao.PictureMetaDao;
import com.ruslanlesko.pichub.core.handlers.AlbumHandler;
import com.ruslanlesko.pichub.core.handlers.PictureHandler;
import com.ruslanlesko.pichub.core.dao.impl.FilePictureDataDao;
import com.ruslanlesko.pichub.core.dao.impl.MongoAlbumDao;
import com.ruslanlesko.pichub.core.dao.impl.MongoPictureMetaDao;
import com.ruslanlesko.pichub.core.security.JWTParser;
import com.ruslanlesko.pichub.core.services.AlbumService;
import com.ruslanlesko.pichub.core.services.PictureService;
import com.ruslanlesko.pichub.core.services.impl.AlbumServiceImpl;
import com.ruslanlesko.pichub.core.services.impl.PictureServiceImpl;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiVerticle extends AbstractVerticle {
    private static Logger logger = LoggerFactory.getLogger("Application");

    @Override
    public void start(Promise<Void> startPromise) {
        final String dbUrl = System.getenv("PIC_DB");
        final MongoClient mongoClient = MongoClients.create(dbUrl);

        PictureDataDao pictureDataDao = new FilePictureDataDao();
        PictureMetaDao pictureMetaDao = new MongoPictureMetaDao(mongoClient);
        AlbumDao albumDao = new MongoAlbumDao(mongoClient);
        JWTParser jwtParser = new JWTParser();

        PictureService pictureService = new PictureServiceImpl(pictureMetaDao, pictureDataDao, jwtParser);
        AlbumService albumService = new AlbumServiceImpl(pictureMetaDao, pictureDataDao, albumDao, jwtParser);

        PictureHandler pictureHandler = new PictureHandler(pictureService);
        AlbumHandler albumHandler = new AlbumHandler(albumService);

        Router router = Router.router(vertx);
        router.route("/pic/:userId*").handler(BodyHandler.create());
        router.route("/album/:userId*").handler(BodyHandler.create());
        router.options().handler(r ->r.response()
                .putHeader("Access-Control-Allow-Headers", "content-type, authorization")
                .putHeader("Access-Control-Allow-Origin", "*")
                .putHeader("Access-Control-Allow-Methods", "GET, DELETE, PATCH, POST, OPTIONS")
                .putHeader("Access-Control-Max-Age", "-1")
                .end()
        );
        router.get("/pic/:userId").produces("application/json").handler(pictureHandler::getIdsForUser);
        router.get("/pic/:userId/:pictureId").produces("image/jpeg").handler(pictureHandler::getById);
        router.delete("/pic/:userId/:pictureId").produces("application/json").handler(pictureHandler::deleteById);
        router.post("/pic/:userId").consumes("image/jpeg").handler(pictureHandler::add);

        router.get("/album/:userId").produces("application/json").handler(albumHandler::getAlbumsForUser);
        router.get("/album/:userId/:albumId").produces("application/json").handler(albumHandler::getAlbumContents);
        router.post("/album/:userId").consumes("application/json").handler(albumHandler::add);
        router.patch("/album/:userId/:albumId").consumes("application/json").handler(albumHandler::renameAlbum);
        router.delete("/album/:userId/:albumId").produces("application/json").handler(albumHandler::deleteAlbum);

        logger.debug("Creating HTTP server on 8081 port");
        vertx.createHttpServer().requestHandler(router)
                .listen(8081, result -> {
            if (result.succeeded()) {
                logger.debug("HTTP server was created");
                startPromise.complete();
            } else {
                logger.error("Failed to deploy HTTP server on port 8081: {}", result.cause().getMessage());
                startPromise.fail(result.cause());
            }
        });
    }
}
