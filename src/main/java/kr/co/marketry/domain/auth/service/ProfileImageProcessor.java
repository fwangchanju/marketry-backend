package kr.co.marketry.domain.auth.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 올라온 JPEG·PNG를 가운데 정사각형으로 잘라 256×256 JPEG로 다시 만든다. 원본은 남기지 않는다. */
@Component
public class ProfileImageProcessor {

    static final int OUTPUT_SIZE = 256;
    static final int MAX_SOURCE_SIDE = 4096;
    private static final float JPEG_QUALITY = 0.85f;
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    public byte[] toProfileJpeg(byte[] source) {
        if (!startsWith(source, JPEG_SIGNATURE) && !startsWith(source, PNG_SIGNATURE)) {
            throw unsupportedImage(null);
        }
        BufferedImage decoded = decode(source);
        return encodeJpeg(cropAndScale(decoded));
    }

    private BufferedImage decode(byte[] source) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            ImageReader reader = readers.hasNext() ? readers.next() : null;
            if (reader == null) {
                throw unsupportedImage(null);
            }
            try {
                reader.setInput(stream, true, true);
                // 픽셀을 읽기 전에 크기부터 확인한다 — 작은 파일이 거대한 화면으로 풀리는 것(압축 폭탄)을 막는다.
                if (reader.getWidth(0) > MAX_SOURCE_SIDE || reader.getHeight(0) > MAX_SOURCE_SIDE) {
                    throw new BadRequestException(ErrorCode.PROFILE_IMAGE_TOO_LARGE_DIMENSION);
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (BadRequestException | ResponseStatusException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw unsupportedImage(e);
        }
    }

    private BufferedImage cropAndScale(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int left = (source.getWidth() - side) / 2;
        int top = (source.getHeight() - side) / 2;
        // 투명 PNG는 흰 배경에 합성한다 — ARGB를 그대로 JPEG로 쓰면 인코딩이 실패한다.
        BufferedImage output = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(
                    source, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE, left, top, left + side, top + side, Color.WHITE, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageOutputStream stream = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
            stream.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "사진을 저장할 수 없습니다.", e);
        } finally {
            writer.dispose();
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

    private ResponseStatusException unsupportedImage(Exception cause) {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "JPEG 또는 PNG 사진만 올릴 수 있습니다.", cause);
    }
}
