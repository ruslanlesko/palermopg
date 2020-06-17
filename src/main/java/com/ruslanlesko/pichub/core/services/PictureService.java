package com.ruslanlesko.pichub.core.services;

import com.ruslanlesko.pichub.core.entity.PictureResponse;
import io.vertx.core.Future;

import java.util.Optional;

public interface PictureService {
    Future<PictureResponse> getPictureData(String token, String clientHash, long userId, long pictureId);
    Future<Long> insertNewPicture(String token, long userId, Optional<Long> albumId, byte[] data);
    Future<Void> rotatePicture(String token, long userId, long pictureId);
    Future<Void> deletePicture(String token, long userId, long pictureId);
}
