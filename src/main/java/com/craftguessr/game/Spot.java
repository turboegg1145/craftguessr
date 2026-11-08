package com.craftguessr.game;

import com.craftguessr.ai.ScanVerdict;

import java.util.Locale;
import java.util.Objects;

/**
 * One entry of the playable pool: a judged viewpoint plus the collage thumbnail that sells it in the
 * album GUI. Immutable; the album edits produce copies that {@link SpotRepository} persists.
 */
public record Spot(
        String id,
        String dimension,
        int x,
        int y,
        int z,
        float yaw,
        float pitch,
        ScanVerdict.Difficulty difficulty,
        String title,
        String commentary,
        String thumbnailFile,
        Source source,
        long createdAtMillis
) {

    public enum Source {
        /** Found and judged by the AI director. */
        AI,
        /** Submitted by a player standing there. */
        MANUAL,
        /** Added by an admin from the pool GUI. */
        ADMIN
    }

    public Spot {
        id = id == null || id.isBlank() ? Long.toHexString(System.nanoTime()) : id.trim();
        dimension = dimension == null || dimension.isBlank() ? "minecraft:overworld" : dimension.trim();
        difficulty = difficulty == null ? ScanVerdict.Difficulty.MEDIUM : difficulty;
        title = title == null ? "" : title.trim();
        commentary = commentary == null ? "" : commentary.trim();
        thumbnailFile = thumbnailFile == null ? "" : thumbnailFile.trim();
        source = source == null ? Source.AI : source;
        createdAtMillis = createdAtMillis <= 0 ? System.currentTimeMillis() : createdAtMillis;
    }

    public static Spot from(int x, int y, int z, ScanVerdict verdict, String dimension, String thumbnailFile, Source source) {
        return new Spot(Long.toHexString(System.nanoTime()), dimension, x, y, z,
                verdict.yaw(), verdict.pitch(), verdict.difficulty(), verdict.title(), verdict.commentary(),
                thumbnailFile, source, System.currentTimeMillis());
    }

    public String label() {
        return title.isBlank() ? "Unnamed spot" : title;
    }

    public String coordinates() {
        return x + ", " + y + ", " + z;
    }

    public boolean isOverworld() {
        return dimension.endsWith("overworld");
    }

    public Spot withTitle(String newTitle) {
        return new Spot(id, dimension, x, y, z, yaw, pitch, difficulty, newTitle, commentary,
                thumbnailFile, source, createdAtMillis);
    }

    public Spot withDifficulty(ScanVerdict.Difficulty newDifficulty) {
        return new Spot(id, dimension, x, y, z, yaw, pitch, newDifficulty, title, commentary,
                thumbnailFile, source, createdAtMillis);
    }

    public Spot withCommentary(String newCommentary) {
        return new Spot(id, dimension, x, y, z, yaw, pitch, difficulty, title, newCommentary,
                thumbnailFile, source, createdAtMillis);
    }

    public Spot withThumbnail(String newThumbnail) {
        return new Spot(id, dimension, x, y, z, yaw, pitch, difficulty, title, commentary,
                newThumbnail, source, createdAtMillis);
    }

    public boolean samePosition(Spot other) {
        return other != null
                && dimension.equals(other.dimension)
                && x == other.x && y == other.y && z == other.z;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s @%s [%d %d %d] %s", label(), dimension, x, y, z, difficulty);
    }

    /** Stable identity for maps and GUI keys. */
    @Override
    public boolean equals(Object o) {
        return o instanceof Spot other && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
