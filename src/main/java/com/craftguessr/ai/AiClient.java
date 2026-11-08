package com.craftguessr.ai;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Thread-safe façade over the protocol adapters: chooses the provider, enforces the concurrency cap,
 * retries transient failures with exponential backoff and feeds the token ledger.
 */
public final class AiClient {

    private static final Map<String, Supplier<AiProvider>> REGISTRY = Map.of(
            "openai", OpenAiProvider::new,
            "anthropic", AnthropicProvider::new,
            "gemini", GeminiProvider::new);

    private final AiConnection connection;
    private final AiProvider provider;
    private final Semaphore permits;
    private final TokenLedger ledger;
    private final int maxAttempts;

    public AiClient(AiConnection connection, int maxConcurrent, TokenLedger ledger, int maxAttempts) {
        this.connection = connection;
        this.provider = providerFor(connection.provider());
        this.permits = new Semaphore(Math.max(1, maxConcurrent), true);
        this.ledger = ledger == null ? new TokenLedger() : ledger;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public static AiProvider providerFor(String id) {
        String key = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        Supplier<AiProvider> supplier = REGISTRY.get(key);
        if (supplier == null) {
            throw new IllegalArgumentException("unknown AI provider: " + id + " (use openai, anthropic or gemini)");
        }
        return supplier.get();
    }

    public AiConnection connection() {
        return connection;
    }

    public TokenLedger ledger() {
        return ledger;
    }

    public AiResponse complete(AiRequest request) throws AiException {
        AiRequest effective = request;
        if (effective.model() == null || effective.model().isBlank()) {
            effective = new AiRequest(connection.model(), request.systemPrompt(), request.userPrompt(),
                    request.images(), request.maxOutputTokens(), request.temperature(), request.jsonOnly());
        }
        AiException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long start = System.nanoTime();
            try {
                permits.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiException("interrupted while waiting for AI slot", e);
            }
            try {
                AiResponse response = provider.complete(connection, effective);
                long latency = (System.nanoTime() - start) / 1_000_000L;
                ledger.record(effective.model(), response.promptTokens(), response.completionTokens(), latency, true, "ok");
                return response;
            } catch (AiException e) {
                long latency = (System.nanoTime() - start) / 1_000_000L;
                ledger.record(effective.model(), 0, 0, latency, false, e.getMessage());
                last = e;
                if (!e.retryable() || attempt == maxAttempts) {
                    throw e;
                }
                sleepBackoff(attempt);
            } finally {
                permits.release();
            }
        }
        throw last == null ? new AiException("AI call failed") : last;
    }

    /** Sends the collage and parses the director verdict. */
    public ScanVerdict judge(byte[] collageJpeg, HostContext context, boolean wantCommentary) throws AiException {
        AiRequest request = new AiRequest(
                connection.model(),
                PromptBuilder.SYSTEM,
                PromptBuilder.userPrompt(context, wantCommentary),
                List.of(AiRequest.ImagePart.jpeg(collageJpeg)),
                700,
                0.4,
                true);
        AiResponse response = complete(request);
        return ScanVerdict.fromJson(LenientJson.parseObject(response.text()), 0f);
    }

    /** Cheap "is the key alive" probe used by the config GUI. */
    public AiResponse ping() throws AiException {
        AiRequest request = new AiRequest(connection.model(), "You are a health check.",
                "Reply with exactly: PONG", List.of(), 16, 0.0, false);
        return complete(request);
    }

    private static void sleepBackoff(int attempt) throws AiException {
        long millis = Math.min(8000L, 700L * (1L << Math.min(4, attempt - 1)));
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("interrupted during retry backoff", e);
        }
    }
}
