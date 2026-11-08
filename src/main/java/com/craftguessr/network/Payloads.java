package com.craftguessr.network;

import com.craftguessr.ai.ScanVerdict;
import com.craftguessr.game.RoomSettings;
import com.craftguessr.game.RoomStateView;
import com.craftguessr.game.RoomSummary;
import com.craftguessr.game.RoundReveal;
import com.craftguessr.game.Spot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

/**
 * Every packet of the mod, as nested records. Keeping them together makes registration and the
 * client/server switch exhaustive at a glance.
 */
public final class Payloads {

    private Payloads() {
    }

    private static CustomPacketPayload.Type<CustomPacketPayload> rawType(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("craftguessr", path));
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> packetType(String path) {
        return (CustomPacketPayload.Type<T>) (CustomPacketPayload.Type<?>) rawType(path);
    }

    // ================================================================ client -> server

    /** Asks for the current room list (sent when the lobby screen opens and on refresh). */
    public record LobbyRequestC2S() implements CustomPacketPayload {
        public static final Type<LobbyRequestC2S> TYPE = packetType("lobby_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, LobbyRequestC2S> CODEC = StreamCodec.of(
                (buf, payload) -> {
                },
                buf -> new LobbyRequestC2S());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CreateRoomC2S(RoomSettings settings) implements CustomPacketPayload {
        public static final Type<CreateRoomC2S> TYPE = packetType("create_room");
        public static final StreamCodec<RegistryFriendlyByteBuf, CreateRoomC2S> CODEC = StreamCodec.of(
                (buf, payload) -> NetCodec.writeRoomSettings(buf, payload.settings()),
                buf -> new CreateRoomC2S(NetCodec.readRoomSettings(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record JoinRoomC2S(String roomId) implements CustomPacketPayload {
        public static final Type<JoinRoomC2S> TYPE = packetType("join_room");
        public static final StreamCodec<RegistryFriendlyByteBuf, JoinRoomC2S> CODEC = StreamCodec.of(
                (buf, payload) -> NetCodec.writeId(buf, payload.roomId()),
                buf -> new JoinRoomC2S(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record LeaveRoomC2S() implements CustomPacketPayload {
        public static final Type<LeaveRoomC2S> TYPE = packetType("leave_room");
        public static final StreamCodec<RegistryFriendlyByteBuf, LeaveRoomC2S> CODEC = StreamCodec.of(
                (buf, payload) -> {
                },
                buf -> new LeaveRoomC2S());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record StartGameC2S() implements CustomPacketPayload {
        public static final Type<StartGameC2S> TYPE = packetType("start_game");
        public static final StreamCodec<RegistryFriendlyByteBuf, StartGameC2S> CODEC = StreamCodec.of(
                (buf, payload) -> {
                },
                buf -> new StartGameC2S());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record NextRoundC2S() implements CustomPacketPayload {
        public static final Type<NextRoundC2S> TYPE = packetType("next_round");
        public static final StreamCodec<RegistryFriendlyByteBuf, NextRoundC2S> CODEC = StreamCodec.of(
                (buf, payload) -> {
                },
                buf -> new NextRoundC2S());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A player's pin: dimension plus block coordinates. */
    public record GuessC2S(String dimension, int x, int z) implements CustomPacketPayload {
        public static final Type<GuessC2S> TYPE = packetType("guess");
        public static final StreamCodec<RegistryFriendlyByteBuf, GuessC2S> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    NetCodec.writeId(buf, payload.dimension());
                    buf.writeVarInt(payload.x());
                    buf.writeVarInt(payload.z());
                },
                buf -> new GuessC2S(buf.readUtf(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Starts an asynchronous scan around the sending player. */
    public record ScanRequestC2S(int radius, String strength, String dimension) implements CustomPacketPayload {
        public static final Type<ScanRequestC2S> TYPE = packetType("scan_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, ScanRequestC2S> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.radius());
                    NetCodec.writeId(buf, payload.strength());
                    NetCodec.writeId(buf, payload.dimension());
                },
                buf -> new ScanRequestC2S(buf.readVarInt(), buf.readUtf(), buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PoolRequestC2S() implements CustomPacketPayload {
        public static final Type<PoolRequestC2S> TYPE = packetType("pool_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, PoolRequestC2S> CODEC = StreamCodec.of(
                (buf, payload) -> {
                },
                buf -> new PoolRequestC2S());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PoolEditC2S(String spotId, String title, String difficulty) implements CustomPacketPayload {
        public static final Type<PoolEditC2S> TYPE = packetType("pool_edit");
        public static final StreamCodec<RegistryFriendlyByteBuf, PoolEditC2S> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    NetCodec.writeId(buf, payload.spotId());
                    NetCodec.writeId(buf, payload.title());
                    NetCodec.writeId(buf, payload.difficulty());
                },
                buf -> new PoolEditC2S(buf.readUtf(), buf.readUtf(), buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PoolDeleteC2S(String spotId) implements CustomPacketPayload {
        public static final Type<PoolDeleteC2S> TYPE = packetType("pool_delete");
        public static final StreamCodec<RegistryFriendlyByteBuf, PoolDeleteC2S> CODEC = StreamCodec.of(
                (buf, payload) -> NetCodec.writeId(buf, payload.spotId()),
                buf -> new PoolDeleteC2S(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Lazy thumbnail fetch for the album grid. */
    public record ThumbRequestC2S(String spotId) implements CustomPacketPayload {
        public static final Type<ThumbRequestC2S> TYPE = packetType("thumb_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, ThumbRequestC2S> CODEC = StreamCodec.of(
                (buf, payload) -> NetCodec.writeId(buf, payload.spotId()),
                buf -> new ThumbRequestC2S(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Manual spot submission from the player's current viewpoint, with a client-made collage. */
    public record SubmitSpotC2S(String dimension, int x, int y, int z, float yaw, float pitch,
                                byte[] collageJpeg) implements CustomPacketPayload {
        public static final Type<SubmitSpotC2S> TYPE = packetType("submit_spot");
        public static final StreamCodec<RegistryFriendlyByteBuf, SubmitSpotC2S> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    NetCodec.writeId(buf, payload.dimension());
                    buf.writeVarInt(payload.x());
                    buf.writeVarInt(payload.y());
                    buf.writeVarInt(payload.z());
                    buf.writeFloat(payload.yaw());
                    buf.writeFloat(payload.pitch());
                    buf.writeByteArray(payload.collageJpeg() == null ? new byte[0] : payload.collageJpeg());
                },
                buf -> new SubmitSpotC2S(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readFloat(), buf.readFloat(), buf.readByteArray()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Requests one map tile at a zoom level. */
    public record TileRequestC2S(String dimension, int zoom, int tileX, int tileZ) implements CustomPacketPayload {
        public static final Type<TileRequestC2S> TYPE = packetType("tile_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, TileRequestC2S> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    NetCodec.writeId(buf, payload.dimension());
                    buf.writeVarInt(payload.zoom());
                    buf.writeVarInt(payload.tileX());
                    buf.writeVarInt(payload.tileZ());
                },
                buf -> new TileRequestC2S(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ================================================================ server -> client

    public record RoomListS2C(List<RoomSummary> rooms) implements CustomPacketPayload {
        public static final Type<RoomListS2C> TYPE = packetType("room_list");
        public static final StreamCodec<RegistryFriendlyByteBuf, RoomListS2C> CODEC = StreamCodec.of(
                (buf, payload) -> NetCodec.writeList(buf, payload.rooms(), NetCodec::writeRoomSummary),
                buf -> new RoomListS2C(NetCodec.readList(buf, NetCodec::readRoomSummary)));

        public RoomListS2C {
            rooms = rooms == null ? List.of() : List.copyOf(rooms);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RoomStateS2C(RoomStateView state) implements CustomPacketPayload {
        public static final Type<RoomStateS2C> TYPE = packetType("room_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, RoomStateS2C> CODEC = StreamCodec.of(
                (buf, payload) -> NetCodec.writeRoomState(buf, payload.state()),
                buf -> new RoomStateS2C(NetCodec.readRoomState(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Round begins. The client only learns the region centre and the clock; the exact answer stays on
     * the server until {@link RevealS2C}.
     */
    public record RoundStartS2C(int round, int totalRounds, String dimension, int centerX, int centerZ,
                                int radius, int seconds, boolean allowMove) implements CustomPacketPayload {
        public static final Type<RoundStartS2C> TYPE = packetType("round_start");
        public static final StreamCodec<RegistryFriendlyByteBuf, RoundStartS2C> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.round());
                    buf.writeVarInt(payload.totalRounds());
                    NetCodec.writeId(buf, payload.dimension());
                    buf.writeVarInt(payload.centerX());
                    buf.writeVarInt(payload.centerZ());
                    buf.writeVarInt(payload.radius());
                    buf.writeVarInt(payload.seconds());
                    buf.writeBoolean(payload.allowMove());
                },
                buf -> new RoundStartS2C(buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RevealS2C(RoundReveal reveal) implements CustomPacketPayload {
        public static final Type<RevealS2C> TYPE = packetType("reveal");
        public static final StreamCodec<RegistryFriendlyByteBuf, RevealS2C> CODEC = StreamCodec.of(
                (buf, payload) -> NetCodec.writeRoundReveal(buf, payload.reveal()),
                buf -> new RevealS2C(NetCodec.readRoundReveal(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GameOverS2C(List<RoomStateView.PlayerView> standings) implements CustomPacketPayload {
        public static final Type<GameOverS2C> TYPE = packetType("game_over");
        public static final StreamCodec<RegistryFriendlyByteBuf, GameOverS2C> CODEC = StreamCodec.of(
                (buf, payload) -> NetCodec.writeList(buf, payload.standings(), NetCodec::writePlayerView),
                buf -> new GameOverS2C(NetCodec.readList(buf, NetCodec::readPlayerView)));

        public GameOverS2C {
            standings = standings == null ? List.of() : List.copyOf(standings);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ScanProgressS2C(String state, int percent, int kept, int rejected,
                                  long promptTokens, long completionTokens, String message,
                                  boolean finished) implements CustomPacketPayload {
        public static final Type<ScanProgressS2C> TYPE = packetType("scan_progress");
        public static final StreamCodec<RegistryFriendlyByteBuf, ScanProgressS2C> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    NetCodec.writeId(buf, payload.state());
                    buf.writeVarInt(payload.percent());
                    buf.writeVarInt(payload.kept());
                    buf.writeVarInt(payload.rejected());
                    buf.writeVarLong(payload.promptTokens());
                    buf.writeVarLong(payload.completionTokens());
                    buf.writeUtf(payload.message() == null ? "" : payload.message());
                    buf.writeBoolean(payload.finished());
                },
                buf -> new ScanProgressS2C(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarLong(), buf.readVarLong(), buf.readUtf(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PoolS2C(List<Spot> spots) implements CustomPacketPayload {
        public static final Type<PoolS2C> TYPE = packetType("pool");
        public static final StreamCodec<RegistryFriendlyByteBuf, PoolS2C> CODEC = StreamCodec.of(
                (buf, payload) -> NetCodec.writeList(buf, payload.spots(), NetCodec::writeSpot),
                buf -> new PoolS2C(NetCodec.readList(buf, NetCodec::readSpot)));

        public PoolS2C {
            spots = spots == null ? List.of() : List.copyOf(spots);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ThumbS2C(String spotId, byte[] jpeg) implements CustomPacketPayload {
        public static final Type<ThumbS2C> TYPE = packetType("thumb");
        public static final StreamCodec<RegistryFriendlyByteBuf, ThumbS2C> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    NetCodec.writeId(buf, payload.spotId());
                    buf.writeByteArray(payload.jpeg() == null ? new byte[0] : payload.jpeg());
                },
                buf -> new ThumbS2C(buf.readUtf(), buf.readByteArray()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record TileS2C(String dimension, int zoom, int tileX, int tileZ, int size, byte[] png)
            implements CustomPacketPayload {
        public static final Type<TileS2C> TYPE = packetType("tile");
        public static final StreamCodec<RegistryFriendlyByteBuf, TileS2C> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    NetCodec.writeId(buf, payload.dimension());
                    buf.writeVarInt(payload.zoom());
                    buf.writeVarInt(payload.tileX());
                    buf.writeVarInt(payload.tileZ());
                    buf.writeVarInt(payload.size());
                    buf.writeByteArray(payload.png() == null ? new byte[0] : payload.png());
                },
                buf -> new TileS2C(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readByteArray()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record NoticeS2C(String message, boolean error) implements CustomPacketPayload {
        public static final Type<NoticeS2C> TYPE = packetType("notice");
        public static final StreamCodec<RegistryFriendlyByteBuf, NoticeS2C> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeUtf(payload.message() == null ? "" : payload.message());
                    buf.writeBoolean(payload.error());
                },
                buf -> new NoticeS2C(buf.readUtf(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Asks the client to open one of its screens (used by /geo and keybinds). */
    public record OpenScreenS2C(String screen, String roomId) implements CustomPacketPayload {
        public static final Type<OpenScreenS2C> TYPE = packetType("open_screen");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreenS2C> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    NetCodec.writeId(buf, payload.screen());
                    NetCodec.writeId(buf, payload.roomId());
                },
                buf -> new OpenScreenS2C(buf.readUtf(), buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PingS2C(String serverVersion, boolean aiReady) implements CustomPacketPayload {
        public static final Type<PingS2C> TYPE = packetType("ping");
        public static final StreamCodec<RegistryFriendlyByteBuf, PingS2C> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    NetCodec.writeId(buf, payload.serverVersion());
                    buf.writeBoolean(payload.aiReady());
                },
                buf -> new PingS2C(buf.readUtf(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
