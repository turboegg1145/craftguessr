package com.craftguessr.game;

import com.craftguessr.ai.AiConnection;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Persisted, human-editable settings ({@code config/craftguessr.json}). Deliberately a plain bean with
 * public fields so Gson round-trips it without adapters and commands can tweak individual values.
 */
public final class GameSettings {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Reads {@code config/craftguessr.json}, falling back to defaults for a missing or corrupt file. */
    public static GameSettings load(Path file) {
        GameSettings settings = new GameSettings();
        try {
            if (Files.isRegularFile(file)) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                GameSettings parsed = GSON.fromJson(json, GameSettings.class);
                if (parsed != null) {
                    settings = parsed;
                }
            }
        } catch (IOException | com.google.gson.JsonParseException ignored) {
            // corrupt or unreadable config: keep sane defaults, the save below repairs the file
        }
        settings.sanitize();
        settings.save(file);
        return settings;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // read-only servers: settings simply stay in memory
        }
    }

    public String provider = "openai";
    public String baseUrl = "https://api.deepseek.com";
    public String apiKey = "";
    public String model = "deepseek-flash";
    public int maxConcurrency = 2;
    public int timeoutSeconds = 90;
    public int maxSpotsPerScan = 200;
    public boolean aiCommentary = false;
    public int maxPoolSize = 2000;

    /** NONE, BLUEMAP, DYNMAP or SQUAREMAP. */
    public String webMapType = "NONE";
    public String webMapUrl = "";
    public boolean xaeroEnabled = true;
    /** Radius in tiles of the native fallback that is pre-generated for the map GUI. */
    public int nativeTileRadius = 8;

    public RoomSettings defaultRoom = RoomSettings.defaults();

    public AiConnection aiConnection() {
        return new AiConnection(provider, baseUrl, apiKey, model, Duration.ofSeconds(timeoutSeconds));
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Clamps hand-edited values into ranges the game actually supports. */
    public void sanitize() {
        provider = switch (provider == null ? "" : provider.toLowerCase(java.util.Locale.ROOT)) {
            case "anthropic", "gemini", "openai" -> provider.trim().toLowerCase(java.util.Locale.ROOT);
            default -> "openai";
        };
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = AiConnection.defaultBaseUrl(provider);
        }
        baseUrl = baseUrl.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        model = model == null || model.isBlank() ? "deepseek-flash" : model.trim();
        maxConcurrency = Math.max(1, Math.min(8, maxConcurrency));
        timeoutSeconds = Math.max(10, Math.min(300, timeoutSeconds));
        maxSpotsPerScan = Math.max(1, Math.min(2000, maxSpotsPerScan));
        maxPoolSize = Math.max(10, Math.min(20_000, maxPoolSize));
        webMapType = switch (webMapType == null ? "" : webMapType.toUpperCase(java.util.Locale.ROOT)) {
            case "BLUEMAP", "DYNMAP", "SQUAREMAP" -> webMapType.trim().toUpperCase(java.util.Locale.ROOT);
            default -> "NONE";
        };
        webMapUrl = webMapUrl == null ? "" : webMapUrl.trim();
        nativeTileRadius = Math.max(1, Math.min(64, nativeTileRadius));
        if (defaultRoom == null) {
            defaultRoom = RoomSettings.defaults();
        }
        defaultRoom = defaultRoom.sanitized();
    }

    public GameSettings copy() {
        GameSettings out = new GameSettings();
        out.provider = provider;
        out.baseUrl = baseUrl;
        out.apiKey = apiKey;
        out.model = model;
        out.maxConcurrency = maxConcurrency;
        out.timeoutSeconds = timeoutSeconds;
        out.maxSpotsPerScan = maxSpotsPerScan;
        out.aiCommentary = aiCommentary;
        out.maxPoolSize = maxPoolSize;
        out.webMapType = webMapType;
        out.webMapUrl = webMapUrl;
        out.xaeroEnabled = xaeroEnabled;
        out.nativeTileRadius = nativeTileRadius;
        out.defaultRoom = defaultRoom;
        return out;
    }

    @Override
    public String toString() {
        return "GameSettings[" + provider + " " + model + " @ " + baseUrl
                + (hasApiKey() ? " key=set" : " key=MISSING") + " concurrency=" + maxConcurrency + "]";
    }
}
