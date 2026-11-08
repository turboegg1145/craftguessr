package com.craftguessr.game;

import java.util.Locale;

/** How much freedom the observer gets during a round. */
public enum MovementMode {
    /** Walk freely inside a spherical radius around the spawn. */
    MOVING("craftguessr.move.moving", true, true),
    /** Feet stay glued to the spawn, but the head turns and FOV zooms. */
    NO_MOVE("craftguessr.move.no_move", false, true),
    /** Position, yaw, pitch and FOV are all locked at the director's shot. */
    NMPZ("craftguessr.move.nmpz", false, false);

    private final String translationKey;
    private final boolean canMove;
    private final boolean canLook;

    MovementMode(String translationKey, boolean canMove, boolean canLook) {
        this.translationKey = translationKey;
        this.canMove = canMove;
        this.canLook = canLook;
    }

    public String translationKey() {
        return translationKey;
    }

    public boolean canMove() {
        return canMove;
    }

    public boolean canLook() {
        return canLook;
    }

    public boolean canZoom() {
        return this != NMPZ;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MovementMode parse(String value, MovementMode fallback) {
        if (value == null) {
            return fallback;
        }
        for (MovementMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return fallback;
    }
}
