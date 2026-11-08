package com.craftguessr.client.screen;

import com.craftguessr.client.ClientState;
import com.craftguessr.game.RoomStateView;
import com.craftguessr.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;

/** Final standings. */
public final class GameOverScreen extends Screen {

    private static final int[] MEDALS = {0xFFFFD54F, 0xFFCFD8DC, 0xFFBCAAA4};

    private final List<RoomStateView.PlayerView> standings;

    public GameOverScreen(List<RoomStateView.PlayerView> standings) {
        super(Component.translatable("gui.craftguessr.lobby.gameover"));
        this.standings = standings.stream()
                .sorted(Comparator.comparingInt(RoomStateView.PlayerView::score).reversed())
                .toList();
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.lobby.leave"),
                        button -> {
                            ClientPlayNetworking.send(new Payloads.LeaveRoomC2S());
                            ClientState.get().setRoom(null);
                            minecraft.gui.setScreen(new LobbyScreen());
                        })
                .bounds(width / 2 - 75, height - 40, 150, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, title.getString(), width / 2, 24, 0xFFFFFFFF);
        int rowY = 60;
        for (int i = 0; i < standings.size(); i++) {
            RoomStateView.PlayerView player = standings.get(i);
            int color = i < MEDALS.length ? MEDALS[i] : 0xFFECEFF1;
            String line = (i + 1) + ".  " + player.name() + "   " + player.score();
            g.centeredText(font, line, width / 2, rowY, color);
            rowY += 18;
        }
        if (standings.isEmpty()) {
            g.centeredText(font, Component.translatable("gui.craftguessr.reveal.nobody").getString(),
                    width / 2, 70, 0xFF90A4AE);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
