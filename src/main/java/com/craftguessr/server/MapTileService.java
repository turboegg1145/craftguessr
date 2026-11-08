package com.craftguessr.server;

import com.craftguessr.map.ImageCollage;
import com.craftguessr.map.TileGrid;
import com.craftguessr.map.TileRaster;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side surface tiles for the native 2D map: MapColor raster plus heightmap shading, one pixel
 * per block, cached as PNG bytes. Used by the guess screen and the reveal map when no web map or Xaero
 * cache is available.
 */
public final class MapTileService {

    private static final int MAX_CACHE = 4096;
    /** Unloaded or ungenerated terrain reads as a neutral slate instead of vanilla void black. */
    private static final int UNKNOWN = 0x2A3342;
    private static final int WATER_RGB = 0x3F76E4;

    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    public int cachedTiles() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
    }

    /**
     * @param zoom client zoom, accepted for protocol compatibility; tiles are always 1:1 block rasters
     * @return PNG bytes of a {@link TileGrid#TILE_SIZE} square, or an empty image when unloaded
     */
    public byte[] tile(ServerLevel level, int zoom, int tileX, int tileZ) {
        String key = level.dimension().identifier() + ":" + tileX + ":" + tileZ;
        byte[] cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            byte[] png = render(level, tileX, tileZ);
            if (cache.size() >= MAX_CACHE) {
                cache.clear();
            }
            cache.put(key, png);
            return png;
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private byte[] render(ServerLevel level, int tileX, int tileZ) throws IOException {
        int size = TileGrid.TILE_SIZE;
        int[] colors = new int[size * size];
        int[] heights = new int[size * size];
        int[] depths = new int[size * size];
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int pz = 0; pz < size; pz++) {
            int worldZ = TileGrid.worldFromPixel(tileZ, pz);
            for (int px = 0; px < size; px++) {
                int worldX = TileGrid.worldFromPixel(tileX, px);
                int index = pz * size + px;
                if (level.getChunkSource().getChunkNow(worldX >> 4, worldZ >> 4) == null) {
                    colors[index] = UNKNOWN;
                    heights[index] = minY;
                    continue;
                }
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ);
                int y = Math.max(minY + 1, Math.min(maxY - 1, surfaceY - 1));
                pos.set(worldX, y, worldZ);
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) {
                    pos.set(worldX, Math.max(minY, surfaceY - 2), worldZ);
                    state = level.getBlockState(pos);
                    y = Math.max(minY, surfaceY - 2);
                }
                int rgb = state.getMapColor(level, pos).calculateARGBColor(MapColor.Brightness.NORMAL);
                if (state.is(Blocks.WATER)) {
                    rgb = WATER_RGB;
                    int floorY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, worldX, worldZ);
                    depths[index] = Math.max(0, surfaceY - floorY);
                    colors[index] = (rgb & 0xFFFFFF) | TileRaster.WATER_FLAG;
                } else {
                    colors[index] = rgb & 0xFFFFFF;
                }
                heights[index] = Math.max(surfaceY, y);
            }
        }
        int[] shaded = TileRaster.shade(colors, heights, depths, size);
        BufferedImage image = ImageCollage.fromPixels(shaded, size, size);
        ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
