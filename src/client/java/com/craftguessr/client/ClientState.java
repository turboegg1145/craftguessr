package com.craftguessr.client;

import com.craftguessr.game.RoomStateView;
import com.craftguessr.game.RoomSummary;
import com.craftguessr.game.RoundReveal;
import com.craftguessr.game.Spot;
import com.craftguessr.network.Payloads;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client mirror of the server-authoritative game state, fed by {@link ClientNetwork} receivers. */
public final class ClientState {

    public static final String FALLBACK_DIMENSION = "minecraft:overworld";

    private static final ClientState INSTANCE = new ClientState();

    public static ClientState get() {
        return INSTANCE;
    }

    private final Map<String, byte[]> tiles = new ConcurrentHashMap<>();
    private final Map<String, byte[]> thumbs = new ConcurrentHashMap<>();
    private final Map<String, Long> tileRequests = new ConcurrentHashMap<>();
    private final Map<String, Long> thumbRequests = new ConcurrentHashMap<>();

    private volatile RoomStateView room;
    private volatile RoundReveal reveal;
    private volatile Payloads.RoundStartS2C roundStart;
    private volatile Payloads.PingS2C ping;
    private volatile List<RoomSummary> rooms = List.of();
    private volatile List<Spot> pool = List.of();
    private volatile Payloads.ScanProgressS2C scan;
    private volatile boolean scanning;

    private ClientState() {
    }

    // ------------------------------------------------------------------ writes

    public void setRoomList(List<RoomSummary> list) {
        this.rooms = List.copyOf(list);
    }

    public void setRoom(RoomStateView view) {
        this.room = view;
        if (view == null) {
            this.reveal = null;
            this.roundStart = null;
        }
    }

    public void setRoundStart(Payloads.RoundStartS2C payload) {
        this.roundStart = payload;
        this.reveal = null;
    }

    public void setReveal(RoundReveal value) {
        this.reveal = value;
    }

    public void setPing(Payloads.PingS2C value) {
        this.ping = value;
    }

    public void setPool(List<Spot> spots) {
        this.pool = List.copyOf(spots);
    }

    public void putTile(String key, byte[] png) {
        if (png != null && png.length > 0) {
            tiles.put(key, png);
        }
    }

    public void putThumb(String spotId, byte[] jpeg) {
        if (jpeg != null && jpeg.length > 0) {
            thumbs.put(spotId, jpeg);
        }
    }

    public void setScan(Payloads.ScanProgressS2C payload) {
        this.scan = payload;
        this.scanning = !payload.finished();
    }

    public void clearScan() {
        this.scan = null;
        this.scanning = false;
    }

    // ------------------------------------------------------------------ reads

    public RoomStateView room() {
        return room;
    }

    public RoundReveal reveal() {
        return reveal;
    }

    public Payloads.RoundStartS2C roundStart() {
        return roundStart;
    }

    public Payloads.PingS2C ping() {
        return ping;
    }

    public List<RoomSummary> rooms() {
        return rooms;
    }

    public List<Spot> pool() {
        return pool;
    }

    public Map<String, byte[]> tiles() {
        return tiles;
    }

    public Map<String, byte[]> thumbs() {
        return thumbs;
    }

    public Payloads.ScanProgressS2C scan() {
        return scan;
    }

    public boolean scanning() {
        return scanning;
    }

    public UUID selfId() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? null : minecraft.player.getUUID();
    }

    public RoomStateView.PlayerView me() {
        RoomStateView view = room;
        UUID id = selfId();
        return view == null || id == null ? null : view.me(id);
    }

    public boolean inRoom() {
        return room != null;
    }

    public boolean isHost() {
        RoomStateView view = room;
        UUID id = selfId();
        return view != null && id != null && id.equals(view.hostId());
    }

    public String roomId() {
        RoomStateView view = room;
        return view == null ? null : view.roomId();
    }

    public String dimension() {
        Payloads.RoundStartS2C round = roundStart;
        return round != null ? round.dimension() : FALLBACK_DIMENSION;
    }

    public int secondsLeft() {
        RoomStateView view = room;
        return view == null ? 0 : view.secondsLeft();
    }

    public Spot poolSpot(String id) {
        for (Spot spot : pool) {
            if (spot.id().equals(id)) {
                return spot;
            }
        }
        return null;
    }

    /** True when this tile should be requested from the server (not cached, not requested recently). */
    public boolean shouldRequestTile(String key) {
        if (tiles.containsKey(key)) {
            return false;
        }
        return throttle(tileRequests, key, 5_000L, 4096);
    }

    public boolean shouldRequestThumb(String spotId) {
        if (thumbs.containsKey(spotId)) {
            return false;
        }
        return throttle(thumbRequests, spotId, 10_000L, 2048);
    }

    private static boolean throttle(Map<String, Long> map, String key, long ttlMillis, int cap) {
        long now = System.currentTimeMillis();
        Long last = map.get(key);
        if (last != null && now - last < ttlMillis) {
            return false;
        }
        map.put(key, now);
        if (map.size() > cap) {
            map.clear();
        }
        return true;
    }
}
