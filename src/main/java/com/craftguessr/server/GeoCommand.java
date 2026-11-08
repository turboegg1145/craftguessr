package com.craftguessr.server;

import com.craftguessr.CraftGuessr;
import com.craftguessr.ai.AiClient;
import com.craftguessr.ai.AiException;
import com.craftguessr.game.GameSettings;
import com.craftguessr.game.RoomSettings;
import com.craftguessr.network.Payloads;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** {@code /geo}: the Human-facing entry point for the lobby, scanning, the pool and the AI config. */
public final class GeoCommand {

    private GeoCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("geo")
                .then(Commands.literal("lobby")
                        .executes(ctx -> {
                            open(ctx.getSource(), "lobby", "");
                            return 1;
                        }))
                .then(Commands.literal("create")
                        .executes(ctx -> {
                            ServerPlayer player = player(ctx.getSource());
                            manager().createRoom(player);
                            open(ctx.getSource(), "lobby", "");
                            return 1;
                        }))
                .then(Commands.literal("join")
                        .then(Commands.argument("room", StringArgumentType.word())
                                .executes(ctx -> {
                                    manager().joinRoom(player(ctx.getSource()),
                                            StringArgumentType.getString(ctx, "room"));
                                    return 1;
                                })))
                .then(Commands.literal("leave")
                        .executes(ctx -> {
                            manager().leaveRoom(player(ctx.getSource()), true);
                            return 1;
                        }))
                .then(Commands.literal("start")
                        .executes(ctx -> {
                            manager().startGame(player(ctx.getSource()));
                            return 1;
                        }))
                .then(Commands.literal("rooms")
                        .executes(ctx -> {
                            ServerNetwork.sendRoomList(player(ctx.getSource()));
                            open(ctx.getSource(), "lobby", "");
                            return 1;
                        }))
                .then(Commands.literal("pool")
                        .executes(ctx -> {
                            open(ctx.getSource(), "pool", "");
                            ServerPlayNetworking.send(player(ctx.getSource()),
                                    new Payloads.PoolS2C(manager().repository().all()));
                            return 1;
                        }))
                .then(Commands.literal("scan")
                        .executes(ctx -> {
                            ServerPlayer player = player(ctx.getSource());
                            manager().scans().start(player, 256, "BALANCED", "");
                            return 1;
                        })
                        .then(Commands.argument("radius", IntegerArgumentType.integer(32, 1024))
                                .executes(ctx -> {
                                    manager().scans().start(player(ctx.getSource()),
                                            IntegerArgumentType.getInteger(ctx, "radius"), "BALANCED", "");
                                    return 1;
                                })
                                .then(Commands.argument("strength", StringArgumentType.word())
                                        .executes(ctx -> {
                                            manager().scans().start(player(ctx.getSource()),
                                                    IntegerArgumentType.getInteger(ctx, "radius"),
                                                    StringArgumentType.getString(ctx, "strength"), "");
                                            return 1;
                                        }))))
                .then(Commands.literal("capture")
                        .executes(ctx -> capture(ctx.getSource(), "", ""))
                        .then(Commands.argument("title", StringArgumentType.string())
                                .executes(ctx -> capture(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "title"), ""))
                                .then(Commands.argument("difficulty", StringArgumentType.word())
                                        .executes(ctx -> capture(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "title"),
                                                StringArgumentType.getString(ctx, "difficulty"))))))
                .then(Commands.literal("tokens")
                        .executes(ctx -> {
                            ServerGameManager manager = manager();
                            ctx.getSource().sendSuccess(() -> Component.translatable("craftguessr.command.tokens",
                                    manager.ledger().promptTokens(), manager.ledger().completionTokens(),
                                    manager.ledger().calls(), manager.ledger().failures()), false);
                            return 1;
                        }))
                .then(Commands.literal("ping")
                        .executes(ctx -> {
                            ServerPlayer player = player(ctx.getSource());
                            ServerGameManager manager = manager();
                            ctx.getSource().sendSuccess(() -> Component.translatable("craftguessr.command.ping_started"), false);
                            Thread.startVirtualThread(() -> {
                                try {
                                    AiClient client = new AiClient(manager.settings().aiConnection(),
                                            manager.settings().maxConcurrency, manager.ledger(), 1);
                                    String reply = client.ping().text();
                                    manager.server().execute(() ->
                                            manager.notice(player, "craftguessr.command.ping_ok", false));
                                    CraftGuessr.LOGGER.info("AI ping ok: {}", reply);
                                } catch (AiException e) {
                                    CraftGuessr.LOGGER.warn("AI ping failed: {}", e.getMessage());
                                    manager.server().execute(() ->
                                            manager.notice(player, "craftguessr.command.ping_failed", true));
                                }
                            });
                            return 1;
                        }))
                .then(Commands.literal("config")
                        .requires(Perms::isAdmin)
                        .then(Commands.literal("show")
                                .executes(ctx -> {
                                    GameSettings settings = manager().settings();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            settings.provider + " | " + settings.baseUrl + " | " + settings.model
                                                    + " | key=" + (settings.apiKey.isBlank() ? "<unset>" : "set")
                                                    + " | concurrency=" + settings.maxConcurrency
                                                    + " | commentary=" + settings.aiCommentary), false);
                                    return 1;
                                }))
                        .then(Commands.literal("reload")
                                .executes(ctx -> {
                                    manager().reloadSettings();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("craftguessr.command.reloaded"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String key = StringArgumentType.getString(ctx, "key");
                                                    String value = StringArgumentType.getString(ctx, "value");
                                                    boolean ok = manager().configure(key, value);
                                                    ctx.getSource().sendSuccess(() -> Component.translatable(
                                                            ok ? "craftguessr.command.config_set" : "craftguessr.command.config_unknown_key",
                                                            key), !ok);
                                                    return 1;
                                                }))))));
    }

    private static ServerGameManager manager() {
        return ServerGameManager.get();
    }

    private static ServerPlayer player(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return source.getPlayerOrException();
    }

    /** Starts a manual quad-capture on the requesting client. */
    private static int capture(CommandSourceStack source, String title, String difficulty)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        ServerPlayNetworking.send(player, new Payloads.CaptureRequestS2C(title, difficulty));
        source.sendSuccess(() -> Component.translatable("craftguessr.command.capture_started"), false);
        return 1;
    }

    private static void open(CommandSourceStack source, String screen, String roomId) {
        try {
            ServerPlayNetworking.send(source.getPlayerOrException(), new Payloads.OpenScreenS2C(screen, roomId));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
            // console: nothing to open
        }
    }
}
