package com.craftguessr.client;

import com.craftguessr.CraftGuessr;
import com.craftguessr.map.ImageCollage;
import com.craftguessr.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-side quad capture: hides the HUD, faces four directions around the player, grabs one
 * screenshot per direction and stitches a 2x2 collage which becomes the spot thumbnail.
 */
public final class CaptureSession {

    private static final int VIEW_SIZE = 512;
    private static final int COLLAGE_SIZE = VIEW_SIZE * 2;
    private static final float[] OFFSETS = {180.0F, -90.0F, 0.0F, 90.0F};

    private static CaptureSession active;

    private final String title;
    private final String difficulty;
    private final float baseYaw;
    private final float pitch;
    private final boolean hudWasHidden;
    private final List<int[]> views = new ArrayList<>(4);

    private int index;
    private int waitTicks;
    private boolean requesting;
    private boolean finished;

    private CaptureSession(String title, String difficulty, float baseYaw, float pitch, boolean hudWasHidden) {
        this.title = title;
        this.difficulty = difficulty;
        this.baseYaw = baseYaw;
        this.pitch = pitch;
        this.hudWasHidden = hudWasHidden;
    }

    public static boolean running() {
        return active != null;
    }

    public static void start(String title, String difficulty) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || active != null) {
            return;
        }
        boolean hidden = minecraft.gui.hud.isHidden();
        if (!hidden) {
            minecraft.gui.hud.toggle();
        }
        active = new CaptureSession(title, difficulty, minecraft.player.getYRot(),
                Math.max(-45.0F, Math.min(45.0F, minecraft.player.getXRot())), hidden);
        active.waitTicks = 10;
        minecraft.gui.setScreen(null);
    }

    public static void tick() {
        CaptureSession session = active;
        if (session == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            session.abort(minecraft, "player gone");
            return;
        }
        if (session.waitTicks > 0) {
            session.waitTicks--;
            return;
        }
        if (session.requesting) {
            return;
        }
        if (session.index >= OFFSETS.length) {
            session.finish(minecraft);
            return;
        }
        float yaw = session.baseYaw + OFFSETS[session.index];
        minecraft.player.setYRot(yaw);
        minecraft.player.setXRot(session.pitch);
        session.requesting = true;
        session.waitTicks = 3;
        session.scheduleShot(minecraft);
    }

    private void scheduleShot(Minecraft minecraft) {
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        Screenshot.takeScreenshot(target, image -> {
            try {
                views.add(cropSquare(image));
            } catch (RuntimeException e) {
                CraftGuessr.LOGGER.warn("Capture view {} failed: {}", index, e.toString());
            } finally {
                image.close();
                index++;
                requesting = false;
            }
        });
    }

    /** Crops the central square and resamples it to {@link #VIEW_SIZE}. */
    private static int[] cropSquare(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int side = Math.min(width, height);
        int offsetX = (width - side) / 2;
        int offsetY = (height - side) / 2;
        int[] pixels = image.getPixels();
        int[] square = new int[side * side];
        for (int y = 0; y < side; y++) {
            int srcRow = (offsetY + y) * width + offsetX;
            System.arraycopy(pixels, srcRow, square, y * side, side);
        }
        return ImageCollage.scalePixels(square, side, side, VIEW_SIZE, VIEW_SIZE);
    }

    private void finish(Minecraft minecraft) {
        if (finished) {
            return;
        }
        finished = true;
        try {
            BufferedImage collage = new BufferedImage(COLLAGE_SIZE, COLLAGE_SIZE, BufferedImage.TYPE_INT_RGB);
            for (int i = 0; i < views.size() && i < 4; i++) {
                int qx = (i % 2) * VIEW_SIZE;
                int qy = (i / 2) * VIEW_SIZE;
                int[] view = views.get(i);
                for (int y = 0; y < VIEW_SIZE; y++) {
                    collage.setRGB(qx, qy + y, VIEW_SIZE, 1, view, y * VIEW_SIZE, VIEW_SIZE);
                }
            }
            byte[] jpeg = ImageCollage.toJpeg(collage, 0.85F);
            if (minecraft.player != null) {
                var position = minecraft.player.position();
                ClientPlayNetworking.send(new Payloads.SubmitSpotC2S(ClientState.get().dimension(),
                        (int) Math.floor(position.x), (int) Math.floor(position.y),
                        (int) Math.floor(position.z), baseYaw, pitch, title, difficulty, jpeg));
            }
            message(minecraft, "craftguessr.capture.sent");
        } catch (IOException | RuntimeException e) {
            CraftGuessr.LOGGER.warn("Capture collage failed: {}", e.toString());
            message(minecraft, "craftguessr.capture.failed");
        } finally {
            end(minecraft);
        }
    }

    private void abort(Minecraft minecraft, String reason) {
        CraftGuessr.LOGGER.warn("Capture aborted: {}", reason);
        message(minecraft, "craftguessr.capture.failed");
        end(minecraft);
    }

    private void end(Minecraft minecraft) {
        if (minecraft.player != null) {
            minecraft.player.setYRot(baseYaw);
            minecraft.player.setXRot(pitch);
        }
        if (!hudWasHidden) {
            minecraft.gui.hud.toggle();
        }
        active = null;
    }

    private static void message(Minecraft minecraft, String key) {
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.translatable(key));
        }
    }
}
