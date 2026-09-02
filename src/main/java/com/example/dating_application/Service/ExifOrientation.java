package com.example.dating_application.Service;


final class ExifOrientation {

    /** Значення за замовчуванням: повертати не треба. */
    static final int NORMAL = 1;

    private static final int TAG_ORIENTATION = 0x0112;

    private ExifOrientation() {
    }

    /**
     * @return значення орієнтації 1..8, або {@link #NORMAL}, якщо тега немає
     *         чи структура файлу несподівана (гадати не намагаємось)
     */
    static int read(byte[] jpeg) {
        try {
            return parse(jpeg);
        } catch (RuntimeException e) {
            // Пошкоджений або нестандартний EXIF — просто вважаємо, що повороту немає
            return NORMAL;
        }
    }

    private static int parse(byte[] d) {
        if (d.length < 4 || u(d[0]) != 0xFF || u(d[1]) != 0xD8) {
            return NORMAL; // не JPEG
        }

        int i = 2;
        while (i + 4 <= d.length) {
            if (u(d[i]) != 0xFF) {
                return NORMAL; // втратили межу сегмента
            }
            int marker = u(d[i + 1]);
            // SOS (FFDA) — далі стиснуті дані, метаданих уже не буде
            if (marker == 0xDA || marker == 0xD9) {
                return NORMAL;
            }
            int length = (u(d[i + 2]) << 8) | u(d[i + 3]);
            int segmentStart = i + 4;
            int segmentEnd = i + 2 + length;
            if (length < 2 || segmentEnd > d.length) {
                return NORMAL;
            }

            // APP1 із сигнатурою "Exif\0\0"
            if (marker == 0xE1 && segmentStart + 6 <= d.length
                    && d[segmentStart] == 'E' && d[segmentStart + 1] == 'x'
                    && d[segmentStart + 2] == 'i' && d[segmentStart + 3] == 'f'
                    && d[segmentStart + 4] == 0) {
                return parseTiff(d, segmentStart + 6, segmentEnd);
            }

            i = segmentEnd;
        }
        return NORMAL;
    }

    /** Усередині APP1 лежить маленький TIFF: заголовок із порядком байтів і каталог тегів. */
    private static int parseTiff(byte[] d, int tiffStart, int limit) {
        if (tiffStart + 8 > limit) {
            return NORMAL;
        }

        boolean bigEndian;
        if (d[tiffStart] == 'M' && d[tiffStart + 1] == 'M') {
            bigEndian = true;
        } else if (d[tiffStart] == 'I' && d[tiffStart + 1] == 'I') {
            bigEndian = false;
        } else {
            return NORMAL;
        }

        int ifdOffset = int32(d, tiffStart + 4, bigEndian);
        int ifd = tiffStart + ifdOffset;
        if (ifd + 2 > limit) {
            return NORMAL;
        }

        int entries = int16(d, ifd, bigEndian);
        for (int e = 0; e < entries; e++) {
            int entry = ifd + 2 + e * 12;
            if (entry + 12 > limit) {
                return NORMAL;
            }
            if (int16(d, entry, bigEndian) == TAG_ORIENTATION) {
                // тип SHORT: значення лежить прямо в полі, у перших двох байтах
                int value = int16(d, entry + 8, bigEndian);
                return (value >= 1 && value <= 8) ? value : NORMAL;
            }
        }
        return NORMAL;
    }

    private static int int16(byte[] d, int at, boolean bigEndian) {
        return bigEndian
                ? (u(d[at]) << 8) | u(d[at + 1])
                : (u(d[at + 1]) << 8) | u(d[at]);
    }

    private static int int32(byte[] d, int at, boolean bigEndian) {
        return bigEndian
                ? (u(d[at]) << 24) | (u(d[at + 1]) << 16) | (u(d[at + 2]) << 8) | u(d[at + 3])
                : (u(d[at + 3]) << 24) | (u(d[at + 2]) << 16) | (u(d[at + 1]) << 8) | u(d[at]);
    }

    private static int u(byte b) {
        return b & 0xFF;
    }
}
