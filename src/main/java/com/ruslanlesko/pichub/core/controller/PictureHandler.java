package com.ruslanlesko.pichub.core.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruslanlesko.pichub.core.dao.impl.FilePictureDao;
import com.ruslanlesko.pichub.core.dao.impl.MongoPictureDao;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Path("/pic")
public class PictureHandler {

    @Inject
    FilePictureDao filePictureDao;

    @Inject
    MongoPictureDao mongoPictureDao;

    @Inject
    JWTParser jwtParser;

    public PictureHandler() {

    }

    public PictureHandler(FilePictureDao pictureDao) {
        this.filePictureDao = pictureDao;
    }

    @GET
    @Path("/{userId}/{pictureId}")
    @Produces({"image/jpeg"})
    public Response getById(@PathParam("userId") long userId,
                            @PathParam("pictureId") long id,
                            @HeaderParam("Authorization") @DefaultValue("") String token) {
        checkAuthorization(token, userId);

        Optional<Picture> picture = filePictureDao.find(userId, id);

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

        List<Long> result = filePictureDao.findPicturesForUser(userId).stream()
                .sorted((picA, picB) -> {
                    LocalDateTime uploadedA = picA.getDateUploaded();
                    LocalDateTime uploadedB = picB.getDateUploaded();
                    LocalDateTime capturedA = picA.getDateCaptured();
                    LocalDateTime capturedB = picB.getDateCaptured();

                    if (uploadedA.getYear() == uploadedB.getYear()
                            && uploadedA.getDayOfYear() == uploadedB.getDayOfYear()) {
                        return capturedB.compareTo(capturedA);
                    }

                    return uploadedB.compareTo(capturedA);
                })
                .map(Picture::getId)
                .collect(Collectors.toList());

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
            long id = filePictureDao.save(userId, new Picture(-1, data, null, null, null));
            Picture newPic = filePictureDao.find(userId, id).orElse(null);
//            mongoPictureDao.save(userId, newPic);
            return id;
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
