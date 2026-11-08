package com.craftguessr.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.awt.image.BufferedImage;

/**
 * A tiny panorama raycaster: renders what a player standing at a spot would see, straight from block
 * state colours. No GPU, no chunk meshes, no client — works on a dedicated server inside an async
 * scan task. Four of these (N/E/S/W) make up the collage the AI director judges.
 */
public final class SurfaceView {

    public static final float FOV_DEGREES = 82.0F;
    private static final double EYE_HEIGHT = 1.62;
    private static final double MAX_DISTANCE = 72.0;
    private static final double STEP = 0.45;
    private static final int HORIZON = 0xBFD4E8;
    private static final int ZENITH = 0x6F9ED8;

    private SurfaceView() {
    }

    /**
     * @param yawDegrees the centre of the view; MC convention (0 = +Z, 90 = -X)
     * @param size       square resolution, e.g. 64 or 96
     */
    public static BufferedImage render(BlockGetter level, int x, int y, int z, float yawDegrees, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[size * size];
        double eyeY = y + EYE_HEIGHT;
        for (int py = 0; py < size; py++) {
            double v = Math.toRadians(28.0 - 56.0 * py / (size - 1));
            double dy = -Math.sin(v);
            int sky = skyColour(py, size);
            for (int px = 0; px < size; px++) {
                double a = Math.toRadians(yawDegrees - FOV_DEGREES / 2.0 + FOV_DEGREES * px / (size - 1));
                double dx = -Math.sin(a);
                double dz = Math.cos(a);
                int colour = march(level, x + 0.5, eyeY, z + 0.5, dx, dy, dz, sky);
                pixels[py * size + px] = colour;
            }
        }
        image.setRGB(0, 0, size, size, pixels, 0, size);
        return image;
    }

    private static int march(BlockGetter level, double ox, double oy, double oz,
                             double dx, double dy, double dz, int sky) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double distance = 0.0;
        while (distance < MAX_DISTANCE) {
            distance += STEP;
            double sx = ox + dx * distance;
            double sy = oy + dy * distance;
            double sz = oz + dz * distance;
            pos.set((int) Math.floor(sx), (int) Math.floor(sy), (int) Math.floor(sz));
            if (pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            boolean liquid = !state.getFluidState().isEmpty();
            if (state.isAir() && !liquid) {
                continue;
            }
            int base;
            if (liquid) {
                base = 0x3F76E4;
            } else {
                MapColor mapColor = state.getMapColor(level, pos);
                base = mapColor == MapColor.NONE ? 0x7F7F7F : mapColor.calculateARGBColor(MapColor.Brightness.NORMAL);
            }
            double fade = Math.min(0.55, distance / MAX_DISTANCE * 0.6);
            int rgb = blend(base & 0xFFFFFF, sky & 0xFFFFFF, fade);
            double light = 0.82 + Math.max(-0.18, Math.min(0.18, -dy * 0.25));
            return shade(rgb, light);
        }
        return sky;
    }

    private static int skyColour(int py, int size) {
        double t = (double) py / Math.max(1, size - 1);
        return blend(ZENITH, HORIZON, t);
    }

    private static int blend(int a, int b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = (int) Math.round(ar + (br - ar) * t);
        int g = (int) Math.round(ag + (bg - ag) * t);
        int bl = (int) Math.round(ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int shade(int rgb, double light) {
        int r = clamp((int) Math.round(((rgb >> 16) & 0xFF) * light));
        int g = clamp((int) Math.round(((rgb >> 8) & 0xFF) * light));
        int b = clamp((int) Math.round((rgb & 0xFF) * light));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(255, value);
    }
}
