package com.example.dating_application.Service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Розбір EXIF-орієнтації: биті файли не мають ні падати, ні читати поза масивом. */
class ExifOrientationTest {

    @Test
    @DisplayName("файл без EXIF читається як «повороту немає»")
    void plainJpegHasNoOrientation() {
        assertThat(ExifOrientation.read(jpegWithoutExif())).isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    @DisplayName("орієнтація 6 (знято з поворотом на 90°) читається з big-endian EXIF")
    void readsOrientationFromBigEndianExif() {
        assertThat(ExifOrientation.read(jpegWithOrientation(6, true))).isEqualTo(6);
    }

    @Test
    @DisplayName("той самий тег читається і з little-endian EXIF")
    void readsOrientationFromLittleEndianExif() {
        assertThat(ExifOrientation.read(jpegWithOrientation(8, false))).isEqualTo(8);
    }

    @Test
    @DisplayName("усі вісім допустимих значень проходять як є")
    void acceptsWholeValidRange() {
        for (int orientation = 1; orientation <= 8; orientation++) {
            assertThat(ExifOrientation.read(jpegWithOrientation(orientation, true)))
                    .as("орієнтація " + orientation)
                    .isEqualTo(orientation);
        }
    }

    @Test
    @DisplayName("значення поза діапазоном 1..8 не приймається — краще не повернути, ніж зіпсувати")
    void ignoresOutOfRangeValue() {
        assertThat(ExifOrientation.read(jpegWithOrientation(42, true)))
                .isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    @DisplayName("не-JPEG не розбирається зовсім")
    void ignoresNonJpeg() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};

        assertThat(ExifOrientation.read(png)).isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    @DisplayName("порожній і надто короткий файл не валять розбір")
    void toleratesEmptyInput() {
        assertThat(ExifOrientation.read(new byte[0])).isEqualTo(ExifOrientation.NORMAL);
        assertThat(ExifOrientation.read(new byte[] {(byte) 0xFF})).isEqualTo(ExifOrientation.NORMAL);
        assertThat(ExifOrientation.read(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    @DisplayName("обрізаний посеред EXIF файл не кидає виняток")
    void toleratesTruncatedExif() {
        byte[] full = jpegWithOrientation(6, true);

        for (int cut = 4; cut < full.length; cut += 3) {
            byte[] truncated = new byte[cut];
            System.arraycopy(full, 0, truncated, 0, cut);

            int result = ExifOrientation.read(truncated);
            assertThat(result)
                    .as("обрив на байті " + cut + " має дати або коректне значення, або NORMAL")
                    .isBetween(1, 8);
        }
    }

    @Test
    @DisplayName("брехлива довжина сегмента не виводить читання за межі масиву")
    void toleratesLyingSegmentLength() {
        byte[] jpeg = jpegWithOrientation(6, true);
        jpeg[4] = (byte) 0x7F;
        jpeg[5] = (byte) 0xFF;

        assertThat(ExifOrientation.read(jpeg)).isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    @DisplayName("чужий порядок байтів у заголовку TIFF відкидається")
    void rejectsUnknownByteOrder() {
        byte[] jpeg = jpegWithOrientation(6, true);
        int tiffStart = 4 + 2 + 6; // довжина сегмента + сигнатура "Exif\0\0"
        jpeg[tiffStart] = 'X';
        jpeg[tiffStart + 1] = 'X';

        assertThat(ExifOrientation.read(jpeg)).isEqualTo(ExifOrientation.NORMAL);
    }

    private static byte[] jpegWithoutExif() {
        return new byte[] {
                (byte) 0xFF, (byte) 0xD8,             // SOI
                (byte) 0xFF, (byte) 0xFE, 0, 2,       // COM завдовжки 2 (тобто порожній)
                (byte) 0xFF, (byte) 0xDA              // SOS — далі стиснуті дані
        };
    }

    private static byte[] jpegWithOrientation(int orientation, boolean bigEndian) {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        write(tiff, bigEndian ? new byte[] {'M', 'M'} : new byte[] {'I', 'I'});
        write16(tiff, 42, bigEndian);          // магічне число TIFF
        write32(tiff, 8, bigEndian);           // зміщення IFD від початку TIFF
        write16(tiff, 1, bigEndian);           // один запис у каталозі
        write16(tiff, 0x0112, bigEndian);      // тег: Orientation
        write16(tiff, 3, bigEndian);           // тип: SHORT
        write32(tiff, 1, bigEndian);           // кількість значень
        write16(tiff, orientation, bigEndian); // саме значення...
        write16(tiff, 0, bigEndian);           // ...і добивка до чотирьох байтів
        write32(tiff, 0, bigEndian);           // наступного IFD немає

        byte[] tiffBytes = tiff.toByteArray();
        int segmentLength = 2 + 6 + tiffBytes.length; // довжина + "Exif\0\0" + TIFF

        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        write(jpeg, new byte[] {(byte) 0xFF, (byte) 0xD8});               // SOI
        write(jpeg, new byte[] {(byte) 0xFF, (byte) 0xE1});               // APP1
        write16(jpeg, segmentLength, true);                               // довжина завжди big-endian
        write(jpeg, new byte[] {'E', 'x', 'i', 'f', 0, 0});
        write(jpeg, tiffBytes);
        write(jpeg, new byte[] {(byte) 0xFF, (byte) 0xDA});               // SOS
        return jpeg.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }

    private static void write16(ByteArrayOutputStream out, int value, boolean bigEndian) {
        if (bigEndian) {
            out.write((value >> 8) & 0xFF);
            out.write(value & 0xFF);
        } else {
            out.write(value & 0xFF);
            out.write((value >> 8) & 0xFF);
        }
    }

    private static void write32(ByteArrayOutputStream out, int value, boolean bigEndian) {
        if (bigEndian) {
            out.write((value >> 24) & 0xFF);
            out.write((value >> 16) & 0xFF);
            out.write((value >> 8) & 0xFF);
            out.write(value & 0xFF);
        } else {
            out.write(value & 0xFF);
            out.write((value >> 8) & 0xFF);
            out.write((value >> 16) & 0xFF);
            out.write((value >> 24) & 0xFF);
        }
    }
}
