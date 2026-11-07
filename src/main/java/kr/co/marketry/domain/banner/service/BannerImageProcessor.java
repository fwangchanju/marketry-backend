package kr.co.marketry.domain.banner.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BannerImageProcessor {

    public static final int MAX_UPLOAD_BYTES = 2 * 1024 * 1024;
    private static final int MAX_WIDTH = 4096;
    private static final int MAX_HEIGHT = 1024;
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    public ProcessedBannerImage validate(byte[] source) {
        if (source == null || source.length == 0 || source.length > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "배너 이미지는 2MB 이하만 올릴 수 있습니다.");
        }
        String contentType = startsWith(source, PNG_SIGNATURE)
                ? "image/png"
                : startsWith(source, JPEG_SIGNATURE) ? "image/jpeg" : null;
        if (contentType == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PNG 또는 JPEG 이미지만 올릴 수 있습니다.");
        }
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw unsupportedImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_WIDTH || height > MAX_HEIGHT) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "배너 이미지 크기는 가로 4096px, 세로 1024px 이하여야 합니다.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw unsupportedImage();
                }
            } finally {
                reader.dispose();
            }
            return new ProcessedBannerImage(source, contentType);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw unsupportedImage();
        }
    }

    private boolean startsWith(byte[] source, byte[] signature) {
        if (source.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (source[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private ResponseStatusException unsupportedImage() {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "올바른 PNG 또는 JPEG 이미지를 올려 주세요.");
    }

    @SuppressWarnings("ArrayRecordComponent")
    public record ProcessedBannerImage(byte[] bytes, String contentType) {}
}
