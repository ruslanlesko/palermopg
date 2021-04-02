package com.leskor.palermopg;

import com.leskor.palermopg.dao.AlbumDao;
import com.leskor.palermopg.dao.LimitsDao;
import com.leskor.palermopg.dao.PictureDataDao;
import com.leskor.palermopg.dao.PictureMetaDao;
import com.leskor.palermopg.dao.impl.FilePictureDataDao;
import com.leskor.palermopg.dao.impl.MongoAlbumDao;
import com.leskor.palermopg.dao.impl.MongoLimitsDao;
import com.leskor.palermopg.dao.impl.MongoPictureMetaDao;
import com.leskor.palermopg.handlers.AlbumHandler;
import com.leskor.palermopg.handlers.PictureHandler;
import com.leskor.palermopg.handlers.StorageHandler;
import com.leskor.palermopg.security.JWTParser;
import com.leskor.palermopg.services.AlbumService;
import com.leskor.palermopg.services.PictureManipulationService;
import com.leskor.palermopg.services.PictureService;
import com.leskor.palermopg.services.StorageService;
import com.leskor.palermopg.services.album.AlbumCreationService;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.ext.web.handler.LoggerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.leskor.palermopg.util.ApiUtils.cors;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private static final String JSON_FORMAT = "application/json";
    private static final String JPEG_FORMAT = "image/jpeg";

    private final Vertx vertx;
    private final JWTParser jwtParser;
    private final PictureHandler pictureHandler;
    private final AlbumHandler albumHandler;
    private final StorageHandler storageHandler;

    private Application() {
        vertx = Vertx.vertx();

        final String dbUrl = System.getenv("PIC_DB");
        final MongoClient asyncMongoClient = MongoClients.create(dbUrl);

        jwtParser = new JWTParser();

        PictureDataDao pictureDataDao = new FilePictureDataDao(vertx.getOrCreateContext());
        PictureMetaDao pictureMetaDao = new MongoPictureMetaDao(asyncMongoClient);
        AlbumDao albumDao = new MongoAlbumDao(asyncMongoClient);
        LimitsDao limitsDao = new MongoLimitsDao(asyncMongoClient);
        PictureManipulationService pmService = new PictureManipulationService(vertx.getOrCreateContext());

        StorageService storageService = new StorageService(pictureMetaDao, pictureDataDao, limitsDao, jwtParser);
        PictureService pictureService = new PictureService(pictureMetaDao, pictureDataDao, albumDao, jwtParser, storageService, pmService);
        AlbumService albumService = new AlbumService(pictureMetaDao, pictureDataDao, albumDao, pictureService);
        AlbumCreationService albumCreationService = new AlbumCreationService(albumDao);

        pictureHandler = new PictureHandler(pictureService);
        albumHandler = new AlbumHandler(albumService, albumCreationService);
        storageHandler = new StorageHandler(storageService);
    }

    public static void main(String[] args) {
        logger.info("Starting PalermoPG 1.18.0");
        Application palermoPG = new Application();
        palermoPG.startHttpServer();
    }

    private void startHttpServer() {
        Router router = createRouter();

        logger.debug("Creating HTTP server on 8081 port");
        vertx.createHttpServer().requestHandler(router)
                .listen(8081, result -> {
                    if (result.succeeded()) logger.debug("HTTP server was created");
                    else logger.error("Failed to deploy HTTP server on port 8081: {}", result.cause().getMessage());
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

        router.route().handler(LoggerHandler.create(LoggerFormat.TINY));

        router.get("/pic/:userId/:pictureId").produces(JPEG_FORMAT).handler(pictureHandler::getById);
        router.route("/pic/:userId*").handler(this::authorize);
        router.route("/pic/:userId*").handler(BodyHandler.create());
        router.post("/pic/:userId").consumes(JPEG_FORMAT).handler(pictureHandler::add);
        router.post("/pic/:userId/:pictureId/rotate").handler(pictureHandler::rotate);
        router.delete("/pic/:userId/:pictureId").produces(JSON_FORMAT).handler(pictureHandler::deleteById);

        router.get("/album/:userId/:albumId/download").handler(albumHandler::downloadAlbum);
        router.route("/album/:userId*").handler(this::authorize);
        router.route("/album/:userId*").handler(BodyHandler.create());
        router.get("/album/:userId").produces(JSON_FORMAT).handler(albumHandler::getAlbumsForUser);
        router.get("/album/:userId/:albumId").produces(JSON_FORMAT).handler(albumHandler::getAlbumContents);
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

    private void authorize(RoutingContext ctx) {
        try {
            String token = ctx.request().getHeader("Authorization");
            long userId = Long.parseLong(ctx.request().getParam("userId"));
            if (jwtParser.validateTokenForUserId(token, userId)) ctx.next();
            else cors(ctx.response().setStatusCode(401)).end();
        } catch (NumberFormatException e) {
            cors(ctx.response().setStatusCode(400)).end();
        }
    }
}
