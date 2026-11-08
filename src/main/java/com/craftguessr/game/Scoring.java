package com.craftguessr.game;

/** Pure scoring math for both game modes; kept free of Minecraft types so it is unit-testable. */
public final class Scoring {

    public static final int MAX_ROUND_SCORE = 5000;

    private Scoring() {
    }

    /**
     * Classic GeoGuessr decay: full marks within {@code perfectRadius}, then
     * {@code 5000 * exp(-10 * d / span)} where {@code span} is the diagonal of the playable area.
     *
     * @param distanceBlocks straight-line (3D) distance between guess and truth
     * @param spanBlocks     characteristic size of the map; larger maps forgive larger errors
     */
    public static int classicScore(double distanceBlocks, double spanBlocks) {
        return classicScore(distanceBlocks, spanBlocks, 3.0);
    }

    public static int classicScore(double distanceBlocks, double spanBlocks, double perfectRadius) {
        if (!Double.isFinite(distanceBlocks) || distanceBlocks < 0) {
            return 0;
        }
        if (distanceBlocks <= perfectRadius) {
            return MAX_ROUND_SCORE;
        }
        double span = Math.max(64.0, spanBlocks);
        double value = MAX_ROUND_SCORE * Math.exp(-10.0 * distanceBlocks / span);
        return (int) Math.max(0, Math.min(MAX_ROUND_SCORE, Math.round(value)));
    }

    /** Streaks rule: the run continues while the error stays inside the threshold. */
    public static boolean streakContinues(double distanceBlocks, double thresholdBlocks) {
        return distanceBlocks >= 0 && distanceBlocks <= Math.max(1.0, thresholdBlocks);
    }

    /** Points awarded in streaks mode: 100 per surviving round keeps a simple, readable ladder. */
    public static int streakPoints(boolean continued) {
        return continued ? 100 : 0;
    }

    /** Distance in blocks between two Minecraft positions (3D). */
    public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Human readable error string; blocks are shown as meters 1:1 (GeoGuessr players read it that way)
     * but large distances switch to kilometres.
     */
    public static String formatDistance(double blocks) {
        if (!Double.isFinite(blocks) || blocks < 0) {
            return "?";
        }
        if (blocks < 1000) {
            return Math.round(blocks) + "m";
        }
        return String.format(java.util.Locale.ROOT, "%.2fkm", blocks / 1000.0);
    }
}
