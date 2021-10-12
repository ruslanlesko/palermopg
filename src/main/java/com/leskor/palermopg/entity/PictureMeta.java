package com.leskor.palermopg.entity;

import java.time.LocalDateTime;

public record PictureMeta(
        long id,
        long userId,
        long albumId,
        long size,
        String path,
        String pathOptimized,
        LocalDateTime dateUploaded,
        LocalDateTime dateCaptured,
        LocalDateTime dateModified
) { }
