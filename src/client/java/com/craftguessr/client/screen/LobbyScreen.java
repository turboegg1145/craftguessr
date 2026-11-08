package com.craftguessr.client.screen;

import com.craftguessr.client.ClientState;
import com.craftguessr.game.GamePhase;
import com.craftguessr.game.RoomStateView;
import com.craftguessr.game.RoomSummary;
import com.craftguessr.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Lobby: browse rooms, create one, or manage the room you are already in. */
public final class LobbyScreen extends Screen {

    private static final int PANEL_WIDTH = 380;
    private static final int ROW_HEIGHT = 24;

    private List<RoomSummary> shownRooms = List.of();
    private boolean shownInRoom;

    public LobbyScreen() {
        super(Component.translatable("gui.craftguessr.lobby.title"));
    }

    @Override
    protected void init() {
        repositionElements();
        if (ClientState.get().room() == null) {
            ClientPlayNetworking.send(new Payloads.LobbyRequestC2S());
        }
    }

    @Override
    protected void repositionElements() {
        clearWidgets();
        RoomStateView room = ClientState.get().room();
        shownInRoom = room != null;
        int left = (width - PANEL_WIDTH) / 2;
        int bottom = height - 34;
        if (room == null) {
            shownRooms = ClientState.get().rooms();
            int max = Math.min(shownRooms.size(), 6);
            for (int i = 0; i < max; i++) {
                RoomSummary summary = shownRooms.get(i);
                addRenderableWidget(Button.builder(roomLabel(summary), button ->
                                ClientPlayNetworking.send(new Payloads.JoinRoomC2S(summary.id())))
                        .bounds(left, 52 + i * ROW_HEIGHT, PANEL_WIDTH, 20).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.lobby.create"),
                            button -> minecraft.gui.setScreen(new CreateRoomScreen()))
                    .bounds(left, bottom, 120, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.lobby.refresh"),
                            button -> ClientPlayNetworking.send(new Payloads.LobbyRequestC2S()))
                    .bounds(left + 130, bottom, 110, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.pool.title"),
                            button -> minecraft.gui.setScreen(new PoolScreen()))
                    .bounds(left + 250, bottom, 130, 20).build());
        } else {
            boolean host = ClientState.get().isHost();
            boolean canStart = host && (room.phase() == GamePhase.IDLE || room.phase() == GamePhase.FINISHED);
            Button start = addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.lobby.start"),
                            button -> ClientPlayNetworking.send(new Payloads.StartGameC2S()))
                    .bounds(left, bottom, 120, 20).build());
            start.active = canStart;
            addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.pool.title"),
                            button -> minecraft.gui.setScreen(new PoolScreen()))
                    .bounds(left + 130, bottom, 110, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.lobby.leave"),
                            button -> {
                                ClientPlayNetworking.send(new Payloads.LeaveRoomC2S());
                                ClientState.get().setRoom(null);
                                rebuildWidgets();
                            })
                    .bounds(left + 250, bottom, 130, 20).build());
        }
    }

    private static Component roomLabel(RoomSummary summary) {
        return Component.translatable("gui.craftguessr.lobby.room_row",
                summary.hostName(), summary.players(), summary.round(), summary.totalRounds(),
                Component.translatable(roomPhaseKey(summary.phase())));
    }

    private static String roomPhaseKey(GamePhase phase) {
        return "craftguessr.phase." + phase.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public void tick() {
        RoomStateView room = ClientState.get().room();
        boolean inRoom = room != null;
        if (inRoom != shownInRoom) {
            rebuildWidgets();
        } else if (!inRoom) {
            List<RoomSummary> latest = ClientState.get().rooms();
            if (latest.size() != shownRooms.size()) {
                rebuildWidgets();
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int left = (width - PANEL_WIDTH) / 2;
        g.centeredText(font, title.getString(), width / 2, 18, 0xFFFFFFFF);
        Payloads.PingS2C ping = ClientState.get().ping();
        if (ping == null) {
            g.centeredText(font, Component.translatable("gui.craftguessr.lobby.connecting").getString(),
                    width / 2, 32, 0xFFFFB74D);
        } else {
            String ai = Component.translatable(ping.aiReady() ? "gui.craftguessr.lobby.ai_ready"
                    : "gui.craftguessr.lobby.ai_missing").getString();
            g.centeredText(font, Component.translatable("gui.craftguessr.lobby.status",
                    ping.serverVersion(), ai).getString(), width / 2, 32,
                    ping.aiReady() ? 0xFF9CCC65 : 0xFFFFB74D);
        }

        RoomStateView room = ClientState.get().room();
        if (room == null) {
            if (shownRooms.isEmpty()) {
                g.centeredText(font, Component.translatable("gui.craftguessr.lobby.empty").getString(),
                        width / 2, height / 2, 0xFF90A4AE);
            }
        } else {
            g.text(font, Component.translatable("gui.craftguessr.lobby.room_header", room.roomId()).getString(),
                    left, 56, 0xFFFFFFFF);
            g.text(font, Component.translatable("gui.craftguessr.lobby.state",
                            Component.translatable(room.phase().translationKey()).getString(),
                            room.round(), room.totalRounds(), room.poolSize()).getString(),
                    left, 70, 0xFFB0BEC5);
            List<RoomStateView.PlayerView> players = new ArrayList<>(room.players());
            players.sort((a, b) -> Integer.compare(b.score(), a.score()));
            int rowY = 92;
            for (RoomStateView.PlayerView player : players) {
                int color = player.online() ? (player.guessed() ? 0xFF9CCC65 : 0xFFECEFF1) : 0xFF78909C;
                String line = player.name() + "  " + player.score()
                        + (player.streak() > 1 ? "  x" + player.streak() : "")
                        + (player.guessed() ? "  \u2713" : "");
                g.text(font, line, left + 8, rowY, color);
                rowY += 16;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
