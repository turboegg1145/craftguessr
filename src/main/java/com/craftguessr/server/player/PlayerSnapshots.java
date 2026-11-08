package com.craftguessr.server.player;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crash-safe store of the pre-round snapshot of every player currently inside an arena. Written to the
 * overworld data storage whenever a round starts or ends, so a server that dies mid-round can put its
 * players back where they came from on the next boot.
 */
public final class PlayerSnapshots extends SavedData {

    private final Map<UUID, Snapshot> snapshots = new LinkedHashMap<>();

    public static final SavedDataType<PlayerSnapshots> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("craftguessr", "player_snapshots"),
            PlayerSnapshots::new,
            codec(),
            DataFixTypes.LEVEL);

    public PlayerSnapshots() {
    }

    public PlayerSnapshots(Map<UUID, Snapshot> initial) {
        snapshots.putAll(initial);
    }

    private static Codec<PlayerSnapshots> codec() {
        return Codec.unboundedMap(UUIDUtil.CODEC, Snapshot.CODEC)
                .xmap(PlayerSnapshots::new, snapshots -> snapshots.snapshots);
    }

    public Map<UUID, Snapshot> all() {
        return Map.copyOf(snapshots);
    }

    public Snapshot get(UUID player) {
        return snapshots.get(player);
    }

    public void put(UUID player, Snapshot snapshot) {
        snapshots.put(player, snapshot);
        setDirty();
    }

    public Snapshot remove(UUID player) {
        Snapshot removed = snapshots.remove(player);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    public boolean isEmpty() {
        return snapshots.isEmpty();
    }
}
