package com.ruslanlesko.pichub.core.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruslanlesko.pichub.core.dao.PictureDao;
import com.ruslanlesko.pichub.core.entity.Picture;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Path("/pic")
public class PictureHandler {

    @Inject
    PictureDao pictureDao;

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
        try {
            byte[] buffer = new byte[is.available()];
            byte[] data;
            int count = is.read(buffer);
            while (count > 0) {
                data = new byte[is.available()];
                count = is.read(data);
                byte[] newBuffer = Arrays.copyOf(buffer, buffer.length + count);
                for (int i = 0; i < count; i++) {
                    newBuffer[newBuffer.length - count + i] = data[i];
                }
                buffer = newBuffer;
            }
            return pictureDao.save(userId, new Picture(-1, buffer));
        } catch (IOException e) {
            return -1;
        }
    }
}
