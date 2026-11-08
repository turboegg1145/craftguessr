package com.craftguessr.game;

import com.craftguessr.ai.ScanVerdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameModelTest {

    @TempDir
    Path tmp;

    private static Spot spot(String id, String dimension, ScanVerdict.Difficulty difficulty, int x) {
        return new Spot(id, dimension, x, 70, 0, 90, -5, difficulty, "Title " + id, "",
                "", Spot.Source.AI, 1_000);
    }

    @Test
    void roomSettingsClampAndCopy() {
        RoomSettings wild = RoomSettings.defaults()
                .withRounds(999)
                .withSecondsPerRound(-1)
                .withMovement(MovementMode.NMPZ)
                .withMovementRadius(99_999)
                .withDifficulties(EnumSet.of(ScanVerdict.Difficulty.HARD));
        assertEquals(RoomSettings.MAX_ROUNDS, wild.rounds());
        assertEquals(10, wild.secondsPerRound());
        assertEquals(RoomSettings.MAX_RADIUS, wild.movementRadius());
        assertEquals(MovementMode.NMPZ, wild.movement());
        assertTrue(wild.allows(ScanVerdict.Difficulty.HARD));
        assertFalse(wild.allows(ScanVerdict.Difficulty.EASY));
        assertFalse(wild.movement().canMove());
        assertFalse(wild.movement().canLook());
    }

    @Test
    void settingsPersistThroughJson() {
        Path file = tmp.resolve("config/craftguessr.json");
        GameSettings settings = new GameSettings();
        settings.apiKey = "sk-test-123456789";
        settings.model = "deepseek-flash";
        settings.aiCommentary = true;
        settings.defaultRoom = RoomSettings.defaults().withRounds(7)
                .withDifficulties(Set.of(ScanVerdict.Difficulty.EASY, ScanVerdict.Difficulty.HARD))
                .withDimensions(Set.of("minecraft:overworld", "minecraft:the_nether"));
        settings.save(file);

        GameSettings loaded = GameSettings.load(file);
        assertEquals("sk-test-123456789", loaded.apiKey);
        assertEquals(7, loaded.defaultRoom.rounds());
        assertTrue(loaded.aiCommentary);
        assertEquals(Set.of(ScanVerdict.Difficulty.EASY, ScanVerdict.Difficulty.HARD), loaded.defaultRoom.difficulties());
        assertEquals(Set.of("minecraft:overworld", "minecraft:the_nether"), loaded.defaultRoom.dimensions());
        assertEquals("deepseek-flash", loaded.model);
        assertEquals("https://api.deepseek.com", loaded.baseUrl);
    }

    @Test
    void corruptConfigFallsBackToDefaults() throws IOException {
        Path file = tmp.resolve("craftguessr.json");
        java.nio.file.Files.writeString(file, "{ this is not json ");
        GameSettings loaded = GameSettings.load(file);
        assertEquals("openai", loaded.provider);
        assertEquals(5, loaded.defaultRoom.rounds());
    }

    @Test
    void repositoryRoundTripWithThumbnail() throws IOException {
        Path root = tmp.resolve("pool");
        SpotRepository repo = SpotRepository.open(root);
        Spot spot = spot("a1", "minecraft:overworld", ScanVerdict.Difficulty.MEDIUM, 10);
        String thumb = repo.saveThumbnail(spot.id(), new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3});
        repo.add(spot.withThumbnail(thumb));
        assertEquals(1, repo.size());

        SpotRepository reloaded = SpotRepository.open(root);
        assertEquals(1, reloaded.size());
        Spot fromDisk = reloaded.get("a1").orElseThrow();
        assertEquals("Title a1", fromDisk.title());
        assertEquals(10, fromDisk.x());
        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3}, reloaded.readThumbnail(fromDisk.thumbnailFile()));
        assertNotNull(reloaded.readThumbnail("thumbs/missing.jpg") == null ? "ok" : null);
    }

    @Test
    void repositoryFiltersAndDeduplicatesPositions() throws IOException {
        SpotRepository repo = SpotRepository.open(tmp.resolve("pool2"));
        repo.add(spot("o1", "minecraft:overworld", ScanVerdict.Difficulty.EASY, 1));
        repo.add(spot("o2", "minecraft:overworld", ScanVerdict.Difficulty.HARD, 2));
        repo.add(spot("n1", "minecraft:the_nether", ScanVerdict.Difficulty.EASY, 3));

        assertEquals(2, repo.eligible(RoomSettings.defaults()).size());
        assertEquals(1, repo.eligible(RoomSettings.defaults()
                .withDifficulties(EnumSet.of(ScanVerdict.Difficulty.HARD))).size());
        assertEquals(1, repo.eligible(RoomSettings.defaults()
                .withDimensions(Set.of("minecraft:the_nether"))).size());
        assertTrue(repo.containsPosition(spot("dupe", "minecraft:overworld", ScanVerdict.Difficulty.EASY, 2)));
        assertFalse(repo.containsPosition(spot("fresh", "minecraft:overworld", ScanVerdict.Difficulty.EASY, 99)));
    }

    @Test
    void repositoryTrimsOldestBeyondLimit() throws IOException {
        SpotRepository repo = SpotRepository.open(tmp.resolve("pool3"));
        for (int i = 0; i < 5; i++) {
            repo.add(new Spot("s" + i, "minecraft:overworld", i, 64, 0, 0, 0,
                    ScanVerdict.Difficulty.MEDIUM, "s" + i, "", "", Spot.Source.ADMIN, 100L + i));
        }
        assertEquals(2, repo.trimTo(3));
        assertEquals(3, repo.size());
        assertTrue(repo.get("s4").isPresent());
        assertTrue(repo.get("s0").isEmpty(), "oldest entry is dropped first");
        assertEquals(3, SpotRepository.open(tmp.resolve("pool3")).size());
    }
}
