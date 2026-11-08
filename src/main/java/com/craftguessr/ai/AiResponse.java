package com.craftguessr.ai;

/** Raw answered extracted from a provider response, with token accounting for the scan console. */
public record AiResponse(String text, int promptTokens, int completionTokens, int httpStatus, String rawJson) {

    public int totalTokens() {
        return promptTokens + completionTokens;
    }

    /** Rough USD estimate used by the in-game token console (rates are configurable). */
    public double estimatedCost(double usdPerMillionPrompt, double usdPerMillionCompletion) {
        return promptTokens / 1_000_000.0 * usdPerMillionPrompt
                + completionTokens / 1_000_000.0 * usdPerMillionCompletion;
    }
}
