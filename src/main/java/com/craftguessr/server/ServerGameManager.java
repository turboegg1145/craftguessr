package com.craftguessr.server;

import com.craftguessr.CraftGuessr;
import com.craftguessr.game.GameMode;
import com.craftguessr.game.GamePhase;
import com.craftguessr.game.GameSettings;
import com.craftguessr.game.MovementMode;
import com.craftguessr.game.RoomSettings;
import com.craftguessr.game.RoomStateView;
import com.craftguessr.game.RoomSummary;
import com.craftguessr.game.RoundReveal;
import com.craftguessr.game.Scoring;
import com.craftguessr.game.Spot;
import com.craftguessr.network.Payloads;
import com.craftguessr.server.player.Snapshot;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Server-authoritative rooms, phases and scoring.
 *
 * <p>One instance lives for the whole integrated/dedicated server run. It owns the spot pool, the
 * active rooms, and every player's pre-round snapshot so a round can always be undone.
 */
public final class ServerGameManager {

    /** Countdown between the teleport and the first allowed input. */
    public static final int PREPARE_SECONDS = 3;
    /** Score distance that halves the round score (blocks). */
    public static final double SCORING_SPAN = 3000.0;
    private static final String ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static ServerGameManager instance;

    private final MinecraftServer server;
    private GameSettings settings;
    private final com.craftguessr.game.SpotRepository repository;
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<UUID, String> roomOf = new HashMap<>();
    private final ScanService scanService;
    private final com.craftguessr.ai.TokenLedger ledger = new com.craftguessr.ai.TokenLedger();
    private final MapTileService tiles = new MapTileService(CraftGuessr.dataDirectory().resolve("tiles"));
    private final com.craftguessr.server.player.PlayerSnapshots snapshots;
    private boolean rulesOverridden;
    private boolean spiesAllowed = true;
    private boolean mobGriefingAllowed = true;

    public ServerGameManager(MinecraftServer server, GameSettings settings) {
        this.server = server;
        this.settings = settings;
        this.repository = com.craftguessr.game.SpotRepository.open(CraftGuessr.dataDirectory());
        this.scanService = new ScanService(this);
        this.snapshots = server.overworld().getDataStorage()
                .computeIfAbsent(com.craftguessr.server.player.PlayerSnapshots.TYPE);
        instance = this;
    }

    public static ServerGameManager get() {
        return instance;
    }

    public static void clear() {
        instance = null;
    }

    public MinecraftServer server() {
        return server;
    }

    public GameSettings settings() {
        return settings;
    }

    public com.craftguessr.game.SpotRepository repository() {
        return repository;
    }

    public void reloadSettings() {
        this.settings = GameSettings.load(CraftGuessr.settingsFile());
        CraftGuessr.LOGGER.info("CraftGuessr settings reloaded for {}", server.getServerDirectory());
    }

    /** Applies one {@code /geo config set} key. Returns false for unknown keys. */
    public boolean configure(String key, String value) {
        GameSettings settings = this.settings;
        switch (key.toLowerCase(Locale.ROOT)) {
            case "provider" -> settings.provider = value.toLowerCase(Locale.ROOT);
            case "baseurl", "base_url", "url" -> settings.baseUrl = value.trim();
            case "model" -> settings.model = value.trim();
            case "apikey", "api_key", "key" -> settings.apiKey = value.trim();
            case "concurrency", "maxconcurrency" -> settings.maxConcurrency =
                    parseInt(value, settings.maxConcurrency);
            case "timeout" -> settings.timeoutSeconds = parseInt(value, settings.timeoutSeconds);
            case "commentary" -> settings.aiCommentary = Boolean.parseBoolean(value);
            case "maxpool", "maxpoolsize" -> settings.maxPoolSize = parseInt(value, settings.maxPoolSize);
            case "maxspots", "maxspotsperscan" -> settings.maxSpotsPerScan = parseInt(value, settings.maxSpotsPerScan);
            case "webmaptype" -> settings.webMapType = value.toUpperCase(Locale.ROOT);
            case "webmapurl" -> settings.webMapUrl = value.trim();
            case "xaero" -> settings.xaeroEnabled = Boolean.parseBoolean(value);
            default -> {
                return false;
            }
        }
        settings.sanitize();
        settings.save(CraftGuessr.settingsFile());
        return true;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public ScanService scans() {
        return scanService;
    }

    public com.craftguessr.ai.TokenLedger ledger() {
        return ledger;
    }

    /** Centroid of the spot pool, used as the default centre of the guess map. */
    public int[] mapCenter() {
        java.util.List<com.craftguessr.game.Spot> spots = repository.all();
        if (spots.isEmpty()) {
            return new int[]{0, 0};
        }
        long sumX = 0;
        long sumZ = 0;
        for (com.craftguessr.game.Spot spot : spots) {
            sumX += spot.x();
            sumZ += spot.z();
        }
        return new int[]{(int) (sumX / spots.size()), (int) (sumZ / spots.size())};
    }

    public MapTileService tiles() {
        return tiles;
    }

    public ServerLevel level(String dimension) {
        return levelOf(dimension);
    }

    public ServerPlayer player(UUID id) {
        return online(id);
    }

    // ================================================================ rooms

    public List<RoomSummary> summaries() {
        List<RoomSummary> out = new ArrayList<>();
        for (Room room : rooms.values()) {
            ServerPlayer host = server.getPlayerList().getPlayer(room.host);
            out.add(new RoomSummary(room.id, host == null ? "?" : host.getName().getString(),
                    room.phase, room.players.size(), room.totalRounds, room.round,
                    room.settings.mode(), room.settings.movement(), false));
        }
        return out;
    }

    public Room roomOf(UUID player) {
        String id = roomOf.get(player);
        return id == null ? null : rooms.get(id);
    }

    public boolean createRoom(ServerPlayer player) {
        return createRoom(player, settings.defaultRoom);
    }

    public boolean createRoom(ServerPlayer player, RoomSettings requested) {
        Room existing = roomOf(player.getUUID());
        if (existing != null) {
            sendState(existing);
            notice(player, "craftguessr.message.already_in_room", true);
            return false;
        }
        String id = newRoomId();
        RoomSettings effective = requested == null ? settings.defaultRoom : requested;
        Room room = new Room(id, player.getUUID(), effective.sanitized());
        rooms.put(id, room);
        PlayerState state = new PlayerState(player.getUUID(), player.getGameProfile().name());
        room.players.put(player.getUUID(), state);
        roomOf.put(player.getUUID(), id);
        notice(player, "craftguessr.message.room_created", false);
        broadcastState(room, true);
        broadcastLists(room);
        return true;
    }

    public boolean joinRoom(ServerPlayer player, String roomId) {
        Room room = rooms.get(roomId.toUpperCase());
        if (room == null) {
            notice(player, "craftguessr.message.room_missing", true);
            return false;
        }
        if (room.phase != GamePhase.IDLE && room.phase != GamePhase.FINISHED) {
            notice(player, "craftguessr.message.room_in_game", true);
            return false;
        }
        leaveRoom(player, false);
        PlayerState state = new PlayerState(player.getUUID(), player.getGameProfile().name());
        room.players.put(player.getUUID(), state);
        roomOf.put(player.getUUID(), room.id);
        notice(player, "craftguessr.message.room_joined", false);
        broadcastState(room, true);
        broadcastLists(room);
        return true;
    }

    /** Restores the player and removes them from any room. */
    public void leaveRoom(ServerPlayer player, boolean restore) {
        Room room = roomOf(player.getUUID());
        roomOf.remove(player.getUUID());
        if (room == null) {
            return;
        }
        PlayerState state = room.players.remove(player.getUUID());
        if (restore && state != null) {
            restore(player, state.snapshot);
        }
        if (room.players.isEmpty()) {
            rooms.remove(room.id);
            scanService.cancelOwner(room.host);
            return;
        }
        if (room.host.equals(player.getUUID())) {
            room.host = room.players.keySet().iterator().next();
        }
        broadcastState(room, true);
    }

    public boolean startGame(ServerPlayer player) {
        Room room = roomOf(player.getUUID());
        if (room == null || !room.host.equals(player.getUUID())) {
            notice(player, "craftguessr.message.not_host", true);
            return false;
        }
        if (room.phase != GamePhase.IDLE && room.phase != GamePhase.FINISHED) {
            notice(player, "craftguessr.message.already_running", true);
            return false;
        }
        List<Spot> eligible = repository.eligible(room.settings);
        if (eligible.isEmpty()) {
            notice(player, "craftguessr.message.no_spots", true);
            return false;
        }
        room.usedSpots.clear();
        room.round = 0;
        room.totalRounds = room.settings.rounds();
        for (PlayerState state : room.players.values()) {
            state.score = 0;
            state.streak = 0;
            state.bestStreak = 0;
        }
        beginRound(room);
        return true;
    }

    public void requestNextRound(ServerPlayer player) {
        Room room = roomOf(player.getUUID());
        if (room == null) {
            return;
        }
        if (room.phase != GamePhase.REVEAL) {
            if (!room.host.equals(player.getUUID())) {
                notice(player, "craftguessr.message.not_host", true);
            }
            return;
        }
        if (!room.host.equals(player.getUUID())) {
            notice(player, "craftguessr.message.not_host", true);
            return;
        }
        advance(room);
    }

    public void submitGuess(ServerPlayer player, String dimension, int x, int z) {
        Room room = roomOf(player.getUUID());
        if (room == null || room.phase != GamePhase.ACTIVE) {
            return;
        }
        PlayerState state = room.players.get(player.getUUID());
        if (state == null || state.guess != null) {
            return;
        }
        state.guess = new Guess(dimension, x, z);
        state.guessedAtMillis = System.currentTimeMillis();
        sendState(room);
        if (room.settings.accelerateAfterFirstGuess() && !room.accelerated) {
            room.accelerated = true;
            long cap = System.currentTimeMillis() + 30_000L;
            if (room.deadline > cap) {
                room.deadline = cap;
            }
        }
        if (allGuessed(room)) {
            beginReveal(room);
        }
    }

    // ================================================================ phases

    private void beginRound(Room room) {
        List<Spot> eligible = new ArrayList<>(repository.eligible(room.settings));
        eligible.removeIf(spot -> room.usedSpots.contains(spot.id()));
        if (eligible.isEmpty()) {
            eligible = new ArrayList<>(repository.eligible(room.settings));
        }
        if (eligible.isEmpty()) {
            finishRoom(room);
            return;
        }
        room.round++;
        room.spot = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        room.usedSpots.add(room.spot.id());
        room.phase = GamePhase.PREPARING;
        room.deadline = System.currentTimeMillis() + PREPARE_SECONDS * 1000L;
        room.accelerated = false;
        for (PlayerState state : room.players.values()) {
            state.guess = null;
            state.guessedAtMillis = 0L;
        }
        ServerLevel level = levelOf(room.spot.dimension());
        for (PlayerState state : room.players.values()) {
            ServerPlayer player = online(state.uuid);
            if (player == null) {
                state.online = false;
                continue;
            }
            state.online = true;
            if (state.snapshot == null) {
                state.snapshot = Snapshot.capture(player);
                snapshots.put(player.getUUID(), state.snapshot);
            }
            enterArena(room, player, level);
        }
        // Never ship the real coordinates: the guess map is panned by the player, not centred on the answer.
        broadcast(room, new Payloads.RoundStartS2C(room.round, room.totalRounds, room.spot.dimension(),
                0, 0, room.settings.movementRadius(), room.settings.secondsPerRound(),
                room.settings.movement().canMove()));
        broadcastState(room, true);
    }

    private void beginActive(Room room) {
        room.phase = GamePhase.ACTIVE;
        room.deadline = System.currentTimeMillis() + room.settings.secondsPerRound() * 1000L;
        broadcastState(room, true);
    }

    private void beginReveal(Room room) {
        if (room.phase != GamePhase.ACTIVE) {
            return;
        }
        long now = System.currentTimeMillis();
        List<RoundReveal.GuessOutcome> outcomes = new ArrayList<>();
        for (PlayerState state : room.players.values()) {
            Guess guess = state.guess;
            double distance = -1.0;
            int points;
            boolean streakBroken;
            if (guess != null && guess.dimension.equals(room.spot.dimension())) {
                distance = Math.hypot(guess.x - room.spot.x(), guess.z - room.spot.z());
            }
            if (room.settings.mode() == GameMode.STREAKS) {
                boolean continues = distance >= 0 && Scoring.streakContinues(distance, room.settings.streakThreshold());
                points = Scoring.streakPoints(continues);
                streakBroken = !continues;
                state.streak = continues ? state.streak + 1 : 0;
            } else {
                points = distance < 0 ? 0 : Scoring.classicScore(distance, SCORING_SPAN);
                streakBroken = false;
                state.streak = distance < 0 ? 0 : state.streak + 1;
            }
            state.bestStreak = Math.max(state.bestStreak, state.streak);
            state.score += points;
            outcomes.add(new RoundReveal.GuessOutcome(state.uuid, state.name, guess != null,
                    guess == null ? room.spot.dimension() : guess.dimension,
                    guess == null ? 0 : guess.x, guess == null ? 0 : guess.z,
                    distance, points, state.streak, state.score, streakBroken));
        }
        outcomes.sort(Comparator.comparingInt(RoundReveal.GuessOutcome::totalScore).reversed());
        room.phase = GamePhase.REVEAL;
        room.deadline = now + room.settings.revealSeconds() * 1000L;
        String commentary = room.settings.aiCommentary() ? room.spot.commentary() : null;
        broadcast(room, new Payloads.RevealS2C(new RoundReveal(room.round, room.totalRounds, room.spot,
                outcomes, commentary, room.settings.revealSeconds(),
                room.round >= room.totalRounds)));
        broadcastState(room, true);
        spawnRevealFx(room);
    }

    private void advance(Room room) {
        if (room.round >= room.totalRounds) {
            finishRoom(room);
        } else {
            beginRound(room);
        }
    }

    private void finishRoom(Room room) {
        room.phase = GamePhase.FINISHED;
        List<RoomStateView.PlayerView> standings = view(room, room.host).leaderboard();
        broadcast(room, new Payloads.GameOverS2C(standings));
        for (PlayerState state : room.players.values()) {
            ServerPlayer player = online(state.uuid);
            if (player != null) {
                restore(player, state.snapshot);
            }
            state.snapshot = null;
            state.guess = null;
            snapshots.remove(state.uuid);
        }
        room.spot = null;
        room.round = 0;
        broadcastState(room, true);
    }

    // ================================================================ arena entry / exit

    private void enterArena(Room room, ServerPlayer player, ServerLevel level) {
        player.setGameMode(GameType.ADVENTURE);
        if (room.settings.invisiblePlayers()) {
            player.setInvisible(true);
        }
        player.setNoGravity(false);
        player.fallDistance = 0.0F;
        BlockPos pos = safeSpot(room.spot, level);
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                Set.<Relative>of(), room.spot.yaw(), room.spot.pitch(), false);
        player.setYRot(room.spot.yaw());
        player.setXRot(room.spot.pitch());
    }

    private void restore(ServerPlayer player, Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        ServerLevel level = server.getLevel(snapshot.dimension());
        if (level != null) {
            player.teleportTo(level, snapshot.x(), snapshot.y(), snapshot.z(), Set.<Relative>of(),
                    snapshot.yaw(), snapshot.pitch(), false);
        }
        player.setGameMode(snapshot.gameMode());
        player.setInvisible(snapshot.invisible());
        player.setPermanentlyInvulnerable(snapshot.invulnerable());
        player.setHealth(Math.min(snapshot.health(), player.getMaxHealth()));
        player.fallDistance = 0.0F;
        snapshots.remove(player.getUUID());
    }

    private BlockPos safeSpot(Spot spot, ServerLevel level) {
        int y = spot.y();
        if (level != null) {
            int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    spot.x(), spot.z());
            if (surface > level.getMinY()) {
                y = surface;
            }
        }
        return new BlockPos(spot.x(), y, spot.z());
    }

    private ServerLevel levelOf(String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) {
                return level;
            }
        }
        return server.overworld();
    }

    // ================================================================ tick

    public void tick() {
        tiles.tick();
        long now = System.currentTimeMillis();
        scanService.tick();
        List<Room> snapshot = new ArrayList<>(rooms.values());
        for (Room room : snapshot) {
            switch (room.phase) {
                case PREPARING -> {
                    if (now >= room.deadline) {
                        beginActive(room);
                    }
                    enforceMovement(room);
                }
                case ACTIVE -> {
                    enforceMovement(room);
                    if (now >= room.deadline || allGuessed(room)) {
                        beginReveal(room);
                    }
                }
                case REVEAL -> {
                    if (now >= room.deadline) {
                        advance(room);
                    }
                }
                default -> {
                }
            }
            if (now - room.lastStateMillis >= 1000L) {
                broadcastState(room, false);
            }
        }
        protectTick();
    }

    // ================================================================ arena protection

    /** True while this player sits inside a live round and must not take damage. */
    public boolean isProtectedPlayer(ServerPlayer player) {
        Room room = roomOf(player.getUUID());
        return room != null && room.phase.isPlaying();
    }

    /** Crash recovery: put a player back if the previous server run died mid-round. */
    public boolean restorePending(ServerPlayer player) {
        com.craftguessr.server.player.Snapshot snapshot = snapshots.get(player.getUUID());
        if (snapshot == null) {
            return false;
        }
        restore(player, snapshot);
        return true;
    }

    private void protectTick() {
        boolean anyPlaying = false;
        for (Room room : rooms.values()) {
            if (!room.phase.isPlaying()) {
                continue;
            }
            anyPlaying = true;
            for (PlayerState state : room.players.values()) {
                ServerPlayer player = online(state.uuid);
                if (player == null) {
                    continue;
                }
                if (!player.isPermanentlyInvulnerable()) {
                    player.setPermanentlyInvulnerable(true);
                }
                if (player.getFoodData().getFoodLevel() < 20) {
                    player.getFoodData().setFoodLevel(20);
                }
                if (player.getFoodData().getSaturationLevel() < 5.0F) {
                    player.getFoodData().setSaturation(5.0F);
                }
                if (player.isOnFire()) {
                    player.clearFire();
                }
            }
            clearHostiles(room);
        }
        applyGameRules(anyPlaying);
    }

    /** Removes hostile mobs from the arena plus its 64-block safety margin. */
    private void clearHostiles(Room room) {
        if (room.spot == null) {
            return;
        }
        ServerLevel level = levelOf(room.spot.dimension());
        if (level == null) {
            return;
        }
        double radius = room.settings.movementRadius() + 64.0;
        double cx = room.spot.x() + 0.5;
        double cz = room.spot.z() + 0.5;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                cx - radius, level.getMinY(), cz - radius,
                cx + radius, level.getMaxY(), cz + radius);
        for (net.minecraft.world.entity.monster.Monster monster
                : level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, box)) {
            monster.discard();
        }
    }

    /** Mob spawning and griefing are disabled only while somebody is playing. */
    private void applyGameRules(boolean anyPlaying) {
        net.minecraft.world.level.gamerules.GameRules rules = server.getGameRules();
        if (anyPlaying && !rulesOverridden) {
            spiesAllowed = rules.get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MONSTERS);
            mobGriefingAllowed = rules.get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING);
            rules.set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MONSTERS, false, server);
            rules.set(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING, false, server);
            rulesOverridden = true;
        } else if (!anyPlaying && rulesOverridden) {
            rules.set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MONSTERS, spiesAllowed, server);
            rules.set(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING, mobGriefingAllowed, server);
            rulesOverridden = false;
        }
    }

    private void enforceMovement(Room room) {
        if (room.spot == null) {
            return;
        }
        double radius = room.settings.movementRadius();
        for (PlayerState state : room.players.values()) {
            ServerPlayer player = online(state.uuid);
            if (player == null) {
                continue;
            }
            Vec3 pos = player.position();
            double dx = pos.x - (room.spot.x() + 0.5);
            double dz = pos.z - (room.spot.z() + 0.5);
            double dist = Math.hypot(dx, dz);
            boolean locked = room.settings.movement() != MovementMode.MOVING;
            double limit = locked ? 1.5 : radius;
            if (dist > limit || pos.y < room.spot.y() - 12 || pos.y > room.spot.y() + 40) {
                BlockPos target = safeSpot(room.spot, (ServerLevel) player.level());
                player.connection.teleport(target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                        player.getYRot(), player.getXRot());
            }
            if (room.settings.movement() == MovementMode.NMPZ) {
                player.setYRot(room.spot.yaw());
                player.setXRot(room.spot.pitch());
                player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                        room.spot.yaw(), room.spot.pitch());
            }
        }
    }

    private boolean allGuessed(Room room) {
        boolean any = false;
        for (PlayerState state : room.players.values()) {
            if (!state.online) {
                continue;
            }
            any = true;
            if (state.guess == null) {
                return false;
            }
        }
        return any;
    }

    // ================================================================ view / send

    public RoomStateView view(Room room, UUID viewer) {
        List<RoomStateView.PlayerView> players = new ArrayList<>();
        for (PlayerState state : room.players.values()) {
            boolean online = online(state.uuid) != null;
            state.online = online;
            players.add(new RoomStateView.PlayerView(state.uuid, state.name, state.score, state.streak,
                    state.guess != null, online));
        }
        long secondsLeft = room.phase == GamePhase.IDLE || room.phase == GamePhase.FINISHED ? 0
                : Math.max(0, (room.deadline - System.currentTimeMillis()) / 1000);
        return new RoomStateView(room.id, room.phase, room.round, room.totalRounds, (int) secondsLeft,
                room.host, players, room.settings, repository.eligible(room.settings).size(),
                room.host.equals(viewer), "");
    }

    public void broadcastState(Room room, boolean force) {
        if (!force && System.currentTimeMillis() - room.lastStateMillis < 900L) {
            return;
        }
        room.lastStateMillis = System.currentTimeMillis();
        for (UUID id : room.players.keySet()) {
            ServerPlayer player = online(id);
            if (player != null) {
                ServerPlayNetworking.send(player, new Payloads.RoomStateS2C(view(room, id)));
            }
        }
    }

    private void broadcastLists(Room room) {
        broadcast(room, new Payloads.RoomListS2C(summaries()));
    }

    public <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void broadcast(Room room, T payload) {
        for (UUID id : room.players.keySet()) {
            ServerPlayer player = online(id);
            if (player != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    public void broadcastRoomsToAll() {
        Payloads.RoomListS2C payload = new Payloads.RoomListS2C(summaries());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private void sendState(Room room) {
        ServerPlayer host = online(room.host);
        if (host != null) {
            ServerPlayNetworking.send(host, new Payloads.RoomStateS2C(view(room, host.getUUID())));
        }
    }

    public void notice(ServerPlayer player, String translationKey, boolean error) {
        ServerPlayNetworking.send(player, new Payloads.NoticeS2C(translationKey, error));
    }

    private ServerPlayer online(UUID id) {
        return server.getPlayerList().getPlayer(id);
    }

    private String newRoomId() {
        for (int attempt = 0; attempt < 64; attempt++) {
            StringBuilder builder = new StringBuilder(5);
            for (int i = 0; i < 5; i++) {
                builder.append(ID_ALPHABET.charAt(ThreadLocalRandom.current().nextInt(ID_ALPHABET.length())));
            }
            String id = builder.toString();
            if (!rooms.containsKey(id)) {
                return id;
            }
        }
        return "ROOM1";
    }

    // ================================================================ disconnect handling

    /** Removes a player who is disconnecting, restoring their pre-round snapshot. */
    public void onDisconnect(ServerPlayer player) {
        scanService.cancel(player.getUUID());
        Room room = roomOf(player.getUUID());
        if (room == null) {
            return;
        }
        PlayerState state = room.players.get(player.getUUID());
        if (state != null) {
            restore(player, state.snapshot);
        }
        leaveRoom(player, false);
        broadcastState(room, true);
    }

    /** Restores everyone still in a room, called when the server stops. */
    public void restoreAll() {
        for (Room room : rooms.values()) {
            for (PlayerState state : room.players.values()) {
                ServerPlayer player = online(state.uuid);
                if (player != null) {
                    restore(player, state.snapshot);
                }
            }
        }
        rooms.clear();
        roomOf.clear();
        applyGameRules(false);
    }

    private void spawnRevealFx(Room room) {
        // The visible beam is driven by the client reveal screen; the server only contributes the
        // particle stab so it is visible even without the GUI.
        ServerLevel level = levelOf(room.spot.dimension());
        if (level == null) {
            return;
        }
        int y = safeSpot(room.spot, level).getY();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                room.spot.x() + 0.5, y + 0.4, room.spot.z() + 0.5, 24, 0.3, 0.1, 0.3, 0.01);
        for (int i = 0; i < 48; i++) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    room.spot.x() + 0.5, y + 0.4 + i * 0.6, room.spot.z() + 0.5, 1, 0.05, 0.02, 0.05, 0.0);
        }
    }

    // ================================================================ inner types

    public static final class Room {
        public final String id;
        public UUID host;
        public final RoomSettings settings;
        public final Map<UUID, PlayerState> players = new LinkedHashMap<>();
        public final Set<String> usedSpots = new HashSet<>();
        public GamePhase phase = GamePhase.IDLE;
        public int round;
        public int totalRounds;
        public Spot spot;
        public long deadline;
        public long lastStateMillis;
        public boolean accelerated;

        Room(String id, UUID host, RoomSettings settings) {
            this.id = id;
            this.host = host;
            this.settings = settings;
            this.totalRounds = settings.rounds();
        }
    }

    public static final class PlayerState {
        public final UUID uuid;
        public final String name;
        public int score;
        public int streak;
        public int bestStreak;
        public Guess guess;
        public long guessedAtMillis;
        public boolean online = true;
        public Snapshot snapshot;

        PlayerState(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    public record Guess(String dimension, int x, int z) {
    }
}
