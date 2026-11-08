package com.craftguessr.scanner;

import com.craftguessr.ai.HostContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a candidate place out of a {@link ServerLevel} without loading chunks: heightmap, a small
 * block sample, line of sight probes, signs and nearby entities. Everything here runs on the scan
 * thread, never on the server thread.
 */
public final class WorldSampler {

    public static final int SAMPLE_RADIUS = 8;
    private static final int SIGHT_RAYS = 12;
    private static final double SIGHT_DISTANCE = 64.0;

    private WorldSampler() {
    }

    /** Diagnostic one-liner used by /geo probe. */
    public static String describe(ServerLevel level, int x, int z) {
        boolean loaded = level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        StringBuilder sb = new StringBuilder("chunk=" + loaded + " minY=" + minY + " maxY=" + maxY
                + " surface=" + surface);
        if (loaded) {
            BlockState ground = level.getBlockState(new BlockPos(x, surface - 1, z));
            BlockState head = level.getBlockState(new BlockPos(x, surface, z));
            sb.append(" ground=").append(BlockClassifier.id(ground))
                    .append(" fluid=").append(!ground.getFluidState().isEmpty())
                    .append(" headAir=").append(head.isAir())
                    .append(" id=").append(BlockClassifier.id(head))
                    .append(" worldSurface=").append(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z))
                    .append(" oceanFloor=").append(level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z));
        }
        return sb.toString();
    }

    /** @return null when the candidate is unusable (unloaded chunk, ocean, bedrock void…) */
    public static Sample sample(ServerLevel level, int x, int z) {
        if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
            return null;
        }
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (surface <= minY + 2 || surface >= maxY - 2) {
            return null;
        }
        BlockPos groundPos = new BlockPos(x, surface - 1, z);
        BlockState ground = level.getBlockState(groundPos);
        if (!ground.getFluidState().isEmpty() || ground.isAir()) {
            return null;
        }
        BlockPos headPos = new BlockPos(x, surface, z);
        if (!level.getBlockState(headPos).isAir()) {
            return null;
        }

        Set<String> types = new HashSet<>();
        Map<String, Integer> artificialCounts = new HashMap<>();
        int total = 0;
        int artificial = 0;
        int natural = 0;
        int air = 0;
        int airSamples = 0;
        int columns = 0;
        long surfaceSum = 0;
        for (int dx = -SAMPLE_RADIUS; dx <= SAMPLE_RADIUS; dx++) {
            for (int dz = -SAMPLE_RADIUS; dz <= SAMPLE_RADIUS; dz++) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz);
                if (h <= minY + 1) {
                    continue;
                }
                columns++;
                surfaceSum += h;
                for (int dy = -2; dy <= 2; dy++) {
                    int y = h - 1 + dy;
                    if (y <= minY || y >= maxY) {
                        continue;
                    }
                    BlockState state = level.getBlockState(new BlockPos(x + dx, y, z + dz));
                    if (state.isAir()) {
                        air++;
                        airSamples++;
                        continue;
                    }
                    total++;
                    airSamples++;
                    types.add(BlockClassifier.id(state));
                    if (BlockClassifier.isArtificial(state)) {
                        artificial++;
                        artificialCounts.merge(BlockClassifier.path(state), 1, Integer::sum);
                    } else {
                        natural++;
                    }
                }
            }
        }
        if (columns == 0 || total == 0) {
            return null;
        }
        double averageSurface = (double) surfaceSum / columns;
        double airRatio = airSamples == 0 ? 0 : (double) air / airSamples;

        Vec3 eye = new Vec3(x + 0.5, surface + 1.62, z + 0.5);
        int open = 0;
        for (int i = 0; i < SIGHT_RAYS; i++) {
            double yaw = Math.toRadians(i * (360.0 / SIGHT_RAYS));
            Vec3 target = new Vec3(eye.x - Math.sin(yaw) * SIGHT_DISTANCE, eye.y,
                    eye.z + Math.cos(yaw) * SIGHT_DISTANCE);
            try {
                BlockHitResult hit = level.clip(new ClipContext(eye, target, ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE, CollisionContext.empty()));
                if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(eye) > 24.0) {
                    open++;
                }
            } catch (RuntimeException ignored) {
                // Broken chunk data in imported worlds must not fail the whole sample.
            }
        }
        int enclosed = 0;
        for (int i = 0; i < 4; i++) {
            double yaw = Math.toRadians(i * 90.0);
            BlockPos side = new BlockPos(x + (int) Math.round(-Math.sin(yaw) * 2.0), surface + 1,
                    z + (int) Math.round(Math.cos(yaw) * 2.0));
            try {
                if (level.getBlockState(side).isSolidRender()) {
                    enclosed++;
                }
            } catch (RuntimeException ignored) {
            }
        }
        double enclosedRatio = enclosed / 4.0;

        List<String> signs = readSigns(level, x, surface, z, minY, maxY);
        List<String> entities = readEntities(level, x, surface, z);
        String biome = level.getBiome(new BlockPos(x, surface, z)).unwrapKey()
                .map(key -> key.identifier().toString()).orElse("unknown");

        long inhabited = level.getChunk(x >> 4, z >> 4).getInhabitedTime();
        int inhabitedInt = (int) Math.min(Integer.MAX_VALUE, inhabited);

        Map<String, Integer> topArtificial = new LinkedHashMap<>();
        artificialCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(6)
                .forEach(entry -> topArtificial.put(entry.getKey(), entry.getValue()));

        BlockStats stats = new BlockStats(total, artificial, natural, types.size(), inhabitedInt,
                airRatio, open, SIGHT_RAYS, enclosedRatio, surface);

        long time = level.getLevelData().getGameTime();
        boolean night = time % 24000L >= 12800L;
        HostContext context = new HostContext(level.dimension().identifier().toString(), x, surface, z,
                biome, night, level.isRaining(), open, SIGHT_RAYS,
                total == 0 ? 0 : (double) artificial / total, types.size(), inhabitedInt,
                topArtificial, signs, entities);
        return new Sample(x, surface, z, (int) Math.round(averageSurface), stats, context);
    }

    private static List<String> readSigns(ServerLevel level, int x, int surface, int z, int minY, int maxY) {
        List<String> out = new ArrayList<>();
        for (int dx = -8; dx <= 8 && out.size() < 4; dx++) {
            for (int dz = -8; dz <= 8 && out.size() < 4; dz++) {
                for (int dy = -2; dy <= 3 && out.size() < 4; dy++) {
                    int y = surface + dy;
                    if (y <= minY || y >= maxY) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x + dx, y, z + dz);
                    if (!level.getBlockState(pos).hasBlockEntity()) {
                        continue;
                    }
                    BlockEntity entity = level.getBlockEntity(pos);
                    if (!(entity instanceof SignBlockEntity sign)) {
                        continue;
                    }
                    StringBuilder line = new StringBuilder();
                    for (var component : sign.getText(SignTextSlot.FRONT).getMessages(false)) {
                        String text = component.getString().trim();
                        if (!text.isEmpty()) {
                            if (!line.isEmpty()) {
                                line.append(" | ");
                            }
                            line.append(text);
                        }
                    }
                    if (!line.isEmpty()) {
                        out.add(line.toString());
                    }
                }
            }
        }
        return out;
    }

    private static List<String> readEntities(ServerLevel level, int x, int surface, int z) {
        List<String> out = new ArrayList<>();
        AABB box = new AABB(x - 20, surface - 8, z - 20, x + 20, surface + 16, z + 20);
        List<Entity> found = level.getEntities((Entity) null, box,
                entity -> !(entity instanceof Player) && entity.isAlive());
        for (Entity entity : found) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getKey(entity.getType()).toString();
            if (!out.contains(id)) {
                out.add(id);
            }
            if (out.size() >= 6) {
                break;
            }
        }
        return out;
    }

    /** One sampled candidate: ground height, statistics and the prompt context. */
    public record Sample(int x, int y, int z, int averageSurface, BlockStats stats, HostContext context) {
    }

    /** Unused but handy when a caller wants the raw map colour of the ground block. */
    public static int mapColour(ServerLevel level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        MapColor color = state.getMapColor(level, new BlockPos(x, y, z));
        return color == MapColor.NONE ? 0x808080 : color.calculateARGBColor(MapColor.Brightness.NORMAL);
    }
}
