package com.leskor.palermopg.verticles;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.dao.LimitsDao;
import com.leskor.palermopg.dao.PictureDataDao;
import com.leskor.palermopg.dao.PictureMetaDao;
import com.leskor.palermopg.dao.impl.MongoAlbumDao;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.leskor.palermopg.dao.impl.FilePictureDataDao;
import com.leskor.palermopg.dao.impl.MongoLimitsDao;
import com.leskor.palermopg.dao.impl.MongoPictureMetaDao;
import com.leskor.palermopg.handlers.AlbumHandler;
import com.leskor.palermopg.handlers.PictureHandler;
import com.leskor.palermopg.handlers.StorageHandler;
import com.leskor.palermopg.security.JWTParser;
import com.leskor.palermopg.services.AlbumService;
import com.leskor.palermopg.services.PictureService;
import com.leskor.palermopg.services.StorageService;
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
    private StorageHandler storageHandler;

    private void setup() {
        final String dbUrl = System.getenv("PIC_DB");
        final MongoClient asyncMongoClient = MongoClients.create(dbUrl);

        PictureDataDao pictureDataDao = new FilePictureDataDao(vertx.getOrCreateContext());
        PictureMetaDao pictureMetaDao = new MongoPictureMetaDao(asyncMongoClient);
        AlbumDao albumDao = new MongoAlbumDao(asyncMongoClient);
        LimitsDao limitsDao = new MongoLimitsDao(asyncMongoClient);
        JWTParser jwtParser = new JWTParser();

        StorageService storageService = new StorageService(pictureMetaDao, pictureDataDao, limitsDao, jwtParser);
        PictureService pictureService = new PictureService(pictureMetaDao, pictureDataDao, albumDao, jwtParser, storageService);
        AlbumService albumService = new AlbumService(pictureMetaDao, pictureDataDao, albumDao, pictureService, jwtParser);

        pictureHandler = new PictureHandler(pictureService);
        albumHandler = new AlbumHandler(albumService);
        storageHandler = new StorageHandler(storageService);
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
        router.get("/album/:userId/:albumId/download").handler(albumHandler::downloadAlbum);
        router.post("/album/:userId").consumes(JSON_FORMAT).handler(albumHandler::add);
        router.patch("/album/:userId/:albumId").consumes(JSON_FORMAT).handler(albumHandler::updateAlbum);
        router.post("/album/:userId/:albumId/share").consumes(JSON_FORMAT).handler(albumHandler::shareAlbum);
        router.delete("/album/:userId/:albumId").produces(JSON_FORMAT).handler(albumHandler::deleteAlbum);

        router.route("/storage/:userId*").handler(BodyHandler.create());
        router.get("/storage").produces(JSON_FORMAT).handler(storageHandler::storageByUsers);
        router.get("/storage/:userId").produces(JSON_FORMAT).handler(storageHandler::storageByUser);
        router.post("/storage/:userId").consumes(JSON_FORMAT).handler(storageHandler::setUserLimit);

        return router;
    }
}