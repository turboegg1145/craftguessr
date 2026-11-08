package com.craftguessr.scanner;

/**
 * Cheap per-candidate statistics computed on the server without loading anything expensive.
 *
 * @param totalBlocks      sampled blocks in the cylinder around the candidate
 * @param artificialBlocks blocks whose registry id looks man-made (planks, bricks, glass, wool…)
 * @param naturalBlocks    everything else
 * @param distinctTypes    number of distinct block types seen (richness/entropy proxy)
 * @param inhabitedBlocks  chunk {@code InhabitedTime} of the candidate chunk
 * @param airRatio         share of air/see-through blocks in the sample
 * @param openSightLines   horizontal rays that hit the sky or a distant target instead of a wall
 * @param totalSightLines  rays cast (usually 8 or 16)
 * @param enclosedRatio    share of the six axis directions immediately facing a solid block
 * @param surfaceY         the sampled ground level
 */
public record BlockStats(
        int totalBlocks,
        int artificialBlocks,
        int naturalBlocks,
        int distinctTypes,
        int inhabitedBlocks,
        double airRatio,
        int openSightLines,
        int totalSightLines,
        double enclosedRatio,
        int surfaceY
) {
    public double artificialRatio() {
        return totalBlocks == 0 ? 0 : (double) artificialBlocks / totalBlocks;
    }
}
