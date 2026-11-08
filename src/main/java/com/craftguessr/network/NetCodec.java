package com.craftguessr.network;

import com.craftguessr.ai.ScanVerdict;
import com.craftguessr.game.GameMode;
import com.craftguessr.game.GamePhase;
import com.craftguessr.game.MovementMode;
import com.craftguessr.game.RoomSettings;
import com.craftguessr.game.RoomStateView;
import com.craftguessr.game.RoomSummary;
import com.craftguessr.game.RoundReveal;
import com.craftguessr.game.Spot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Hand-written binary codecs for the shared model records. Kept explicit (instead of nested
 * {@code StreamCodec.composite} chains) because a single swapped field would silently corrupt every
 * client, and because sets/enums/lists decode more readably here.
 */
public final class NetCodec {

    private NetCodec() {
    }

    // ---------------------------------------------------------------- primitives

    public static void writeId(FriendlyByteBuf buf, String id) {
        buf.writeUtf(id == null ? "" : id);
    }

    public static Identifier readIdentifier(FriendlyByteBuf buf) {
        return buf.readIdentifier();
    }

    public static void writeIdentifier(FriendlyByteBuf buf, Identifier id) {
        buf.writeIdentifier(id);
    }

    public static <T> void writeList(FriendlyByteBuf buf, List<T> list, BiConsumer<FriendlyByteBuf, T> writer) {
        buf.writeVarInt(list == null ? 0 : list.size());
        if (list != null) {
            for (T value : list) {
                writer.accept(buf, value);
            }
        }
    }

    public static <T> List<T> readList(FriendlyByteBuf buf, Function<FriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        List<T> out = new ArrayList<>(Math.max(0, Math.min(size, 4096)));
        for (int i = 0; i < size; i++) {
            out.add(reader.apply(buf));
        }
        return List.copyOf(out);
    }

    private static <E extends Enum<E>> void writeEnum(FriendlyByteBuf buf, E value, E fallback) {
        buf.writeVarInt((value == null ? fallback : value).ordinal());
    }

    private static <E extends Enum<E>> E readEnum(FriendlyByteBuf buf, E[] values, E fallback) {
        int ordinal = buf.readVarInt();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }

    // ---------------------------------------------------------------- settings

    public static void writeRoomSettings(FriendlyByteBuf buf, RoomSettings settings) {
        RoomSettings s = settings == null ? RoomSettings.defaults() : settings.sanitized();
        writeEnum(buf, s.mode(), GameMode.CLASSIC);
        buf.writeVarInt(s.rounds());
        buf.writeVarInt(s.secondsPerRound());
        writeEnum(buf, s.movement(), MovementMode.MOVING);
        buf.writeVarInt(s.movementRadius());
        int bits = 0;
        for (ScanVerdict.Difficulty difficulty : s.difficulties()) {
            bits |= 1 << difficulty.ordinal();
        }
        buf.writeVarInt(bits);
        writeList(buf, List.copyOf(s.dimensions()), NetCodec::writeId);
        buf.writeBoolean(s.invisiblePlayers());
        buf.writeVarInt(s.revealSeconds());
        buf.writeBoolean(s.aiCommentary());
        buf.writeBoolean(s.accelerateAfterFirstGuess());
        buf.writeVarInt(s.streakThreshold());
    }

    public static RoomSettings readRoomSettings(FriendlyByteBuf buf) {
        GameMode mode = readEnum(buf, GameMode.values(), GameMode.CLASSIC);
        int rounds = buf.readVarInt();
        int seconds = buf.readVarInt();
        MovementMode movement = readEnum(buf, MovementMode.values(), MovementMode.MOVING);
        int radius = buf.readVarInt();
        int bits = buf.readVarInt();
        EnumSet<ScanVerdict.Difficulty> difficulties = EnumSet.noneOf(ScanVerdict.Difficulty.class);
        for (ScanVerdict.Difficulty difficulty : ScanVerdict.Difficulty.values()) {
            if ((bits & (1 << difficulty.ordinal())) != 0) {
                difficulties.add(difficulty);
            }
        }
        List<String> dims = readList(buf, NetCodec::readIdString);
        boolean invisible = buf.readBoolean();
        int revealSeconds = buf.readVarInt();
        boolean commentary = buf.readBoolean();
        boolean accelerate = buf.readBoolean();
        int streak = buf.readVarInt();
        return new RoomSettings(mode, rounds, seconds, movement, radius, difficulties,
                new LinkedHashSet<>(dims), invisible, revealSeconds, commentary, accelerate, streak);
    }

    private static String readIdString(FriendlyByteBuf buf) {
        return buf.readUtf();
    }

    // ---------------------------------------------------------------- room summary / state

    public static void writeRoomSummary(FriendlyByteBuf buf, RoomSummary summary) {
        writeId(buf, summary.id());
        writeId(buf, summary.hostName());
        writeEnum(buf, summary.phase(), GamePhase.IDLE);
        buf.writeVarInt(summary.players());
        buf.writeVarInt(summary.totalRounds());
        buf.writeVarInt(summary.round());
        writeEnum(buf, summary.mode(), GameMode.CLASSIC);
        writeEnum(buf, summary.movement(), MovementMode.MOVING);
        buf.writeBoolean(summary.locked());
    }

    public static RoomSummary readRoomSummary(FriendlyByteBuf buf) {
        return new RoomSummary(buf.readUtf(), buf.readUtf(),
                readEnum(buf, GamePhase.values(), GamePhase.IDLE),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                readEnum(buf, GameMode.values(), GameMode.CLASSIC),
                readEnum(buf, MovementMode.values(), MovementMode.MOVING),
                buf.readBoolean());
    }

    public static void writePlayerView(FriendlyByteBuf buf, RoomStateView.PlayerView player) {
        buf.writeUUID(player.id());
        buf.writeUtf(player.name());
        buf.writeVarInt(player.score());
        buf.writeVarInt(player.streak());
        buf.writeBoolean(player.guessed());
        buf.writeBoolean(player.online());
    }

    public static RoomStateView.PlayerView readPlayerView(FriendlyByteBuf buf) {
        return new RoomStateView.PlayerView(buf.readUUID(), buf.readUtf(),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    public static void writeRoomState(FriendlyByteBuf buf, RoomStateView state) {
        writeId(buf, state.roomId());
        writeEnum(buf, state.phase(), GamePhase.IDLE);
        buf.writeVarInt(state.round());
        buf.writeVarInt(state.totalRounds());
        buf.writeVarInt(state.secondsLeft());
        buf.writeUUID(state.hostId() == null ? new UUID(0, 0) : state.hostId());
        writeList(buf, state.players(), NetCodec::writePlayerView);
        writeRoomSettings(buf, state.settings());
        buf.writeVarInt(state.poolSize());
        buf.writeBoolean(state.host());
        buf.writeUtf(state.notice());
    }

    public static RoomStateView readRoomState(FriendlyByteBuf buf) {
        String roomId = buf.readUtf();
        GamePhase phase = readEnum(buf, GamePhase.values(), GamePhase.IDLE);
        int round = buf.readVarInt();
        int total = buf.readVarInt();
        int secondsLeft = buf.readVarInt();
        UUID hostId = buf.readUUID();
        List<RoomStateView.PlayerView> players = readList(buf, NetCodec::readPlayerView);
        RoomSettings settings = readRoomSettings(buf);
        int poolSize = buf.readVarInt();
        boolean host = buf.readBoolean();
        String notice = buf.readUtf();
        return new RoomStateView(roomId, phase, round, total, secondsLeft, hostId, players, settings, poolSize, host, notice);
    }

    // ---------------------------------------------------------------- spot

    public static void writeSpot(FriendlyByteBuf buf, Spot spot) {
        writeId(buf, spot.id());
        writeId(buf, spot.dimension());
        buf.writeVarInt(spot.x());
        buf.writeVarInt(spot.y());
        buf.writeVarInt(spot.z());
        buf.writeFloat(spot.yaw());
        buf.writeFloat(spot.pitch());
        writeEnum(buf, spot.difficulty(), ScanVerdict.Difficulty.MEDIUM);
        buf.writeUtf(spot.title());
        buf.writeUtf(spot.commentary());
        writeId(buf, spot.thumbnailFile());
        writeEnum(buf, spot.source(), Spot.Source.AI);
        buf.writeLong(spot.createdAtMillis());
    }

    public static Spot readSpot(FriendlyByteBuf buf) {
        return new Spot(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(),
                readEnum(buf, ScanVerdict.Difficulty.values(), ScanVerdict.Difficulty.MEDIUM),
                buf.readUtf(), buf.readUtf(), buf.readUtf(),
                readEnum(buf, Spot.Source.values(), Spot.Source.AI), buf.readLong());
    }

    // ---------------------------------------------------------------- reveal

    public static void writeGuessOutcome(FriendlyByteBuf buf, RoundReveal.GuessOutcome outcome) {
        buf.writeUUID(outcome.id());
        buf.writeUtf(outcome.name());
        buf.writeBoolean(outcome.guessed());
        writeId(buf, outcome.dimension());
        buf.writeVarInt(outcome.gx());
        buf.writeVarInt(outcome.gz());
        buf.writeDouble(outcome.distance());
        buf.writeVarInt(outcome.points());
        buf.writeVarInt(outcome.streak());
        buf.writeVarInt(outcome.totalScore());
        buf.writeBoolean(outcome.streakBroken());
    }

    public static RoundReveal.GuessOutcome readGuessOutcome(FriendlyByteBuf buf) {
        return new RoundReveal.GuessOutcome(buf.readUUID(), buf.readUtf(), buf.readBoolean(), buf.readUtf(),
                buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readBoolean());
    }

    public static void writeRoundReveal(FriendlyByteBuf buf, RoundReveal reveal) {
        buf.writeVarInt(reveal.round());
        buf.writeVarInt(reveal.totalRounds());
        writeSpot(buf, reveal.spot());
        writeList(buf, reveal.outcomes(), NetCodec::writeGuessOutcome);
        buf.writeUtf(reveal.commentary());
        buf.writeVarInt(reveal.secondsUntilNext());
        buf.writeBoolean(reveal.finished());
    }

    public static RoundReveal readRoundReveal(FriendlyByteBuf buf) {
        int round = buf.readVarInt();
        int total = buf.readVarInt();
        Spot spot = readSpot(buf);
        List<RoundReveal.GuessOutcome> outcomes = readList(buf, NetCodec::readGuessOutcome);
        String commentary = buf.readUtf();
        int seconds = buf.readVarInt();
        boolean finished = buf.readBoolean();
        return new RoundReveal(round, total, spot, outcomes, commentary, seconds, finished);
    }
}
