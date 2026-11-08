package com.craftguessr.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared HTTP plumbing for the three protocol adapters.
 *
 * <p>Uses {@link java.net.http.HttpClient} so no extra dependency is needed inside the mod. A single
 * client instance is reused; it owns its own executor so calls do not touch the server tick thread.
 */
public final class AiHttp {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private AiHttp() {
    }

    public static JsonObject postJson(String url, JsonObject body, Duration timeout, String... headers) throws AiException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "CraftGuessr/1.0 (Minecraft 26.3)")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        for (int i = 0; i + 1 < headers.length; i += 2) {
            if (headers[i + 1] != null && !headers[i + 1].isBlank()) {
                builder.header(headers[i], headers[i + 1]);
            }
        }
        try {
            HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String text = response.body() == null ? "" : response.body();
            if (response.statusCode() / 100 != 2) {
                throw new AiException("HTTP " + response.statusCode() + ": " + truncate(text, 600), response.statusCode(), null);
            }
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new AiException("response is not a JSON object: " + truncate(text, 300), response.statusCode(), null);
            }
            return parsed.getAsJsonObject();
        } catch (AiException e) {
            throw e;
        } catch (IOException e) {
            throw new AiException("network error: " + e.getMessage(), -1, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("interrupted", -1, e);
        } catch (RuntimeException e) {
            throw new AiException("bad response: " + e.getMessage(), -1, e);
        }
    }

    // ---------------------------------------------------------------- helpers

    public static JsonObject obj() {
        return new JsonObject();
    }

    public static JsonArray arr() {
        return new JsonArray();
    }

    public static JsonObject imageUrlPart(String mime, String base64) {
        JsonObject imageUrl = new JsonObject();
        imageUrl.addProperty("url", "data:" + mime + ";base64," + base64);
        JsonObject part = new JsonObject();
        part.addProperty("type", "image_url");
        part.add("image_url", imageUrl);
        return part;
    }

    public static JsonObject textPart(String text) {
        JsonObject part = new JsonObject();
        part.addProperty("type", "text");
        part.addProperty("text", text);
        return part;
    }

    public static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    /** Navigates a dotted path, returning null when any step is missing. */
    public static JsonObject at(JsonObject root, String path) {
        if (root == null) {
            return null;
        }
        List<String> steps = new ArrayList<>(List.of(path.split("\\.")));
        JsonObject cur = root;
        for (int i = 0; i < steps.size(); i++) {
            JsonElement el = cur.get(steps.get(i));
            if (el == null || !el.isJsonObject()) {
                return null;
            }
            cur = el.getAsJsonObject();
        }
        return cur;
    }

    public static int intAt(JsonObject root, String path, int fallback) {
        if (root == null) {
            return fallback;
        }
        JsonObject parent = root;
        String[] steps = path.split("\\.");
        for (int i = 0; i < steps.length - 1; i++) {
            parent = at(parent, steps[i]);
            if (parent == null) {
                return fallback;
            }
        }
        JsonElement el = parent.get(steps[steps.length - 1]);
        return el == null || !el.isJsonPrimitive() ? fallback : el.getAsInt();
    }

    public static String stringAt(JsonObject root, String path, String fallback) {
        if (root == null) {
            return fallback;
        }
        JsonObject parent = root;
        String[] steps = path.split("\\.");
        for (int i = 0; i < steps.length - 1; i++) {
            parent = at(parent, steps[i]);
            if (parent == null) {
                return fallback;
            }
        }
        JsonElement el = parent.get(steps[steps.length - 1]);
        return el == null || !el.isJsonPrimitive() ? fallback : el.getAsString();
    }
}
