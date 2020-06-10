package com.ruslanlesko.pichub.core.verticles;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.ruslanlesko.pichub.core.dao.AlbumDao;
import com.ruslanlesko.pichub.core.dao.PictureDataDao;
import com.ruslanlesko.pichub.core.dao.PictureMetaDao;
import com.ruslanlesko.pichub.core.dao.impl.FilePictureDataDao;
import com.ruslanlesko.pichub.core.dao.impl.MongoAlbumDao;
import com.ruslanlesko.pichub.core.dao.impl.MongoPictureMetaDao;
import com.ruslanlesko.pichub.core.handlers.AlbumHandler;
import com.ruslanlesko.pichub.core.handlers.PictureHandler;
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
    private static final String JSON_FORMAT = "application/json";
    private static final String JPEG_FORMAT = "image/jpeg";

    private static final Logger logger = LoggerFactory.getLogger("Application");

    private PictureHandler pictureHandler;
    private AlbumHandler albumHandler;

    private void setup() {
        final String dbUrl = System.getenv("PIC_DB");
        final MongoClient mongoClient = MongoClients.create(dbUrl);

        PictureDataDao pictureDataDao = new FilePictureDataDao();
        PictureMetaDao pictureMetaDao = new MongoPictureMetaDao(mongoClient);
        AlbumDao albumDao = new MongoAlbumDao(mongoClient);
        JWTParser jwtParser = new JWTParser();

        PictureService pictureService = new PictureServiceImpl(pictureMetaDao, pictureDataDao, albumDao, jwtParser);
        AlbumService albumService = new AlbumServiceImpl(pictureMetaDao, albumDao, pictureService, jwtParser);

        pictureHandler = new PictureHandler(pictureService);
        albumHandler = new AlbumHandler(albumService);
    }

    @Override
    public void start(Promise<Void> startPromise) {
        setup();
        Router router = createRouter();

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

    private Router createRouter() {
        Router router = Router.router(vertx);

        router.options().handler(r ->r.response()
                .putHeader("Access-Control-Allow-Headers", "content-type, authorization")
                .putHeader("Access-Control-Allow-Origin", "*")
                .putHeader("Access-Control-Allow-Methods", "GET, DELETE, PATCH, POST, OPTIONS")
                .putHeader("Access-Control-Max-Age", "-1")
                .end()
        );

        router.route("/pic/:userId*").handler(BodyHandler.create());
        router.get("/pic/:userId/:pictureId").produces(JPEG_FORMAT).handler(pictureHandler::getById);
        router.post("/pic/:userId").consumes(JPEG_FORMAT).handler(pictureHandler::add);
        router.post("/pic/:userId/:pictureId/rotate").handler(pictureHandler::rotate);
        router.delete("/pic/:userId/:pictureId").produces(JSON_FORMAT).handler(pictureHandler::deleteById);

        router.route("/album/:userId*").handler(BodyHandler.create());
        router.get("/album/:userId").produces(JSON_FORMAT).handler(albumHandler::getAlbumsForUser);
        router.get("/album/:userId/:albumId").produces(JSON_FORMAT).handler(albumHandler::getAlbumContents);
        router.post("/album/:userId").consumes(JSON_FORMAT).handler(albumHandler::add);
        router.patch("/album/:userId/:albumId").consumes(JSON_FORMAT).handler(albumHandler::renameAlbum);
        router.post("/album/:userId/:albumId/share").consumes(JSON_FORMAT).handler(albumHandler::shareAlbum);
        router.delete("/album/:userId/:albumId").produces(JSON_FORMAT).handler(albumHandler::deleteAlbum);

        return router;
    }
}
