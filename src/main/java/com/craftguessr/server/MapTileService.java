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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side surface tiles for the native 2D map: a MapColor raster plus heightmap shading, rendered as
 * a three-level pyramid (1:1, 1:4, 1:16 blocks per pixel). Tiles are written to disk under
 * {@code <craftguessr data>/tiles/<dimension>/<lod>/} so the pool never has to be re-rendered after a
 * restart. Rendering is incremental: chunks are loaded a few at a time across server ticks so a big
 * pyramid level cannot stall the tick loop, and each client gets a small token bucket.
 */
public final class MapTileService {

    private static final int MAX_CACHE = 4096;
    /** Unloaded or ungenerated terrain reads as a neutral slate instead of vanilla void black. */
    private static final int UNKNOWN = 0x2A3342;
    private static final int WATER_RGB = 0x3F76E4;

    /** Per-player token bucket: 10 tiles per second, burst of 20. */
    private static final int BURST = 20;
    private static final double RATE = 10.0;

    /** Rendering budgets per server tick. */
    private static final int ROWS_PER_TICK = 24;
    private static final int CHUNK_LOADS_PER_TICK = 12;

    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();
    /** Server thread only. */
    private final Map<String, TileJob> pending = new LinkedHashMap<>();
    private final Path root;

    private record Bucket(double tokens, long lastMillis) {
    }

    public MapTileService(Path root) {
        this.root = root;
    }

    public int cachedTiles() {
        return cache.size();
    }

    public int pendingTiles() {
        return pending.size();
    }

    public void clear() {
        cache.clear();
        pending.clear();
    }

    /** Rate limit a single client; returns false when the request should be dropped. */
    public boolean allow(UUID player) {
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.compute(player, (uuid, old) -> {
            double tokens = old == null
                    ? BURST
                    : Math.min(BURST, old.tokens() + (now - old.lastMillis()) / 1000.0 * RATE);
            return new Bucket(tokens - 1.0, now);
        });
        if (bucket.tokens() < 0) {
            return false;
        }
        if (buckets.size() > 256) {
            buckets.clear();
        }
        return true;
    }

    /**
     * @param zoom pyramid level 0 (1:1) .. 2 (1:16); anything else is clamped
     * @return PNG bytes of a {@link TileGrid#TILE_SIZE} square, or an empty image when unloaded
     */
    public byte[] tile(ServerLevel level, int zoom, int tileX, int tileZ) {
        int lod = Math.max(0, Math.min(TileGrid.LOD_COUNT - 1, zoom));
        String dimension = level.dimension().identifier().toString();
        String key = TileGrid.tileKey(dimension, lod, tileX, tileZ);
        byte[] cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Path file = fileFor(dimension, lod, tileX, tileZ);
            if (Files.isRegularFile(file)) {
                byte[] bytes = Files.readAllBytes(file);
                cache.put(key, bytes);
                return bytes;
            }
        } catch (IOException ignored) {
            // fall through to a render
        }
        pending.computeIfAbsent(key, ignored -> new TileJob(level, lod, tileX, tileZ, key));
        return new byte[0];
    }

    /** Advance pending tile renders; called from the server tick. */
    public void tick() {
        if (pending.isEmpty()) {
            return;
        }
        int rowsLeft = ROWS_PER_TICK;
        int[] chunkBudget = {CHUNK_LOADS_PER_TICK};
        Iterator<Map.Entry<String, TileJob>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext() && rowsLeft > 0 && chunkBudget[0] > 0) {
            Map.Entry<String, TileJob> entry = iterator.next();
            TileJob job = entry.getValue();
            int used = job.render(rowsLeft, chunkBudget);
            if (used <= 0) {
                continue;
            }
            rowsLeft -= used;
            if (job.rows == TileGrid.TILE_SIZE) {
                byte[] png = job.finish();
                if (png != null) {
                    if (cache.size() >= MAX_CACHE) {
                        cache.clear();
                    }
                    cache.put(entry.getKey(), png);
                }
                iterator.remove();
            }
        }
    }

    private Path fileFor(String dimension, int lod, int tileX, int tileZ) {
        String safe = dimension.replace(':', '_').replace('/', '_');
        return root.resolve(safe).resolve(Integer.toString(lod)).resolve(tileX + "_" + tileZ + ".png");
    }

    private final class TileJob {
        private final ServerLevel level;
        private final int lod;
        private final int tileX;
        private final int tileZ;
        private final int[] colors = new int[TileGrid.TILE_SIZE * TileGrid.TILE_SIZE];
        private final int[] heights = new int[TileGrid.TILE_SIZE * TileGrid.TILE_SIZE];
        private final int[] depths = new int[TileGrid.TILE_SIZE * TileGrid.TILE_SIZE];
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private int rows;
        private int column;

        private TileJob(ServerLevel level, int lod, int tileX, int tileZ, String key) {
            this.level = level;
            this.lod = lod;
            this.tileX = tileX;
            this.tileZ = tileZ;
        }

        /**
         * Render at most {@code maxRows} rows.
         *
         * @return rows rendered, or -1 when the job is waiting for a chunk budget
         */
        private int render(int maxRows, int[] chunkBudget) {
            int done = 0;
            int minY = level.getMinY();
            int maxY = level.getMaxY();
            int bpp = TileGrid.blocksPerPixel(lod);
            while (done < maxRows && rows < TileGrid.TILE_SIZE) {
                int worldZ = TileGrid.worldFromPixel(tileZ, rows, lod);
                for (; column < TileGrid.TILE_SIZE; column++) {
                    int worldX = TileGrid.worldFromPixel(tileX, column, lod);
                    int index = rows * TileGrid.TILE_SIZE + column;
                    if (level.getChunkSource().getChunkNow(worldX >> 4, worldZ >> 4) == null) {
                        if (chunkBudget[0] <= 0) {
                            return done;
                        }
                        try {
                            level.getChunk(worldX >> 4, worldZ >> 4);
                        } catch (RuntimeException e) {
                            colors[index] = UNKNOWN;
                            heights[index] = minY;
                            continue;
                        }
                        chunkBudget[0]--;
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
                    int rgb;
                    try {
                        rgb = state.getMapColor(level, pos).calculateARGBColor(MapColor.Brightness.NORMAL);
                    } catch (RuntimeException e) {
                        rgb = UNKNOWN;
                    }
                    if (state.is(Blocks.WATER)) {
                        rgb = WATER_RGB;
                        int floorY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, worldX, worldZ);
                        depths[index] = Math.max(0, (surfaceY - floorY) / bpp);
                        colors[index] = (rgb & 0xFFFFFF) | TileRaster.WATER_FLAG;
                    } else {
                        colors[index] = rgb & 0xFFFFFF;
                    }
                    heights[index] = Math.max(surfaceY, y);
                }
                column = 0;
                rows++;
                done++;
            }
            return done;
        }

        private byte[] finish() {
            try {
                int size = TileGrid.TILE_SIZE;
                int[] shaded = TileRaster.shade(colors, heights, depths, size);
                BufferedImage image = ImageCollage.fromPixels(shaded, size, size);
                ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
                ImageIO.write(image, "png", out);
                byte[] png = out.toByteArray();
                persist(png);
                return png;
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }

        private void persist(byte[] png) {
            String dimension = level.dimension().identifier().toString();
            Path file = fileFor(dimension, lod, tileX, tileZ);
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.write(tmp, png);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // A read-only data directory must not break the map.
            }
        }
    }
}
