package com.craftguessr.ai;

/**
 * Minimal contract every protocol adapter implements.
 *
 * <p>Adapters are stateless and must be safe to call from any thread: the scanner pipeline runs on a
 * worker pool so the server tick never blocks on network I/O.
 */
public interface AiProvider {

    /** Stable protocol id used in config files: {@code anthropic}, {@code openai}, {@code gemini}. */
    String id();

    /**
     * Performs one blocking call. Implementations must throw {@link AiException} on failure and never
     * return {@code null}.
     */
    AiResponse complete(AiConnection connection, AiRequest request) throws AiException;
}
