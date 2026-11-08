package com.craftguessr.scanner;

/** Zero-token pre-filter: decides whether a candidate is worth an AI call, and ranks survivors. */
public final class LocalHeuristic {

    /** Result of a pre-filter pass. {@code score} is only meaningful when {@code pass} is true. */
    public record Verdict(boolean pass, double score, String reason) {
        public static Verdict reject(String reason) {
            return new Verdict(false, -1, reason);
        }
    }

    private LocalHeuristic() {
    }

    public static Verdict evaluate(BlockStats stats, FilterStrength strength) {
        if (stats.totalBlocks() == 0) {
            return Verdict.reject("no blocks sampled");
        }
        // Hard rejects: physically unusable viewpoints.
        if (stats.enclosedRatio() > 0.8) {
            return Verdict.reject("enclosed (walls on most sides)");
        }
        if (stats.openSightLines() < strength.minOpenSightLines()) {
            return Verdict.reject("blocked sight lines " + stats.openSightLines() + "/" + stats.totalSightLines());
        }
        if (stats.airRatio() < 0.10) {
            return Verdict.reject("buried in solid material");
        }

        // Imported/creative worlds have no accumulated inhabited time, so that signal is a
        // ranking bonus only; man-made density and block variety are what actually identify
        // a build. Inhabited time still boosts the score in manMadeScore().
        boolean hasManMade = stats.artificialRatio() >= strength.minArtificialRatio()
                && stats.distinctTypes() >= strength.minDiversity();

        if (strength == FilterStrength.WILD) {
            if (stats.distinctTypes() < strength.minDiversity()) {
                return Verdict.reject("too monotonous (" + stats.distinctTypes() + " types)");
            }
            return new Verdict(true, wildScore(stats), "natural landmark");
        }
        if (!hasManMade) {
            return Verdict.reject(missReason(stats, strength));
        }
        return new Verdict(true, manMadeScore(stats), "man-made cluster");
    }

    private static String missReason(BlockStats stats, FilterStrength strength) {
        if (stats.artificialRatio() < strength.minArtificialRatio()) {
            return String.format(java.util.Locale.ROOT, "artificial %.1f%% < %.1f%%",
                    stats.artificialRatio() * 100, strength.minArtificialRatio() * 100);
        }
        return "block variety " + stats.distinctTypes() + " < " + strength.minDiversity();
    }

    /** Weighted 0..100 ranking score for man-made spots. */
    static double manMadeScore(BlockStats stats) {
        double artificial = Math.min(1.0, stats.artificialRatio() / 0.25) * 40;
        double diversity = Math.min(1.0, stats.distinctTypes() / 28.0) * 25;
        double openness = stats.totalSightLines() == 0 ? 0 : (double) stats.openSightLines() / stats.totalSightLines() * 25;
        double age = Math.min(1.0, Math.log10(1 + stats.inhabitedBlocks()) / 6.0) * 10;
        return artificial + diversity + openness + age;
    }

    /** Weighted 0..100 ranking score for natural spots (openness and variety matter most). */
    static double wildScore(BlockStats stats) {
        double openness = stats.totalSightLines() == 0 ? 0 : (double) stats.openSightLines() / stats.totalSightLines() * 55;
        double diversity = Math.min(1.0, stats.distinctTypes() / 16.0) * 30;
        double notEnclosed = (1.0 - stats.enclosedRatio()) * 15;
        return openness + diversity + notEnclosed;
    }
}
