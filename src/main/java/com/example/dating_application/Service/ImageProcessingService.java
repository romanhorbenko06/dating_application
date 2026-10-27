package com.example.dating_application.Service;

import com.example.dating_application.Exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Підготовка фото до збереження: повертає за EXIF-орієнтацією, зменшує
 * й перекодовує в JPEG, через що метадані (зокрема GPS) зникають.
 */
@Service
public class ImageProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(ImageProcessingService.class);

    public static final ImageFormat OUTPUT_FORMAT = ImageFormat.JPEG;

    private final int maxDimension;
    private final float quality;
    private final long maxPixels;

    public ImageProcessingService(@Value("${app.photos.max-dimension:1080}") int maxDimension,
                                  @Value("${app.photos.jpeg-quality:0.85}") float quality,
                                  @Value("${app.photos.max-pixels:50000000}") long maxPixels) {
        this.maxDimension = maxDimension;
        this.quality = quality;
        this.maxPixels = maxPixels;
    }

    public byte[] process(byte[] data, ImageFormat source) {
        int orientation = (source == ImageFormat.JPEG)
                ? ExifOrientation.read(data)
                : ExifOrientation.NORMAL;

        BufferedImage image = decode(data);

        image = applyOrientation(image, orientation);
        image = resizeIfNeeded(image);
        image = flattenOntoWhite(image);

        return encodeJpeg(image);
    }

    /** Спершу перевіряємо розміри із заголовка, і лише потім розпаковуємо пікселі. */
    private BufferedImage decode(byte[] data) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BusinessException("Unsupported or corrupted image file");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input);

                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > maxPixels) {
                    throw new BusinessException("Image resolution is too large");
                }

                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new BusinessException("Unsupported or corrupted image file");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BusinessException("Unsupported or corrupted image file");
        }
    }

    private BufferedImage applyOrientation(BufferedImage image, int orientation) {
        if (orientation == ExifOrientation.NORMAL) {
            return image;
        }

        int w = image.getWidth();
        int h = image.getHeight();
        boolean swapsSides = orientation >= 5;

        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.scale(-1, 1); t.translate(-w, 0); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.scale(1, -1); t.translate(0, -h); }
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(-Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(-Math.PI / 2); }
            default -> {
                return image;
            }
        }

        BufferedImage result = new BufferedImage(
                swapsSides ? h : w,
                swapsSides ? w : h,
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(image, t, null);
        g.dispose();

        logger.debug("Applied EXIF orientation {}", orientation);
        return result;
    }

    /** Великий масштаб зменшуємо кроками по половині, інакше з'являються сходинки. */
    private BufferedImage resizeIfNeeded(BufferedImage image) {
        int longSide = Math.max(image.getWidth(), image.getHeight());
        if (longSide <= maxDimension) {
            return image;
        }

        double ratio = (double) maxDimension / longSide;
        int targetW = Math.max(1, (int) Math.round(image.getWidth() * ratio));
        int targetH = Math.max(1, (int) Math.round(image.getHeight() * ratio));

        BufferedImage current = image;
        int w = current.getWidth();
        int h = current.getHeight();

        while (w / 2 > targetW && h / 2 > targetH) {
            w /= 2;
            h /= 2;
            current = drawScaled(current, w, h);
        }

        return drawScaled(current, targetW, targetH);
    }

    private BufferedImage drawScaled(BufferedImage source, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return scaled;
    }

    /** Без білої підкладки прозорі ділянки PNG стали б чорними. */
    private BufferedImage flattenOntoWhite(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private byte[] encodeJpeg(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No JPEG writer available in this JVM");
        }
        ImageWriter writer = writers.next();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }

            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode processed image", e);
        } finally {
            writer.dispose();
        }

        return out.toByteArray();
    }
}
