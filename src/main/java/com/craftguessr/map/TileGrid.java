package com.craftguessr.map;

/**
 * Tile/world coordinate math for the native surface map.
 *
 * <p>One tile covers {@link #TILE_SIZE}×{@link #TILE_SIZE} blocks, one pixel per block. Tile axes grow
 * with world +X (east) and +Z (south), so tile-space y already matches screen-space y; the map draws
 * north up without any vertical flip.
 */
public final class TileGrid {

    public static final int TILE_SIZE = 128;
    /** Zoom levels the client renders by scaling the 1:1 block raster. */
    public static final int MIN_ZOOM = 0;
    public static final int MAX_ZOOM = 5;

    private TileGrid() {
    }

    public static int tileOf(int worldCoord) {
        return Math.floorDiv(worldCoord, TILE_SIZE);
    }

    public static int pixelInTile(int worldCoord) {
        return Math.floorMod(worldCoord, TILE_SIZE);
    }

    public static int worldFromPixel(int tile, int pixel) {
        return tile * TILE_SIZE + pixel;
    }

    /** Native pyramid depth: 1:1, 1:4 and 1:16 block rasters. */
    public static final int LOD_COUNT = 3;

    /** Blocks covered by one pixel at a LOD (1, 4, 16). */
    public static int blocksPerPixel(int lod) {
        return 1 << (2 * Math.max(0, Math.min(LOD_COUNT - 1, lod)));
    }

    /** Blocks covered by one tile at a LOD (128, 512, 2048). */
    public static int blocksPerTile(int lod) {
        return TILE_SIZE * blocksPerPixel(lod);
    }

    public static int tileOf(int worldCoord, int lod) {
        return Math.floorDiv(worldCoord, blocksPerTile(lod));
    }

    /** Centre block of the given pixel in a LOD tile. */
    public static int worldFromPixel(int tile, int pixel, int lod) {
        int bpp = blocksPerPixel(lod);
        return tile * blocksPerTile(lod) + pixel * bpp + bpp / 2;
    }

    /** Pick the LOD whose pixels are about one screen pixel at the given scale (px per block). */
    public static int lodForScale(double scale) {
        if (scale >= 0.5) {
            return 0;
        }
        if (scale >= 0.125) {
            return 1;
        }
        return 2;
    }

    /** Screen scale factor for a zoom level (2^zoom pixels per block). */
    public static double scaleForZoom(int zoom) {
        int clamped = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
        return Math.pow(2, clamped - 2); // zoom 2 == 1 px per block
    }

    /** Deterministic cache key for a tile of a given dimension and LOD. */
    public static String tileKey(String dimension, int lod, int tileX, int tileZ) {
        return dimension + ":" + lod + ":" + tileX + ":" + tileZ;
    }
}
