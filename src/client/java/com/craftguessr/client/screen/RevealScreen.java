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
        Spot revealSpot = reveal.spot();
        if (revealSpot != null && revealSpot.dimension().contains("the_nether")) {
            addRenderableWidget(Button.builder(layerLabel(),
                            button -> {
                                map.layer = (map.layer + 1) % 3;
                                button.setMessage(layerLabel());
                            })
                    .bounds(width - SIDE_WIDTH - 4, height - 74, SIDE_WIDTH - 8, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.lobby.leave"),
                        button -> {
                            ClientPlayNetworking.send(new Payloads.LeaveRoomC2S());
                            ClientState.get().setRoom(null);
                            minecraft.gui.setScreen(new LobbyScreen());
                        })
                .bounds(width - SIDE_WIDTH - 4, height - 50, SIDE_WIDTH - 8, 20).build());
    }

    private static Component layerLabel() {
        return Component.translatable("gui.craftguessr.map.layer");
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
        drawGuessLines(g);
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

    /** Animated dashed lines from every guess to the true spot, the first half of the dual reveal. */
    private void drawGuessLines(GuiGraphicsExtractor g) {
        Spot spot = reveal.spot();
        if (spot == null) {
            return;
        }
        double ax = map.worldToScreenX(spot.x());
        double ay = map.worldToScreenY(spot.z());
        long time = System.currentTimeMillis();
        for (RoundReveal.GuessOutcome outcome : reveal.outcomes()) {
            if (!outcome.guessed() || outcome.distance() < 0) {
                continue;
            }
            int color = outcome.distance() <= 50 ? 0xCC42A5F5 : 0xCCEF5350;
            dashedLine(g, map.worldToScreenX(outcome.gx()), map.worldToScreenY(outcome.gz()), ax, ay, color, time);
        }
    }

    private void dashedLine(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2,
                            int color, long time) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.hypot(dx, dy);
        if (length < 2.0) {
            return;
        }
        double ux = dx / length;
        double uy = dy / length;
        double dash = 5.0;
        double period = 9.0;
        double offset = (time % 900L) / 900.0 * period;
        for (double d = -offset; d < length; d += period) {
            double start = Math.max(0.0, d);
            double end = Math.min(length, d + dash);
            for (double p = start; p <= end; p += 1.0) {
                int px = (int) Math.round(x1 + ux * p);
                int py = (int) Math.round(y1 + uy * p);
                if (!map.contains(px, py)) {
                    continue;
                }
                g.fill(px, py, px + 2, py + 2, color);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
