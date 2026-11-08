package com.craftguessr.client.mixin;

import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Compatibility shim for software OpenGL (Mesa llvmpipe on Xvfb).
 *
 * <p>26.3 asks SDL for an sRGB-capable framebuffer. Xvfb's llvmpipe exports GLX
 * configs that do not advertise {@code GLX_FRAMEBUFFER_SRGB_CAPABLE_ARB} for the
 * requested visual, so window creation dies with "Couldn't find matching GLX
 * visual". Dropping only that one hint when {@code -Dcraftguessr.softwareGL} is
 * set lets the dev client render headlessly. Harmless on real GPUs.
 */
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlBackend")
public abstract class GlBackendMixin {

    @Redirect(
            method = "createWindow",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_GL_SetAttribute(II)Z"))
    private boolean craftguessr$relaxSrgb(int attribute, int value) {
        if (attribute == SDLVideo.SDL_GL_FRAMEBUFFER_SRGB_CAPABLE
                && System.getProperty("craftguessr.softwareGL") != null) {
            return true;
        }
        return SDLVideo.SDL_GL_SetAttribute(attribute, value);
    }
}
