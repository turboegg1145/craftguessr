package com.craftguessr.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * File-backed spot pool: {@code config/craftguessr/spots.json} plus {@code thumbs/*.jpg}. All methods
 * are synchronized because the server tick thread, the scan worker and GUI callbacks all touch it.
 */
public final class SpotRepository {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path root;
    private final Path indexFile;
    private final Path thumbsDir;
    private final java.util.List<Spot> spots = new java.util.ArrayList<>();

    private record Index(int version, java.util.List<Spot> spots) {
    }

    public SpotRepository(Path root) throws IOException {
        this.root = root;
        this.indexFile = root.resolve("spots.json");
        this.thumbsDir = root.resolve("thumbs");
        Files.createDirectories(thumbsDir);
    }

    public Path root() {
        return root;
    }

    public synchronized void load() throws IOException {
        spots.clear();
        if (!Files.isRegularFile(indexFile)) {
            return;
        }
        String json = Files.readString(indexFile, StandardCharsets.UTF_8);
        Index index = GSON.fromJson(json, Index.class);
        if (index != null && index.spots() != null) {
            for (Spot spot : index.spots()) {
                if (spot != null) {
                    spots.add(spot);
                }
            }
        }
    }

    public synchronized void save() throws IOException {
        Files.createDirectories(root);
        String json = GSON.toJson(new Index(1, java.util.List.copyOf(spots)));
        Path tmp = indexFile.resolveSibling("spots.json.tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, indexFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, indexFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public synchronized java.util.List<Spot> all() {
        return java.util.List.copyOf(spots);
    }

    public synchronized int size() {
        return spots.size();
    }

    public synchronized java.util.Optional<Spot> get(String id) {
        for (Spot spot : spots) {
            if (spot.id().equals(id)) {
                return java.util.Optional.of(spot);
            }
        }
        return java.util.Optional.empty();
    }

    public synchronized boolean containsPosition(Spot candidate) {
        for (Spot spot : spots) {
            if (spot.samePosition(candidate)) {
                return true;
            }
        }
        return false;
    }

    public synchronized Spot add(Spot spot) throws IOException {
        spots.add(spot);
        save();
        return spot;
    }

    public synchronized boolean remove(String id) throws IOException {
        boolean removed = spots.removeIf(spot -> spot.id().equals(id));
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized Spot update(Spot spot) throws IOException {
        for (int i = 0; i < spots.size(); i++) {
            if (spots.get(i).id().equals(spot.id())) {
                spots.set(i, spot);
                save();
                return spot;
            }
        }
        return add(spot);
    }

    /** Saves a JPEG collage and returns the relative file name stored on the spot. */
    public synchronized String saveThumbnail(String id, byte[] jpeg) throws IOException {
        String name = "thumbs/" + id.replaceAll("[^A-Za-z0-9_-]", "_") + ".jpg";
        Files.write(root.resolve(name), jpeg);
        return name;
    }

    public synchronized byte[] readThumbnail(String relative) throws IOException {
        if (relative == null || relative.isBlank()) {
            return null;
        }
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            return null;
        }
        return Files.readAllBytes(file);
    }

    /** Spots that match the room's difficulty and dimension filters. */
    public synchronized java.util.List<Spot> eligible(RoomSettings settings) {
        java.util.List<Spot> out = new java.util.ArrayList<>();
        for (Spot spot : spots) {
            if (settings.allows(spot.difficulty()) && settings.allowsDimension(spot.dimension())) {
                out.add(spot);
            }
        }
        return out;
    }

    public synchronized java.util.List<Spot> byDimension(String dimension) {
        java.util.List<Spot> out = new java.util.ArrayList<>();
        for (Spot spot : spots) {
            if (dimension == null || spot.dimension().equals(dimension)) {
                out.add(spot);
            }
        }
        return out;
    }

    /** Drops the oldest entries beyond {@code max} and saves once. */
    public synchronized int trimTo(int max) throws IOException {
        int limit = Math.max(1, max);
        if (spots.size() <= limit) {
            return 0;
        }
        spots.sort(java.util.Comparator.comparingLong(Spot::createdAtMillis).reversed());
        java.util.List<Spot> dropped = new java.util.ArrayList<>(spots.subList(limit, spots.size()));
        spots.subList(limit, spots.size()).clear();
        for (Spot spot : dropped) {
            if (!spot.thumbnailFile().isBlank()) {
                Path file = root.resolve(spot.thumbnailFile()).normalize();
                if (file.startsWith(root)) {
                    Files.deleteIfExists(file);
                }
            }
        }
        save();
        return dropped.size();
    }

    /** Loads lazily-created repositories, logging instead of throwing for the server startup path. */
    public static SpotRepository open(Path root) {
        try {
            SpotRepository repository = new SpotRepository(root);
            repository.load();
            return repository;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("cannot open spot repository at " + root, e);
        }
    }
}
