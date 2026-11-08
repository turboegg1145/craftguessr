package com.craftguessr.client.screen;

import com.craftguessr.client.ClientImages;
import com.craftguessr.client.ClientState;
import com.craftguessr.map.TileGrid;
import com.craftguessr.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Pannable, zoomable north-up map canvas fed by server-side tile PNGs. */
public final class MapView {

    private static final double MIN_SCALE = 1.0 / 16.0;
    private static final double MAX_SCALE = 8.0;
    private static final int MAX_VISIBLE_TILES = 512;

    public int x;
    public int y;
    public int width;
    public int height;

    public String dimension = ClientState.FALLBACK_DIMENSION;
    public int layer;
    public double centerX;
    public double centerZ;
    public double scale = 1.0;

    public boolean hasPin;
    public double pinX;
    public double pinZ;

    public final List<Marker> markers = new ArrayList<>();

    public record Marker(double x, double z, int color, String label) {
    }

    public MapView(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public void panPixels(double dx, double dy) {
        centerX -= dx / scale;
        centerZ -= dy / scale;
        clampCenter();
    }

    public void zoomAt(double mouseX, double mouseY, double factor) {
        double worldMouseX = screenToWorldX(mouseX);
        double worldMouseZ = screenToWorldY(mouseY);
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
        centerX = worldMouseX - (mouseX - (x + width / 2.0)) / scale;
        centerZ = worldMouseZ - (mouseY - (y + height / 2.0)) / scale;
        clampCenter();
    }

    public void clampCenter() {
        centerX = Math.max(-30_000_000.0, Math.min(30_000_000.0, centerX));
        centerZ = Math.max(-30_000_000.0, Math.min(30_000_000.0, centerZ));
    }

    public double screenToWorldX(double sx) {
        return centerX + (sx - (x + width / 2.0)) / scale;
    }

    public double screenToWorldY(double sy) {
        return centerZ + (sy - (y + height / 2.0)) / scale;
    }

    public double worldToScreenX(double wx) {
        return x + width / 2.0 + (wx - centerX) * scale;
    }

    public double worldToScreenY(double wz) {
        return y + height / 2.0 + (wz - centerZ) * scale;
    }

    public int pinBlockX() {
        return (int) Math.floor(pinX);
    }

    public int pinBlockZ() {
        return (int) Math.floor(pinZ);
    }

    public void draw(GuiGraphicsExtractor g, Font font) {
        g.fill(x, y, x + width, y + height, 0xF20A1117);
        g.enableScissor(x, y, x + width, y + height);
        drawTiles(g);
        drawGrid(g);
        for (Marker marker : markers) {
            drawDiamond(g, marker.x(), marker.z(), 3, marker.color());
        }
        if (hasPin) {
            drawDiamond(g, pinX, pinZ, 5, 0xFFFFD54F);
        }
        g.disableScissor();
        g.outline(x, y, width, height, 0x66FFFFFF);
        String coords = "X " + (hasPin ? pinBlockX() : (int) Math.floor(centerX))
                + "   Z " + (hasPin ? pinBlockZ() : (int) Math.floor(centerZ));
        g.text(font, coords, x + 5, y + height - 12, 0xFFDDE6EE);
    }

    private void drawTiles(GuiGraphicsExtractor g) {
        int lod = TileGrid.lodForScale(scale);
        int span = TileGrid.blocksPerTile(lod);
        double tilePx = span * scale;
        int minTileX = Math.floorDiv((int) Math.floor(screenToWorldX(x)), span);
        int maxTileX = Math.floorDiv((int) Math.floor(screenToWorldX(x + width)), span);
        int minTileZ = Math.floorDiv((int) Math.floor(screenToWorldY(y)), span);
        int maxTileZ = Math.floorDiv((int) Math.floor(screenToWorldY(y + height)), span);
        long count = (long) (maxTileX - minTileX + 1) * (maxTileZ - minTileZ + 1);
        boolean tooMany = count > MAX_VISIBLE_TILES;
        for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
            for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
                double sx = worldToScreenX((double) tileX * span);
                double sy = worldToScreenY((double) tileZ * span);
                if (tooMany) {
                    g.fill((int) sx, (int) sy, (int) sx + 2, (int) sy + 2, 0x33FFFFFF);
                    continue;
                }
                String key = ClientImages.tileKey(dimension, lod, tileX, tileZ, layer);
                Identifier texture = ClientImages.get(key);
                if (texture == null) {
                    g.fill((int) sx, (int) sy, (int) (sx + tilePx), (int) (sy + tilePx), 0x99131C24);
                    if (ClientState.get().shouldRequestTile(key)) {
                        ClientPlayNetworking.send(new Payloads.TileRequestC2S(
                                dimension, lod, tileX, tileZ, layer));
                    }
                    continue;
                }
                g.pose().pushMatrix();
                g.pose().translate((float) sx, (float) sy);
                g.pose().scale((float) (tilePx / TileGrid.TILE_SIZE), (float) (tilePx / TileGrid.TILE_SIZE));
                g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0F, 0.0F,
                        TileGrid.TILE_SIZE, TileGrid.TILE_SIZE, TileGrid.TILE_SIZE, TileGrid.TILE_SIZE);
                g.pose().popMatrix();
            }
        }
    }

    private void drawGrid(GuiGraphicsExtractor g) {
        double step = 512.0;
        while (step * scale < 56.0) {
            step *= 2.0;
        }
        double firstX = Math.ceil(screenToWorldX(x) / step) * step;
        for (double wx = firstX; worldToScreenX(wx) < x + width; wx += step) {
            int sx = (int) Math.round(worldToScreenX(wx));
            g.fill(sx, y, sx + 1, y + height, 0x22FFFFFF);
        }
        double firstZ = Math.ceil(screenToWorldY(y) / step) * step;
        for (double wz = firstZ; worldToScreenY(wz) < y + height; wz += step) {
            int sy = (int) Math.round(worldToScreenY(wz));
            g.fill(x, sy, x + width, sy + 1, 0x22FFFFFF);
        }
    }

    private void drawDiamond(GuiGraphicsExtractor g, double wx, double wz, int radius, int color) {
        int sx = (int) Math.round(worldToScreenX(wx));
        int sy = (int) Math.round(worldToScreenY(wz));
        g.fill(sx - radius, sy - radius, sx + radius + 1, sy + radius + 1, color);
        g.outline(sx - radius - 1, sy - radius - 1, radius * 2 + 3, radius * 2 + 3, 0xCC000000);
    }
}
