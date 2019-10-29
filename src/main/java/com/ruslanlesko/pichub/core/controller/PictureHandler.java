package com.ruslanlesko.pichub.core.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruslanlesko.pichub.core.dao.PictureDao;
import com.ruslanlesko.pichub.core.entity.Picture;
import com.ruslanlesko.pichub.core.exception.AuthorizationException;
import com.ruslanlesko.pichub.core.security.JWTParser;
import org.apache.commons.io.IOUtils;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Path("/pic")
public class PictureHandler {

    @Inject
    PictureDao pictureDao;

    @Inject
    JWTParser jwtParser;

    public PictureHandler() {

    }

    public PictureHandler(PictureDao pictureDao) {
        this.pictureDao = pictureDao;
    }

    @GET
    @Path("/{userId}/{pictureId}")
    @Produces({"image/jpeg"})
    public Response getById(@PathParam("userId") long userId,
                            @PathParam("pictureId") long id,
                            @HeaderParam("Authorization") @DefaultValue("") String token) {
//        checkAuthorization(token, userId);

        Optional<Picture> picture = pictureDao.find(userId, id);

        if (picture.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok((StreamingOutput) outputStream -> {
            outputStream.write(picture.get().getData());
            outputStream.flush();
        }).build();
    }

    @GET
    @Path("/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    public String getIdsForUser(
            @PathParam("userId") long userId,
            @HeaderParam("Authorization") String token) {
        checkAuthorization(token, userId);

        List<Long> result = pictureDao.findIdsForUser(userId);
        if (result == null) {
            return "[]";
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    @POST
    @Path("/{userId}")
    @Consumes({"image/jpeg"})
    @Produces(MediaType.TEXT_PLAIN)
    public long add(
            @PathParam("userId") long userId,
            InputStream is,
            @HeaderParam("Authorization") String token) {
        checkAuthorization(token, userId);

        try {
            byte[] data = IOUtils.toByteArray(is);
            return pictureDao.save(userId, new Picture(-1, data));
        } catch (IOException e) {
            return -1;
        }
    }

    private void checkAuthorization(String token, long userId) {
        if (!jwtParser.validateTokenForUserId(token, userId)) {
            throw new AuthorizationException();
        }
    }
}
