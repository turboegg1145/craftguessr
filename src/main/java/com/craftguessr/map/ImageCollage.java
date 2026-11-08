package com.craftguessr.map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Builds the 2x2 ghost-camera collage and encodes it as a small JPEG.
 *
 * <p>All scaling happens on plain {@code int[]} pixel buffers, never through {@link java.awt.Graphics2D}:
 * a dedicated server has no display, and even {@code BufferedImage.createGraphics()} would try to connect
 * to X11 there. This way the pipeline works identically on client and server.
 */
public final class ImageCollage {

    private ImageCollage() {
    }

    /** Compose up to four images into a 2x2 grid, each quadrant scaled to {@code quadrantSize}². */
    public static BufferedImage collage(List<BufferedImage> images, int quadrantSize) {
        int size = Math.max(32, quadrantSize);
        int total = size * 2;
        int[] canvas = new int[total * total];
        for (int i = 0; i < 4; i++) {
            if (images == null || i >= images.size() || images.get(i) == null) {
                continue;
            }
            BufferedImage quad = scaleTo(images.get(i), size, size);
            int[] pixels = pixels(quad);
            int ox = (i % 2) * size;
            int oy = (i / 2) * size;
            for (int y = 0; y < size; y++) {
                System.arraycopy(pixels, y * size, canvas, (oy + y) * total + ox, size);
            }
        }
        return fromPixels(canvas, total, total);
    }

    /** Scale keeping aspect ratio and letterboxing (never stretches a screenshot). */
    public static BufferedImage scaleTo(BufferedImage src, int width, int height) {
        double scale = Math.min((double) width / src.getWidth(), (double) height / src.getHeight());
        int w = Math.min(width, Math.max(1, (int) Math.round(src.getWidth() * scale)));
        int h = Math.min(height, Math.max(1, (int) Math.round(src.getHeight() * scale)));
        BufferedImage scaled = fromPixels(scalePixels(pixels(src), src.getWidth(), src.getHeight(), w, h), w, h);
        if (w == width && h == height) {
            return scaled;
        }
        int[] canvas = new int[width * height];
        int[] pixels = pixels(scaled);
        int ox = (width - w) / 2;
        int oy = (height - h) / 2;
        for (int y = 0; y < h; y++) {
            System.arraycopy(pixels, y * w, canvas, (oy + y) * width + ox, w);
        }
        return fromPixels(canvas, width, height);
    }

    /** Bilinear resample of an ARGB buffer. Pure integer/float math, no AWT rendering context. */
    public static int[] scalePixels(int[] src, int srcW, int srcH, int dstW, int dstH) {
        int[] out = new int[dstW * dstH];
        if (dstW == srcW && dstH == srcH) {
            System.arraycopy(src, 0, out, 0, out.length);
            return out;
        }
        double sx = (double) srcW / dstW;
        double sy = (double) srcH / dstH;
        for (int y = 0; y < dstH; y++) {
            double fy = Math.max(0, (y + 0.5) * sy - 0.5);
            int y0 = (int) fy;
            int y1 = Math.min(srcH - 1, y0 + 1);
            double ty = fy - y0;
            for (int x = 0; x < dstW; x++) {
                double fx = Math.max(0, (x + 0.5) * sx - 0.5);
                int x0 = (int) fx;
                int x1 = Math.min(srcW - 1, x0 + 1);
                double tx = fx - x0;
                out[y * dstW + x] = blend(
                        src[y0 * srcW + x0], src[y0 * srcW + x1],
                        src[y1 * srcW + x0], src[y1 * srcW + x1],
                        tx, ty);
            }
        }
        return out;
    }

    private static int blend(int c00, int c10, int c01, int c11, double tx, double ty) {
        double w00 = (1 - tx) * (1 - ty);
        double w10 = tx * (1 - ty);
        double w01 = (1 - tx) * ty;
        double w11 = tx * ty;
        int a = channel((c00 >> 24) & 0xFF, (c10 >> 24) & 0xFF, (c01 >> 24) & 0xFF, (c11 >> 24) & 0xFF, w00, w10, w01, w11);
        int r = channel((c00 >> 16) & 0xFF, (c10 >> 16) & 0xFF, (c01 >> 16) & 0xFF, (c11 >> 16) & 0xFF, w00, w10, w01, w11);
        int g = channel((c00 >> 8) & 0xFF, (c10 >> 8) & 0xFF, (c01 >> 8) & 0xFF, (c11 >> 8) & 0xFF, w00, w10, w01, w11);
        int b = channel(c00 & 0xFF, c10 & 0xFF, c01 & 0xFF, c11 & 0xFF, w00, w10, w01, w11);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int c00, int c10, int c01, int c11,
                               double w00, double w10, double w01, double w11) {
        double v = c00 * w00 + c10 * w10 + c01 * w01 + c11 * w11;
        return Math.max(0, Math.min(255, (int) Math.round(v)));
    }

    /** Copies an image into an opaque ARGB buffer. */
    public static int[] pixels(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] argb = new int[w * h];
        image.getRGB(0, 0, w, h, argb, 0, w);
        return argb;
    }

    /** Wraps an ARGB buffer in an image; {@code setRGB} needs no graphics environment. */
    public static BufferedImage fromPixels(int[] argb, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        return image;
    }

    /** JPEG encode with explicit quality (0.1 – 1.0); quality 0.7 keeps a 1024² collage well under 150 KB. */
    public static byte[] toJpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(Math.max(0.1f, Math.min(1.0f, quality)));
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 * 1024);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
