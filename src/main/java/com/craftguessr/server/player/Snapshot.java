package com.craftguessr.server.player;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/**
 * Everything a round overwrites on a player: position, look, game mode and invisibility. Captured
 * before the first teleport and replayed on leave, disconnect, game end or server shutdown.
 */
public record Snapshot(
        ResourceKey<Level> dimension,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        GameType gameMode,
        boolean invisible,
        float health
) {

    public static Snapshot capture(ServerPlayer player) {
        return new Snapshot(player.level().dimension(), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), player.gameMode(), player.isInvisible(), player.getHealth());
    }
}
