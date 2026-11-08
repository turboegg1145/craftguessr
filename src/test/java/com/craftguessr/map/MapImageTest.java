package com.craftguessr.map;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapImageTest {

    @Test
    void tileMathHandlesNegatives() {
        assertEquals(-1, TileGrid.tileOf(-1));
        assertEquals(127, TileGrid.pixelInTile(-1));
        assertEquals(0, TileGrid.tileOf(0));
        assertEquals(1, TileGrid.tileOf(128));
        assertEquals(-256, TileGrid.worldFromPixel(-2, 0));
    }

    @Test
    void shadingDarkensTheDownhillSide() {
        int size = 4;
        int[] colors = new int[size * size];
        int[] heights = new int[size * size];
        java.util.Arrays.fill(colors, 0x00AAAAAA);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                heights[z * size + x] = 64 + x * x; // concave slope: steeper towards the east
            }
        }
        int[] shaded = TileRaster.shade(colors, heights, size);
        int west = shaded[0] & 0xFFFFFF;
        int east = shaded[size - 1] & 0xFFFFFF;
        assertNotEquals(west, east, "a slope must produce different brightness");
        assertTrue(luma(east) > luma(west), "higher ground should catch the light");
        assertEquals(0xFF, (shaded[0] >>> 24));
    }

    @Test
    void waterGetsBlueTint() {
        int size = 2;
        int[] colors = {0x00505050, 0x00505050, 0x00505050, 0x00505050};
        int[] heights = {62, 62, 62, 62};
        int[] depths = {6, 6, 6, 6};
        int[] dry = TileRaster.shade(colors, heights, size);
        int[] wet = TileRaster.shade(colors, heights, depths, size);
        int dryBlue = dry[0] & 0xFF;
        int wetBlue = wet[0] & 0xFF;
        assertTrue(wetBlue > dryBlue, "deep water should be bluer");
    }

    private static int luma(int rgb) {
        return ((rgb >> 16) & 0xFF) * 3 + ((rgb >> 8) & 0xFF) * 6 + (rgb & 0xFF);
    }

    @Test
    void collageIsSquareAndJpegIsSmall() throws IOException {
        BufferedImage a = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        BufferedImage collage = ImageCollage.collage(List.of(a, a, a, a), 256);
        assertEquals(512, collage.getWidth());
        assertEquals(512, collage.getHeight());
        byte[] jpeg = ImageCollage.toJpeg(collage, 0.7f);
        assertTrue(jpeg.length > 100, "jpeg must not be empty");
        assertTrue(jpeg.length < 200_000, "collage should stay small, was " + jpeg.length);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertEquals(512, decoded.getWidth());
    }

    @Test
    void scaleKeepsAspectRatioAndLetterboxes() {
        BufferedImage wide = new BufferedImage(800, 200, BufferedImage.TYPE_INT_RGB);
        int[] white = new int[800 * 200];
        java.util.Arrays.fill(white, 0xFFFFFFFF);
        wide.setRGB(0, 0, 800, 200, white, 0, 800);
        BufferedImage scaled = ImageCollage.scaleTo(wide, 128, 128);
        assertEquals(128, scaled.getWidth(), "canvas fills the requested box");
        assertEquals(128, scaled.getHeight());
        assertEquals(0xFF000000, scaled.getRGB(64, 0), "top bar is black letterbox");
        assertEquals(0xFFFFFFFF, scaled.getRGB(64, 64), "content keeps its aspect ratio, not stretched");
    }
}
