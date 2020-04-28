package com.ruslanlesko.pichub.core;

import com.ruslanlesko.pichub.core.verticles.ApiVerticle;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {
    private final static Logger logger = LoggerFactory.getLogger("Application");

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        logger.info("Starting Core 1.1");
        vertx.deployVerticle(new ApiVerticle());
    }
}
