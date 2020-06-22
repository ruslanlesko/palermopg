package com.ruslanlesko.pichub.core.util;

import com.ruslanlesko.pichub.core.exception.AuthorizationException;
import com.ruslanlesko.pichub.core.exception.MissingItemException;
import io.vertx.core.http.HttpServerResponse;

public class ApiUtils {
    public static void handleFailure(Throwable cause, HttpServerResponse response) {
        if (cause instanceof AuthorizationException) {
            cors(response.setStatusCode(401)).end();
            return;
        }
        if (cause instanceof MissingItemException) {
            cors(response.setStatusCode(404)).end();
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
