package com.example.dating_application.Service;

public enum ImageFormat {

    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    GIF("gif", "image/gif"),
    BMP("bmp", "image/bmp");

    // WEBP не підтримуємо: у JDK немає декодера, а кожне фото ми перекодовуємо.

    private final String extension;
    private final String contentType;

    ImageFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String getExtension() {
        return extension;
    }

    public String getContentType() {
        return contentType;
    }

    public static ImageFormat detect(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }

        // JPEG: FF D8 FF
        if (u(data[0]) == 0xFF && u(data[1]) == 0xD8 && u(data[2]) == 0xFF) {
            return JPEG;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (u(data[0]) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G'
                && u(data[4]) == 0x0D && u(data[5]) == 0x0A && u(data[6]) == 0x1A && u(data[7]) == 0x0A) {
            return PNG;
        }
        // GIF: "GIF8"
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8') {
            return GIF;
        }
        // BMP: "BM"
        if (data[0] == 'B' && data[1] == 'M') {
            return BMP;
        }

        return null;
    }

    public static ImageFormat byExtension(String extension) {
        if (extension == null) {
            return null;
        }
        String ext = extension.toLowerCase();
        for (ImageFormat format : values()) {
            if (format.extension.equals(ext)) {
                return format;
            }
        }
        return null;
    }

    public static String allowed() {
        StringBuilder sb = new StringBuilder();
        for (ImageFormat format : values()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(format.name());
        }
        return sb.toString();
    }

    private static int u(byte b) {
        return b & 0xFF;
    }
}
