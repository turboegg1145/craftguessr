package com.craftguessr.ai;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiJsonTest {

    @Test
    void extractsPlainObject() throws Exception {
        JsonObject json = LenientJson.parseObject("{\"keep\":true,\"yaw\":135}");
        assertTrue(json.get("keep").getAsBoolean());
    }

    @Test
    void extractsFromMarkdownFence() throws Exception {
        String answer = "Sure!\n```json\n{\"keep\":false,\"title\":\"cave\"}\n```\nbye";
        JsonObject json = LenientJson.parseObject(answer);
        assertFalse(json.get("keep").getAsBoolean());
        assertEquals("cave", json.get("title").getAsString());
    }

    @Test
    void ignoresBracesInsideStrings() throws Exception {
        JsonObject json = LenientJson.parseObject("{\"title\":\"a } b { c\",\"keep\":true}");
        assertEquals("a } b { c", json.get("title").getAsString());
    }

    @Test
    void rejectsGarbage() {
        assertThrows(AiException.class, () -> LenientJson.parseObject("no json here"));
    }

    @Test
    void verdictNormalisesYawAndClampsPitch() throws Exception {
        JsonObject json = LenientJson.parseObject("{\"keep\":true,\"yaw\":270,\"pitch\":-120,\"difficulty\":\"hard\",\"title\":\"  Tower \"}");
        ScanVerdict verdict = ScanVerdict.fromJson(json, 0);
        assertEquals(-90f, verdict.yaw(), 1e-4);
        assertEquals(-89f, verdict.pitch(), 1e-4);
        assertEquals(ScanVerdict.Difficulty.HARD, verdict.difficulty());
        assertEquals("Tower", verdict.title());
    }

    @Test
    void verdictAcceptsChineseDifficulty() throws Exception {
        JsonObject json = LenientJson.parseObject("{\"keep\":true,\"difficulty\":\"困难\"}");
        assertEquals(ScanVerdict.Difficulty.HARD, ScanVerdict.fromJson(json, 0).difficulty());
    }

    @Test
    void verdictDefaultsAreSane() throws Exception {
        JsonObject json = LenientJson.parseObject("{\"keep\":true}");
        ScanVerdict verdict = ScanVerdict.fromJson(json, 42);
        assertEquals(42f, verdict.yaw(), 1e-4);
        assertEquals(-5f, verdict.pitch(), 1e-4);
        assertEquals(ScanVerdict.Difficulty.MEDIUM, verdict.difficulty());
    }
}
