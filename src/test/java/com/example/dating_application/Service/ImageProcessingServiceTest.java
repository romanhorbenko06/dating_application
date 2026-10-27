package com.example.dating_application.Service;

import com.example.dating_application.Exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Обробка фото: метадані зрізані, розмір обмежений, на виході JPEG. */
class ImageProcessingServiceTest {

    private static final int MAX_DIMENSION = 1080;

    private final ImageProcessingService service =
            new ImageProcessingService(MAX_DIMENSION, 0.85f, 50_000_000L);

    @Test
    @DisplayName("на виході завжди JPEG, хай що прийшло")
    void alwaysOutputsJpeg() throws Exception {
        byte[] png = image(200, 100, "png");

        byte[] processed = service.process(png, ImageFormat.PNG);

        assertThat(ImageFormat.detect(processed))
                .as("формат на диску один — інакше видача мала б вгадувати тип")
                .isEqualTo(ImageFormat.JPEG);
    }

    @Test
    @DisplayName("велике фото зменшується до межі, зберігаючи пропорції")
    void oversizedImageIsScaledDown() throws Exception {
        byte[] huge = image(3000, 1500, "jpg");

        BufferedImage result = decode(service.process(huge, ImageFormat.JPEG));

        assertThat(result.getWidth()).isEqualTo(MAX_DIMENSION);
        assertThat(result.getHeight())
                .as("пропорція 2:1 має вціліти")
                .isEqualTo(MAX_DIMENSION / 2);
    }

    @Test
    @DisplayName("невелике фото не розтягується")
    void smallImageKeepsItsSize() throws Exception {
        byte[] small = image(320, 240, "jpg");

        BufferedImage result = decode(service.process(small, ImageFormat.JPEG));

        assertThat(result.getWidth()).isEqualTo(320);
        assertThat(result.getHeight()).isEqualTo(240);
    }

    @Test
    @DisplayName("метадані EXIF зникають — у них бувають GPS-координати")
    void stripsExifMetadata() throws Exception {
        byte[] withExif = jpegWithExifOrientation(1);
        assertThat(containsExifMarker(withExif)).as("вихідний файл справді містить EXIF").isTrue();

        byte[] processed = service.process(withExif, ImageFormat.JPEG);

        assertThat(containsExifMarker(processed))
                .as("це питання приватності, а не охайності")
                .isFalse();
    }

    @Test
    @DisplayName("поворот із EXIF застосовується до самих пікселів")
    void appliesRotationToPixels() throws Exception {
        // Орієнтація 6 — поворот на 90°: широке фото має стати високим.
        byte[] rotated = jpegWithExifOrientation(6);

        BufferedImage result = decode(service.process(rotated, ImageFormat.JPEG));

        assertThat(result.getHeight())
                .as("сторони мають помінятись місцями")
                .isGreaterThan(result.getWidth());
    }

    @Test
    @DisplayName("усі вісім варіантів орієнтації обробляються без падінь")
    void handlesEveryOrientation() throws Exception {
        // Поворот на чверть (5..8) міняє сторони місцями, решта — ні.
        for (int orientation = 1; orientation <= 8; orientation++) {
            BufferedImage result = decode(service.process(
                    jpegWithExifOrientation(orientation), ImageFormat.JPEG));

            boolean quarterTurn = orientation >= 5;
            if (quarterTurn) {
                assertThat(result.getHeight())
                        .as("орієнтація %d повертає фото на чверть", orientation)
                        .isGreaterThan(result.getWidth());
            } else {
                assertThat(result.getWidth())
                        .as("орієнтація %d сторін не міняє", orientation)
                        .isGreaterThan(result.getHeight());
            }
        }
    }

    @Test
    @DisplayName("висока картинка теж зменшується за довшою стороною")
    void tallImageIsScaledByItsLongerSide() throws Exception {
        byte[] tall = image(1000, 2400, "jpg");

        BufferedImage result = decode(service.process(tall, ImageFormat.JPEG));

        assertThat(result.getHeight()).isEqualTo(MAX_DIMENSION);
        assertThat(result.getWidth()).isEqualTo(450);
    }

    @Test
    @DisplayName("GIF і BMP теж приймаються й стають JPEG")
    void acceptsOtherSupportedFormats() throws Exception {
        for (String format : new String[] {"gif", "bmp"}) {
            byte[] processed = service.process(image(100, 100, format),
                    format.equals("gif") ? ImageFormat.GIF : ImageFormat.BMP);

            assertThat(ImageFormat.detect(processed)).isEqualTo(ImageFormat.JPEG);
        }
    }

    @Test
    @DisplayName("порожні байти відхиляються")
    void rejectsEmptyData() {
        assertThatThrownBy(() -> service.process(new byte[0], ImageFormat.JPEG))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("не-зображення відхиляється зрозумілою помилкою, а не падінням")
    void rejectsGarbage() {
        byte[] garbage = "це просто текст, а не картинка".getBytes();

        assertThatThrownBy(() -> service.process(garbage, ImageFormat.JPEG))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("надто велике за кількістю пікселів фото не декодується")
    void rejectsTooManyPixels() throws Exception {
        // Межа мізерна, щоб не декодувати в тесті справжню велику картинку.
        var strict = new ImageProcessingService(MAX_DIMENSION, 0.85f, 100L);
        byte[] image = image(200, 200, "jpg");

        assertThatThrownBy(() -> strict.process(image, ImageFormat.JPEG))
                .isInstanceOf(BusinessException.class);
    }

    private static byte[] image(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.PINK);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static byte[] jpegWithExifOrientation(int orientation) throws IOException {
        byte[] plain = image(400, 200, "jpg");

        byte[] exif = {
                (byte) 0xFF, (byte) 0xE1, 0, 30,
                'E', 'x', 'i', 'f', 0, 0,
                'M', 'M', 0, 42, 0, 0, 0, 8,
                0, 1,
                0x01, 0x12, 0, 3, 0, 0, 0, 1,
                0, (byte) orientation, 0, 0
        };

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plain, 0, 2);                    // SOI
        out.write(exif, 0, exif.length);           // APP1 одразу після нього
        out.write(plain, 2, plain.length - 2);     // решта файлу
        return out.toByteArray();
    }

    private static boolean containsExifMarker(byte[] data) {
        for (int i = 0; i + 6 < data.length; i++) {
            if (data[i] == 'E' && data[i + 1] == 'x' && data[i + 2] == 'i'
                    && data[i + 3] == 'f' && data[i + 4] == 0) {
                return true;
            }
        }
        return false;
    }

    private static BufferedImage decode(byte[] data) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(data));
    }
}
