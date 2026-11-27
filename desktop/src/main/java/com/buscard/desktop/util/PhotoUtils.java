package com.buscard.desktop.util;

import com.buscard.common.CardConstants;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Helper for compressing passenger portrait photos to fit within the card's 8286-byte EEPROM limit. */
public final class PhotoUtils {

    private PhotoUtils() {
    }

    public static byte[] preparePhotoForCard(File file) throws IOException {
        BufferedImage src = ImageIO.read(file);
        if (src == null) {
            throw new IOException("Cannot read image from file: " + file.getName());
        }

        // Scale to standard card portrait (max 180 width x 240 height)
        int targetW = 180;
        int targetH = 240;
        double srcRatio = (double) src.getWidth() / src.getHeight();
        double targetRatio = (double) targetW / targetH;
        if (srcRatio > targetRatio) {
            targetH = (int) (targetW / srcRatio);
        } else {
            targetW = (int) (targetH * srcRatio);
        }

        BufferedImage resized = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();

        // Compress with JPEG quality, decreasing quality if it exceeds max size
        int maxAllowed = CardConstants.PHOTO_MAX - 2; // leave 2 bytes for length prefix
        for (float quality = 0.85f; quality >= 0.20f; quality -= 0.10f) {
            byte[] jpeg = writeJpeg(resized, quality);
            if (jpeg.length <= maxAllowed) {
                return jpeg;
            }
        }
        return writeJpeg(resized, 0.15f);
    }

    public static BufferedImage bytesToImage(byte[] jpegBytes) {
        if (jpegBytes == null || jpegBytes.length == 0) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(jpegBytes));
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] writeJpeg(BufferedImage img, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No JPEG writer found");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream mcos = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(mcos);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
