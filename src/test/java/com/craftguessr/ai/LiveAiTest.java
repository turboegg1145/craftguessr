package com.craftguessr.ai;

import com.craftguessr.map.ImageCollage;
import com.craftguessr.map.TileRaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hits the real API. Run with:
 * {@code ./dev.sh gw test --tests '*LiveAiTest' -Pcraftguessr.live=true -Pcraftguessr.apiKey=...}
 */
@EnabledIfSystemProperty(named = "craftguessr.live", matches = "true")
class LiveAiTest {

    private static String key() {
        String key = System.getProperty("craftguessr.apiKey", "");
        assertFalse(key.isBlank(), "pass -Pcraftguessr.apiKey=...");
        return key;
    }

    private static String base() {
        return System.getProperty("craftguessr.aiBase", "https://api.deepseek.com");
    }

    private static String model() {
        return System.getProperty("craftguessr.aiModel", "deepseek-flash");
    }

    private static HostContext context() {
        return new HostContext("minecraft:overworld", 128, 74, -256, "minecraft:plains", false, false,
                6, 8, 0.11, 21, 40_000,
                Map.of("minecraft:oak_planks", 12, "minecraft:cobblestone", 9, "minecraft:glass", 4),
                List.of("WELCOME TO THE FARM"),
                List.of("minecraft:cow", "minecraft:player"));
    }

    /** A fake four-way "ghost camera" collage so the image upload path is exercised for real. */
    private static byte[] syntheticCollage(int quadrant) throws IOException {
        BufferedImage[] quads = new BufferedImage[4];
        for (int q = 0; q < 4; q++) {
            int size = quadrant;
            int[] colors = new int[size * size];
            int[] heights = new int[size * size];
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    int idx = z * size + x;
                    heights[idx] = 64 + (int) (Math.sin((x + q * 31) * 0.08) * 6 + Math.cos(z * 0.06) * 4);
                    int green = 96 + (heights[idx] - 60) * 3;
                    colors[idx] = 0x00387832 | Math.min(255, Math.max(0, green)) << 8;
                }
            }
            quads[q] = TileRaster.toImage(TileRaster.shade(colors, heights, size), size);
        }
        return ImageCollage.toJpeg(ImageCollage.collage(List.of(quads), quadrant), 0.7f);
    }

    @Test
    void openAiProtocolVisionRoundTrip() throws Exception {
        TokenLedger ledger = new TokenLedger();
        AiClient client = new AiClient(
                new AiConnection("openai", base(), key(), model(), Duration.ofSeconds(120)),
                1, ledger, 2);

        AiResponse pong = client.ping();
        assertNotNull(pong.text());
        System.out.println("[live] openai ping -> " + pong.text().trim());

        byte[] collage = syntheticCollage(256);
        System.out.println("[live] collage bytes = " + collage.length);
        ScanVerdict verdict = client.judge(collage, context(), true);
        System.out.println("[live] verdict keep=" + verdict.keep() + " yaw=" + verdict.yaw()
                + " pitch=" + verdict.pitch() + " difficulty=" + verdict.difficulty()
                + " title=" + verdict.title() + " commentary=" + verdict.commentary());
        assertNotNull(verdict.difficulty(), "difficulty must parse to a known value");
        assertNotNull(verdict.title());

        long tokens = ledger.totalTokens();
        System.out.println("[live] tokens=" + tokens + " prompt=" + ledger.promptTokens() + " completion=" + ledger.completionTokens());
        assertTrue(tokens > 0, "usage should be reported");
    }

    @Test
    void anthropicProtocolPing() throws Exception {
        TokenLedger ledger = new TokenLedger();
        AiClient client = new AiClient(
                new AiConnection("anthropic", base() + "/anthropic", key(), model(), Duration.ofSeconds(120)),
                1, ledger, 1);
        AiResponse pong = client.ping();
        System.out.println("[live] anthropic ping -> " + pong.text().trim()
                + " tokens=" + ledger.totalTokens());
        assertFalse(pong.text().isBlank());
    }
}
