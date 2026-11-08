package com.craftguessr.server;

import com.craftguessr.CraftGuessr;
import com.craftguessr.game.Spot;
import com.craftguessr.game.SpotRepository;
import com.craftguessr.map.ImageCollage;
import com.craftguessr.map.SurfaceView;
import com.craftguessr.network.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/** Server-side handlers for every client-to-server payload. All work happens on the server thread. */
public final class ServerNetwork {

    private ServerNetwork() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(Payloads.LobbyRequestC2S.TYPE, (payload, context) ->
                context.server().execute(() -> sendRoomList(context.player())));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.CreateRoomC2S.TYPE, (payload, context) ->
                context.server().execute(() -> manager().createRoom(context.player(), payload.settings())));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.JoinRoomC2S.TYPE, (payload, context) ->
                context.server().execute(() -> manager().joinRoom(context.player(), payload.roomId())));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.LeaveRoomC2S.TYPE, (payload, context) ->
                context.server().execute(() -> manager().leaveRoom(context.player(), true)));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.StartGameC2S.TYPE, (payload, context) ->
                context.server().execute(() -> manager().startGame(context.player())));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.NextRoundC2S.TYPE, (payload, context) ->
                context.server().execute(() -> manager().requestNextRound(context.player())));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.GuessC2S.TYPE, (payload, context) ->
                context.server().execute(() -> manager().submitGuess(context.player(), payload.dimension(),
                        payload.x(), payload.z())));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.ScanRequestC2S.TYPE, (payload, context) ->
                context.server().execute(() -> manager().scans().start(context.player(), payload.radius(),
                        payload.strength(), payload.dimension())));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.PoolRequestC2S.TYPE, (payload, context) ->
                context.server().execute(() -> sendPool(context.player())));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.PoolEditC2S.TYPE, (payload, context) ->
                context.server().execute(() -> editPool(context.player(), payload)));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.PoolDeleteC2S.TYPE, (payload, context) ->
                context.server().execute(() -> deletePool(context.player(), payload.spotId())));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.ThumbRequestC2S.TYPE, (payload, context) ->
                context.server().execute(() -> sendThumbnail(context.player(), payload.spotId())));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.SubmitSpotC2S.TYPE, (payload, context) ->
                context.server().execute(() -> submitSpot(context.player(), payload)));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.TileRequestC2S.TYPE, (payload, context) ->
                context.server().execute(() -> sendTile(context.player(), payload)));
    }

    private static ServerGameManager manager() {
        return ServerGameManager.get();
    }

    public static void sendRoomList(ServerPlayer player) {
        ServerGameManager manager = manager();
        if (manager == null) {
            return;
        }
        ServerPlayNetworking.send(player, new Payloads.RoomListS2C(manager.summaries()));
    }

    private static void sendPool(ServerPlayer player) {
        ServerGameManager manager = manager();
        if (manager == null) {
            return;
        }
        ServerPlayNetworking.send(player, new Payloads.PoolS2C(manager.repository().all()));
    }

    private static void sendThumbnail(ServerPlayer player, String spotId) {
        ServerGameManager manager = manager();
        if (manager == null) {
            return;
        }
        try {
            byte[] bytes = manager.repository().readThumbnail(spotId);
            if (bytes != null && bytes.length > 0) {
                ServerPlayNetworking.send(player, new Payloads.ThumbS2C(spotId, bytes));
            }
        } catch (IOException e) {
            CraftGuessr.LOGGER.warn("Thumbnail {} unreadable: {}", spotId, e.toString());
        }
    }

    private static void editPool(ServerPlayer player, Payloads.PoolEditC2S payload) {
        ServerGameManager manager = manager();
        if (manager == null || !Perms.isAdmin(player)) {
            return;
        }
        manager.repository().get(payload.spotId()).ifPresent(spot -> {
            Spot edited = spot;
            if (payload.title() != null && !payload.title().isBlank()) {
                edited = edited.withTitle(payload.title().trim());
            }
            if (payload.difficulty() != null) {
                edited = edited.withDifficulty(com.craftguessr.ai.ScanVerdict.Difficulty.parse(
                        payload.difficulty(), spot.difficulty()));
            }
            try {
                manager.repository().update(edited);
            } catch (IOException e) {
                CraftGuessr.LOGGER.warn("Pool edit failed: {}", e.toString());
            }
        });
        sendPool(player);
    }

    private static void deletePool(ServerPlayer player, String spotId) {
        ServerGameManager manager = manager();
        if (manager == null || !Perms.isAdmin(player)) {
            return;
        }
        try {
            manager.repository().remove(spotId);
        } catch (IOException e) {
            CraftGuessr.LOGGER.warn("Pool delete failed: {}", e.toString());
        }
        sendPool(player);
    }

    private static void submitSpot(ServerPlayer player, Payloads.SubmitSpotC2S payload) {
        ServerGameManager manager = manager();
        if (manager == null) {
            return;
        }
        ServerLevel level = manager.level(payload.dimension());
        if (level == null) {
            manager.notice(player, "craftguessr.message.unknown_dimension", true);
            return;
        }
        try {
            String id = "manual-" + Long.toHexString(System.currentTimeMillis()) + "-"
                    + Integer.toHexString(player.getUUID().hashCode());
            com.craftguessr.ai.ScanVerdict.Difficulty difficulty =
                    com.craftguessr.ai.ScanVerdict.Difficulty.parse(payload.difficulty(),
                            com.craftguessr.ai.ScanVerdict.Difficulty.MEDIUM);
            String title = payload.title() == null ? "" : payload.title().trim();
            Spot spot = new Spot(id, payload.dimension(), payload.x(), payload.y(), payload.z(),
                    payload.yaw(), payload.pitch(), difficulty,
                    title, "", "", Spot.Source.MANUAL, System.currentTimeMillis());
            byte[] collage = payload.collageJpeg();
            if (collage == null || collage.length == 0) {
                collage = renderPreview(level, payload.x(), payload.y(), payload.z(), payload.yaw());
            }
            if (collage != null && collage.length > 0) {
                String thumb = manager.repository().saveThumbnail(id, collage);
                spot = spot.withThumbnail(thumb);
            }
            if (manager.repository().containsPosition(spot)) {
                manager.notice(player, "craftguessr.message.spot_duplicate", true);
                return;
            }
            manager.repository().add(spot);
            manager.notice(player, "craftguessr.message.spot_added", false);
            sendPool(player);
        } catch (IOException e) {
            CraftGuessr.LOGGER.warn("Manual spot submit failed: {}", e.toString());
            manager.notice(player, "craftguessr.message.spot_failed", true);
        }
    }

    /**
     * Server-side fallback preview: four raymarched surface views stitched into one collage so a
     * manual spot still gets a picture even though the client sent no capture.
     */
    private static byte[] renderPreview(ServerLevel level, int x, int y, int z, float yaw) {
        int view = 128;
        int eye = y + 1;
        List<BufferedImage> views = List.of(
                SurfaceView.render(level, x, eye, z, yaw + 180.0F, view),
                SurfaceView.render(level, x, eye, z, yaw - 90.0F, view),
                SurfaceView.render(level, x, eye, z, yaw, view),
                SurfaceView.render(level, x, eye, z, yaw + 90.0F, view));
        try {
            return ImageCollage.toJpeg(ImageCollage.collage(views, view), 0.85F);
        } catch (IOException e) {
            CraftGuessr.LOGGER.warn("Preview collage failed at {} {} {}: {}", x, y, z, e.toString());
            return null;
        }
    }

    private static void sendTile(ServerPlayer player, Payloads.TileRequestC2S payload) {
        ServerGameManager manager = manager();
        if (manager == null) {
            return;
        }
        ServerLevel level = manager.level(payload.dimension());
        if (level == null) {
            return;
        }
        if (!manager.tiles().allow(player.getUUID())) {
            return;
        }
        byte[] png = manager.tiles().tile(level, payload.zoom(), payload.tileX(), payload.tileZ());
        ServerPlayNetworking.send(player, new Payloads.TileS2C(payload.dimension(), payload.zoom(),
                payload.tileX(), payload.tileZ(), com.craftguessr.map.TileGrid.TILE_SIZE, png));
    }

    /** Utility for the debug command: base64 preview of one thumbnail. */
    public static String encodeThumbnail(Path path) throws IOException {
        return Base64.getEncoder().encodeToString(Files.readAllBytes(path));
    }
}
