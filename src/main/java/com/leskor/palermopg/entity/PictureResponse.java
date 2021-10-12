package com.leskor.palermopg.entity;

public record PictureResponse(byte[] data, boolean notModified, String hash) { }
