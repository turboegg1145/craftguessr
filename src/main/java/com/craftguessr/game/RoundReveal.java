package com.craftguessr.game;

import java.util.List;

/**
 * Everything the reveal screen and the 3D holograms need for one finished round: the true spot plus
 * one row per player with their guess, error and the resulting score.
 */
public record RoundReveal(
        int round,
        int totalRounds,
        Spot spot,
        List<GuessOutcome> outcomes,
        String commentary,
        int secondsUntilNext,
        boolean finished
) {

    public RoundReveal {
        outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        commentary = commentary == null ? "" : commentary;
    }

    public List<GuessOutcome> sortedByScore() {
        java.util.List<GuessOutcome> sorted = new java.util.ArrayList<>(outcomes);
        sorted.sort(java.util.Comparator.comparingInt(GuessOutcome::points).reversed());
        return List.copyOf(sorted);
    }

    /**
     * One player's guess relative to the answer. {@code gx}/{@code gz} are the pinned block coordinates,
     * {@code distance} is the 3D error in blocks, {@code points} the round score.
     */
    public record GuessOutcome(
            java.util.UUID id,
            String name,
            boolean guessed,
            String dimension,
            int gx,
            int gz,
            double distance,
            int points,
            int streak,
            int totalScore,
            boolean streakBroken
    ) {
        public GuessOutcome {
            name = name == null || name.isBlank() ? "?" : name;
            dimension = dimension == null ? "" : dimension;
        }
    }
}
