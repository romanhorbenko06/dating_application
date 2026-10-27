package com.example.dating_application.Service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Формат визначається за сигнатурою, а не за розширенням чи Content-Type. */
class ImageFormatTest {

    @Test
    @DisplayName("формати впізнаються за першими байтами")
    void detectsByMagicBytes() {
        assertThat(ImageFormat.detect(pad(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo(ImageFormat.JPEG);
        assertThat(ImageFormat.detect(pad(0x89, 'P', 'N', 'G', 13, 10, 26, 10))).isEqualTo(ImageFormat.PNG);
        assertThat(ImageFormat.detect(pad('G', 'I', 'F', '8', '9', 'a'))).isEqualTo(ImageFormat.GIF);
        assertThat(ImageFormat.detect(pad('B', 'M'))).isEqualTo(ImageFormat.BMP);
    }

    @Test
    @DisplayName("WEBP свідомо не приймається — його нічим декодувати")
    void rejectsWebp() {
        byte[] webp = pad('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P');

        assertThat(ImageFormat.detect(webp))
                .as("прийняти формат, який потім не обробимо, гірше за чесну відмову")
                .isNull();
    }

    @Test
    @DisplayName("порожнє, коротке й чуже відкидається без винятків")
    void rejectsGarbage() {
        assertThat(ImageFormat.detect(null)).isNull();
        assertThat(ImageFormat.detect(new byte[0])).isNull();
        assertThat(ImageFormat.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).as("замало байтів").isNull();
        assertThat(ImageFormat.detect(pad('%', 'P', 'D', 'F', '-', '1', '.', '4'))).isNull();
    }

    @Test
    @DisplayName("кожен формат знає своє розширення й MIME-тип")
    void carriesExtensionAndContentType() {
        assertThat(ImageFormat.JPEG.getExtension()).isEqualTo("jpg");
        assertThat(ImageFormat.JPEG.getContentType()).isEqualTo("image/jpeg");
        assertThat(ImageFormat.PNG.getContentType()).isEqualTo("image/png");
        assertThat(ImageFormat.allowed()).contains("JPEG", "PNG", "GIF", "BMP");
    }

    private static byte[] pad(int... signature) {
        byte[] data = new byte[16];
        for (int i = 0; i < signature.length; i++) {
            data[i] = (byte) signature[i];
        }
        return data;
    }
}
