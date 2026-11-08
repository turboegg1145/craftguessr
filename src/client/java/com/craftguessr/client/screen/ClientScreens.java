package com.craftguessr.client.screen;

import com.craftguessr.client.ClientNetwork;
import com.craftguessr.client.ClientState;
import com.craftguessr.game.RoundReveal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Central router for every CraftGuessr screen. */
public final class ClientScreens {

    private ClientScreens() {
    }

    public static void open(String name, String roomId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (name == null) {
            return;
        }
        switch (name) {
            case "lobby" -> minecraft.gui.setScreen(new LobbyScreen());
            case "create" -> minecraft.gui.setScreen(new CreateRoomScreen());
            case "pool" -> minecraft.gui.setScreen(new PoolScreen());
            case "guess" -> minecraft.gui.setScreen(new GuessScreen());
            case "reveal" -> openReveal();
            default -> ClientNetwork.message("craftguessr.screen.unknown");
        }
    }

    public static void openReveal() {
        RoundReveal reveal = ClientState.get().reveal();
        if (reveal != null) {
            Minecraft.getInstance().gui.setScreen(new RevealScreen(reveal));
        }
    }

    public static void openGameOver(java.util.List<com.craftguessr.game.RoomStateView.PlayerView> standings) {
        Minecraft.getInstance().gui.setScreen(new GameOverScreen(standings));
    }

    public static void close() {
        Minecraft.getInstance().gui.setScreen(null);
    }

    /** Translucent scrim so screens stay readable over bright terrain. */
    static void dimBackground(GuiGraphicsExtractor g) {
        g.fillGradient(0, 0, 10_000, 10_000, 0xC8141414, 0xE0141414);
    }
}
