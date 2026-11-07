package kr.co.marketry.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import kr.co.marketry.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ProfileImageProcessorTest {

    private final ProfileImageProcessor processor = new ProfileImageProcessor();

    @Test
    void 정상_JPEG는_256x256_JPEG로_다시_만든다() throws IOException {
        byte[] result = processor.toProfileJpeg(encode(solid(600, 400, Color.BLUE, BufferedImage.TYPE_INT_RGB), "jpg"));

        BufferedImage output = ImageIO.read(new ByteArrayInputStream(result));
        assertThat(isJpeg(result)).isTrue();
        assertThat(output.getWidth()).isEqualTo(256);
        assertThat(output.getHeight()).isEqualTo(256);
    }

    @Test
    void 정상_PNG도_256x256_JPEG로_다시_만든다() throws IOException {
        byte[] result = processor.toProfileJpeg(encode(solid(300, 300, Color.RED, BufferedImage.TYPE_INT_RGB), "png"));

        BufferedImage output = ImageIO.read(new ByteArrayInputStream(result));
        assertThat(isJpeg(result)).isTrue();
        assertThat(output.getWidth()).isEqualTo(256);
        assertThat(output.getHeight()).isEqualTo(256);
    }

    @Test
    void 투명_PNG는_흰_배경으로_합성한다() throws IOException {
        BufferedImage transparent = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);

        byte[] result = processor.toProfileJpeg(encode(transparent, "png"));

        BufferedImage output = ImageIO.read(new ByteArrayInputStream(result));
        Color center = new Color(output.getRGB(128, 128));
        assertThat(center.getRed()).isGreaterThan(240);
        assertThat(center.getGreen()).isGreaterThan(240);
        assertThat(center.getBlue()).isGreaterThan(240);
    }

    @Test
    void 직사각형_사진은_가운데_정사각형만_남긴다() throws IOException {
        // 가로 600×세로 200: 왼쪽 200은 빨강, 가운데 200은 파랑, 오른쪽 200은 초록. 가운데만 남아야 한다.
        BufferedImage wide = new BufferedImage(600, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = wide.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 200, 200);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(200, 0, 200, 200);
        graphics.setColor(Color.GREEN);
        graphics.fillRect(400, 0, 200, 200);
        graphics.dispose();

        byte[] result = processor.toProfileJpeg(encode(wide, "png"));

        BufferedImage output = ImageIO.read(new ByteArrayInputStream(result));
        Color corner = new Color(output.getRGB(5, 5));
        assertThat(corner.getBlue()).isGreaterThan(200);
        assertThat(corner.getRed()).isLessThan(60);
        assertThat(corner.getGreen()).isLessThan(60);
    }

    @Test
    void JPEG_PNG가_아닌_파일은_415를_던진다() {
        byte[] text = "이것은 사진이 아닙니다".getBytes();

        assertThatThrownBy(() -> processor.toProfileJpeg(text))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode())
                        .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void GIF는_415를_던진다() throws IOException {
        byte[] gif = encode(solid(50, 50, Color.BLACK, BufferedImage.TYPE_INT_RGB), "gif");

        assertThatThrownBy(() -> processor.toProfileJpeg(gif))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode())
                        .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void 확장자만_JPG이고_내용은_텍스트인_파일은_415를_던진다() {
        byte[] fake = "not-a-jpeg.jpg".getBytes();

        assertThatThrownBy(() -> processor.toProfileJpeg(fake))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode())
                        .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void 헤더만_있는_손상된_JPEG는_415를_던진다() throws IOException {
        byte[] jpeg = encode(solid(300, 300, Color.BLUE, BufferedImage.TYPE_INT_RGB), "jpg");
        byte[] headerOnly = java.util.Arrays.copyOf(jpeg, 20);

        assertThatThrownBy(() -> processor.toProfileJpeg(headerOnly))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode())
                        .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void 한_변이_4096픽셀을_넘으면_BadRequestException을_던진다() throws IOException {
        byte[] tooWide = encode(solid(4097, 8, Color.WHITE, BufferedImage.TYPE_BYTE_GRAY), "png");

        assertThatThrownBy(() -> processor.toProfileJpeg(tooWide)).isInstanceOf(BadRequestException.class);
    }

    private BufferedImage solid(int width, int height, Color color, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    private byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return bytes.toByteArray();
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length > 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF;
    }
}
