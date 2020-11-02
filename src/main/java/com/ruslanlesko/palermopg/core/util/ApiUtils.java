package com.ruslanlesko.palermopg.core.util;

import com.ruslanlesko.palermopg.core.exception.AuthorizationException;
import com.ruslanlesko.palermopg.core.exception.MissingItemException;
import com.ruslanlesko.palermopg.core.exception.StorageLimitException;
import io.vertx.core.http.HttpServerResponse;

public class ApiUtils {
    public static void handleFailure(Throwable cause, HttpServerResponse response) {
        if (cause instanceof AuthorizationException) {
            cors(response.setStatusCode(401)).end();
            return;
        }
        if (cause instanceof MissingItemException) {
            cors(response.setStatusCode(404)).end();
            return;
        }
        if (cause instanceof StorageLimitException) {
            cors(response.setStatusCode(400)).end(((StorageLimitException) cause).json());
            return;
        }
        cors(response.setStatusCode(500)).end();
    }

    public static HttpServerResponse cors(HttpServerResponse response) {
        return response.putHeader("Access-Control-Allow-Headers", "content-type, authorization")
                .putHeader("Access-Control-Allow-Origin", "*")
                .putHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PATCH, OPTIONS")
                .putHeader("Access-Control-Max-Age", "-1");
    }
}
