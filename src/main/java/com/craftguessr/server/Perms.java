package com.craftguessr.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/** Permission helpers: 26.3 replaced numeric permission levels with {@link Permissions} constants. */
public final class Perms {

    private Perms() {
    }

    /** True for vanilla gamemaster and above (the old level 2). */
    public static boolean isAdmin(ServerPlayer player) {
        return isAdmin(player.createCommandSourceStack());
    }

    public static boolean isAdmin(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
