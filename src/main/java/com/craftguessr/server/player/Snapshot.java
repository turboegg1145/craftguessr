package com.craftguessr.server.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/**
 * Everything a round overwrites on a player: position, look, game mode, invisibility, invulnerability
 * and health. Captured before the first teleport and replayed on leave, disconnect, game end or server
 * shutdown, and persisted through {@link PlayerSnapshots} so a crash cannot strand a player.
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
        boolean invulnerable,
        float health
) {

    public static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Snapshot::dimension),
            Codec.DOUBLE.fieldOf("x").forGetter(Snapshot::x),
            Codec.DOUBLE.fieldOf("y").forGetter(Snapshot::y),
            Codec.DOUBLE.fieldOf("z").forGetter(Snapshot::z),
            Codec.FLOAT.fieldOf("yaw").forGetter(Snapshot::yaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(Snapshot::pitch),
            GameType.CODEC.fieldOf("game_mode").forGetter(Snapshot::gameMode),
            Codec.BOOL.optionalFieldOf("invisible", false).forGetter(Snapshot::invisible),
            Codec.BOOL.optionalFieldOf("invulnerable", false).forGetter(Snapshot::invulnerable),
            Codec.FLOAT.optionalFieldOf("health", 20.0F).forGetter(Snapshot::health)
    ).apply(instance, Snapshot::new));

    public static Snapshot capture(ServerPlayer player) {
        return new Snapshot(player.level().dimension(), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), player.gameMode(), player.isInvisible(),
                player.isInvulnerable(), player.getHealth());
    }
}
