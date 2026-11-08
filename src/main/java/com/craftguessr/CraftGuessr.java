package com.craftguessr;

import com.craftguessr.game.GameSettings;
import com.craftguessr.network.PayloadRegistry;
import com.craftguessr.network.Payloads;
import com.craftguessr.server.GeoCommand;
import com.craftguessr.server.ServerGameManager;
import com.craftguessr.server.ServerNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/** Common entry point: payload registration, the server-authoritative game manager and {@code /geo}. */
public final class CraftGuessr implements ModInitializer {

    public static final String MOD_ID = "craftguessr";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MinecraftServer server;

    @Override
    public void onInitialize() {
        PayloadRegistry.init();
        ServerNetwork.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, selection) -> GeoCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            server = started;
            GameSettings settings = GameSettings.load(settingsFile());
            new ServerGameManager(started, settings);
            LOGGER.info("CraftGuessr {} ready -- provider={} model={} key={} pool={} spots",
                    VERSION, settings.provider, settings.model,
                    settings.apiKey.isBlank() ? "<unset>" : "set",
                    ServerGameManager.get().repository().all().size());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
            ServerGameManager manager = ServerGameManager.get();
            if (manager != null) {
                manager.restoreAll();
                ServerGameManager.clear();
            }
            server = null;
        });

        ServerTickEvents.END_SERVER_TICK.register(ticking -> {
            ServerGameManager manager = ServerGameManager.get();
            if (manager != null) {
                manager.tick();
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, mcServer) -> {
            ServerGameManager manager = ServerGameManager.get();
            if (manager == null) {
                return;
            }
            int[] center = manager.mapCenter();
            ServerPlayNetworking.send(handler.player, new Payloads.PingS2C(VERSION,
                    !manager.settings().apiKey.isBlank(), center[0], center[1]));
            ServerNetwork.sendRoomList(handler.player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, mcServer) -> {
            ServerGameManager manager = ServerGameManager.get();
            if (manager != null) {
                manager.onDisconnect(handler.player);
            }
        });
    }

    /** Per-world data directory: {@code <server dir>/craftguessr}. */
    public static Path dataDirectory() {
        return server == null ? Path.of(MOD_ID) : server.getServerDirectory().resolve(MOD_ID);
    }

    public static Path settingsFile() {
        return dataDirectory().resolve("config.json");
    }
}
