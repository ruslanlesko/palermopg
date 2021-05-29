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
import com.leskor.palermopg.services.PictureManipulationService;
import com.leskor.palermopg.services.PictureService;
import com.leskor.palermopg.services.StorageService;
import com.leskor.palermopg.services.album.*;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.PrometheusScrapingHandler;
import io.vertx.micrometer.VertxPrometheusOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

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
    private final Handler<RoutingContext> metricsHandler = PrometheusScrapingHandler.create();

    private final String metricsCredentialsEncoded;

    private Application() {
        VertxOptions options = new VertxOptions().setMetricsOptions(
                new MicrometerMetricsOptions()
                        .setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true))
                        .setEnabled(true));

        vertx = Vertx.vertx(options);

        final String metricsUser = System.getenv("METRICS_USER");
        final String metricsPassword = System.getenv("METRICS_PASSWORD");

        if (metricsUser != null && !metricsUser.isBlank() && metricsPassword != null && !metricsPassword.isBlank()) {
            metricsCredentialsEncoded = Base64.getEncoder().encodeToString((metricsUser + ":" + metricsPassword).getBytes());
        } else {
            metricsCredentialsEncoded = null;
        }

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
        AlbumCreationService albumCreationService = new AlbumCreationService(albumDao);
        AlbumFetchingService albumFetchingService = new AlbumFetchingService(albumDao, pictureMetaDao, pictureDataDao);
        AlbumSharingService albumSharingService = new AlbumSharingService(albumDao);
        AlbumUpdatingService albumUpdatingService = new AlbumUpdatingService(albumDao);
        AlbumDeletingService albumDeletingService = new AlbumDeletingService(pictureService, albumDao, pictureMetaDao);

        pictureHandler = new PictureHandler(pictureService);
        albumHandler = new AlbumHandler(albumCreationService, albumFetchingService, albumSharingService, albumUpdatingService, albumDeletingService);
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

        router.options().handler(r -> r.response()
                .putHeader("Access-Control-Allow-Headers", "content-type, authorization")
                .putHeader("Access-Control-Allow-Origin", "*")
                .putHeader("Access-Control-Allow-Methods", "GET, DELETE, PATCH, POST, OPTIONS")
                .putHeader("Access-Control-Max-Age", "-1")
                .end()
        );

        router.route("/metrics").handler(this::metrics);

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

    private void metrics(RoutingContext ctx) {
        String authHeader = ctx.request().getHeader("Authorization");
        String[] tokens = authHeader == null ? new String[]{} : authHeader.split(" ");
        if (metricsCredentialsEncoded != null
                && (tokens.length != 2 || !tokens[0].equals("Basic") || !tokens[1].equals(metricsCredentialsEncoded))) {
            cors(ctx.response().setStatusCode(401)).end();
            return;
        }
        metricsHandler.handle(ctx);
    }
}
