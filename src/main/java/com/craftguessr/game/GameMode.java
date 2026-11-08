package com.craftguessr.game;

/** The two game formats from the design: classic 5000-point rounds or endless streak survival. */
public enum GameMode {
    CLASSIC("craftguessr.mode.classic"),
    STREAKS("craftguessr.mode.streaks");

    private final String translationKey;

    GameMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    public boolean isClassic() {
        return this == CLASSIC;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static GameMode parse(String value, GameMode fallback) {
        if (value == null) {
            return fallback;
        }
        for (GameMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return fallback;
    }
}
