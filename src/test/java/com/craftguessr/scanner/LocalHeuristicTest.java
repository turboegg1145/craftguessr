package com.craftguessr.scanner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHeuristicTest {

    private static BlockStats stats(int inhabited, int artificial, int total, int types, int open) {
        return new BlockStats(total, artificial, total - artificial, types, inhabited, 0.6, open, 8, 0.1, 70);
    }

    @Test
    void strictRejectsModestBaseThatBalancedAccepts() {
        BlockStats small = stats(9_000, 40, 1000, 9, 6);
        assertFalse(LocalHeuristic.evaluate(small, FilterStrength.STRICT).pass(),
                "STRICT wants a long-inhabited area (>= 60s of ticking)");
        assertTrue(LocalHeuristic.evaluate(small, FilterStrength.BALANCED).pass());
    }

    @Test
    void strictAcceptsLongInhabitedVillage() {
        BlockStats village = stats(70_000, 180, 1000, 18, 6);
        assertTrue(LocalHeuristic.evaluate(village, FilterStrength.STRICT).pass());
    }

    @Test
    void blockedSightLinesAlwaysReject() {
        BlockStats tunnel = stats(90_000, 300, 1000, 20, 1);
        for (FilterStrength strength : FilterStrength.values()) {
            assertFalse(LocalHeuristic.evaluate(tunnel, strength).pass(), "rejected for " + strength);
        }
    }

    @Test
    void enclosedSpotRejected() {
        BlockStats hole = new BlockStats(1000, 0, 1000, 4, 0, 0.3, 8, 8, 0.9, 20);
        assertFalse(LocalHeuristic.evaluate(hole, FilterStrength.RELAXED).pass());
    }

    @Test
    void wildAcceptsMonotonousButOpenLandscape() {
        BlockStats plains = new BlockStats(1000, 0, 1000, 6, 0, 0.9, 7, 8, 0.05, 64);
        assertTrue(LocalHeuristic.evaluate(plains, FilterStrength.WILD).pass());
        assertFalse(LocalHeuristic.evaluate(plains, FilterStrength.BALANCED).pass());
    }

    @Test
    void rankingPrefersRicherSpots() {
        LocalHeuristic.Verdict rich = LocalHeuristic.evaluate(stats(80_000, 300, 1000, 30, 7), FilterStrength.BALANCED);
        LocalHeuristic.Verdict plain = LocalHeuristic.evaluate(stats(10_000, 60, 1000, 9, 2), FilterStrength.BALANCED);
        assertTrue(rich.score() > plain.score());
    }
}
