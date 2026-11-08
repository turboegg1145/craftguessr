package com.craftguessr.client;

import com.craftguessr.CraftGuessr;
import com.craftguessr.client.screen.ClientScreens;
import com.craftguessr.game.GamePhase;
import com.craftguessr.map.TileGrid;
import com.craftguessr.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Registers every server-to-client payload and folds it into {@link ClientState}. */
public final class ClientNetwork {

    private ClientNetwork() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Payloads.RoomListS2C.TYPE, (payload, context) ->
                context.client().execute(() -> ClientState.get().setRoomList(payload.rooms())));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.RoomStateS2C.TYPE, (payload, context) ->
                context.client().execute(() -> ClientState.get().setRoom(payload.state())));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.RoundStartS2C.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    ClientState.get().setRoundStart(payload);
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.gui.setScreen(null);
                    minecraft.gui.hud.setTitle(Component.translatable("text.craftguessr.round_start",
                            payload.round(), payload.totalRounds()));
                    minecraft.gui.hud.setSubtitle(Component.translatable("text.craftguessr.round_start_location",
                            dimensionName(payload.dimension())));
                }));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.RevealS2C.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    ClientState.get().setReveal(payload.reveal());
                    ClientScreens.openReveal();
                }));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.GameOverS2C.TYPE, (payload, context) ->
                context.client().execute(() -> ClientScreens.openGameOver(payload.standings())));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.ScanProgressS2C.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    ClientState.get().setScan(payload);
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.player != null && payload.state().startsWith("craftguessr.scan.")) {
                        minecraft.gui.hud.setOverlayMessage(Component.translatable(payload.state(),
                                payload.percent(), payload.kept(), payload.rejected()), false);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.PoolS2C.TYPE, (payload, context) ->
                context.client().execute(() -> ClientState.get().setPool(payload.spots())));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.ThumbS2C.TYPE, (payload, context) ->
                context.client().execute(() -> ClientState.get().putThumb(payload.spotId(), payload.jpeg())));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.TileS2C.TYPE, (payload, context) ->
                context.client().execute(() -> ClientState.get().putTile(
                        TileGrid.tileKey(payload.dimension(), payload.zoom(), payload.tileX(), payload.tileZ()),
                        payload.png())));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.NoticeS2C.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.player != null) {
                        minecraft.player.sendSystemMessage(message(payload.message()));
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenScreenS2C.TYPE, (payload, context) ->
                context.client().execute(() -> ClientScreens.open(payload.screen(), payload.roomId())));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.CaptureRequestS2C.TYPE, (payload, context) ->
                context.client().execute(() -> CaptureSession.start(payload.title(), payload.difficulty())));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.PingS2C.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    ClientState.get().setPing(payload);
                    CraftGuessr.LOGGER.info("CraftGuessr server {} (AI {}, map {},{})", payload.serverVersion(),
                            payload.aiReady() ? "ready" : "not configured",
                            payload.mapCenterX(), payload.mapCenterZ());
                }));
    }

    /** Server messages are translation keys or plain text. */
    public static Component message(String raw) {
        if (raw == null || raw.isBlank()) {
            return Component.empty();
        }
        return raw.indexOf(' ') >= 0 ? Component.literal(raw) : Component.translatable(raw);
    }

    /** mm:ss for HUD and screen counters. */
    public static String formatTime(int seconds) {
        int clamped = Math.max(0, seconds);
        return String.format(java.util.Locale.ROOT, "%d:%02d", clamped / 60, clamped % 60);
    }

    public static String dimensionName(String dimension) {
        if (dimension == null) {
            return "?";
        }
        int colon = dimension.indexOf(':');
        return colon >= 0 ? dimension.substring(colon + 1) : dimension;
    }

    public static boolean playing() {
        var room = ClientState.get().room();
        return room != null && (room.phase() == GamePhase.PREPARING || room.phase() == GamePhase.ACTIVE);
    }
}
