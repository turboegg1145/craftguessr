package com.craftguessr.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoringTest {

    @Test
    void perfectGuessGetsFullMarks() {
        assertEquals(5000, Scoring.classicScore(0, 2000));
        assertEquals(5000, Scoring.classicScore(2.9, 2000));
    }

    @Test
    void scoreDecaysWithDistance() {
        int near = Scoring.classicScore(100, 2000);
        int far = Scoring.classicScore(500, 2000);
        assertTrue(near > far, "closer guess must score more");
        assertTrue(far > 0, "500 of 2000 still gives some points");
    }

    @Test
    void hugeDistanceScoresZero() {
        assertEquals(0, Scoring.classicScore(120_000, 2000));
    }

    @Test
    void negativeAndNaNDistanceScoreZero() {
        assertEquals(0, Scoring.classicScore(-5, 2000));
        assertEquals(0, Scoring.classicScore(Double.NaN, 2000));
    }

    @Test
    void streakThreshold() {
        assertTrue(Scoring.streakContinues(49, 50));
        assertFalse(Scoring.streakContinues(51, 50));
        assertEquals(100, Scoring.streakPoints(true));
        assertEquals(0, Scoring.streakPoints(false));
    }

    @Test
    void distanceFormatting() {
        assertEquals("428m", Scoring.formatDistance(428.4));
        assertEquals("1.50km", Scoring.formatDistance(1500));
    }

    @Test
    void threeDimensionalDistance() {
        assertEquals(5.0, Scoring.distance(0, 0, 0, 3, 4, 0), 1e-9);
    }
}
