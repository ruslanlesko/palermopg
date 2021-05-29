module com.leskor.palermopg {
    // Logging
    requires org.slf4j;
    requires org.apache.logging.log4j;
    requires java.scripting;

    // Vert.x core, web & metrics
    requires io.vertx.core;
    requires io.vertx.web;
    requires io.vertx.metrics.micrometer;

    // Annotations for JSON fields
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    // JWT processing
    requires jose4j;

    // MongoDB
    requires org.mongodb.bson;
    requires org.mongodb.driver.core;
    requires mongodb.driver.reactivestreams;
    requires org.reactivestreams;

    // Image processing
    requires metadata.extractor;
    requires java.desktop;

    opens com.leskor.palermopg.services;
    opens com.leskor.palermopg.services.album;
    opens com.leskor.palermopg.entity;
}