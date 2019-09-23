package com.ruslanlesko.pichub.core.filters;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class GlobalFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext containerRequestContext,
                       ContainerResponseContext containerResponseContext)
            throws IOException {
        containerResponseContext.getHeaders().putSingle("Access-Control-Allow-Origin", "*");
        containerResponseContext.getHeaders().putSingle("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        containerResponseContext.getHeaders().putSingle("Access-Control-Allow-Headers", "Authorization, content-type");
    }
}
