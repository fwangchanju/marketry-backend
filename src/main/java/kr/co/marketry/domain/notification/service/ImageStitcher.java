package kr.co.marketry.domain.notification.service;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.common.exception.EscalateException;

// 스크린샷 여러 장을 위→아래 순서로 이어붙여 한 장으로 병합 (1장이면 그대로 통과)
public final class ImageStitcher {

    private static final String IMAGE_FORMAT = "png";

    private ImageStitcher() {}

    public static byte[] stitchVertically(List<byte[]> images) {
        List<BufferedImage> sections = new ArrayList<>();
        for (byte[] image : images) {
            sections.add(readImage(image));
        }

        int width = 0;
        int totalHeight = 0;
        for (BufferedImage section : sections) {
            width = Math.max(width, section.getWidth());
            totalHeight += section.getHeight();
        }

        BufferedImage combined = new BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = combined.createGraphics();
        int y = 0;
        for (BufferedImage section : sections) {
            graphics.drawImage(section, 0, y, null);
            y += section.getHeight();
        }
        graphics.dispose();

        return writeImage(combined);
    }

    private static BufferedImage readImage(byte[] data) {
        try {
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new EscalateException(ErrorCode.IMAGE_STITCH_FAILED, e);
        }
    }

    private static byte[] writeImage(BufferedImage image) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, IMAGE_FORMAT, output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new EscalateException(ErrorCode.IMAGE_STITCH_FAILED, e);
        }
    }
}
