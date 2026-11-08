# Minecraft 26.3 client GUI API — verified against the deobf jars

Source of truth: `javap -p` (plus `javap -c` where noted "bytecode-verified") on
`minecraft-common-deobf-26.3.jar` + `minecraft-clientonly-deobf-26.3.jar` inside container `geoguessr-dev`.
`$` in a name means a nested class. javap exposes **no parameter names** (the jars carry no
LocalVariableTable), so only types and order are guaranteed; names in prose are inferred from bytecode
where marked. `Error: class not found` = removed class.

---

## 1. `net.minecraft.client.gui.screens.Screen`

VERIFIED SIGNATURES

```java
public abstract class net.minecraft.client.gui.screens.Screen
    extends net.minecraft.client.gui.components.events.AbstractContainerEventHandler
    implements net.minecraft.client.gui.components.Renderable {
  protected final net.minecraft.network.chat.Component title;
  protected final net.minecraft.client.Minecraft minecraft;
  public int width;
  public int height;
  protected final net.minecraft.client.gui.Font font;
  protected final java.util.concurrent.Executor screenExecutor;
  public static final net.minecraft.resources.Identifier MENU_BACKGROUND;
  protected net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component);
  protected net.minecraft.client.gui.screens.Screen(net.minecraft.client.Minecraft, net.minecraft.client.gui.Font, net.minecraft.network.chat.Component);
  public final void extractRenderStateWithTooltipAndSubtitles(net.minecraft.client.gui.GuiGraphicsExtractor, int, int, float);
  public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor, int, int, float);
  public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor, int, int, float);
  protected void extractBlurredBackground(net.minecraft.client.gui.GuiGraphicsExtractor);
  protected void extractMenuBackground(net.minecraft.client.gui.GuiGraphicsExtractor);
  public void extractTransparentBackground(net.minecraft.client.gui.GuiGraphicsExtractor);
  public final void init(int, int);
  protected void init();
  public void tick();
  public void onClose();
  public void removed();
  public void added();
  public boolean keyPressed(net.minecraft.client.input.KeyEvent);
  public boolean isPauseScreen();
  public boolean isMouseOver(double, double);
  public net.minecraft.client.gui.Font getFont();
  public void resize(int, int);
  public net.minecraft.client.gui.navigation.ScreenRectangle getRectangle();
  protected <T extends net.minecraft.client.gui.components.events.GuiEventListener & net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.narration.NarratableEntry> T addRenderableWidget(T);
  protected <T extends net.minecraft.client.gui.components.Renderable> T addRenderableOnly(T);
  protected <T extends net.minecraft.client.gui.components.events.GuiEventListener & net.minecraft.client.gui.narration.NarratableEntry> T addWidget(T);
  protected void removeWidget(net.minecraft.client.gui.components.events.GuiEventListener);
  protected void clearWidgets();
  protected void rebuildWidgets();
}
```

Handlers that `Screen` does **not** declare (inherited defaults from `ContainerEventHandler`):
`mouseClicked(MouseButtonEvent, boolean)`, `mouseReleased(MouseButtonEvent)`,
`mouseDragged(MouseButtonEvent, double, double)`, `mouseMoved(double, double)`,
`mouseScrolled(double, double, double, double)`, `charTyped(CharacterEvent)`, `keyReleased(KeyEvent)`.

VERIFIED ABSENT from the `Screen` dump: `render`, `renderBackground`, `drawString`, `mouseClicked`,
`mouseDragged`, `mouseReleased`, `mouseScrolled`, `charTyped`.

- There is no `render`. Per-frame drawing is `extractRenderState(GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick)`; `extractRenderStateWithTooltipAndSubtitles` is `final` (engine entry point — never call/override it).
- `init(int,int)` is `final`; override `protected void init()` and register widgets with `addRenderableWidget(...)`. `width`/`height`/`font` are already set when `init()` runs.
- Mouse/char events arrive as the new record types (`MouseButtonEvent`, `KeyEvent`, `CharacterEvent`) — the old `(double,double,int)` signatures no longer exist. `Screen` only overrides `keyPressed`.
- Minecraft instance is the field `this.minecraft` (protected final); `Minecraft.getInstance()` also exists but is not needed.

---

## 2. `net.minecraft.client.gui.GuiGraphicsExtractor`

`net.minecraft.client.gui.GuiGraphics` — **NOT FOUND in jar** (class not found).

VERIFIED SIGNATURES

```java
public class net.minecraft.client.gui.GuiGraphicsExtractor {
  public net.minecraft.client.gui.GuiGraphicsExtractor(net.minecraft.client.Minecraft, net.minecraft.client.renderer.state.gui.GuiRenderState, int, int);
  public int guiWidth();
  public int guiHeight();
  public org.joml.Matrix3x2fStack pose();
  public void enableScissor(int, int, int, int);
  public void disableScissor();
  public boolean containsPointInScissor(int, int);
  public void fill(int, int, int, int, int);
  public void fill(com.mojang.renderpearl.api.pipeline.RenderPipeline, int, int, int, int, int);
  public void fill(com.mojang.renderpearl.api.pipeline.RenderPipeline, net.minecraft.client.gui.render.TextureSetup, int, int, int, int);
  public void fillGradient(int, int, int, int, int, int);
  public void outline(int, int, int, int, int);
  public void horizontalLine(int, int, int, int);
  public void verticalLine(int, int, int, int);
  public void textHighlight(int, int, int, int, boolean);
  public void text(net.minecraft.client.gui.Font, java.lang.String, int, int, int);
  public void text(net.minecraft.client.gui.Font, java.lang.String, int, int, int, boolean);
  public void centeredText(net.minecraft.client.gui.Font, java.lang.String, int, int, int);
  public int textWithWordWrap(net.minecraft.client.gui.Font, net.minecraft.network.chat.FormattedText, int, int, int, int);
  public void textWithBackdrop(net.minecraft.client.gui.Font, net.minecraft.network.chat.Component, int, int, int, int);
  public void blit(com.mojang.renderpearl.api.pipeline.RenderPipeline, net.minecraft.resources.Identifier, int, int, float, float, int, int, int, int);
  public void blit(com.mojang.renderpearl.api.pipeline.RenderPipeline, net.minecraft.resources.Identifier, int, int, float, float, int, int, int, int, int);
  public void blit(com.mojang.renderpearl.api.pipeline.RenderPipeline, net.minecraft.resources.Identifier, int, int, float, float, int, int, int, int, int, int);
  public void blit(com.mojang.renderpearl.api.pipeline.RenderPipeline, net.minecraft.resources.Identifier, int, int, float, float, int, int, int, int, int, int, int);
  public void blit(net.minecraft.resources.Identifier, int, int, int, int, float, float, float, float);
  public void blit(com.mojang.renderpearl.api.textures.GpuTextureView, com.mojang.renderpearl.api.textures.GpuSampler, int, int, int, int, float, float, float, float);
  public void blitSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline, net.minecraft.resources.Identifier, int, int, int, int);
  public void blitSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline, net.minecraft.resources.Identifier, int, int, int, int, int);
  public void blitSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline, net.minecraft.client.renderer.texture.TextureAtlasSprite, int, int, int, int);
  public void blitSprite(com.mojang.renderpearl.api.pipeline.RenderPipeline, net.minecraft.client.renderer.texture.TextureAtlasSprite, int, int, int, int, int);
  public void setTooltipForNextFrame(/* several overloads: ItemStack/Component/List<FormattedCharSequence> + int,int */);
  public net.minecraft.client.gui.ActiveTextCollector textRenderer();
}
```

Argument order, bytecode-verified (`javap -c`):

| call | chained body |
|---|---|
| `fill(a,b,c,d,e)` | `fill(RenderPipelines.GUI, a,b,c,d,e)` |
| `fill(pipeline,a,b,c,d,e)` | normalizes so first/second ints are x1<x2 and third/fourth are y1<y2 ⇒ `(x1,y1,x2,y2,color)` |
| `fillGradient(a,b,c,d,e,f)` | `innerFill(GUI, TextureSetup.noTexture(), a,b,c,d, e, Integer(f))` ⇒ `(x1,y1,x2,y2,colorTop,colorBottom)` |
| `outline(a,b,c,d,e)` | draws `fill(a, b, a+c, b+1, e)` etc. ⇒ `(x, y, width, height, color)` |
| `enableScissor(a,b,c,d)` | `new ScreenRectangle(a, b, c-a, d-b)` ⇒ **`(x1,y1,x2,y2)`, not `(x,y,w,h)`** |
| `text(font,s,x,y,c)` | `text(font,s,x,y,c,true)` ⇒ last int is the ARGB color |
| `centeredText(font,s,x,y,c)` | `text(font,s, x - font.width(s)/2, y, c)` |

- `blit` requires a `RenderPipeline` first for every overload except `blit(Identifier, ...)` and `blit(GpuTextureView, GpuSampler, ...)`, which hardcode `RenderPipelines.GUI_TEXTURED`.
- Bytecode-verified blit chains: 10-param = `(pipeline, id, x, y, u, v, w, h, texW, texH)`; 11-param adds a trailing `int color`; 12-param = `(…, srcW, srcH, texW, texH)`; 13-param adds color. The 10-param and 12-param overloads end up passing `-1` (opaque white) as the color. The no-pipeline `blit(Identifier, int,int,int,int, float,float,float,float)` is **corner-based** `(x1,y1,x2,y2, u1,v1,u2,v2)` (it forwards ints 2,4,3,5 to `innerBlit(x1,x2,y1,y2,…)`, which is verified from the private sprite blit passing `(x, x+w, y, y+h)`).
- `enableScissor` is a stack: every `enableScissor` needs a `disableScissor`. 26.3 `enableScissor` takes corners and transforms them by `pose()`.
- No `drawString`/`drawCenteredString`/`renderOutline` remain; the equivalents are `text`/`centeredText`/`outline`.

---

## 3. Widgets: `Button`, `AbstractWidget`, `EditBox`, `StringWidget`, `Checkbox`, `CycleButton`

VERIFIED SIGNATURES

```java
public abstract class net.minecraft.client.gui.components.Button extends net.minecraft.client.gui.components.AbstractButton {
  public static final int SMALL_WIDTH;
  public static final int DEFAULT_WIDTH;
  public static final int BIG_WIDTH;
  public static final int DEFAULT_HEIGHT;
  public static final int DEFAULT_SPACING;
  public static net.minecraft.client.gui.components.Button$Builder builder(net.minecraft.network.chat.Component, net.minecraft.client.gui.components.Button$OnPress);
  public void onPress(net.minecraft.client.input.InputWithModifiers);
}
public class net.minecraft.client.gui.components.Button$Builder {
  public net.minecraft.client.gui.components.Button$Builder pos(int, int);
  public net.minecraft.client.gui.components.Button$Builder width(int);
  public net.minecraft.client.gui.components.Button$Builder size(int, int);
  public net.minecraft.client.gui.components.Button$Builder bounds(int, int, int, int);
  public net.minecraft.client.gui.components.Button$Builder tooltip(net.minecraft.client.gui.components.Tooltip);
  public net.minecraft.client.gui.components.Button build();
}

public abstract class net.minecraft.client.gui.components.AbstractWidget implements net.minecraft.client.gui.layouts.LayoutElement, net.minecraft.client.gui.components.Renderable, net.minecraft.client.gui.components.events.GuiEventListener, net.minecraft.client.gui.narration.NarratableEntry {
  protected int width;
  protected int height;
  protected net.minecraft.network.chat.Component message;
  protected boolean isHovered;
  public boolean active;
  public boolean visible;
  protected float alpha;
  public final void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor, int, int, float);
  protected abstract void extractWidgetRenderState(net.minecraft.client.gui.GuiGraphicsExtractor, int, int, float);
  protected void extractTooltipForNextRenderPass(net.minecraft.client.gui.GuiGraphicsExtractor, int, int);
  public void onClick(net.minecraft.client.input.MouseButtonEvent, boolean);
  public void onRelease(net.minecraft.client.input.MouseButtonEvent);
  public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent, boolean);
  public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent);
  public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent, double, double);
  public void setX(int); public int getX(); public void setY(int); public int getY();
  public void setSize(int, int); public void setRectangle(int, int, int, int);
  public net.minecraft.client.gui.navigation.ScreenRectangle getRectangle();
}

public class net.minecraft.client.gui.components.EditBox extends net.minecraft.client.gui.components.AbstractWidget {
  public net.minecraft.client.gui.components.EditBox(net.minecraft.client.gui.Font, net.minecraft.network.chat.Component);
  public net.minecraft.client.gui.components.EditBox(net.minecraft.client.gui.Font, int, int, net.minecraft.network.chat.Component);
  public net.minecraft.client.gui.components.EditBox(net.minecraft.client.gui.Font, int, int, int, int, net.minecraft.network.chat.Component);
  public net.minecraft.client.gui.components.EditBox(net.minecraft.client.gui.Font, int, int, int, int, net.minecraft.client.gui.components.EditBox, net.minecraft.network.chat.Component);
  public void setResponder(java.util.function.Consumer<java.lang.String>);
  public void setValue(java.lang.String);
  public java.lang.String getValue();
  public void setMaxLength(int);
  public void setBordered(boolean);
  public void setTextColor(int);
  public boolean keyPressed(net.minecraft.client.input.KeyEvent);
  public boolean charTyped(net.minecraft.client.input.CharacterEvent);
  public void extractWidgetRenderState(net.minecraft.client.gui.GuiGraphicsExtractor, int, int, float);
}

public class net.minecraft.client.gui.components.StringWidget extends net.minecraft.client.gui.components.AbstractStringWidget {
  public net.minecraft.client.gui.components.StringWidget(net.minecraft.network.chat.Component, net.minecraft.client.gui.Font);
  public net.minecraft.client.gui.components.StringWidget(int, int, net.minecraft.network.chat.Component, net.minecraft.client.gui.Font);
  public net.minecraft.client.gui.components.StringWidget(int, int, int, int, net.minecraft.network.chat.Component, net.minecraft.client.gui.Font);
  public net.minecraft.client.gui.components.StringWidget setMaxWidth(int);
}

public class net.minecraft.client.gui.components.Checkbox extends net.minecraft.client.gui.components.AbstractButton {
  public static net.minecraft.client.gui.components.Checkbox$Builder builder(net.minecraft.network.chat.Component, net.minecraft.client.gui.Font);
  public static int getBoxSize(net.minecraft.client.gui.Font);
  public boolean selected();
}
public class net.minecraft.client.gui.components.Checkbox$Builder {
  public net.minecraft.client.gui.components.Checkbox$Builder pos(int, int);
  public net.minecraft.client.gui.components.Checkbox$Builder onValueChange(net.minecraft.client.gui.components.Checkbox$OnValueChange);
  public net.minecraft.client.gui.components.Checkbox$Builder selected(boolean);
  public net.minecraft.client.gui.components.Checkbox$Builder selected(net.minecraft.client.OptionInstance<java.lang.Boolean>);
  public net.minecraft.client.gui.components.Checkbox build();
}

public class net.minecraft.client.gui.components.CycleButton<T> extends net.minecraft.client.gui.components.AbstractButton implements net.minecraft.client.gui.components.ResettableOptionWidget {
  public static <T> net.minecraft.client.gui.components.CycleButton$Builder<T> builder(java.util.function.Function<T, net.minecraft.network.chat.Component>, T);
  public static <T> net.minecraft.client.gui.components.CycleButton$Builder<T> builder(java.util.function.Function<T, net.minecraft.network.chat.Component>, java.util.function.Supplier<T>);
  public static net.minecraft.client.gui.components.CycleButton$Builder<java.lang.Boolean> onOffBuilder(boolean);
  public void setValue(T);
  public T getValue();
  public boolean mouseScrolled(double, double, double, double);
}
```

- `Button.builder(Component, Button$OnPress).bounds(x, y, w, h).build()` is intact; `bounds` is the builder method (there is no `dimensions()`).
- `AbstractWidget.extractRenderState` is `final`; concrete widgets implement `protected abstract void extractWidgetRenderState(GGE, int, int, float)`. `Button` itself declares no draw code — rendering lives in `AbstractButton` (`extractContents(GGE,int,int,float)` is the override point, seen on `CycleButton`/`Checkbox`).
- `EditBox` ctor for a text field: `(font, x, y, width, height, Component)`; callbacks are `setResponder(Consumer<String>)`, poll with `getValue()`.
- `AbstractWidget` still uses `active`/`visible`/`alpha` **public/protected fields**; `setMessage`, `setWidth`, `setHeight`, `setX`, `setY` remain.

---

## 4. `net.minecraft.client.Minecraft`

VERIFIED SIGNATURES

```java
public class net.minecraft.client.Minecraft extends net.minecraft.util.thread.ReentrantBlockableEventLoop<java.lang.Runnable> implements com.mojang.blaze3d.platform.WindowEventHandler {
  public final net.minecraft.client.gui.Font font;
  public final net.minecraft.client.renderer.GameRenderer gameRenderer;
  public final net.minecraft.client.gui.Gui gui;
  public final net.minecraft.client.Options options;
  public final net.minecraft.client.MouseHandler mouseHandler;
  public final net.minecraft.client.KeyboardHandler keyboardHandler;
  public net.minecraft.client.multiplayer.ClientLevel level;
  public net.minecraft.client.player.LocalPlayer player;
  public net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode;
  public static net.minecraft.client.Minecraft getInstance();
  public boolean hasShiftDown();
  public boolean hasControlDown();
  public boolean hasAltDown();
  public net.minecraft.client.renderer.texture.TextureManager getTextureManager();
  public com.mojang.blaze3d.platform.Window getWindow();
  public void onTextInputFocusChange(net.minecraft.client.gui.components.events.GuiEventListener, boolean);
  public net.minecraft.client.gui.components.DebugScreenOverlay getDebugOverlay();
  public void setScreenAndShow(net.minecraft.client.gui.screens.Screen);
  public com.mojang.renderpearl.api.device.GpuSurface windowSurface();
  // inherited from net.minecraft.util.thread.BlockableEventLoop:
  // public void execute(java.lang.Runnable);  public boolean isSameThread();
}
```

VERIFIED ABSENT from `Minecraft`: a **`screen` field**, any **`setScreen(...)`**, `getMainRenderTarget()`, `getMainFramebuffer()`.

- The current screen lives on `Minecraft.gui`: `minecraft.gui.setScreen(screen)` / `minecraft.gui.screen()`. There is no `Minecraft.setScreen`.
- `Minecraft.gameRenderer` is a public final field (`net.minecraft.client.renderer.GameRenderer`); the render target comes from `gameRenderer.mainRenderTarget()`.
- Main-thread scheduling still goes through the inherited `execute(Runnable)` (Minecraft *is* a `BlockableEventLoop<Runnable>`/`Executor`).
- `onTextInputFocusChange(GuiEventListener, boolean)` is the hook the engine uses for IME/soft-keyboard focus; call it if a custom screen hosts an `EditBox`.

---

## 5. `Gui` / `Hud`

VERIFIED SIGNATURES

```java
public class net.minecraft.client.gui.Gui {
  public final net.minecraft.client.gui.Hud hud;
  public void tick();
  public void update();
  public void extractRenderState(net.minecraft.client.DeltaTracker, boolean, boolean);
  public net.minecraft.client.gui.screens.Screen screen();
  public void setScreen(net.minecraft.client.gui.screens.Screen);
  public net.minecraft.client.gui.screens.Overlay overlay();
  public void setOverlay(net.minecraft.client.gui.screens.Overlay);
  public boolean isPausing();
  public boolean canInterruptScreen();
}

public class net.minecraft.client.gui.Hud {
  public static final net.minecraft.resources.Identifier NAUSEA_LOCATION;
  public float vignetteBrightness;
  public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor, net.minecraft.client.DeltaTracker);
  public void extractDebugOverlay(net.minecraft.client.gui.GuiGraphicsExtractor);
  public void extractDeferredSubtitles();
  public void tick(boolean);
  public void setOverlayMessage(net.minecraft.network.chat.Component, boolean);   // (message, tinted)
  public void setTimes(int, int, int);
  public void setSubtitle(net.minecraft.network.chat.Component);
  public void setTitle(net.minecraft.network.chat.Component);
  public void clearTitles();
  public void setNowPlaying(net.minecraft.network.chat.Component);
  public boolean isHidden();
  public void toggle();
  public int getGuiTicks();
  public net.minecraft.client.gui.Font getFont();
  public net.minecraft.client.gui.components.DebugScreenOverlay getDebugOverlay();
  public net.minecraft.client.gui.components.ChatComponent getChat();
  public net.minecraft.client.gui.components.PlayerTabOverlay getTabList();
  public net.minecraft.client.gui.components.BossHealthOverlay getBossOverlay();
  public void extractSavingIndicator(net.minecraft.client.gui.GuiGraphicsExtractor, net.minecraft.client.DeltaTracker);
}
```

- Screen navigation is `Minecraft.gui.setScreen(...)`, read back with `Minecraft.gui.screen()`. For a HUD-only action, `Minecraft.gui.hud` is a public field.
- HUD overlay text = `hud.setOverlayMessage(Component, boolean)` (the second arg is the tinted/"actionbar" flag).
- The F3 overlay is `net.minecraft.client.gui.components.DebugScreenOverlay` (note: **not** `.components.debug.DebugScreenOverlay`, which is NOT FOUND); reach it via `Minecraft.getDebugOverlay()` or `hud.getDebugOverlay()`, and it is drawn by `Hud.extractDebugOverlay(GGE)`.
- Everything HUD-ish now extracts through `GuiGraphicsExtractor`; there is no `render(GuiGraphics, DeltaTracker)` / `RenderSystem` path on `Hud`.

---

## 6. Input event records + coordinate helpers

VERIFIED SIGNATURES

```java
public final class net.minecraft.client.input.MouseButtonEvent extends java.lang.Record implements net.minecraft.client.input.InputWithModifiers {
  public net.minecraft.client.input.MouseButtonEvent(double, double, net.minecraft.client.input.MouseButtonInfo);
  public int input();
  public int button();
  public int modifiers();
  public double x();
  public double y();
  public net.minecraft.client.input.MouseButtonInfo buttonInfo();
}
public final class net.minecraft.client.input.MouseButtonInfo extends java.lang.Record implements net.minecraft.client.input.InputWithModifiers {
  public net.minecraft.client.input.MouseButtonInfo(int, int);
  public int button();
  public int modifiers();
}
public final class net.minecraft.client.input.KeyEvent extends java.lang.Record implements net.minecraft.client.input.InputWithModifiers {
  public net.minecraft.client.input.KeyEvent(int, int, int);
  public int input();
  public int shortcutKey();
  public int key();
  public int keycode();
  public int modifiers();
}
public final class net.minecraft.client.input.CharacterEvent extends java.lang.Record {
  public net.minecraft.client.input.CharacterEvent(int);
  public java.lang.String codepointAsString();
  public boolean isAllowedChatCharacter();
  public int codepoint();
}
public interface net.minecraft.client.input.InputWithModifiers {
  public abstract int input();
  public default int shortcutKey();
  public abstract int modifiers();
  public default boolean hasShiftDown();
  public default boolean hasControlDown();
  public default boolean hasAltDown();
  public default boolean isSelection();
  public default boolean isConfirmation();
  public default boolean isEscape();
  public default boolean isLeft(); public default boolean isRight();
  public default boolean isUp();   public default boolean isDown();
  public default boolean isCycleFocus();
  public default boolean isCopy(); public default boolean isPaste(); public default boolean isCut();
  public default boolean isSelectAll();
}
```

- `MouseButtonEvent` carries `x()`/`y()` (double, already GUI-scaled) plus `button()`; `hasShiftDown()`/`hasControlDown()`/`hasAltDown()` are defaults on `InputWithModifiers` — no need for `Minecraft.hasShiftDown()` inside handlers.
- `KeyEvent` has **`key()`, `keycode()`, `modifiers()` only — there is no `scanCode()`** anywhere in the record (NOT FOUND; the ctor is `(int,int,int)`).
- `CharacterEvent` has `codepoint()` (int), not `char`. `charTyped(CharacterEvent)` is the only typing hook; `EditBox` also takes `preeditUpdated(PreeditEvent)` for IME.
- There are **no** coordinate helpers on `Screen` beyond `isMouseOver(double,double)`; layout size comes from the public `width`/`height` fields or `GuiGraphicsExtractor.guiWidth()/guiHeight()`. `Screen.getRectangle()` returns a `net.minecraft.client.gui.navigation.ScreenRectangle`.

---

## 7. `net.minecraft.client.renderer.RenderPipelines`

VERIFIED SIGNATURES

```java
public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI_TEXT;
public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI_TEXT_GRAYSCALE;
public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI;
public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI_INVERT;
public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI_TEXT_HIGHLIGHT;
public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI_TEXTURED;
public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI_TEXTURED_PREMULTIPLIED_ALPHA;
public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI_OPAQUE_TEXTURED_BACKGROUND;
public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI_NAUSEA_OVERLAY;
```

- GUI textures/blits use `RenderPipelines.GUI_TEXTURED` (that is what the pipeline-less `blit` overload itself picks). Solid fills use `RenderPipelines.GUI`; text is `GUI_TEXT`/`GUI_TEXT_GRAYSCALE`.
- The type is `com.mojang.renderpearl.api.pipeline.RenderPipeline`. Constants are public static finals, safe to pass directly to `GuiGraphicsExtractor.blit/fill`.

---

## 8. Textures

VERIFIED SIGNATURES

```java
public class net.minecraft.client.renderer.texture.DynamicTexture extends net.minecraft.client.renderer.texture.AbstractTexture implements net.minecraft.client.renderer.texture.Dumpable {
  public net.minecraft.client.renderer.texture.DynamicTexture(java.util.function.Supplier<java.lang.String>, com.mojang.blaze3d.platform.NativeImage);
  public net.minecraft.client.renderer.texture.DynamicTexture(java.lang.String, int, int, boolean);
  public net.minecraft.client.renderer.texture.DynamicTexture(java.util.function.Supplier<java.lang.String>, int, int, boolean);
  public void upload();
  public com.mojang.blaze3d.platform.NativeImage getPixels();
  public void setPixels(com.mojang.blaze3d.platform.NativeImage);
  public void close();
  public void dumpContents(net.minecraft.resources.Identifier, java.nio.file.Path) throws java.io.IOException;
}

public class net.minecraft.client.renderer.texture.TextureManager implements net.minecraft.server.packs.resources.PreparableReloadListener, java.lang.AutoCloseable {
  public void registerAndLoad(net.minecraft.resources.Identifier, net.minecraft.client.renderer.texture.ReloadableTexture);
  public void registerForNextReload(net.minecraft.resources.Identifier);
  public void register(net.minecraft.resources.Identifier, net.minecraft.client.renderer.texture.AbstractTexture);
  public net.minecraft.client.renderer.texture.AbstractTexture getTexture(net.minecraft.resources.Identifier);
  public void release(net.minecraft.resources.Identifier);
  public void tick();
}

public abstract class net.minecraft.client.renderer.texture.AbstractTexture implements java.lang.AutoCloseable {
  public com.mojang.renderpearl.api.textures.GpuTexture getTexture();
  public com.mojang.renderpearl.api.textures.GpuTextureView getTextureView();
  public com.mojang.renderpearl.api.textures.GpuSampler getSampler();
}
```

- Runtime-generated images: build a `NativeImage`, wrap in `DynamicTexture(Supplier<String>, image)`, `textureManager.register(id, texture)`, then `blit(GUI_TEXTURED, id, ...)`. After mutating pixels call `setPixels(image)` + `upload()`.
- `TextureManager.registerForNextFrame` — **NOT FOUND in jar**. The reload-cycle twin is `registerForNextReload(Identifier)`. (Scanned all 11,383 classes in both jars for the string `registerForNextFrame`: zero hits.)

---

## 9. `Screenshot` + `NativeImage`

VERIFIED SIGNATURES

```java
public class net.minecraft.client.Screenshot {
  public static final java.lang.String SCREENSHOT_DIR;
  public static void takeScreenshot(com.mojang.blaze3d.pipeline.RenderTarget, java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage>);
  public static void takeScreenshot(com.mojang.blaze3d.pipeline.RenderTarget, int, java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage>);
  public static void grab(java.io.File, com.mojang.blaze3d.pipeline.RenderTarget, java.util.function.Consumer<net.minecraft.network.chat.Component>);
  public static void grab(net.minecraft.client.Minecraft, boolean);
  public static void grab(java.io.File, java.lang.String, com.mojang.blaze3d.pipeline.RenderTarget, int, java.util.function.Consumer<net.minecraft.network.chat.Component>);
}

public final class com.mojang.blaze3d.platform.NativeImage implements java.lang.AutoCloseable {  // NOT net.minecraft.client.renderer.texture.NativeImage
  public com.mojang.blaze3d.platform.NativeImage(int, int, boolean);
  public com.mojang.blaze3d.platform.NativeImage(com.mojang.blaze3d.platform.NativeImage$Format, int, int, boolean);
  public static com.mojang.blaze3d.platform.NativeImage read(java.io.InputStream) throws java.io.IOException;
  public int getWidth();
  public int getHeight();
  public int getPixel(int, int);
  public void setPixel(int, int, int);
  public void setPixelABGR(int, int, int);
  public int[] getPixels();
  public int[] makePixelArray();
  public java.nio.ByteBuffer getPixelBytes();
  public void writeToFile(java.io.File) throws java.io.IOException;
  public void writeToFile(java.nio.file.Path) throws java.io.IOException;
  public void fillRect(int, int, int, int, int);
  public void copyRect(int, int, int, int, int, int, boolean, boolean);
  public void resizeSubRectTo(int, int, int, int, com.mojang.blaze3d.platform.NativeImage);
  public com.mojang.blaze3d.platform.NativeImage mappedCopy(java.util.function.IntUnaryOperator);
  public void close();
}
```

- Screenshotting is async/callback: `Screenshot.takeScreenshot(RenderTarget, Consumer<NativeImage>)` or `(RenderTarget, int downscale, Consumer<NativeImage>)`. Get the target from `minecraft.gameRenderer.mainRenderTarget()` (Minecraft has no `getMainRenderTarget`).
- `NativeImage` lives in `com.mojang.blaze3d.platform` now. `asByteArray()` — **NOT FOUND**; use `makePixelArray()` (int[] ARGB) or `getPixelBytes()` (ByteBuffer). `upscale`/`scale` — **NOT FOUND**; only `resizeSubRectTo(...)` exists. `writeToFile(File|Path)` still saves PNG.

---

## 10. Layouts, scroll areas and selection lists

VERIFIED SIGNATURES

```java
public interface net.minecraft.client.gui.layouts.LayoutElement {
  public abstract void setX(int);
  public abstract void setY(int);
  public abstract int getX();
  public abstract int getY();
  public abstract int getWidth();
  public abstract int getHeight();
  public default net.minecraft.client.gui.navigation.ScreenRectangle getRectangle();
  public default void setPosition(int, int);
  public abstract void visitWidgets(java.util.function.Consumer<net.minecraft.client.gui.components.AbstractWidget>);
}

public class net.minecraft.client.gui.layouts.LinearLayout implements net.minecraft.client.gui.layouts.Layout {
  public net.minecraft.client.gui.layouts.LinearLayout(int, int, net.minecraft.client.gui.layouts.LinearLayout$Orientation);
  public static net.minecraft.client.gui.layouts.LinearLayout vertical();
  public static net.minecraft.client.gui.layouts.LinearLayout horizontal();
  public net.minecraft.client.gui.layouts.LinearLayout spacing(int);
  public <T extends net.minecraft.client.gui.layouts.LayoutElement> T addChild(T);
  public <T extends net.minecraft.client.gui.layouts.LayoutElement> T addChild(T, net.minecraft.client.gui.layouts.LayoutSettings);
  public void arrangeElements();
}

public class net.minecraft.client.gui.layouts.GridLayout extends net.minecraft.client.gui.layouts.AbstractLayout {
  public net.minecraft.client.gui.layouts.GridLayout();
  public net.minecraft.client.gui.layouts.GridLayout(int, int);
  public <T extends net.minecraft.client.gui.layouts.LayoutElement> T addChild(T, int, int);
  public <T extends net.minecraft.client.gui.layouts.LayoutElement> T addChild(T, int, int, net.minecraft.client.gui.layouts.LayoutSettings);
  public net.minecraft.client.gui.layouts.GridLayout rowSpacing(int);
  public net.minecraft.client.gui.layouts.GridLayout columnSpacing(int);
  public net.minecraft.client.gui.layouts.GridLayout$RowHelper createRowHelper(int);
}

public class net.minecraft.client.gui.layouts.FrameLayout extends net.minecraft.client.gui.layouts.AbstractLayout {
  public net.minecraft.client.gui.layouts.FrameLayout();
  public net.minecraft.client.gui.layouts.FrameLayout(int, int, int, int);
  public <T extends net.minecraft.client.gui.layouts.LayoutElement> T addChild(T);
  public static void centerInRectangle(net.minecraft.client.gui.layouts.LayoutElement, int, int, int, int);
  public static void centerInRectangle(net.minecraft.client.gui.layouts.LayoutElement, net.minecraft.client.gui.navigation.ScreenRectangle);
}

public class net.minecraft.client.gui.layouts.HeaderAndFooterLayout implements net.minecraft.client.gui.layouts.Layout {
  public net.minecraft.client.gui.layouts.HeaderAndFooterLayout(net.minecraft.client.gui.screens.Screen);
  public <T extends net.minecraft.client.gui.layouts.LayoutElement> T addToHeader(T);
  public <T extends net.minecraft.client.gui.layouts.LayoutElement> T addToFooter(T);
  public <T extends net.minecraft.client.gui.layouts.LayoutElement> T addToContents(T);
  public void addTitleHeader(net.minecraft.network.chat.Component, net.minecraft.client.gui.Font);
  public int getHeaderHeight(); public int getFooterHeight(); public int getContentHeight();
}

public abstract class net.minecraft.client.gui.components.AbstractScrollArea extends net.minecraft.client.gui.components.AbstractWidget {
  public static final int SCROLLBAR_WIDTH;
  public net.minecraft.client.gui.components.AbstractScrollArea(int, int, int, int, net.minecraft.network.chat.Component, net.minecraft.client.gui.components.AbstractScrollArea$ScrollbarSettings);
  public boolean mouseScrolled(double, double, double, double);
  public double scrollAmount();
  public void setScrollAmount(double);
  protected abstract int contentHeight();
  protected void extractScrollbar(net.minecraft.client.gui.GuiGraphicsExtractor, int, int);
}
public abstract class net.minecraft.client.gui.components.AbstractContainerWidget extends net.minecraft.client.gui.components.AbstractScrollArea implements net.minecraft.client.gui.components.events.ContainerEventHandler {
  public net.minecraft.client.gui.components.AbstractContainerWidget(int, int, int, int, net.minecraft.network.chat.Component);
}
public abstract class net.minecraft.client.gui.components.AbstractSelectionList<E extends net.minecraft.client.gui.components.AbstractSelectionList$Entry<E>> extends net.minecraft.client.gui.components.AbstractContainerWidget {
  public net.minecraft.client.gui.components.AbstractSelectionList(net.minecraft.client.Minecraft, int, int, int, int);
  protected int addEntry(E);
  public E getSelected();
  public void setSelected(E);
  public void updateSize(int, net.minecraft.client.gui.layouts.HeaderAndFooterLayout);
}
public abstract class net.minecraft.client.gui.components.ObjectSelectionList<E extends net.minecraft.client.gui.components.ObjectSelectionList$Entry<E>> extends net.minecraft.client.gui.components.AbstractSelectionList<E> {
  public net.minecraft.client.gui.components.ObjectSelectionList(net.minecraft.client.Minecraft, int, int, int, int);
}
```

- All of `LinearLayout`, `GridLayout`, `FrameLayout`, `HeaderAndFooterLayout`, `LayoutElement`, `LayoutSettings` exist and are the intended way to lay out widgets; a screen typically does `layout.visitWidgets(this::addRenderableWidget)`.
- Scrolling containers are `AbstractScrollArea` → `AbstractContainerWidget` → `AbstractSelectionList` → `ObjectSelectionList`/`ContainerObjectSelectionList`. There is no plain "ScrollView"; subclass `AbstractContainerWidget` for a custom list, or `ContainerObjectSelectionList` for row widgets.

---

## 11. `Component`, `Style`, `ChatFormatting`

VERIFIED SIGNATURES

```java
public interface net.minecraft.network.chat.Component extends com.mojang.brigadier.Message, net.minecraft.network.chat.FormattedText {
  public static net.minecraft.network.chat.MutableComponent literal(java.lang.String);
  public static net.minecraft.network.chat.MutableComponent translatable(java.lang.String);
  public static net.minecraft.network.chat.MutableComponent translatable(java.lang.String, java.lang.Object...);
  public static net.minecraft.network.chat.MutableComponent empty();
  public default java.lang.String getString();
  public abstract net.minecraft.network.chat.Style getStyle();
  public abstract net.minecraft.util.FormattedCharSequence getVisualOrderText();
}
public final class net.minecraft.network.chat.MutableComponent implements net.minecraft.network.chat.Component {
  public net.minecraft.network.chat.MutableComponent setStyle(net.minecraft.network.chat.Style);
  public net.minecraft.network.chat.MutableComponent append(java.lang.String);
  public net.minecraft.network.chat.MutableComponent append(net.minecraft.network.chat.Component);
  public net.minecraft.network.chat.MutableComponent withStyle(java.util.function.UnaryOperator<net.minecraft.network.chat.Style>);
  public net.minecraft.network.chat.MutableComponent withStyle(net.minecraft.network.chat.Style);
  public net.minecraft.network.chat.MutableComponent withStyle(net.minecraft.ChatFormatting...);
  public net.minecraft.network.chat.MutableComponent withStyle(net.minecraft.ChatFormatting);
  public net.minecraft.network.chat.MutableComponent withColor(int);
  public net.minecraft.network.chat.MutableComponent withColor(net.minecraft.network.chat.TextColor);
}
public final class net.minecraft.network.chat.Style {
  public static final net.minecraft.network.chat.Style EMPTY;
  public static final int NO_SHADOW;
  public net.minecraft.network.chat.Style withColor(net.minecraft.network.chat.TextColor);
  public net.minecraft.network.chat.Style withColor(net.minecraft.ChatFormatting);
  public net.minecraft.network.chat.Style withColor(int);
  public net.minecraft.network.chat.Style withShadowColor(int);
  public net.minecraft.network.chat.Style withoutShadow();
  public net.minecraft.network.chat.Style withBold(java.lang.Boolean);
  public net.minecraft.network.chat.Style withItalic(java.lang.Boolean);
  public net.minecraft.network.chat.Style withUnderlined(java.lang.Boolean);
  public net.minecraft.network.chat.Style applyFormat(net.minecraft.ChatFormatting);
  public net.minecraft.network.chat.Style applyFormats(net.minecraft.ChatFormatting...);
  public net.minecraft.network.chat.TextColor getColor();
}
public final class net.minecraft.ChatFormatting extends java.lang.Enum<net.minecraft.ChatFormatting> {
  public static final net.minecraft.ChatFormatting BOLD;
  public static final net.minecraft.ChatFormatting RESET;
  public static java.lang.String stripFormatting(java.lang.String);
  public static net.minecraft.ChatFormatting getByCode(char);
}
public final class net.minecraft.network.chat.TextColor {
  public static net.minecraft.network.chat.TextColor fromRgb(int);
  public static net.minecraft.network.chat.TextColor fromLegacyFormat(net.minecraft.ChatFormatting);
  public int getValue();
}
public class net.minecraft.client.gui.Font {
  public final int lineHeight;
  public int width(java.lang.String);
  public int width(net.minecraft.network.chat.FormattedText);
  public java.util.List<net.minecraft.util.FormattedCharSequence> split(net.minecraft.network.chat.FormattedText, int);
}
```

- `Component.literal(...)` / `Component.translatable(key, args...)` still return `MutableComponent`; style chain is `.withStyle(ChatFormatting.GOLD)`, `.withStyle(s -> s.withBold(true))`, `.withColor(0xFF55FF)`.
- `ChatFormatting` **is** still present and usable — `withStyle(ChatFormatting...)` and `Style.withColor(ChatFormatting)` both accept it (it is converted via `TextColor.fromLegacyFormat`). `Style.withColor(int)` takes ARGB and is the precise-color path.
- `Font.lineHeight` is a public final int field (no `lineHeight()` method); center text with `font.width(str)`. `centeredText` already does `x - width/2`.
