# Client Render / HUD / Camera Recon — Minecraft 26.3 + Fabric API 0.165.2

All blocks below are **verbatim `javap -p` output** from the deobf jars in `geoguessr-dev`.
Nothing here is inferred unless it is under a **Implication** bullet.

Sources:
- `MCJAR=/home/dev/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common-deobf/26.3/minecraft-common-deobf-26.3.jar`
- `CLJAR=/home/dev/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/26.3/minecraft-clientonly-deobf-26.3.jar`
- `FAPI` module jars extracted from
  `/home/dev/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.165.2+26.3/2e364989e3f6efb97e1dbd12d80d835c7ab8c26c/fabric-api-0.165.2+26.3.jar`
  (`META-INF/jars/*` nested jars; `fabric-rendering-v1-27.2.0+eb9fe70b5d`,
  `fabric-lifecycle-events-v1-4.1.9+ffef5f675d`, `fabric-key-mapping-api-v1-2.0.8+3434d6d95d`).
  `javap -p -classpath "$MCJAR:$CLJAR:<extracted module dirs>"`.

**Read this first — three structural changes in 26.3 that invalidate every 1.21.x tutorial:**

1. The renderer is **extraction + submission based**. `Hud`, `Gui`, `Camera`, `GameRenderer` all have
   `extract…(…, GuiGraphicsExtractor | …RenderState, DeltaTracker)` methods, and drawing happens later
   from a `…RenderState`. There is no immediate-mode `GuiGraphics#drawString` in the HUD path.
2. The GL-style wrappers are gone. `RenderTarget` has **no `bindWrite`/`unbindWrite`** (verified absent in
   *both* jars); targets are `com.mojang.renderpearl.api.textures.GpuTexture(View)` handed to a
   `com.mojang.blaze3d.framegraph.FrameGraphBuilder` + `CommandEncoder`.
3. `Options.hideGui` and `Minecraft#getMainRenderTarget()` **no longer exist** (see §9/§10/§5).

---

## 1. Fabric HUD API

Package `net.fabricmc.fabric.api.client.rendering.v1.hud` contains exactly 5 public types + `package-info`:
`HudElement`, `HudElementRegistry`, `HudStatusBarHeightRegistry`, `StatusBarHeightProvider`, `VanillaHudElements`.

```java
public interface net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry {
  public static void addFirst(net.minecraft.resources.Identifier, net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement);
  public static void addLast(net.minecraft.resources.Identifier, net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement);
  public static void attachElementBefore(net.minecraft.resources.Identifier, net.minecraft.resources.Identifier, net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement);
  public static void attachElementAfter(net.minecraft.resources.Identifier, net.minecraft.resources.Identifier, net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement);
  public static void removeElement(net.minecraft.resources.Identifier);
  public static void replaceElement(net.minecraft.resources.Identifier, java.util.function.Function<net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement, net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement>);
}
```

Parameter names recovered from `LocalVariableTable` / null-check string constants in `HudElementRegistry.class`:

```java
addFirst(Identifier identifier, HudElement hudElement)
addLast (Identifier identifier, HudElement hudElement)
attachElementBefore(Identifier beforeThis, Identifier identifier, HudElement hudElement)
attachElementAfter (Identifier afterThis,  Identifier identifier, HudElement hudElement)
```

Element interface — **one method only**, named `extractRenderState`:

```java
public interface net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement {
  public abstract void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor, net.minecraft.client.DeltaTracker);
}
```

```java
public final class net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements {
  public static final net.minecraft.resources.Identifier MISC_OVERLAYS;
  public static final net.minecraft.resources.Identifier CROSSHAIR;
  public static final net.minecraft.resources.Identifier SPECTATOR_MENU;
  public static final net.minecraft.resources.Identifier HOTBAR;
  public static final net.minecraft.resources.Identifier ARMOR_BAR;
  public static final net.minecraft.resources.Identifier HEALTH_BAR;
  public static final net.minecraft.resources.Identifier FOOD_BAR;
  public static final net.minecraft.resources.Identifier AIR_BAR;
  public static final net.minecraft.resources.Identifier MOUNT_HEALTH;
  public static final net.minecraft.resources.Identifier INFO_BAR;
  public static final net.minecraft.resources.Identifier EXPERIENCE_LEVEL;
  public static final net.minecraft.resources.Identifier INFO_BAR_FOREGROUND;
  public static final net.minecraft.resources.Identifier HELD_ITEM_TOOLTIP;
  public static final net.minecraft.resources.Identifier SPECTATOR_TOOLTIP;
  public static final net.minecraft.resources.Identifier MOB_EFFECTS;
  public static final net.minecraft.resources.Identifier BOSS_BAR;
  public static final net.minecraft.resources.Identifier SLEEP;
  public static final net.minecraft.resources.Identifier DEMO_TIMER;
  public static final net.minecraft.resources.Identifier SCOREBOARD;
  public static final net.minecraft.resources.Identifier OVERLAY_MESSAGE;
  public static final net.minecraft.resources.Identifier TITLE_AND_SUBTITLE;
  public static final net.minecraft.resources.Identifier CHAT;
  public static final net.minecraft.resources.Identifier PLAYER_LIST;
  public static final net.minecraft.resources.Identifier SUBTITLES;
  private net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements();
  static {};
}
```

There is **no `DEBUG` / `DEBUG_OVERLAY` constant** in `VanillaHudElements` (full list above).

```java
public interface net.minecraft.client.DeltaTracker {
  public static final net.minecraft.client.DeltaTracker ZERO;
  public static final net.minecraft.client.DeltaTracker ONE;
  public abstract float getGameTimeDeltaTicks();
  public abstract float getGameTimeDeltaPartialTick(boolean);
  public abstract float getRealtimeDeltaTicks();
  static {};
}
```

```java
public final class net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry {
  public static void addLeft(net.minecraft.resources.Identifier, net.fabricmc.fabric.api.client.rendering.v1.hud.StatusBarHeightProvider);
  public static void addRight(net.minecraft.resources.Identifier, net.fabricmc.fabric.api.client.rendering.v1.hud.StatusBarHeightProvider);
  public static void replaceLeft(net.minecraft.resources.Identifier, java.util.function.Function<net.fabricmc.fabric.api.client.rendering.v1.hud.StatusBarHeightProvider, net.fabricmc.fabric.api.client.rendering.v1.hud.StatusBarHeightProvider>);
  public static void replaceRight(net.minecraft.resources.Identifier, java.util.function.Function<net.fabricmc.fabric.api.client.rendering.v1.hud.StatusBarHeightProvider, net.fabricmc.fabric.api.client.rendering.v1.hud.StatusBarHeightProvider>);
  public static int getHeight(net.minecraft.resources.Identifier);
  public static int getElementHeight(net.minecraft.resources.Identifier);
  private net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry();
}
```

- There is **no `register(HudElement)`** and **no `Identifier.of(...)`-free overload** — every entry point needs an
  `Identifier` id of your own, plus (for before/after) the id of the anchor element.
- `HudElement` has **no render/push/pop methods at all**. You draw inside `extractRenderState` via
  `GuiGraphicsExtractor.fill/text/blit/…`; there is no `poseStack()` on the HUD path — but let me be exact:
  ```java
  public org.joml.Matrix3x2fStack pose();   // net.minecraft.client.gui.GuiGraphicsExtractor
  ```
  That is a **2D** `Matrix3x2fStack`. For our HUD overlay (a score/round readout) that is sufficient.
- Signature changes vs 1.21.x: `HudRenderCallback` and `HudElementRegistry.register` are gone; the old
  `(DrawContext, RenderTickCounter)` is now `(GuiGraphicsExtractor, DeltaTracker)`.

---

## 2. Fabric world/level rendering events (new in 26.x)

### 2.1 Exact package contents — `net.fabricmc.fabric.api.client.rendering.v1.level`

`unzip -l` of `fabric-rendering-v1-27.2.0+eb9fe70b5d.jar` gives exactly these 19 classes:

```
AbstractLevelRenderContext.class
LevelExtractionContext.class
LevelExtractionEvents$AfterBlockOutlineExtraction.class
LevelExtractionEvents$EndExtraction.class
LevelExtractionEvents.class
LevelRenderContext.class
LevelRenderEvents$AfterOpaqueTerrain.class
LevelRenderEvents$AfterSolidFeatures.class
LevelRenderEvents$AfterTranslucentFeatures.class
LevelRenderEvents$AfterTranslucentTerrain.class
LevelRenderEvents$BeforeBlockOutline.class
LevelRenderEvents$BeforeGizmos.class
LevelRenderEvents$BeforeTranslucentTerrain.class
LevelRenderEvents$CollectSubmits.class
LevelRenderEvents$EndMain.class
LevelRenderEvents$StartMain.class
LevelRenderEvents.class
LevelTerrainRenderContext.class
package-info.class
```

Note: there is **no `WorldRenderEvents`**, no `WorldRenderContext`, no `WorldRenderEvents.AFTER_ENTITIES` in this
package — the 1.21.6+ `level.*` API fully replaces them.

### 2.2 Event holders

```java
public class net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents {
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents$AfterBlockOutlineExtraction> AFTER_BLOCK_OUTLINE_EXTRACTION;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents$EndExtraction> END_EXTRACTION;
  public net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents();
}
```

```java
public final class net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents {
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents$StartMain> START_MAIN;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents$AfterOpaqueTerrain> AFTER_OPAQUE_TERRAIN;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents$CollectSubmits> COLLECT_SUBMITS;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents$AfterSolidFeatures> AFTER_SOLID_FEATURES;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents$AfterTranslucentFeatures> AFTER_TRANSLUCENT_FEATURES;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents$BeforeBlockOutline> BEFORE_BLOCK_OUTLINE;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents$BeforeGizmos> BEFORE_GIZMOS;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents$BeforeTranslucentTerrain> BEFORE_TRANSLUCENT_TERRAIN;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents$AfterTranslucentTerrain> AFTER_TRANSLUCENT_TERRAIN;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents$EndMain> END_MAIN;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents$AfterBlockOutlineExtraction> AFTER_BLOCK_OUTLINE_EXTRACTION;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents$EndExtraction> END_EXTRACTION;
  private net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents();
}
```

`LevelRenderEvents` re-exports the two extraction events, so `LevelRenderEvents.END_EXTRACTION` is valid too.

### 2.3 Listener functional interfaces (exact params)

```java
public interface LevelRenderEvents$StartMain                    { public abstract void startMain(net.fabricmc.fabric.api.client.rendering.v1.level.LevelTerrainRenderContext); }
public interface LevelRenderEvents$AfterOpaqueTerrain           { public abstract void afterOpaqueTerrain(net.fabricmc.fabric.api.client.rendering.v1.level.LevelTerrainRenderContext); }
public interface LevelRenderEvents$CollectSubmits               { public abstract void collectSubmits(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext); }
public interface LevelRenderEvents$AfterSolidFeatures           { public abstract void afterSolidFeatures(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext); }
public interface LevelRenderEvents$AfterTranslucentFeatures     { public abstract void afterTranslucentFeatures(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext); }
public interface LevelRenderEvents$BeforeBlockOutline           { public abstract boolean beforeBlockOutline(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext, net.minecraft.client.renderer.state.level.BlockOutlineRenderState); }
public interface LevelRenderEvents$BeforeGizmos                 { public abstract void beforeGizmos(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext); }
public interface LevelRenderEvents$BeforeTranslucentTerrain     { public abstract void beforeTranslucentTerrain(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext); }
public interface LevelRenderEvents$AfterTranslucentTerrain      { public abstract void afterTranslucentTerrain(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext); }
public interface LevelRenderEvents$EndMain                      { public abstract void endMain(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext); }

public interface LevelExtractionEvents$AfterBlockOutlineExtraction { public abstract void afterBlockOutlineExtraction(net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext, net.minecraft.world.phys.HitResult); }
public interface LevelExtractionEvents$EndExtraction               { public abstract void endExtraction(net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext); }
```

`BeforeBlockOutline` is the **only** boolean-returning listener (return `false` to cancel the outline).

Registration is the standard Fabric `Event`:

```java
public abstract class net.fabricmc.fabric.api.event.Event<T> {
  protected volatile T invoker;
  public static final net.minecraft.resources.Identifier DEFAULT_PHASE;
  public final T invoker();
  public abstract void register(T);
  public void register(net.minecraft.resources.Identifier, T);
  public void addPhaseOrdering(net.minecraft.resources.Identifier, net.minecraft.resources.Identifier);
}
```

### 2.4 Context interfaces — and what you actually get in the draw phase

```java
public interface AbstractLevelRenderContext {
  public abstract net.minecraft.client.renderer.GameRenderer gameRenderer();
  public abstract net.minecraft.client.renderer.LevelRenderer levelRenderer();
  public abstract net.minecraft.client.renderer.state.level.LevelRenderState levelState();
}

public interface LevelTerrainRenderContext extends AbstractLevelRenderContext {
  public abstract net.minecraft.client.renderer.chunk.ChunkSectionsToRender sectionsToRender();
}

public interface LevelRenderContext extends LevelTerrainRenderContext {
  public abstract net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector();
  public abstract com.mojang.blaze3d.vertex.PoseStack poseStack();
}

public interface LevelExtractionContext extends AbstractLevelRenderContext {
  public abstract net.minecraft.client.multiplayer.ClientLevel level();
  public abstract net.minecraft.client.Camera camera();
  public abstract net.minecraft.client.DeltaTracker deltaTracker();
}
```

- **`LevelRenderContext` does NOT expose `camera()`, `level()`, `buffers()`, `matrixStack()`, `consumers()`,
  `world()`, `tickDelta()`, `frustum()`, or `cameraNode()`.** The only draw-phase handles are `poseStack()`,
  `submitNodeCollector()`, `sectionsToRender()`, `gameRenderer()`, `levelRenderer()`, `levelState()`.
  (Verified: the full `javap` output for the interface is the 2 methods above plus the 1 inherited.)
- `poseStack()` is a real 3D `com.mojang.blaze3d.vertex.PoseStack` — but note `LevelRenderer.render()` itself
  pushes the camera transform onto `RenderSystem.getModelViewStack()` before the events fire, so your
  `poseStack()` is already camera-relative (world-space origin is the camera).
- There are **no `VertexConsumerProvider`/`VertexConsumer` "buffers"** in the context: to draw geometry you
  submit nodes to `submitNodeCollector()` (a `SubmitNodeCollector`, itself
  `extends OrderedSubmitNodeCollector { OrderedSubmitNodeCollector order(int); }`) which are batched by
  `FeatureRenderDispatcher` and executed inside the framegraph.
- `levelState()` gives the *extracted* frame: `LevelRenderState.cameraRenderState`, `.entityRenderStates`,
  `.blockEntityRenderStates`, `.gameTime`, `.worldPartialTicks`, `.cloudColor`, `.skyRenderState`, etc. —
  all `public` mutable fields (see §4.4).
- `LevelExtractionContext` is the one that has `camera()` / `level()` / `deltaTracker()`, but it is an
  **extraction-phase** hook (no `PoseStack`, no submit collector).

---

## 3. Client tick / keybindings

```java
public final class net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents {
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$StartTick> START_CLIENT_TICK;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$EndTick> END_CLIENT_TICK;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$StartLevelTick> START_LEVEL_TICK;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$EndLevelTick> END_LEVEL_TICK;
  private net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents();
}
public interface ClientTickEvents$StartTick      { public abstract void onStartTick(net.minecraft.client.Minecraft); }
public interface ClientTickEvents$EndTick        { public abstract void onEndTick(net.minecraft.client.Minecraft); }
public interface ClientTickEvents$StartLevelTick { public abstract void onStartTick(net.minecraft.client.multiplayer.ClientLevel); }
public interface ClientTickEvents$EndLevelTick   { public abstract void onEndTick(net.minecraft.client.multiplayer.ClientLevel); }
```

```java
public final class net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper {
  private net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper();
  public static net.minecraft.client.KeyMapping registerKeyMapping(net.minecraft.client.KeyMapping);
  public static com.mojang.blaze3d.platform.InputConstants$Key getBoundKeyOf(net.minecraft.client.KeyMapping);
}
```

```java
public class net.minecraft.client.KeyMapping implements java.lang.Comparable<net.minecraft.client.KeyMapping> {
  private final java.lang.String name;
  private final com.mojang.blaze3d.platform.InputConstants$Key defaultKey;
  private final net.minecraft.client.KeyMapping$Category category;
  protected com.mojang.blaze3d.platform.InputConstants$Key key;
  private boolean isDown;
  private int clickCount;
  private final int order;

  public static void click(com.mojang.blaze3d.platform.InputConstants$Key);
  public static void set(com.mojang.blaze3d.platform.InputConstants$Key, boolean);
  public static void setAll();
  public static void releaseAll();
  public static void restoreToggleStatesOnScreenClosed();
  public static void resetToggleKeys();
  public static void resetMapping();

  public net.minecraft.client.KeyMapping(java.lang.String name, int keysym, net.minecraft.client.KeyMapping$Category category);
  public net.minecraft.client.KeyMapping(java.lang.String name, com.mojang.blaze3d.platform.InputConstants$Type type, int keysym, net.minecraft.client.KeyMapping$Category category);
  public net.minecraft.client.KeyMapping(java.lang.String name, com.mojang.blaze3d.platform.InputConstants$Type type, int keysym, net.minecraft.client.KeyMapping$Category category, int order);

  public boolean isDown();
  public net.minecraft.client.KeyMapping$Category getCategory();
  public boolean consumeClick();
  protected void release();
  public java.lang.String getName();
  public com.mojang.blaze3d.platform.InputConstants$Key getDefaultKey();
  public void setKey(com.mojang.blaze3d.platform.InputConstants$Key);
  public boolean same(net.minecraft.client.KeyMapping);
  public boolean isUnbound();
  public boolean matches(net.minecraft.client.input.KeyEvent);
  public boolean matchesMouse(net.minecraft.client.input.MouseButtonEvent);
  public boolean matches(com.mojang.blaze3d.platform.InputConstants$Key);
  public net.minecraft.network.chat.Component getTranslatedKeyMessage();
  public boolean isDefault();
  public java.lang.String saveString();
  public void setDown(boolean);
  public static net.minecraft.client.KeyMapping get(java.lang.String);
}
```

(`name`/`keysym`/`type`/`category`/`order` are the actual `LocalVariableTable` names.)

```java
public final class net.minecraft.client.KeyMapping$Category extends java.lang.Record {
  private final net.minecraft.resources.Identifier id;
  public static final net.minecraft.client.KeyMapping$Category MOVEMENT;
  public static final net.minecraft.client.KeyMapping$Category MISC;
  public static final net.minecraft.client.KeyMapping$Category MULTIPLAYER;
  public static final net.minecraft.client.KeyMapping$Category GAMEPLAY;
  public static final net.minecraft.client.KeyMapping$Category INVENTORY;
  public static final net.minecraft.client.KeyMapping$Category CREATIVE;
  public static final net.minecraft.client.KeyMapping$Category SPECTATOR;
  public static final net.minecraft.client.KeyMapping$Category DEBUG;
  public net.minecraft.client.KeyMapping$Category(net.minecraft.resources.Identifier);
  private static net.minecraft.client.KeyMapping$Category register(java.lang.String);
  public static net.minecraft.client.KeyMapping$Category register(net.minecraft.resources.Identifier);
  public net.minecraft.network.chat.Component label();
  public net.minecraft.resources.Identifier id();
}
```

- `Category` is a **record over `Identifier`** with 8 built-in constants. To make your own group:
  `KeyMapping.Category.register(Identifier.fromNamespaceAndPath("geoguessr","main"))` — the `String` overload is
  `private`, so you must pass an `Identifier`.
- Key code is a **raw `int` keysym**, not `InputConstants.Key`; for a mouse button use
  `InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_LEFT` or use the `int` ctor with `-1`/"unbound" style
  constants — `KeyMapping` exposes no public `UNKNOWN` constant in this jar, so use
  `com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue()` style lookups or bind a real key.
- `consumeClick()` and `isDown()` are unchanged, so the usual
  `while (key.consumeClick()) { … }` inside `ClientTickEvents.END_CLIENT_TICK` still works.
- `KeyMappingHelper.getBoundKeyOf(km)` returns the **live** `InputConstants.Key` (respects user rebinds) —
  use it instead of the ctor argument.

---

## 4. Camera / GameRenderer / LevelRenderer — where one frame comes from

### 4.1 Per-frame call order (recovered from bytecode)

`GameRenderer.update(DeltaTracker)`:
```
Profiler.push
Camera.update(DeltaTracker)          <-- camera entity follow / rotation / position happens HERE
Profiler.pop
```
`GameRenderer.extract(DeltaTracker, boolean)`:
```
Minecraft.isGameLoadFinished
DeltaTracker.getGameTimeDeltaPartialTick(Z)F
extractWindow()
extractOptions()
LightmapRenderStateExtractor.extract(LightmapRenderState, float)
extractCamera(DeltaTracker, float)                     <-- CameraRenderState built HERE
LevelExtractor.extract(DeltaTracker, Camera, float)    <-- entities/BEs/particles extracted HERE
PlayerRenderState.reset()
Gui.extractRenderState(DeltaTracker, boolean, boolean)
```
`GameRenderer.render()`:
```
resize(w, h)
CommandEncoder.clearColorAndDepthTextures(colorTex, clearColor, depthTex, 1.0)
GlobalSettingsUniform.update(...)
preparePostEffects(...)
Lightmap.render(LightmapRenderState)
renderLevel()                       <-- world, see below
tryTakeScreenshotIfNeeded()
LevelRenderer.blitEntityOutline()
applyPostEffects()
FogRenderer.endFrame()
CommandEncoder.clearDepthTexture(depthTex, 1.0)
Lighting.setupFor(...)
GuiRenderer.render(); GuiRenderer.endFrame()
RenderBuffers.endFrame()
CrossFrameResourcePool.endFrame()
```
`GameRenderer.renderLevel()`:
```
new Matrix4f(...) ; new PoseStack()
bobHurt(CameraRenderState, PoseStack) ; bobView(CameraRenderState, PoseStack)   // view bobbing
projectionMatrixBuffer.getBuffer(Matrix4f) -> GpuBufferSlice ; RenderSystem.setProjectionMatrix(slice, ProjectionType)
FogRenderer.updateBuffer(FogData) ; FogRenderer.getBuffer(FogMode)
BossHealthOverlay.shouldCreateWorldFog()
LevelRenderer.render(GraphicsResourceAllocator, boolean, CameraRenderState, GpuBufferSlice, Vector4f, boolean, boolean)
render3dHud(CameraRenderState, PlayerRenderState, OptionsRenderState, boolean)
```
`GameRenderer.extractCamera(DeltaTracker, float)`:
```
Camera.extractRenderState(CameraRenderState, DeltaTracker)
Camera.getFluidInCamera() -> FogType
Options.getEffectiveRenderDistance() -> int
bossOverlayWorldDarkening(float)
FogRenderer.setupFog(Camera, int, DeltaTracker, float, ClientLevel) -> FogData
```

- The camera is a **single long-lived object** (`GameRenderer.mainCamera`) mutated once per frame by
  `Camera.update(DeltaTracker)`; the renderer never reads `Camera` — it reads `CameraRenderState`, which is a
  plain mutable POJO. **This is the ghost-camera seam**: you do not need to move `Camera` at all, you can build
  a second `CameraRenderState`.
- `GameRenderer#render()` clears and then renders into `GameRenderer.mainRenderTarget()`. There is no
  parameter to redirect it — see §4.5.

### 4.2 `net.minecraft.client.Camera`

```java
public class net.minecraft.client.Camera implements net.minecraft.world.waypoints.TrackedWaypoint$Camera {
  public static final float PROJECTION_Z_NEAR;
  public static final float BASE_HUD_FOV;
  private net.minecraft.world.level.Level level;
  private net.minecraft.world.entity.Entity entity;
  private net.minecraft.world.phys.Vec3 position;
  private float xRot;
  private float yRot;
  private final org.joml.Quaternionf rotation;
  private boolean detached;
  private float eyeHeight;
  private final net.minecraft.client.renderer.Projection projection;
  private net.minecraft.client.renderer.culling.Frustum cullFrustum;
  private float fov;
  private float hudFov;
  private float depthFar;
  private final net.minecraft.client.Minecraft minecraft;

  public net.minecraft.client.Camera();
  public void tick();
  public void update(net.minecraft.client.DeltaTracker);
  public float getCameraEntityPartialTicks(net.minecraft.client.DeltaTracker);
  public void extractRenderState(net.minecraft.client.renderer.state.level.CameraRenderState, net.minecraft.client.DeltaTracker);
  public net.minecraft.client.renderer.culling.Frustum getCullFrustum();
  public net.minecraft.client.renderer.culling.Frustum getCapturedFrustum();
  public void captureFrustum();
  public void killFrustum();
  public boolean isPanoramicMode();
  public float getFov();
  private void setupPerspective(float, float, float, float, float);
  private void setupOrtho(float, float, float, float, boolean);
  protected void move(float, float, float);
  protected void setRotation(float, float);
  protected void setPosition(double, double, double);
  protected void setPosition(net.minecraft.world.phys.Vec3);
  public net.minecraft.world.phys.Vec3 position();
  public net.minecraft.core.BlockPos blockPosition();
  public float xRot();
  public float yRot();
  public float yaw();
  public org.joml.Quaternionf rotation();
  public org.joml.Matrix4f getViewRotationMatrix(org.joml.Matrix4f);
  public org.joml.Matrix4f getViewRotationProjectionMatrix(org.joml.Matrix4f);
  public net.minecraft.world.entity.Entity entity();
  public void setEntity(net.minecraft.world.entity.Entity);
  public boolean isInitialized();
  public boolean isDetached();
  public net.minecraft.world.attribute.EnvironmentAttributeProbe attributeProbe();
  public net.minecraft.client.Camera$NearPlane getNearPlane(float);
  public net.minecraft.world.level.material.FogType getFluidInCamera();
  public org.joml.Vector3fc forwardVector();
  public org.joml.Vector3fc panoramicForwards();
  public org.joml.Vector3fc upVector();
  public org.joml.Vector3fc leftVector();
  public void reset();
  public void setLevel(net.minecraft.client.multiplayer.ClientLevel);
  public void enablePanoramicMode();
  public void disablePanoramicMode();
  static {};
}
```

- Accessor names changed: `getPosition()` → **`position()`**, `getXRot()` → **`xRot()`**, `getYRot()` → **`yRot()`**.
  There is **no `rotate(...)`** method on `Camera`; rotation is `protected setRotation(float, float)`
  (`@Invoker`), and the derived quaternion is `rotation()`.
- `move`, `setRotation`, `setPosition` are **`protected`** → directly reachable from a mixin in
  `net.minecraft.client.Camera` via `@Invoker`, or from a subclass-less `@Redirect` on the call site.
- `setupPerspective(...)` / `setupOrtho(...)` are **`private`** → need `@Invoker` + `@Shadow` or an
  access widener.

### 4.3 `net.minecraft.client.renderer.GameRenderer` (relevant members only)

```java
  private final com.mojang.blaze3d.pipeline.RenderTarget mainRenderTarget;
  private final com.mojang.blaze3d.pipeline.RenderTarget hud3DTarget;
  private final net.minecraft.client.Camera mainCamera;
  private final com.mojang.blaze3d.resource.CrossFrameResourcePool resourcePool;
  private final net.minecraft.client.renderer.fog.FogRenderer fogRenderer;

  public net.minecraft.client.renderer.GameRenderer(net.minecraft.client.Minecraft, net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer, net.minecraft.client.resources.model.ModelManager, net.minecraft.client.renderer.item.ItemModelResolver);
  public void resize(int, int);
  public void update(net.minecraft.client.DeltaTracker);
  public void extract(net.minecraft.client.DeltaTracker, boolean);
  public void render();
  public void renderLevel();
  private void extractCamera(net.minecraft.client.DeltaTracker, float);
  private void extractWindow();
  private void extractOptions();
  private void render3dHud(net.minecraft.client.renderer.state.level.CameraRenderState, net.minecraft.client.renderer.state.level.PlayerRenderState, net.minecraft.client.renderer.state.OptionsRenderState, boolean);
  public net.minecraft.client.Camera mainCamera();
  public com.mojang.blaze3d.pipeline.RenderTarget mainRenderTarget();
  public net.minecraft.world.phys.Vec3 projectPointToScreen(net.minecraft.world.phys.Vec3);
  public net.minecraft.client.renderer.RenderBuffers renderBuffers();
  public net.minecraft.client.renderer.feature.FeatureRenderDispatcher featureRenderDispatcher();
  public net.minecraft.client.renderer.state.GameRenderState gameRenderState();
  public boolean useImprovedTransparency();
  public void setLevel(net.minecraft.client.multiplayer.ClientLevel);
```

- There is **no `getMainCamera()` and no `extractCamera()` overload returning anything** — it is
  `public Camera mainCamera()` and `private void extractCamera(DeltaTracker, float)`.
- `mainRenderTarget()` is the only public accessor for the screen framebuffer.

### 4.4 `CameraRenderState` — the ghost-camera payload (all fields public & mutable)

```java
public class net.minecraft.client.renderer.state.level.CameraRenderState {
  public net.minecraft.core.BlockPos blockPos;
  public net.minecraft.world.phys.Vec3 pos;
  public float xRot;
  public float yRot;
  public boolean initialized;
  public boolean isPanoramicMode;
  public boolean isFrustumCaptured;
  public boolean isFirstPerson;
  public boolean smartCull;
  public org.joml.Quaternionf orientation;
  public float cameraEntityPartialTicks;
  public net.minecraft.client.renderer.culling.Frustum cullFrustum;
  public net.minecraft.world.level.material.FogType fogType;
  public net.minecraft.client.renderer.fog.FogData fogData;
  public float hudFov;
  public float depthFar;
  public org.joml.Matrix4f projectionMatrix;
  public org.joml.Matrix4f viewRotationMatrix;
  public net.minecraft.client.renderer.state.level.CameraEntityRenderState entityRenderState;
  public net.minecraft.client.renderer.state.level.CameraRenderState();
}
```

```java
public class net.minecraft.client.renderer.state.level.LevelRenderState {
  public net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState;
  public final net.minecraft.client.renderer.state.level.PlayerRenderState playerRenderState;
  public final java.util.List<net.minecraft.client.renderer.state.level.SectionUpdateRenderState> sectionUpdateRenderStates;
  public final java.util.List<net.minecraft.client.renderer.entity.state.EntityRenderState> entityRenderStates;
  public final java.util.List<net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState> blockEntityRenderStates;
  public net.minecraft.client.renderer.state.level.BlockOutlineRenderState blockOutlineRenderState;
  public final java.util.List<net.minecraft.client.renderer.state.level.BlockBreakingRenderState> blockBreakingRenderStates;
  public final net.minecraft.client.renderer.state.level.WeatherRenderState weatherRenderState;
  public final net.minecraft.client.renderer.state.level.WorldBorderRenderState worldBorderRenderState;
  public final net.minecraft.client.renderer.state.level.SkyRenderState skyRenderState;
  public final net.minecraft.client.renderer.state.level.ParticlesRenderState particlesRenderState;
  public long gameTime;
  public float worldPartialTicks;
  public int lastEntityRenderStateCount;
  public int cloudColor;
  public float cloudHeight;
  public boolean render3dCrosshair;
  public boolean renderWireframeTerrain;
  public boolean shouldUseMultiDrawIndirectForTerrain;
  public java.lang.Runnable playerCompiledSectionCallback;
  public net.minecraft.client.renderer.state.level.ChunkLoadingRenderState chunkLoadingRenderState;
  public boolean shouldResetChunkLayerSampler;
  public boolean shouldShowEntityOutlines;
  public boolean shouldResetSkyRenderer;
  public net.minecraft.client.renderer.state.level.LevelRenderState();
  public void reset();
}
```

### 4.5 `net.minecraft.client.renderer.LevelRenderer`

```java
public class net.minecraft.client.renderer.LevelRenderer implements java.lang.AutoCloseable {
  public static final int OIT_WAVELET_RANK;
  public static final int OIT_COEFFICIENT_COUNT;
  public static final int OIT_TRANSMITTANCE_TARGET_COUNT;
  private final net.minecraft.client.renderer.state.level.LevelRenderState levelRenderState;
  private final net.minecraft.client.renderer.state.OptionsRenderState optionsRenderState;
  private final com.mojang.blaze3d.pipeline.RenderTarget entityOutlineTarget;
  private final net.minecraft.client.renderer.LevelTargetBundle targets;

  public net.minecraft.client.renderer.LevelRenderer(net.minecraft.client.renderer.entity.EntityRenderDispatcher, net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher, net.minecraft.client.resources.model.ModelManager, net.minecraft.client.renderer.texture.TextureManager, net.minecraft.client.resources.model.sprite.AtlasManager, net.minecraft.client.renderer.ShaderManager, net.minecraft.client.renderer.GameRenderer, int, int);

  public void render(com.mojang.blaze3d.resource.GraphicsResourceAllocator, boolean, net.minecraft.client.renderer.state.level.CameraRenderState, com.mojang.renderpearl.api.buffers.GpuBufferSlice, org.joml.Vector4f, boolean, boolean);
  private void repositionCamera(net.minecraft.client.renderer.state.level.CameraRenderState);
  private void submitFeatures(net.minecraft.client.renderer.state.level.LevelRenderState, net.minecraft.client.renderer.SubmitNodeCollector, boolean);
  public net.minecraft.client.renderer.chunk.ChunkSectionsToRender prepareChunkRenders(org.joml.Matrix4fc, boolean);
  public net.minecraft.client.renderer.chunk.ChunkSectionsToRender prepareChunkRendersIndirect(org.joml.Matrix4fc, boolean);
  public void addTransientBlock(long, net.minecraft.client.renderer.state.level.TransientBlockRenderState);
  public void removeTransientBlocksInSection(long, long);
  public void resize(int, int);
  public void endFrame();
  public void close();
  public void blitEntityOutline();
  public void invalidateCompiledGeometry(net.minecraft.client.multiplayer.ClientLevel, net.minecraft.client.Options, net.minecraft.client.Camera, net.minecraft.client.color.block.BlockColors);
  public void clearVisibleSections();
  public void resetLevelRenderData();
  public boolean hasRenderedAllSections();
  public boolean isSectionCompiledAndVisible(net.minecraft.core.BlockPos, long);
  public net.minecraft.client.renderer.chunk.SectionRenderDispatcher sectionRenderDispatcher();
  public net.minecraft.client.renderer.ViewArea viewArea();
  public it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection> visibleSections();
  public net.minecraft.client.renderer.SectionOcclusionGraph sectionOcclusionGraph();
  public net.minecraft.client.renderer.SkyRenderer skyRenderer();
  public net.minecraft.client.renderer.CloudRenderer cloudRenderer();
  public net.minecraft.client.renderer.WeatherEffectRenderer weatherEffectRenderer();
  public net.minecraft.client.renderer.WorldBorderRenderer worldBorderRenderer();
}
```

`render` parameter names (recovered from `LocalVariableTable` of `LevelRenderer.class`):

```java
render(GraphicsResourceAllocator resourceAllocator,
       boolean renderOutline,
       CameraRenderState cameraState,
       GpuBufferSlice terrainFog,
       Vector4f fogColor,
       boolean shouldRenderSky,
       boolean consistentDepthRequired)
```

- There is **no `setupRender`**, **no `renderLevel`**, **no `renderSky`, no `renderChunkLayer`** on
  `LevelRenderer`. The single entry point is `render(...)` above; `GameRenderer.renderLevel()` is the thing
  named "renderLevel".
- `LevelRenderer.render` starts with `GameRenderer.useImprovedTransparency()` →
  `SubmitNodeStorage.setUseImprovedTransparency`, then `repositionCamera(cameraState)` (private), then
  `RenderSystem.getModelViewStack().pushMatrix().mul(...)`, then `submitFeatures(...)`, then builds a
  `FrameGraphBuilder`, imports `GameRenderer.mainRenderTarget()` via `importExternal`, creates internal
  targets from `RenderTargetDescriptor`, and executes. Nothing is bound globally.
- **Ghost-camera seam (highest leverage):** `LevelRenderer.targets` is a `LevelTargetBundle` with a public
  `replace(Identifier, ResourceHandle<RenderTarget>)`, and `MAIN_TARGET_ID` is public. Redirecting the main
  target + supplying a doctored `CameraRenderState` is the intended path; `@Redirect`/`@ModifyArg` on
  `GameRenderer.mainRenderTarget()` inside `render` also works.
- `LevelRenderer.render` has **no `setSectionDirty` / `needsUpdate`** (verified by grepping every class in
  `CLJAR` — only `LevelExtractor`, `ClientLevel`, `ClientChunkCache`, `ClientPacketListener` mention
  `setSectionDirty`, and `needsUpdate` only appears in the lightmap classes).

### 4.6 Mixin target shortlist

| Goal | Target | Visibility |
|---|---|---|
| Replace the camera used for one frame | `LevelRenderer#render(GraphicsResourceAllocator, boolean, CameraRenderState, GpuBufferSlice, Vector4f, boolean, boolean)` — `@ModifyVariable` arg 3 | `public` |
| Force camera position/rotation | `Camera#update(DeltaTracker)` or `Camera#extractRenderState(CameraRenderState, DeltaTracker)` | `public` |
| Move/rotate the camera directly | `Camera#move(FFF)`, `Camera#setRotation(FF)`, `Camera#setPosition(Vec3)` | `protected` |
| Change projection | `Camera#setupPerspective(FFFFF)` / `Camera#setupOrtho(FFFFZ)` | `private` |
| Swap the framebuffer | `LevelRenderer#render` → `targets` field, or `@Redirect` on `GameRenderer.mainRenderTarget()` | `private final` field / `public` method |
| Hide world for a screenshot | `LevelRenderer#render` args 6/7 (`shouldRenderSky`, `consistentDepthRequired`) | `public` |
| Insert after world, before HUD | `LevelRenderEvents.END_MAIN` | Fabric event |

---

## 5. `RenderTarget` / `TextureTarget` / main framebuffer

```java
public abstract class com.mojang.blaze3d.pipeline.RenderTarget {
  private static int UNNAMED_RENDER_TARGETS;
  public int width;
  public int height;
  protected final java.lang.String label;
  protected final com.mojang.renderpearl.api.GpuFormat colorFormat;
  protected final com.mojang.renderpearl.api.GpuFormat depthFormat;
  protected com.mojang.renderpearl.api.textures.GpuTexture colorTexture;
  protected com.mojang.renderpearl.api.textures.GpuTextureView colorTextureView;
  protected com.mojang.renderpearl.api.textures.GpuTexture depthTexture;
  protected com.mojang.renderpearl.api.textures.GpuTextureView depthTextureView;
  public com.mojang.blaze3d.pipeline.RenderTarget(java.lang.String, com.mojang.renderpearl.api.GpuFormat, com.mojang.renderpearl.api.GpuFormat);
  public void resize(int, int);
  public void destroyBuffers();
  public void copyDepthFrom(com.mojang.blaze3d.pipeline.RenderTarget);
  public void copyColorFrom(com.mojang.blaze3d.pipeline.RenderTarget);
  public void createBuffers(int, int);
  public void blitAndBlendToTexture(com.mojang.renderpearl.api.textures.GpuTextureView, com.mojang.renderpearl.api.textures.GpuTextureView);
  public com.mojang.renderpearl.api.textures.GpuTexture getColorTexture();
  public com.mojang.renderpearl.api.textures.GpuTextureView getColorTextureView();
  public com.mojang.renderpearl.api.textures.GpuTexture getDepthTexture();
  public com.mojang.renderpearl.api.textures.GpuTextureView getDepthTextureView();
  public boolean hasDepth();
  static {};
}
```

```java
public class com.mojang.blaze3d.pipeline.TextureTarget extends com.mojang.blaze3d.pipeline.RenderTarget {
  public com.mojang.blaze3d.pipeline.TextureTarget(java.lang.String, int, int, com.mojang.renderpearl.api.GpuFormat, com.mojang.renderpearl.api.GpuFormat);
}
```

- **`bindWrite` / `unbindWrite` do NOT exist** — `grep -rl "bindWrite\|unbindWrite"` over *every* class in
  both `MCJAR` and `CLJAR` returns nothing. Any `target.bindWrite(true)` recipe from 1.21.x will not compile.
  Rendering into a target is done by handing its `GpuTextureView` to a `FrameGraphBuilder` /
  `GpuDevice.createCommandEncoder()`, or by using `Screenshot.takeScreenshot(RenderTarget, Consumer<NativeImage>)`
  which does the readback for you.
- `TextureTarget` ctor needs **two `GpuFormat`s** (color, depth); the old `(label, w, h, useDepth)` is gone.
- `width`/`height` are still **public mutable `int` fields**; `resize(int,int)` and `createBuffers(int,int)` remain.
- `Minecraft#getMainRenderTarget()` — **NOT FOUND in jar**. Verified: `javap -p net.minecraft.client.Minecraft`
  has no `renderTarget` member, and the only four `MainRenderTarget` references in `Minecraft.class` bytecode are
  `invokevirtual net/minecraft/client/renderer/GameRenderer.mainRenderTarget:()Lcom/mojang/blaze3d/pipeline/RenderTarget;`.
  Correct call: `Minecraft.getInstance().gameRenderer.mainRenderTarget()`.

---

## 6. `NativeImage`

The package in the brief (`net.minecraft.client.renderer.texture.NativeImage`) is **NOT FOUND in jar**
(`unzip -l` returns `0 files` in both jars). The real class is
`com.mojang.blaze3d.platform.NativeImage` (clientonly jar, 18106 bytes).

```java
public final class com.mojang.blaze3d.platform.NativeImage implements java.lang.AutoCloseable {
  private final com.mojang.blaze3d.platform.NativeImage$Format format;
  private final int width;
  private final int height;
  private final boolean useStbFree;
  private long pixels;
  private final long size;

  public com.mojang.blaze3d.platform.NativeImage(int, int, boolean);
  public com.mojang.blaze3d.platform.NativeImage(com.mojang.blaze3d.platform.NativeImage$Format, int, int, boolean);
  public com.mojang.blaze3d.platform.NativeImage(com.mojang.blaze3d.platform.NativeImage$Format, int, int, boolean, long);

  public static com.mojang.blaze3d.platform.NativeImage read(java.io.InputStream) throws java.io.IOException;
  public static com.mojang.blaze3d.platform.NativeImage read(byte[]) throws java.io.IOException;
  public static com.mojang.blaze3d.platform.NativeImage read(java.nio.ByteBuffer) throws java.io.IOException;

  public void close();
  public boolean isClosed();
  public int getWidth();
  public int getHeight();
  public com.mojang.blaze3d.platform.NativeImage$Format format();
  public int getPixel(int, int);
  public void setPixel(int, int, int);
  public void setPixelABGR(int, int, int);
  public com.mojang.blaze3d.platform.NativeImage mappedCopy(java.util.function.IntUnaryOperator);
  public int[] getPixelsABGR();
  public int[] getPixels();
  public byte getLuminanceOrAlpha(int, int);
  public int[] makePixelArray();
  public void writeToFile(java.io.File) throws java.io.IOException;
  public void writeToFile(java.nio.file.Path) throws java.io.IOException;
  public boolean copyFromFont(org.lwjgl.util.freetype.FT_Face, int);
  public void copyFrom(com.mojang.blaze3d.platform.NativeImage);
  public void fillRect(int, int, int, int, int);
  public void copyRect(int, int, int, int, int, int, boolean, boolean);
  public void copyRect(com.mojang.blaze3d.platform.NativeImage, int, int, int, int, int, int, boolean, boolean);
  public void resizeSubRectTo(int, int, int, int, com.mojang.blaze3d.platform.NativeImage);
  public void untrack();
  public long getPointer();
  public java.nio.ByteBuffer getPixelBytes();
  public com.mojang.blaze3d.platform.Transparency computeTransparency(int, int, int, int);
  public com.mojang.blaze3d.platform.Transparency computeTransparency();
  static {};
}
```

```java
public final class com.mojang.blaze3d.platform.NativeImage$Format extends java.lang.Enum<com.mojang.blaze3d.platform.NativeImage$Format> {
  public static final com.mojang.blaze3d.platform.NativeImage$Format RGBA;
  public static final com.mojang.blaze3d.platform.NativeImage$Format RGB;
  public static final com.mojang.blaze3d.platform.NativeImage$Format LUMINANCE_ALPHA;
  public static final com.mojang.blaze3d.platform.NativeImage$Format LUMINANCE;
  public int components();
  public boolean hasRed();  public boolean hasGreen();  public boolean hasBlue();
  public boolean hasLuminance();  public boolean hasAlpha();
  public int redOffset();   public int greenOffset();  public int blueOffset();
  public int luminanceOffset();  public int alphaOffset();
  public boolean hasLuminanceOrRed();   public boolean hasLuminanceOrGreen();
  public boolean hasLuminanceOrBlue();  public boolean hasLuminanceOrAlpha();
  public int luminanceOrRedOffset();  public int luminanceOrGreenOffset();
  public int luminanceOrBlueOffset(); public int luminanceOrAlphaOffset();
}
```

- **`asByteArray()` does NOT exist.** The byte/raw accessors are `getPixelBytes() : ByteBuffer`,
  `getPixels() : int[]`, `getPixelsABGR() : int[]`, `makePixelArray() : int[]`, and
  `writeToFile(Path|File)` (which encodes PNG).
- No scale/resize helper beyond `resizeSubRectTo(int,int,int,int,NativeImage)` (destination must pre-exist with
  matching dims) and `copyFrom(NativeImage)` / `copyRect(...)`. There is **no `getPixelRGBA`**; `getPixel` +
  `setPixel`/`setPixelABGR` is the pair.
- Field order in `getPixel`'s int: the class exposes both an ABGR-explicit setter and a plain one, so prefer
  `setPixelABGR`/`getPixelsABGR` to avoid channel-order surprises when building the screenshot PNG.
- Readback path that already exists: `net.minecraft.client.Screenshot`:
  ```java
  public static void grab(java.io.File, com.mojang.blaze3d.pipeline.RenderTarget, java.util.function.Consumer<net.minecraft.network.chat.Component>);
  public static void grab(net.minecraft.client.Minecraft, boolean);
  public static void grab(java.io.File, java.lang.String, com.mojang.blaze3d.pipeline.RenderTarget, int, java.util.function.Consumer<net.minecraft.network.chat.Component>);
  public static void takeScreenshot(com.mojang.blaze3d.pipeline.RenderTarget, java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage>);
  public static void takeScreenshot(com.mojang.blaze3d.pipeline.RenderTarget, int, java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage>);
  ```
  `takeScreenshot(renderTarget, consumer)` gives you the `NativeImage` asynchronously — build our
  "ghost camera" PNG on top of this instead of hand-rolling a GPU download.

---

## 7. `com.mojang.blaze3d.vertex.PoseStack`

```java
public class com.mojang.blaze3d.vertex.PoseStack {
  private final java.util.List<com.mojang.blaze3d.vertex.PoseStack$Pose> poses;
  private int lastIndex;
  public com.mojang.blaze3d.vertex.PoseStack();
  public void translate(double, double, double);
  public void translate(float, float, float);
  public void translate(net.minecraft.world.phys.Vec3);
  public void scale(float, float, float);
  public void rotate(org.joml.Quaternionfc);
  public void rotate(com.mojang.math.Axis, float);
  public void rotateDegrees(com.mojang.math.Axis, float);
  public void rotateAround(org.joml.Quaternionfc, float, float, float);
  public void pushPose();
  public void popPose();
  public com.mojang.blaze3d.vertex.PoseStack$Pose last();
  public boolean isEmpty();
  public void setIdentity();
  public void mulPose(org.joml.Matrix4fc);
  public void mulPose(com.mojang.math.Transformation);
}
```

- No `push()`/`pop()` aliases and no `translate(Vector3f)`; use `pushPose()`/`popPose()`, `translate(Vec3)` or
  the 3-float form. `last().pose()` is the `org.joml.Matrix4f` (confirmed used in `GameRenderer.renderLevel`).
- Everything takes `org.joml` types, not `com.mojang.math.Matrix4f` — `Matrix4f` is gone from this package.

---

## 8. Fog / render-distance control (low priority, verified anyway)

```java
public class net.minecraft.client.renderer.fog.FogRenderer implements java.lang.AutoCloseable {
  public static final int FOG_UBO_SIZE;
  private static boolean fogEnabled;
  public net.minecraft.client.renderer.fog.FogRenderer();
  public void close();
  public void endFrame();
  public com.mojang.renderpearl.api.buffers.GpuBufferSlice getBuffer(net.minecraft.client.renderer.fog.FogRenderer$FogMode);
  public static boolean toggleFog();
  public net.minecraft.client.renderer.fog.FogData setupFog(net.minecraft.client.Camera, int, net.minecraft.client.DeltaTracker, float, net.minecraft.client.multiplayer.ClientLevel);
  public void updateBuffer(net.minecraft.client.renderer.fog.FogData);
}
public final class net.minecraft.client.renderer.fog.FogRenderer$FogMode extends java.lang.Enum<...> {
  public static final net.minecraft.client.renderer.fog.FogRenderer$FogMode NONE;
  public static final net.minecraft.client.renderer.fog.FogRenderer$FogMode WORLD;
}
public class net.minecraft.client.renderer.fog.FogData {
  public float environmentalStart;
  public float renderDistanceStart;
  public float environmentalEnd;
  public float renderDistanceEnd;
  public float skyEnd;
  public float cloudEnd;
  public org.joml.Vector4f color;
}
```

- `FogShape` — **NOT FOUND in jar** (no class name containing `FogShape` in either jar). Only
  `FogRenderer$FogMode.NONE/WORLD` and `FogData` exist. `net.minecraft.client.renderer.fog.environment.*`
  (`AtmosphericFogEnvironment`, `WaterFogEnvironment`, `LavaFogEnvironment`, `BlindnessFogEnvironment`,
  `DarknessFogEnvironment`, `PowderedSnowFogEnvironment`, `MobEffectFogEnvironment`) holds per-medium fog.
- Killing fog for the ghost shot: `@ModifyVariable`/`@Redirect` on `FogRenderer.setupFog` returning a
  `FogData` with huge `renderDistanceStart/End`, or intercept `FogRenderer.getBuffer(FogMode)` and substitute
  `getBuffer(FogMode.NONE)`. `toggleFog()` is a global static toggle — not per-frame safe.
- Chunk rebuild after teleporting the ghost camera:
  ```java
  // net.minecraft.client.multiplayer.ClientLevel
  public void setSectionDirtyWithNeighbors(int, int, int);
  public void setSectionRangeDirty(int, int, int, int, int, int);
  // net.minecraft.client.renderer.extract.LevelExtractor
  public void setSectionDirty(int, int, int);
  public void setSectionDirtyWithNeighbors(int, int, int);
  public void setSectionRangeDirty(int, int, int, int, int, int);
  public void allChanged();
  public void setLevel(net.minecraft.client.multiplayer.ClientLevel);
  ```
  `LevelExtractor` is reachable only through a mixin/accessor today (it is not exposed on
  `GameRenderer`/`LevelRenderer` by any public getter we found).

---

## 9. Hiding the F3 debug overlay

```java
// net.minecraft.client.gui.Hud
public void extractDebugOverlay(net.minecraft.client.gui.GuiGraphicsExtractor);
private final net.minecraft.client.gui.components.DebugScreenOverlay debugOverlay;

// net.minecraft.client.gui.components.DebugScreenOverlay
public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor);
public boolean showDebugScreen();
public boolean showProfilerChart();
public boolean showNetworkCharts();
public boolean showFpsCharts();
public boolean showLightmapTexture();
public void toggleProfilerChart();
public void toggleFpsCharts();
public void toggleNetworkCharts();
public void toggleLightmapTexture();
public void reset();
```

`Hud.extractDebugOverlay` is a pure delegate — its bytecode is exactly:

```
Field debugOverlay:Lnet/minecraft/client/gui/components/DebugScreenOverlay;
Method net.minecraft.client.gui.components.DebugScreenOverlay.extractRenderState:(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V
```

so **there is no boolean gate inside `Hud.extractDebugOverlay`**; the gate lives in
`Hud.extractRenderState` (which reads `Hud.isHidden`) and/or in `DebugScreenOverlay.extractRenderState`
via `DebugScreenEntryList`.

```java
public class net.minecraft.client.gui.components.debug.DebugScreenEntryList {
  private boolean isOverlayVisible;
  public boolean isCurrentlyEnabled(net.minecraft.resources.Identifier);
  public void setStatus(net.minecraft.resources.Identifier, net.minecraft.client.gui.components.debug.DebugScreenEntryStatus);
  public boolean toggleStatus(net.minecraft.resources.Identifier);
  public java.util.Collection<net.minecraft.resources.Identifier> getCurrentlyEnabled();
  public void toggleDebugOverlay();
  public void setOverlayVisible(boolean);
  public boolean isOverlayVisible();
  public void rebuildCurrentList();
  public long getCurrentlyEnabledVersion();
}
```

```java
public class net.minecraft.client.gui.components.debug.DebugScreenEntries {
  private static final java.util.Map<net.minecraft.resources.Identifier, net.minecraft.client.gui.components.debug.DebugScreenEntry> ENTRIES_BY_ID;
  public static final net.minecraft.resources.Identifier GAME_VERSION;
  public static final net.minecraft.resources.Identifier FPS;
  public static final net.minecraft.resources.Identifier TPS;
  public static final net.minecraft.resources.Identifier MEMORY;
  public static final net.minecraft.resources.Identifier DETAILED_MEMORY;
  public static final net.minecraft.resources.Identifier SYSTEM_SPECS;
  public static final net.minecraft.resources.Identifier LOOKING_AT_BLOCK_STATE;
  public static final net.minecraft.resources.Identifier LOOKING_AT_BLOCK_TAGS;
  public static final net.minecraft.resources.Identifier LOOKING_AT_FLUID_STATE;
  public static final net.minecraft.resources.Identifier LOOKING_AT_FLUID_TAGS;
  public static final net.minecraft.resources.Identifier LOOKING_AT_ENTITY;
  public static final net.minecraft.resources.Identifier LOOKING_AT_ENTITY_TAGS;
  public static final net.minecraft.resources.Identifier CHUNK_RENDER_STATS;
  … (further Identifier constants; class is large, not fully dumped)
}
```

- **Cleanest hook:** `Minecraft.debugEntries` is a `public final DebugScreenEntryList` field, so at runtime:
  `mc.debugEntries.setOverlayVisible(false)` and restore the previous `isOverlayVisible()` after the round.
  No mixin needed.
- If a mixin is preferred: `@Inject(method = "extractDebugOverlay", at = @At("HEAD"), cancellable = true)` on
  `net.minecraft.client.gui.Hud` (public, exact descriptor `(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V`).
- **`Minecraft#options` has NO `renderDebug` and NO `debugEnabled` field** (verified: `javap -p
  net.minecraft.client.Options` contains neither; `Options` only exposes `keyDebugOverlay` and
  `keyDebugModifier` `KeyMapping`s). The old `Options.renderDebug` is gone — use `Minecraft.debugEntries`.

---

## 10. `Options` — fields relevant to our UI

```java
public class net.minecraft.client.Options {
  public static final int RENDER_DISTANCE_SHORT;
  public static final int RENDER_DISTANCE_FAR;
  public static final int RENDER_DISTANCE_REALLY_FAR;
  public static final int RENDER_DISTANCE_EXTREME;
  public static final int UNLIMITED_FRAMERATE_CUTOFF;
  public static final int AUTO_GUI_SCALE;
  public static final int DEBUG_GUI_SCALE_UNCHANGED;

  public java.util.List<java.lang.String> resourcePacks;
  public java.util.List<java.lang.String> incompatibleResourcePacks;
  public java.lang.String fullscreenVideoModeString;
  public boolean hideServerAddress;
  public boolean advancedItemTooltips;
  public boolean pauseOnLostFocus;
  public int overrideWidth;
  public int overrideHeight;
  public net.minecraft.client.tutorial.TutorialSteps tutorialStep;
  public boolean joinedFirstServer;
  public int glDebugVerbosity;
  public boolean skipMultiplayerWarning;
  public java.lang.String lastMpIp;
  public boolean smoothCamera;
  public java.lang.String languageCode;
  public boolean onboardAccessibility;
  public boolean syncWrites;
  public boolean startedCleanly;

  public final net.minecraft.client.KeyMapping keyUp;
  public final net.minecraft.client.KeyMapping keyLeft;
  public final net.minecraft.client.KeyMapping keyDown;
  public final net.minecraft.client.KeyMapping keyRight;
  public final net.minecraft.client.KeyMapping keyJump;
  public final net.minecraft.client.KeyMapping keyShift;
  public final net.minecraft.client.KeyMapping keySprint;
  public final net.minecraft.client.KeyMapping keyInventory;
  public final net.minecraft.client.KeyMapping keySwapOffhand;
  public final net.minecraft.client.KeyMapping keyDrop;
  public final net.minecraft.client.KeyMapping keyUse;
  public final net.minecraft.client.KeyMapping keyAttack;
  public final net.minecraft.client.KeyMapping keyPickItem;
  public final net.minecraft.client.KeyMapping keyChat;
  public final net.minecraft.client.KeyMapping keyPlayerList;
  public final net.minecraft.client.KeyMapping keyCommand;
  public final net.minecraft.client.KeyMapping keyFriends;
  public final net.minecraft.client.KeyMapping keySocialInteractions;
  public final net.minecraft.client.KeyMapping keyScreenshot;
  public final net.minecraft.client.KeyMapping keyTogglePerspective;
  public final net.minecraft.client.KeyMapping keySmoothCamera;
  public final net.minecraft.client.KeyMapping keyFullscreen;
  public final net.minecraft.client.KeyMapping keyAdvancements;
  public final net.minecraft.client.KeyMapping keyQuickActions;
  public final net.minecraft.client.KeyMapping keyToggleGui;
  public final net.minecraft.client.KeyMapping keyToggleSpectatorShaderEffects;
  public final net.minecraft.client.KeyMapping[] keyHotbarSlots;
  public final net.minecraft.client.KeyMapping keySaveHotbarActivator;
  public final net.minecraft.client.KeyMapping keyLoadHotbarActivator;
  public final net.minecraft.client.KeyMapping keySpectatorOutlines;
  public final net.minecraft.client.KeyMapping keySpectatorHotbar;
  public final net.minecraft.client.KeyMapping keyDebugOverlay;
  public final net.minecraft.client.KeyMapping keyDebugModifier;
  public final net.minecraft.client.KeyMapping keyDebugCrash;
  public final net.minecraft.client.KeyMapping keyDebugReloadChunk;
  public final net.minecraft.client.KeyMapping keyDebugShowHitboxes;
  public final net.minecraft.client.KeyMapping keyDebugClearChat;
  public final net.minecraft.client.KeyMapping keyDebugShowChunkBorders;
  public final net.minecraft.client.KeyMapping keyDebugShowAdvancedTooltips;
  public final net.minecraft.client.KeyMapping keyDebugCopyRecreateCommand;
  public final net.minecraft.client.KeyMapping keyDebugSpectate;
  public final net.minecraft.client.KeyMapping keyDebugSwitchGameMode;
  public final net.minecraft.client.KeyMapping keyDebugDebugOptions;
  public final net.minecraft.client.KeyMapping keyDebugFocusPause;
  public final net.minecraft.client.KeyMapping keyDebugDumpDynamicTextures;
  public final net.minecraft.client.KeyMapping keyDebugReloadResourcePacks;
  public final net.minecraft.client.KeyMapping keyDebugProfiling;
  public final net.minecraft.client.KeyMapping keyDebugCopyLocation;
  public final net.minecraft.client.KeyMapping keyDebugDumpVersion;
  public final net.minecraft.client.KeyMapping keyDebugPofilingChart;
  public final net.minecraft.client.KeyMapping keyDebugFpsCharts;
  public final net.minecraft.client.KeyMapping keyDebugNetworkCharts;
  public final net.minecraft.client.KeyMapping keyDebugLightmapTexture;
  public final net.minecraft.client.KeyMapping keyDebugSwitchTranslucencyMode;
  public final net.minecraft.client.KeyMapping[] debugKeys;
  public final net.minecraft.client.KeyMapping[] keyMappings;

  public net.minecraft.client.OptionInstance<java.lang.Integer> renderDistance();
  public net.minecraft.client.OptionInstance<java.lang.Integer> simulationDistance();
  public net.minecraft.client.OptionInstance<java.lang.Integer> framerateLimit();
  public net.minecraft.client.OptionInstance<java.lang.Integer> cloudRange();
  public net.minecraft.client.OptionInstance<java.lang.Integer> weatherRadius();
  public net.minecraft.client.OptionInstance<java.lang.Integer> mipmapLevels();
  public net.minecraft.client.OptionInstance<java.lang.Integer> biomeBlendRadius();
  public net.minecraft.client.OptionInstance<java.lang.Integer> guiScale();
  public net.minecraft.client.OptionInstance<java.lang.Integer> debugGuiScale();
  public net.minecraft.client.OptionInstance<java.lang.Double> sensitivity();
  public net.minecraft.client.OptionInstance<java.lang.Double> entityDistanceScaling();
  public net.minecraft.client.OptionInstance<java.lang.Double> chatOpacity();
  public net.minecraft.client.OptionInstance<java.lang.Double> textBackgroundOpacity();
  public net.minecraft.client.OptionInstance<java.lang.Double> chatScale();
  public net.minecraft.client.OptionInstance<java.lang.Double> chatWidth();
  public net.minecraft.client.OptionInstance<java.lang.Double> chatLineSpacing();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> vignette();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> improvedTransparency();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> ambientOcclusion();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> cutoutLeaves();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> highContrast();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> highContrastBlockOutline();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> autoJump();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> autoSuggestions();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> reducedDebugInfo();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> forceUnicodeFont();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> hideMatchedNames();
  …
  public int getEffectiveRenderDistance();   // used by GameRenderer.extractCamera
}
```

- **`hideGui` is NOT FOUND in `net.minecraft.client.Options`.** Verified by `grep -rl "hideGui"` across all
  extracted `CLJAR` classes: exactly one hit, `net/minecraft/client/renderer/ScreenEffectRenderer.class`
  (a constant-pool string, not a field). `Options`, `Minecraft`, `Hud` and `GameRenderer` contain **zero**
  occurrences. Any `mc.options.hideGui = true` from 1.21.x will not compile.
  The replacement is `net.minecraft.client.gui.Hud`:
  ```java
  private boolean isHidden;
  public void toggle();
  public boolean isHidden();
  ```
  `Hud.toggle()` is literally `isHidden = !isHidden`. Reachable as `Minecraft.getInstance().gui.hud`
  (`public final Gui gui` on `Minecraft`, `public final Hud hud` on `Gui`). `isHidden` is private, so
  either call `toggle()` (and toggle back) or `@Accessor` it.
- `Minecraft#options` — the field exists and its real name is exactly `options`:
  ```java
  public final net.minecraft.client.Options options;      // net.minecraft.client.Minecraft
  public final net.minecraft.client.renderer.GameRenderer gameRenderer;
  public final net.minecraft.client.renderer.LevelRenderer levelRenderer;
  public final net.minecraft.client.gui.Gui gui;
  public final net.minecraft.client.gui.components.debug.DebugScreenEntryList debugEntries;
  public net.minecraft.client.multiplayer.ClientLevel level;
  public net.minecraft.client.player.LocalPlayer player;
  public net.minecraft.world.phys.HitResult hitResult;
  ```
  All of `gameRenderer`, `levelRenderer`, `gui`, `options`, `debugEntries`, `level`, `player` are
  **public fields** — no `getInstance()`-chained getters needed beyond `Minecraft.getInstance()`.
- `renderDistance()` returns `OptionInstance<Integer>` (not a raw int) — call `.get()`. For the ghost camera
  keep in mind `Options.getEffectiveRenderDistance()` is what `GameRenderer.extractCamera` feeds to
  `FogRenderer.setupFog`, i.e. fog is driven by the **local player's** render distance, not the ghost camera's.
- Window/GUI scale for HUD coordinates (`com.mojang.blaze3d.platform.Window`):
  ```java
  public int getWidth();  public int getHeight();
  public int getScreenWidth();  public int getScreenHeight();
  public int getGuiScaledWidth();  public int getGuiScaledHeight();
  public int getGuiScale();  public void setGuiScale(int);
  ```
  and `GuiGraphicsExtractor` also exposes `guiWidth()` / `guiHeight()` directly — use those inside a
  `HudElement` rather than reading `Window`.
