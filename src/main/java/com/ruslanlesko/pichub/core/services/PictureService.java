package com.ruslanlesko.pichub.core.services;

import com.ruslanlesko.pichub.core.entity.PictureResponse;
import io.vertx.core.Future;

import java.util.List;
import java.util.Optional;

public interface PictureService {
    Future<PictureResponse> getPictureData(String token, String clientHash, long userId, long pictureId);
    List<Long> getPictureIdsForUserId(String token, long userId);
    Optional<Long> insertNewPicture(String token, long userId, Optional<Long> albumId, byte[] data);
    boolean rotatePicture(String token, long userId, long pictureId);
    boolean deletePicture(String token, long userId, long pictureId);
}
