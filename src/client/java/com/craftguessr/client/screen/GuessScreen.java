package com.craftguessr.client.screen;

import com.craftguessr.client.ClientNetwork;
import com.craftguessr.client.ClientState;
import com.craftguessr.game.MovementMode;
import com.craftguessr.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Full-screen pin map; the answer is never used to centre the canvas. */
public final class GuessScreen extends Screen {

    private final MapView map = new MapView(0, 0, 100, 100);
    private boolean dragging;
    private double lastMouseX;
    private double lastMouseY;
    private Component status;

    public GuessScreen() {
        super(Component.translatable("gui.craftguessr.map.title"));
    }

    @Override
    protected void init() {
        repositionElements();
        Payloads.RoundStartS2C round = ClientState.get().roundStart();
        if (round != null) {
            map.dimension = round.dimension();
        }
        Payloads.PingS2C ping = ClientState.get().ping();
        if (ping != null && map.centerX == 0.0 && map.centerZ == 0.0) {
            map.centerX = ping.mapCenterX();
            map.centerZ = ping.mapCenterZ();
        }
        if (ClientState.get().roundStart() == null || ClientState.get().scanning()) {
            status = Component.translatable("gui.craftguessr.map.waiting_tiles");
        }
    }

    @Override
    protected void repositionElements() {
        clearWidgets();
        int margin = 8;
        map.x = margin;
        map.y = 26;
        map.width = width - margin * 2;
        map.height = height - 26 - 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.map.confirm"),
                        button -> confirm())
                .bounds(width / 2 - 80, height - 26, 160, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.map.cancel"),
                        button -> minecraft.gui.setScreen(null))
                .bounds(width - 70, 4, 62, 18).build());
    }

    private void confirm() {
        if (!map.hasPin) {
            status = Component.translatable("gui.craftguessr.map.guess");
            return;
        }
        ClientPlayNetworking.send(new Payloads.GuessC2S(ClientState.get().dimension(),
                map.pinBlockX(), map.pinBlockZ()));
        status = Component.translatable("gui.craftguessr.map.guessed",
                map.pinBlockX(), map.pinBlockZ());
        minecraft.gui.setScreen(null);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        ClientScreens.dimBackground(g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        map.draw(g, font);
        g.centeredText(font, title.getString(), width / 2, 8, 0xFFFFFFFF);
        Payloads.RoundStartS2C round = ClientState.get().roundStart();
        if (round != null) {
            g.text(font, Component.translatable("gui.craftguessr.map.zoom",
                    String.format(java.util.Locale.ROOT, "%.2fx", map.scale)).getString(),
                    map.x + 5, map.y + 5, 0xFFB0BEC5);
            if (round.seconds() > 0) {
                g.text(font, ClientNetwork.formatTime(round.seconds()),
                        map.x + map.width - 60, map.y + 5, 0xFFFFD54F);
            }
        }
        if (status != null) {
            g.centeredText(font, status.getString(), width / 2, height - 38, 0xFFFFD54F);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (map.contains(event.x(), event.y())) {
            if (event.button() == 3) {
                dragging = true;
                lastMouseX = event.x();
                lastMouseY = event.y();
                return true;
            }
            if (event.button() == 1) {
                map.hasPin = true;
                map.pinX = map.screenToWorldX(event.x());
                map.pinZ = map.screenToWorldY(event.y());
                status = null;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            map.panPixels(event.x() - lastMouseX, event.y() - lastMouseY);
            lastMouseX = event.x();
            lastMouseY = event.y();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (map.contains(mouseX, mouseY) && scrollY != 0.0) {
            map.zoomAt(mouseX, mouseY, scrollY > 0 ? 1.25 : 0.8);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** True when the current movement mode forbids zooming (NMPZ). */
    static boolean zoomBlocked() {
        var room = ClientState.get().room();
        return room != null && room.settings().movement() == MovementMode.NMPZ;
    }
}
