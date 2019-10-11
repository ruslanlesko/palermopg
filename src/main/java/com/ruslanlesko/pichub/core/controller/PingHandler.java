package com.ruslanlesko.pichub.core.controller;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/ping")
public class PingHandler {

    @GET
    public String ping() {
        return "pong";
    }
}
