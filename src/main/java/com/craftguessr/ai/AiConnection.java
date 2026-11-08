package com.craftguessr.ai;

import java.time.Duration;

/**
 * Immutable connection settings for one API endpoint.
 *
 * @param provider  protocol id, see {@link AiProvider#id()}
 * @param baseUrl   endpoint root, without the protocol path (e.g. {@code https://api.deepseek.com})
 * @param apiKey    bearer / x-api-key credential; never logged
 * @param model     model id understood by the endpoint
 * @param timeout   per-request timeout
 */
public record AiConnection(String provider, String baseUrl, String apiKey, String model, Duration timeout) {

    public AiConnection {
        baseUrl = stripTrailingSlash(baseUrl == null || baseUrl.isBlank() ? defaultBaseUrl(provider) : baseUrl);
        timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(90) : timeout;
    }

    public static String defaultBaseUrl(String provider) {
        return switch (provider == null ? "" : provider.toLowerCase(java.util.Locale.ROOT)) {
            case "anthropic" -> "https://api.anthropic.com";
            case "gemini", "google" -> "https://generativelanguage.googleapis.com";
            default -> "https://api.openai.com";
        };
    }

    private static String stripTrailingSlash(String url) {
        String out = url.trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    public AiConnection withModel(String newModel) {
        return new AiConnection(provider, baseUrl, apiKey, newModel, timeout);
    }

    /** Redacted form safe to print in logs / GUI. */
    public String describe() {
        String key = apiKey == null || apiKey.length() < 8 ? "(none)" : apiKey.substring(0, 5) + "..." + apiKey.substring(apiKey.length() - 4);
        return provider + " " + baseUrl + " model=" + model + " key=" + key;
    }
}
