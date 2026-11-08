package com.craftguessr.server;

import com.craftguessr.CraftGuessr;
import com.craftguessr.network.Payloads;
import com.craftguessr.scanner.FilterStrength;
import com.craftguessr.scanner.LocalHeuristic;
import com.craftguessr.scanner.WorldSampler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Auto-pilot tour: teleports the operator through a list of pre-filtered candidate spots and asks the
 * client to run the regular quad capture at each one. Every submitted collage advances the tour, so the
 * client needs no tour-specific code — it just receives {@link Payloads.CaptureRequestS2C} events.
 */
public final class TourService {

    private static final int MAX_COUNT = 32;
    private static final int MIN_SPACING = 48;

    private final ServerGameManager manager;
    private final Map<UUID, Tour> tours = new HashMap<>();

    private record Tour(String dimension, List<int[]> spots, int index, double originX, double originY,
                        double originZ, float originYaw, float originPitch, String originDimension) {
    }

    public TourService(ServerGameManager manager) {
        this.manager = manager;
    }

    public boolean isTouring(ServerPlayer player) {
        return tours.containsKey(player.getUUID());
    }

    /**
     * Collects candidates around the player and starts the tour.
     *
     * @return number of scheduled spots, 0 when nothing passed the local filter
     */
    public int start(ServerPlayer player, int radius, int count, String strengthId) {
        ServerLevel level = (ServerLevel) player.level();
        int clampedRadius = Math.max(64, Math.min(1024, radius));
        int wanted = Math.max(1, Math.min(MAX_COUNT, count));
        FilterStrength strength;
        try {
            strength = FilterStrength.valueOf(strengthId.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            strength = FilterStrength.BALANCED;
        }
        List<int[]> spots = new ArrayList<>();
        int step = Math.max(24, clampedRadius / 10);
        for (int ring = 1; ring <= 3 && spots.size() < wanted; ring++) {
            int ringRadius = clampedRadius * ring / 3;
            for (int dx = -ringRadius; dx <= ringRadius; dx += step) {
                for (int dz = -ringRadius; dz <= ringRadius; dz += step) {
                    if (spots.size() >= wanted) {
                        break;
                    }
                    int x = (int) Math.floor(player.getX()) + dx;
                    int z = (int) Math.floor(player.getZ()) + dz;
                    WorldSampler.Sample sample = WorldSampler.sample(level, x, z);
                    if (sample == null || !LocalHeuristic.evaluate(sample.stats(), strength).pass()) {
                        continue;
                    }
                    if (spots.stream().anyMatch(s -> Math.hypot(s[0] - x, s[1] - z) < MIN_SPACING)) {
                        continue;
                    }
                    spots.add(new int[]{x, z});
                }
            }
        }
        if (spots.isEmpty()) {
            return 0;
        }
        Tour tour = new Tour(level.dimension().identifier().toString(), spots, 0,
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                level.dimension().identifier().toString());
        tours.put(player.getUUID(), tour);
        CraftGuessr.LOGGER.info("tour start for {}: {} spots radius {} strength {}",
                player.getName().getString(), spots.size(), clampedRadius, strength);
        gotoStep(player, tour, 0);
        return spots.size();
    }

    /** Called after a spot captured during a tour has been stored. */
    public void onSubmitted(ServerPlayer player) {
        Tour tour = tours.get(player.getUUID());
        if (tour == null) {
            return;
        }
        int next = tour.index() + 1;
        if (next >= tour.spots().size()) {
            finish(player, tour);
            return;
        }
        tours.put(player.getUUID(), new Tour(tour.dimension(), tour.spots(), next, tour.originX(),
                tour.originY(), tour.originZ(), tour.originYaw(), tour.originPitch(), tour.originDimension()));
        gotoStep(player, tour, next);
    }

    public void cancel(ServerPlayer player) {
        Tour tour = tours.remove(player.getUUID());
        if (tour != null) {
            restore(player, tour);
            manager.notice(player, "craftguessr.command.tour_cancelled", false);
        }
    }

    public void onDisconnect(ServerPlayer player) {
        tours.remove(player.getUUID());
    }

    private void gotoStep(ServerPlayer player, Tour tour, int index) {
        int[] spot = tour.spots().get(index);
        ServerLevel level = manager.level(tour.dimension());
        if (level == null) {
            finish(player, tour);
            return;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spot[0], spot[1]);
        player.teleportTo(level, spot[0] + 0.5, y, spot[1] + 0.5, Set.<Relative>of(), 0.0F, 0.0F, false);
        ServerPlayNetworking.send(player, new Payloads.NoticeS2C("Tour step " + (index + 1) + "/" + tour.spots().size(), false));
        ServerPlayNetworking.send(player, new Payloads.CaptureRequestS2C("", ""));
    }

    private void finish(ServerPlayer player, Tour tour) {
        tours.remove(player.getUUID());
        restore(player, tour);
        manager.notice(player, "craftguessr.command.tour_finished", false);
    }

    private void restore(ServerPlayer player, Tour tour) {
        ServerLevel level = manager.level(tour.originDimension());
        if (level == null) {
            return;
        }
        player.teleportTo(level, tour.originX(), tour.originY(), tour.originZ(), Set.<Relative>of(),
                tour.originYaw(), tour.originPitch(), false);
    }

    /** Drops tours whose player vanished, called from the server tick. */
    public void tick() {
        if (tours.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Tour>> iterator = tours.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Tour> entry = iterator.next();
            if (manager.player(entry.getKey()) == null) {
                iterator.remove();
            }
        }
    }
}
