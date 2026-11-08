package com.craftguessr.client;

import com.craftguessr.client.hud.HudOverlay;
import com.craftguessr.client.screen.ClientScreens;
import com.craftguessr.client.screen.GuessScreen;
import com.craftguessr.client.screen.LobbyScreen;
import com.craftguessr.client.screen.PoolScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Client entrypoint: payloads, HUD element, key binds and the F3 coordinate curtain. */
public final class CraftGuessrClient implements ClientModInitializer {

    private static KeyMapping lobbyKey;
    private static KeyMapping mapKey;
    private static KeyMapping poolKey;

    private static boolean hidDebugOverlay;

    @Override
    public void onInitializeClient() {
        ClientNetwork.init();

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("craftguessr", "round_overlay"),
                new HudOverlay());

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("craftguessr", "keys"));
        lobbyKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.craftguessr.lobby", 71  /* GLFW_KEY_G */, category));
        mapKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.craftguessr.map", 77  /* GLFW_KEY_M */, category));
        poolKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.craftguessr.pool", 80  /* GLFW_KEY_P */, category));

        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (minecraft.player == null) {
                return;
            }
            while (lobbyKey.consumeClick()) {
                minecraft.gui.setScreen(new LobbyScreen());
            }
            while (mapKey.consumeClick()) {
                if (ClientState.get().inRoom()) {
                    minecraft.gui.setScreen(new GuessScreen());
                }
            }
            while (poolKey.consumeClick()) {
                minecraft.gui.setScreen(new PoolScreen());
            }
            applyDebugOverlayCurtain(minecraft);
        });
    }

    /** F3 must not leak coordinates while a round is live; restore the previous state after it ends. */
    private static void applyDebugOverlayCurtain(net.minecraft.client.Minecraft minecraft) {
        boolean playing = ClientNetwork.playing();
        if (playing) {
            if (minecraft.debugEntries.isOverlayVisible()) {
                minecraft.debugEntries.setOverlayVisible(false);
                hidDebugOverlay = true;
            }
        } else if (hidDebugOverlay) {
            minecraft.debugEntries.setOverlayVisible(true);
            hidDebugOverlay = false;
        }
    }
}
