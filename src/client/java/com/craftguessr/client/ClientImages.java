package com.craftguessr.client;

import com.craftguessr.map.TileGrid;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns PNG/JPEG bytes shipped by the server into registered GUI textures. Render thread only.
 */
public final class ClientImages {

    private static final int MAX_TEXTURES = 768;
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();

    private ClientImages() {
    }

    public static String tileKey(String dimension, int lod, int tileX, int tileZ) {
        return "tile/" + TileGrid.tileKey(dimension, lod, tileX, tileZ);
    }

    public static String thumbKey(String spotId) {
        return "thumb/" + spotId;
    }

    /** Registers (once) the texture for {@code key} from its bytes, or null while unavailable. */
    public static Identifier get(String key) {
        Identifier existing = TEXTURES.get(key);
        if (existing != null) {
            return existing;
        }
        if (FAILED.contains(key)) {
            return null;
        }
        byte[] bytes = bytesOf(key);
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            if (TEXTURES.size() >= MAX_TEXTURES) {
                releaseAll();
            }
            Identifier identifier = Identifier.fromNamespaceAndPath("craftguessr", pathOf(key));
            TextureManager manager = Minecraft.getInstance().getTextureManager();
            manager.register(identifier, new DynamicTexture(() -> "craftguessr " + key, image));
            TEXTURES.put(key, identifier);
            return identifier;
        } catch (IOException | RuntimeException e) {
            FAILED.add(key);
            return null;
        }
    }

    private static byte[] bytesOf(String key) {
        ClientState state = ClientState.get();
        if (key.startsWith("tile/")) {
            return state.tiles().get(key.substring("tile/".length()));
        }
        if (key.startsWith("thumb/")) {
            return state.thumbs().get(key.substring("thumb/".length()));
        }
        return null;
    }

    private static String pathOf(String key) {
        String cleaned = key.toLowerCase(Locale.ROOT).replace(':', '/');
        StringBuilder builder = new StringBuilder("dynamic/");
        for (char c : cleaned.toCharArray()) {
            builder.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.' || c == '_' || c == '-'
                    ? c : '_');
        }
        return builder.toString();
    }

    private static void releaseAll() {
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        for (Identifier id : TEXTURES.values()) {
            manager.release(id);
        }
        TEXTURES.clear();
        FAILED.clear();
    }
}
