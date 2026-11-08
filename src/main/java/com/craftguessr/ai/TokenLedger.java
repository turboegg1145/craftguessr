package com.craftguessr.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory token/cost accounting shown by the scan console GUI. */
public final class TokenLedger {

    /** One finished AI call, kept for the console log pane. */
    public record Entry(long timeMillis, String model, int promptTokens, int completionTokens,
                        long latencyMillis, boolean success, String note) {
    }

    private static final int MAX_ENTRIES = 64;

    private final AtomicLong promptTokens = new AtomicLong();
    private final AtomicLong completionTokens = new AtomicLong();
    private final AtomicLong calls = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final Deque<Entry> recent = new ArrayDeque<>();

    public void record(String model, int prompt, int completion, long latencyMillis, boolean success, String note) {
        calls.incrementAndGet();
        if (success) {
            promptTokens.addAndGet(prompt);
            completionTokens.addAndGet(completion);
        } else {
            failures.incrementAndGet();
        }
        synchronized (recent) {
            recent.addFirst(new Entry(System.currentTimeMillis(), model, prompt, completion, latencyMillis, success, note));
            while (recent.size() > MAX_ENTRIES) {
                recent.removeLast();
            }
        }
    }

    public long promptTokens() {
        return promptTokens.get();
    }

    public long completionTokens() {
        return completionTokens.get();
    }

    public long calls() {
        return calls.get();
    }

    public long failures() {
        return failures.get();
    }

    public long totalTokens() {
        return promptTokens.get() + completionTokens.get();
    }

    public List<Entry> recent() {
        synchronized (recent) {
            return new ArrayList<>(recent);
        }
    }

    public void reset() {
        promptTokens.set(0);
        completionTokens.set(0);
        calls.set(0);
        failures.set(0);
        synchronized (recent) {
            recent.clear();
        }
    }
}
