package com.craftguessr.client.screen;

import com.craftguessr.client.ClientNetwork;
import com.craftguessr.client.ClientState;
import com.craftguessr.game.RoundReveal;
import com.craftguessr.game.Scoring;
import com.craftguessr.game.Spot;
import com.craftguessr.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Dual reveal: every guess on the map, the true spot, then the scoreboard. */
public final class RevealScreen extends Screen {

    private static final int SIDE_WIDTH = 190;

    private final RoundReveal reveal;
    private final MapView map = new MapView(0, 0, 100, 100);

    public RevealScreen(RoundReveal reveal) {
        super(Component.translatable("gui.craftguessr.reveal.title", reveal.round()));
        this.reveal = reveal;
    }

    @Override
    protected void init() {
        Spot spot = reveal.spot();
        if (spot != null) {
            map.dimension = spot.dimension();
            map.centerX = spot.x();
            map.centerZ = spot.z();
            map.scale = 0.5;
            map.markers.add(new MapView.Marker(spot.x(), spot.z(), 0xFF66BB6A, spot.label()));
            for (RoundReveal.GuessOutcome outcome : reveal.outcomes()) {
                if (!outcome.guessed() || outcome.distance() < 0) {
                    continue;
                }
                int color = outcome.distance() <= 50 ? 0xFF42A5F5 : 0xFFEF5350;
                map.markers.add(new MapView.Marker(outcome.gx(), outcome.gz(), color, outcome.name()));
            }
            map.hasPin = true;
            map.pinX = spot.x();
            map.pinZ = spot.z();
        }
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        clearWidgets();
        map.x = 8;
        map.y = 34;
        map.width = width - SIDE_WIDTH - 24;
        map.height = height - 34 - 12;
        if (ClientState.get().isHost()) {
            Component label = reveal.finished()
                    ? Component.translatable("gui.craftguessr.reveal.finish")
                    : Component.translatable("gui.craftguessr.reveal.next");
            addRenderableWidget(Button.builder(label,
                            button -> ClientPlayNetworking.send(new Payloads.NextRoundC2S()))
                    .bounds(width - SIDE_WIDTH - 4, height - 26, SIDE_WIDTH - 8, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.lobby.leave"),
                        button -> {
                            ClientPlayNetworking.send(new Payloads.LeaveRoomC2S());
                            ClientState.get().setRoom(null);
                            minecraft.gui.setScreen(new LobbyScreen());
                        })
                .bounds(width - SIDE_WIDTH - 4, height - 50, SIDE_WIDTH - 8, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        ClientScreens.dimBackground(g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        map.draw(g, font);
        Spot spot = reveal.spot();
        int sideX = width - SIDE_WIDTH - 4;
        g.text(font, title.getString(), 10, 8, 0xFFFFFFFF);
        if (spot != null) {
            g.text(font, Component.translatable("gui.craftguessr.reveal.subtitle",
                    ClientNetwork.dimensionName(spot.dimension()), spot.coordinates()).getString(),
                    10, 20, 0xFFB0BEC5);
            g.text(font, spot.title() == null ? spot.label() : spot.title(), sideX, 34, 0xFFFDD835);
            g.text(font, Component.translatable(spot.difficulty().translationKey()).getString(),
                    sideX, 46, 0xFF90A4AE);
        }
        List<RoundReveal.GuessOutcome> outcomes = reveal.sortedByScore();
        int rowY = 68;
        if (outcomes.isEmpty()) {
            g.text(font, Component.translatable("gui.craftguessr.reveal.nobody").getString(),
                    sideX, rowY, 0xFF90A4AE);
        }
        for (RoundReveal.GuessOutcome outcome : outcomes) {
            String distance = outcome.distance() < 0
                    ? "-"
                    : Scoring.formatDistance(outcome.distance());
            String line = (outcome.streakBroken() ? "!" : "") + outcome.name() + "  " + distance
                    + "  +" + outcome.points();
            int color = outcome.distance() >= 0 && outcome.distance() <= 50 ? 0xFF66BB6A : 0xFFECEFF1;
            g.text(font, line, sideX, rowY, color);
            rowY += 13;
        }
        if (reveal.commentary() != null && !reveal.commentary().isBlank()) {
            g.text(font, reveal.commentary(), 10, height - 20, 0xFFB0BEC5);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
