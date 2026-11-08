package com.craftguessr.server.player;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSnapshotsCodecTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void roundTripsThroughJson() {
        UUID id = UUID.fromString("198a8791-de78-6b6a-b1f9-40a3f4a1b0c2");
        Snapshot snapshot = new Snapshot(ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                Identifier.fromNamespaceAndPath("minecraft", "overworld")),
                12.5, 64, -3.5, 90.0F, 10.0F, GameType.SURVIVAL, true, false, 18.0F);
        PlayerSnapshots data = new PlayerSnapshots(Map.of(id, snapshot));

        var json = PlayerSnapshots.CODECS.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        PlayerSnapshots decoded = PlayerSnapshots.CODECS.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(1, decoded.all().size());
        Snapshot restored = decoded.get(id);
        assertEquals(snapshot.dimension(), restored.dimension());
        assertEquals(snapshot.x(), restored.x(), 1e-9);
        assertEquals(snapshot.yaw(), restored.yaw(), 1e-6);
        assertEquals(GameType.SURVIVAL, restored.gameMode());
        assertTrue(restored.invisible());
        assertEquals(18.0F, restored.health(), 1e-6);
        assertEquals(Level.OVERWORLD, snapshot.dimension());
    }
}
