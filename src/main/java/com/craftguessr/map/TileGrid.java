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

    /** Screen scale factor for a zoom level (2^zoom pixels per block). */
    public static double scaleForZoom(int zoom) {
        int clamped = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
        return Math.pow(2, clamped - 2); // zoom 2 == 1 px per block
    }

    /** Deterministic cache key for a tile of a given dimension. */
    public static String tileKey(String dimension, int tileX, int tileZ) {
        return dimension + ":" + tileX + ":" + tileZ;
    }
}
