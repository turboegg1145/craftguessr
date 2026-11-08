package com.craftguessr.client.hud;

import com.craftguessr.client.ClientNetwork;
import com.craftguessr.client.ClientState;
import com.craftguessr.game.GamePhase;
import com.craftguessr.game.RoomStateView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.network.chat.Component;

/** Slim round/timer panel in the top centre while a game is running. */
public final class HudOverlay implements HudElement {

    private static final int WIDTH = 190;
    private static final int HEIGHT = 34;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        RoomStateView room = ClientState.get().room();
        if (room == null || !room.phase().isPlaying()) {
            return;
        }
        int left = (g.guiWidth() - WIDTH) / 2;
        int top = 4;
        g.fill(left, top, left + WIDTH, top + HEIGHT, 0xC0101820);
        g.outline(left, top, WIDTH, HEIGHT, 0x66FFFFFF);

        String round = Component.translatable("hud.craftguessr.round", room.round(), room.totalRounds()).getString();
        g.text(minecraft.font, round, left + 6, top + 5, 0xFFECEFF1);
        String phase = Component.translatable(room.phase().translationKey()).getString();
        g.text(minecraft.font, phase, left + 6, top + 19, 0xFF90A4AE);

        int seconds = room.secondsLeft();
        boolean urgent = seconds <= 15;
        String time = ClientNetwork.formatTime(Math.max(0, seconds));
        int timeX = left + WIDTH - minecraft.font.width(time) - 6;
        g.text(minecraft.font, time, timeX, top + 5, urgent ? 0xFFFF5252 : 0xFFFFD54F);

        RoomStateView.PlayerView me = ClientState.get().me();
        if (me != null) {
            String tail = me.guessed()
                    ? Component.translatable("hud.craftguessr.guessed").getString()
                    : (me.streak() > 1
                    ? Component.translatable("hud.craftguessr.streak", me.streak()).getString()
                    : Integer.toString(me.score()));
            int tailX = left + WIDTH - minecraft.font.width(tail) - 6;
            g.text(minecraft.font, tail, tailX, top + 19, me.guessed() ? 0xFF9CCC65 : 0xFFCFD8DC);
        }
    }

    /** Overlay is only meaningful while a round is live. */
    public static boolean active() {
        RoomStateView room = ClientState.get().room();
        return room != null && (room.phase() == GamePhase.PREPARING || room.phase() == GamePhase.ACTIVE);
    }
}
