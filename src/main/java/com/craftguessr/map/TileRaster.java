package com.craftguessr.map;

import java.awt.image.BufferedImage;

/**
 * Pure pixel shading for the native surface map: takes per-pixel terrain colours and heights and adds
 * a slope-based light so hills, cliffs and valleys read as 3D even without textures.
 */
public final class TileRaster {

    /** Light coming from the north-west, like vanilla maps. */
    private static final int LIGHT_NW = 1;
    /** Marks a pixel as water; shading then applies a depth tint. */
    public static final int WATER_FLAG = 1 << 24;

    private TileRaster() {
    }

    /**
     * @param colors   ARGB top-block colours, row-major (index = z * size + x)
     * @param heights  top solid block Y per pixel (water surface Y for water pixels)
     * @param depths   water depth per pixel, 0 for dry land
     * @param size     tile edge length
     * @return shaded ARGB pixels
     */
    public static int[] shade(int[] colors, int[] heights, int[] depths, int size) {
        int[] out = new int[colors.length];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int idx = z * size + x;
                int rgb = colors[idx] & 0xFFFFFF;
                int h = heights[idx];
                int hNorth = heights[Math.max(0, z - 1) * size + x];
                int hWest = heights[z * size + Math.max(0, x - 1)];
                int hEast = heights[z * size + Math.min(size - 1, x + 1)];
                int hSouth = heights[Math.min(size - 1, z + 1) * size + x];
                // central difference gradient, north-west light
                double gx = (hEast - hWest) * 0.5;
                double gz = (hSouth - hNorth) * 0.5;
                double light = 1.0 + (gx + gz) * 0.045;
                light = Math.max(0.62, Math.min(1.34, light));

                int r = (int) Math.min(255, ((rgb >> 16) & 0xFF) * light);
                int g = (int) Math.min(255, ((rgb >> 8) & 0xFF) * light);
                int b = (int) Math.min(255, (rgb & 0xFF) * light);

                int depth = depths[idx];
                if (depth > 0) {
                    double d = Math.min(1.0, depth / 12.0);
                    r = (int) (r * (0.72 - 0.32 * d) + 30 * (0.2 + d));
                    g = (int) (g * (0.82 - 0.25 * d) + 60 * (0.2 + d));
                    b = (int) (b * (0.95 - 0.15 * d) + 120 * (0.2 + d));
                }
                out[idx] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }

    /** Renders shaded pixels into an image ready for a PNG/JPEG encoder. */
    public static BufferedImage toImage(int[] argb, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, size, size, argb, 0, size);
        return image;
    }

    /** Convenience overload without water. */
    public static int[] shade(int[] colors, int[] heights, int size) {
        return shade(colors, heights, new int[colors.length], size);
    }
}
