package com.craftguessr.scanner;

/**
 * Filter strength presets: how eagerly the local pre-filter accepts candidate spots before spending
 * a single token on the AI director.
 */
public enum FilterStrength {
    /** Big builds / old survival main towns: high inhabited time, rich man-made palette. */
    STRICT("craftguessr.filter.strict", 60_000, 0.12, 12, 2),
    /** Default: mid-size bases, remodelled villages, junctions. */
    BALANCED("craftguessr.filter.balanced", 8_000, 0.04, 8, 2),
    /** New or sparse servers: tiny huts, waypoint markers, cave entrances. */
    RELAXED("craftguessr.filter.relaxed", 600, 0.008, 5, 2),
    /** Natural wonders only: surface safety + sight lines, no man-made requirement. */
    WILD("craftguessr.filter.wild", 0, 0.0, 3, 3);

    private final String translationKey;
    private final int minInhabited;
    private final double minArtificialRatio;
    private final int minDiversity;
    private final int minOpenSightLines;

    FilterStrength(String translationKey, int minInhabited, double minArtificialRatio, int minDiversity,
                   int minOpenSightLines) {
        this.translationKey = translationKey;
        this.minInhabited = minInhabited;
        this.minArtificialRatio = minArtificialRatio;
        this.minDiversity = minDiversity;
        this.minOpenSightLines = minOpenSightLines;
    }

    public String translationKey() {
        return translationKey;
    }

    public int minInhabited() {
        return minInhabited;
    }

    public double minArtificialRatio() {
        return minArtificialRatio;
    }

    public int minDiversity() {
        return minDiversity;
    }

    public int minOpenSightLines() {
        return minOpenSightLines;
    }

    public FilterStrength next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
