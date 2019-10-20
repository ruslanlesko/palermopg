package com.ruslanlesko.pichub.core.exception;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

@ApplicationScoped
public class AuthorizationExceptionMapper implements ExceptionMapper<AuthorizationException> {
    @Override
    public Response toResponse(AuthorizationException e) {
        System.out.println("Auth exception was catched");
        return Response.status(401).build();
    }
}
