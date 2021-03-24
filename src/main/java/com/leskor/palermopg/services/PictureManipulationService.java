package com.leskor.palermopg.services;

import com.leskor.palermopg.meta.MetaParser;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PictureManipulationService {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private static final int TARGET_MAX_WIDTH = 1792;
    private static final int TARGET_MAX_HEIGHT = 1120;

    private final Context context;

    public PictureManipulationService(Context context) {
        this.context = context;
    }

    public Future<byte[]> rotateToCorrectOrientation(byte[] data) {
        Promise<byte[]> resultPromise = Promise.promise();

        context.executeBlocking(call -> {
            try {
                MetaParser metaParser = new MetaParser(data);
                int degrees = metaParser.getRotation();
                resultPromise.complete(rotateImage(data, degrees));
            } catch (Exception ex) {
                logger.error("Failed to rotate image", ex);
                resultPromise.fail(ex);
            } finally {
                call.complete();
            }
        });

        return resultPromise.future();
    }

    public Future<byte[]> rotate90(byte[] data) {
        Promise<byte[]> resultPromise = Promise.promise();

        context.executeBlocking(call -> {
            try {
                resultPromise.complete(rotateImage(data, 90));
            } catch (Exception ex) {
                logger.error("Failed to rotate image", ex);
                resultPromise.fail(ex);
            } finally {
                call.complete();
            }
        });

        return resultPromise.future();
    }

    public Future<byte[]> convertToOptimized(byte[] data) {
        Promise<byte[]> resultPromise = Promise.promise();

        context.executeBlocking(call -> {
            try {
                resultPromise.complete(optimizeImage(data));
            } catch (Exception ex) {
                logger.error("Failed to optimize image", ex);
                resultPromise.fail(ex);
            } finally {
                call.complete();
            }
        });

        return resultPromise.future();
    }

    private byte[] rotateImage(byte[] bytes, int degrees) throws IOException {
        if (degrees <= 0) {
            return bytes;
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            BufferedImage image = ImageIO.read(bais);

            final double rads = Math.toRadians(degrees);
            final double sin = Math.abs(Math.sin(rads));
            final double cos = Math.abs(Math.cos(rads));
            final int w = (int) Math.floor(image.getWidth() * cos + image.getHeight() * sin);
            final int h = (int) Math.floor(image.getHeight() * cos + image.getWidth() * sin);
            final BufferedImage rotatedImage = new BufferedImage(w, h, image.getType());
            final AffineTransform at = new AffineTransform();
            at.translate(w / 2.0, h / 2.0);
            at.rotate(rads, 0, 0);
            at.translate(-image.getWidth() / 2.0, -image.getHeight() / 2.0);
            final AffineTransformOp rotateOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BICUBIC);
            rotateOp.filter(image, rotatedImage);

            ImageIO.write(rotatedImage, "JPEG", baos);
            baos.flush();
            return baos.toByteArray();
        } finally {
            try {
                bais.close();
                baos.close();
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }
    }

    private byte[] optimizeImage(byte[] bytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            BufferedImage image = ImageIO.read(bais);
            int originalHeight = image.getHeight();
            int originalWidth = image.getWidth();

            if (originalHeight <= TARGET_MAX_HEIGHT && originalWidth <= TARGET_MAX_WIDTH) {
                return bytes;
            }

            double percent = originalHeight > originalWidth ?
                    (double) TARGET_MAX_HEIGHT / (double) originalHeight
                    : (double) TARGET_MAX_WIDTH / (double) originalWidth;

            AffineTransform resize = AffineTransform.getScaleInstance(percent, percent);
            AffineTransformOp op = new AffineTransformOp(resize, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
            BufferedImage resultImage = op.filter(image, null);

            ImageIO.write(resultImage, "JPEG", baos);
            baos.flush();
            return baos.toByteArray();
        } finally {
            try {
                bais.close();
                baos.close();
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }
    }
}
