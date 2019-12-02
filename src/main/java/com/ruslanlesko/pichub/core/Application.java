package com.ruslanlesko.pichub.core;

import com.ruslanlesko.pichub.core.verticles.ApiVerticle;
import io.vertx.core.Vertx;
import org.slf4j.impl.SimpleLoggerFactory;

public class Application {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        new SimpleLoggerFactory().getLogger("Application").info("Deploying ApiVerticle");
        vertx.deployVerticle(new ApiVerticle());
    }
}
