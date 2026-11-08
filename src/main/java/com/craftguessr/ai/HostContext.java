package com.craftguessr.ai;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cheap textual context about a candidate spot, gathered on the server with zero token cost.
 *
 * <p>The scanner fills this from block statistics, sign text and line-of-sight results; the prompt
 * builder turns it into a few dense lines the model can cross-check against the screenshot.
 */
public record HostContext(
        String dimension,
        int x,
        int y,
        int z,
        String biome,
        boolean night,
        boolean raining,
        int openSightLines,
        int totalSightLines,
        double artificialRatio,
        int blockDiversity,
        int inhabitedBlocks,
        Map<String, Integer> topArtificialBlocks,
        List<String> signTexts,
        List<String> nearbyEntities
) {
    public HostContext {
        topArtificialBlocks = Map.copyOf(topArtificialBlocks);
        signTexts = List.copyOf(signTexts);
        nearbyEntities = List.copyOf(nearbyEntities);
    }

    public String toPromptText() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("dimension: ").append(dimension).append('\n');
        sb.append("coords: x=").append(x).append(" y=").append(y).append(" z=").append(z).append('\n');
        sb.append("biome: ").append(biome == null || biome.isBlank() ? "unknown" : biome).append('\n');
        sb.append("time: ").append(night ? "night" : "day").append(raining ? ", raining" : "").append('\n');
        sb.append(String.format(Locale.ROOT, "open sight lines: %d/%d%n", openSightLines, totalSightLines));
        sb.append(String.format(Locale.ROOT, "artificial block ratio: %.0f%%%n", artificialRatio * 100.0));
        sb.append("distinct block types nearby: ").append(blockDiversity).append('\n');
        sb.append("region inhabited-time (blocks): ").append(inhabitedBlocks).append('\n');
        if (!topArtificialBlocks.isEmpty()) {
            sb.append("top man-made blocks: ");
            int i = 0;
            for (Map.Entry<String, Integer> entry : topArtificialBlocks.entrySet()) {
                if (i++ > 0) {
                    sb.append(", ");
                }
                sb.append(entry.getKey()).append('x').append(entry.getValue());
                if (i >= 6) {
                    break;
                }
            }
            sb.append('\n');
        }
        if (!signTexts.isEmpty()) {
            sb.append("sign text: ");
            sb.append(String.join(" | ", signTexts.subList(0, Math.min(4, signTexts.size()))));
            sb.append('\n');
        }
        if (!nearbyEntities.isEmpty()) {
            sb.append("entities: ").append(String.join(", ", nearbyEntities)).append('\n');
        }
        return sb.toString();
    }
}
