package com.craftguessr.game;

import com.craftguessr.ai.ScanVerdict;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Everything a host can tune for one room. Immutable: every setter returns a modified copy, so the
 * lobby GUI can freely build candidate settings and only publish the final one to the server.
 */
public record RoomSettings(
        GameMode mode,
        int rounds,
        int secondsPerRound,
        MovementMode movement,
        int movementRadius,
        Set<ScanVerdict.Difficulty> difficulties,
        Set<String> dimensions,
        boolean invisiblePlayers,
        int revealSeconds,
        boolean aiCommentary,
        boolean accelerateAfterFirstGuess,
        int streakThreshold
) {

    public static final int MAX_ROUNDS = 50;
    public static final int MAX_SECONDS = 1800;
    public static final int MAX_RADIUS = 512;

    public RoomSettings {
        mode = mode == null ? GameMode.CLASSIC : mode;
        movement = movement == null ? MovementMode.MOVING : movement;
        rounds = clamp(rounds, 1, MAX_ROUNDS);
        secondsPerRound = clamp(secondsPerRound, 10, MAX_SECONDS);
        movementRadius = clamp(movementRadius, 0, MAX_RADIUS);
        revealSeconds = clamp(revealSeconds, 3, 60);
        streakThreshold = clamp(streakThreshold, 3, 10_000);
        difficulties = difficulties == null || difficulties.isEmpty()
                ? EnumSet.allOf(ScanVerdict.Difficulty.class)
                : Collections.unmodifiableSet(EnumSet.copyOf(difficulties));
        dimensions = dimensions == null || dimensions.isEmpty()
                ? Set.of("minecraft:overworld")
                : Collections.unmodifiableSet(new LinkedHashSet<>(dimensions));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static RoomSettings defaults() {
        return new RoomSettings(GameMode.CLASSIC, 5, 120, MovementMode.MOVING, 15,
                EnumSet.allOf(ScanVerdict.Difficulty.class), Set.of("minecraft:overworld"),
                true, 7, false, true, 50);
    }

    /** Sanitised copy used whenever settings come from disk or the network. */
    public RoomSettings sanitized() {
        return new RoomSettings(mode, rounds, secondsPerRound, movement, movementRadius,
                difficulties, dimensions, invisiblePlayers, revealSeconds, aiCommentary,
                accelerateAfterFirstGuess, streakThreshold);
    }

    public RoomSettings withMode(GameMode newMode) {
        return new RoomSettings(newMode, rounds, secondsPerRound, movement, movementRadius, difficulties,
                dimensions, invisiblePlayers, revealSeconds, aiCommentary, accelerateAfterFirstGuess, streakThreshold);
    }

    public RoomSettings withRounds(int value) {
        return new RoomSettings(mode, value, secondsPerRound, movement, movementRadius, difficulties,
                dimensions, invisiblePlayers, revealSeconds, aiCommentary, accelerateAfterFirstGuess, streakThreshold);
    }

    public RoomSettings withSecondsPerRound(int value) {
        return new RoomSettings(mode, rounds, value, movement, movementRadius, difficulties,
                dimensions, invisiblePlayers, revealSeconds, aiCommentary, accelerateAfterFirstGuess, streakThreshold);
    }

    public RoomSettings withMovement(MovementMode value) {
        return new RoomSettings(mode, rounds, secondsPerRound, value, movementRadius, difficulties,
                dimensions, invisiblePlayers, revealSeconds, aiCommentary, accelerateAfterFirstGuess, streakThreshold);
    }

    public RoomSettings withMovementRadius(int value) {
        return new RoomSettings(mode, rounds, secondsPerRound, movement, value, difficulties,
                dimensions, invisiblePlayers, revealSeconds, aiCommentary, accelerateAfterFirstGuess, streakThreshold);
    }

    public RoomSettings withDifficulties(Set<ScanVerdict.Difficulty> value) {
        return new RoomSettings(mode, rounds, secondsPerRound, movement, movementRadius, value,
                dimensions, invisiblePlayers, revealSeconds, aiCommentary, accelerateAfterFirstGuess, streakThreshold);
    }

    public RoomSettings withDimensions(Set<String> value) {
        return new RoomSettings(mode, rounds, secondsPerRound, movement, movementRadius, difficulties,
                value, invisiblePlayers, revealSeconds, aiCommentary, accelerateAfterFirstGuess, streakThreshold);
    }

    public RoomSettings withInvisiblePlayers(boolean value) {
        return new RoomSettings(mode, rounds, secondsPerRound, movement, movementRadius, difficulties,
                dimensions, value, revealSeconds, aiCommentary, accelerateAfterFirstGuess, streakThreshold);
    }

    public RoomSettings withRevealSeconds(int value) {
        return new RoomSettings(mode, rounds, secondsPerRound, movement, movementRadius, difficulties,
                dimensions, invisiblePlayers, value, aiCommentary, accelerateAfterFirstGuess, streakThreshold);
    }

    public RoomSettings withAiCommentary(boolean value) {
        return new RoomSettings(mode, rounds, secondsPerRound, movement, movementRadius, difficulties,
                dimensions, invisiblePlayers, revealSeconds, value, accelerateAfterFirstGuess, streakThreshold);
    }

    public RoomSettings withAccelerateAfterFirstGuess(boolean value) {
        return new RoomSettings(mode, rounds, secondsPerRound, movement, movementRadius, difficulties,
                dimensions, invisiblePlayers, revealSeconds, aiCommentary, value, streakThreshold);
    }

    public RoomSettings withStreakThreshold(int value) {
        return new RoomSettings(mode, rounds, secondsPerRound, movement, movementRadius, difficulties,
                dimensions, invisiblePlayers, revealSeconds, aiCommentary, accelerateAfterFirstGuess, value);
    }

    public boolean allows(ScanVerdict.Difficulty difficulty) {
        return difficulty != null && difficulties.contains(difficulty);
    }

    public boolean allowsDimension(String dimension) {
        return dimension != null && dimensions.contains(dimension);
    }

    /** Compact one-line description used by the lobby list. */
    public String summary() {
        List<String> parts = List.of(
                String.valueOf(rounds) + "r",
                movement.id(),
                String.valueOf(secondsPerRound) + "s");
        return String.join(" · ", parts).toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "RoomSettings[" + mode + " " + rounds + "x" + secondsPerRound + "s " + movement
                + " r=" + movementRadius + " diffs=" + difficulties + " dims=" + dimensions + "]";
    }
}
