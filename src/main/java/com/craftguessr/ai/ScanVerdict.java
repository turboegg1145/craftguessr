package com.craftguessr.ai;

import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * The director's verdict for one candidate spot, parsed from the minimal JSON response.
 *
 * <p>Default (token saving) shape:
 * {@code {"keep":true,"yaw":135,"pitch":-5,"difficulty":"MEDIUM","title":"钟楼侧巷"}}
 * plus an optional {@code commentary} when the "AI commentary" switch is on.
 */
public record ScanVerdict(
        boolean keep,
        float yaw,
        float pitch,
        Difficulty difficulty,
        String title,
        String commentary
) {
    public enum Difficulty {
        EASY, MEDIUM, HARD;

        public static Difficulty parse(String raw, Difficulty fallback) {
            if (raw == null) {
                return fallback;
            }
            String upper = raw.trim().toUpperCase(Locale.ROOT);
            for (Difficulty d : values()) {
                if (d.name().equals(upper)) {
                    return d;
                }
            }
            return switch (upper) {
                case "简单", "EINFACH" -> EASY;
                case "困难", "难", "SCHWER" -> HARD;
                default -> fallback;
            };
        }

        public String translationKey() {
            return "craftguessr.difficulty." + name().toLowerCase(Locale.ROOT);
        }
    }

    public static ScanVerdict rejected() {
        return new ScanVerdict(false, 0f, 0f, Difficulty.MEDIUM, "", "");
    }

    public static ScanVerdict fromJson(JsonObject json, float fallbackYaw) {
        boolean keep = bool(json, "keep", true);
        float yaw = (float) normalizeYaw(number(json, "yaw", fallbackYaw));
        float pitch = (float) clamp(number(json, "pitch", -5), -89, 89);
        Difficulty difficulty = Difficulty.parse(str(json, "difficulty"), Difficulty.MEDIUM);
        String title = str(json, "title");
        String commentary = str(json, "commentary");
        if (title == null || title.isBlank()) {
            title = str(json, "name");
        }
        return new ScanVerdict(keep, yaw, pitch, difficulty, title == null ? "" : title.trim(),
                commentary == null ? "" : commentary.trim());
    }

    private static double normalizeYaw(double yaw) {
        double wrapped = yaw % 360.0;
        if (wrapped <= -180) {
            wrapped += 360;
        }
        if (wrapped > 180) {
            wrapped -= 360;
        }
        return wrapped;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double number(JsonObject json, String key, double fallback) {
        var el = json.get(key);
        if (el == null || !el.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return el.getAsDouble();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        var el = json.get(key);
        if (el == null || !el.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return el.getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String str(JsonObject json, String key) {
        var el = json.get(key);
        return el == null || el.isJsonNull() || !el.isJsonPrimitive() ? null : el.getAsString();
    }
}
