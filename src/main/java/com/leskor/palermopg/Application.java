package com.leskor.palermopg;

import com.leskor.palermopg.verticles.ApiVerticle;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {
    private final static Logger logger = LoggerFactory.getLogger("Application");

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        logger.info("Starting PalermoPG 1.15");
        vertx.deployVerticle(new ApiVerticle());
    }
}