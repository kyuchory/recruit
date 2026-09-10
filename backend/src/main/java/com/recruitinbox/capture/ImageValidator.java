package com.recruitinbox.capture;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;

/**
 * Validates an uploaded image by content, not by extension (v1.1 section 12.2):
 * PNG/JPEG/WebP magic bytes only (SVG/HTML/scripts rejected), size + pixel caps,
 * and a successful decode.
 */
@Component
public class ImageValidator {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 25_000_000L;

    public record Dimensions(String mime, int width, int height, long bytes) {
    }

    public Dimensions validate(byte[] data, String declaredMime) {
        if (data == null || data.length == 0) {
            throw new ApiException(ErrorCode.INVALID_IMAGE, "empty upload");
        }
        if (data.length > MAX_BYTES) {
            throw new ApiException(ErrorCode.ASSET_TOO_LARGE, "image exceeds 5 MiB");
        }
        String sniffed = sniff(data);
        if (sniffed == null) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA, "only PNG, JPEG and WebP are accepted");
        }
        if (declaredMime != null && !declaredMime.isBlank()
                && !normalize(declaredMime).equals(sniffed)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA,
                    "declared type " + declaredMime + " does not match the file contents");
        }
        try {
            var img = ImageIO.read(new ByteArrayInputStream(data));
            if (img == null) {
                // WebP has no bundled ImageIO reader; accept on magic bytes + size only.
                if ("image/webp".equals(sniffed)) {
                    return new Dimensions(sniffed, 0, 0, data.length);
                }
                throw new ApiException(ErrorCode.INVALID_IMAGE, "image could not be decoded");
            }
            long pixels = (long) img.getWidth() * img.getHeight();
            if (pixels > MAX_PIXELS) {
                throw new ApiException(ErrorCode.ASSET_TOO_LARGE, "image exceeds 25 megapixels");
            }
            return new Dimensions(sniffed, img.getWidth(), img.getHeight(), data.length);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INVALID_IMAGE, "image could not be read");
        }
    }

    private static String normalize(String mime) {
        String m = mime.toLowerCase().trim();
        return m.equals("image/jpg") ? "image/jpeg" : m;
    }

    private static String sniff(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8 && (b[2] & 0xff) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
