package com.craftguessr.game;

/** Lifecycle of a room; drives what every client screen should show. */
public enum GamePhase {
    /** No round running, players can wander. */
    IDLE,
    /** Host/admin is building the candidate pool; clients see progress. */
    SCANNING,
    /** Round announced, players are being teleported into position. */
    PREPARING,
    /** Players are looking around and pinning guesses, timer running. */
    ACTIVE,
    /** Everyone guessed or the timer fired: map + hologram reveal. */
    REVEAL,
    /** Final leaderboard. */
    FINISHED;

    public boolean isPlaying() {
        return this == PREPARING || this == ACTIVE;
    }
}
