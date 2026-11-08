# Minecraft 26.3 (Fabric) 防 AI 幻觉 API 避坑与开发规范指南 (v2.0)

> [!CAUTION]
> **致执行代码编写的 AI Agent（必读指令）**：
> Minecraft 自 `26.1` 起进行了史上最大规模的底层重构，并在 `26.2` 和 `26.3`（"Wilderness Bound"）继续彻底重写了渲染管线、窗口输入后端与大量核心类。
> **严禁凭借训练集中 1.20.x / 1.21.x 的旧记忆编写代码！** 任何写出 `ResourceLocation`、`GuiGraphics`、`Screen#render`、`Gui#render`、`RenderSystem.disableDepthTest()`、`PoseStack#mulPose`、`modImplementation`、`remapJar`、`KeyBindingHelper`、`HudRenderCallback`、`SignBlockEntity#getFrontText` 或同步版 `Screenshot#takeScreenshot(RenderTarget)` 的代码在 `26.3` 下**100% 无法通过编译**。请严格遵循本文档给出的真实签名与代码模板。

---

## 1. 构建系统与工程配置巨变（Unobfuscated Loom & Java 25）

从 `26.1` 开始，**Minecraft 官方彻底移除了代码混淆（Unobfuscated）**，并且内置了官方参数名：
1. **没有混淆映射（No Mappings）**：不再存在 `mappings loom.officialMojangMappings()` 或 Yarn 映射；Fabric 已停止维护 Yarn，全局直接使用 Mojang 官方无混淆类名。
2. **Loom 插件 ID 变更**：无混淆版本必须使用 `id 'net.fabricmc.fabric-loom'`（旧版带重映射的插件已被拆分为 `net.fabricmc.fabric-loom-remap`）。
3. **废除 `mod*` 依赖配置与 `remapJar`**：`modImplementation`、`modCompileOnly`、`modRuntimeOnly` 已全部移除！直接用 Gradle 原生的 `implementation`、`compileOnly`、`runtimeOnly`；打包直接用 `jar` 任务，不再有 `remapJar`。
4. **Java 与 Gradle 硬性要求**：Minecraft `26.3` 强制要求 **Java 25**（`JavaVersion.VERSION_25`，Mixin `compatibilityLevel: "JAVA_25"`）以及 **Gradle 9.6.0+**（官方示例使用 `Gradle 9.8.2`）。充分利用 Java 25 的 **`Executors.newVirtualThreadPerTaskExecutor()`** 处理 AI HTTP 请求与本地瓦片磁盘 I/O。
5. **`fabric.mod.json` 依赖 ID**：Fabric API 的 mod ID 只能写 `"fabric-api"`，旧的 `"fabric"` 别名已在 26.1 彻底移除。

### 1.1 标准 `gradle.properties`（26.3 官方真实版本）

```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
org.gradle.configuration-cache=true

# Fabric Properties (Minecraft 26.3 Official Stable)
minecraft_version=26.3
loader_version=0.20.1
loom_version=1.19-SNAPSHOT
fabric_api_version=0.165.2+26.3

# Mod Properties
version=1.0.0
group=com.craftguessr
```

### 1.2 标准 `build.gradle`（26.3 无混淆写法）

```groovy
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
    id 'maven-publish'
}

loom {
    splitEnvironmentSourceSets()

    mods {
        "craftguessr" {
            sourceSet sourceSets.main
            sourceSet sourceSets.client
        }
    }
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    // 注意：26.1+ 严禁使用 modImplementation，一律使用标准 implementation！
    // 注意：26.1+ 严禁添加 mappings 配置行！
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
}

tasks.withType(JavaCompile).configureEach {
    it.options.release = 25
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
```

### 1.3 标准 `fabric.mod.json` 与 `mixins.json`

```json
{
  "schemaVersion": 1,
  "id": "craftguessr",
  "version": "${version}",
  "name": "CraftGuessr",
  "environment": "*",
  "entrypoints": {
    "main": ["com.craftguessr.CraftGuessrMod"],
    "client": ["com.craftguessr.client.CraftGuessrClient"]
  },
  "mixins": [
    "craftguessr.mixins.json",
    {
      "config": "craftguessr.client.mixins.json",
      "environment": "client"
    }
  ],
  "depends": {
    "fabricloader": ">=0.20.1",
    "minecraft": "~26.3",
    "java": ">=25",
    "fabric-api": "*"
  }
}
```
> **注意**：`craftguessr.mixins.json` 和 `craftguessr.client.mixins.json` 中的 `"compatibilityLevel"` 必须写 `"JAVA_25"`，且无需配置 `refmap`。

---

## 2. AI 最容易写错的 17 大致命幻觉对照表（1.20/1.21 旧写法 vs 26.3 真写法）

| 序号 | 场景 | ❌ 1.20 / 1.21 旧写法（26.3 编译报错） | ✅ Minecraft 26.3 正确写法 |
| :--- | :--- | :--- | :--- |
| **1** | **资源标识符类名** | `ResourceLocation.fromNamespaceAndPath(ns, path)` | `net.minecraft.resources.Identifier.fromNamespaceAndPath(ns, path)` 或 `Identifier.withDefaultNamespace(path)`（Mojang 在 26.1 将 `ResourceLocation` 正式改名为 `Identifier`！） |
| **2** | **GUI 绘图上下文** | `GuiGraphics` / `DrawContext` | `net.minecraft.client.gui.GuiGraphicsExtractor` |
| **3** | **GUI 绘制文字** | `graphics.drawString(font, text, x, y, color, shadow)`<br/>`graphics.drawCenteredString(...)` | `graphics.text(font, text, x, y, color, shadow)`<br/>`graphics.centeredText(font, text, x, y, color)`<br/>⚠️ **颜色必须带 Alpha 通道（ARGB，如 `0xFFFFFFFF`）**，传 `0xFFFFFF` 会完全透明！ |
| **4** | **GUI 绘制贴图** | `graphics.blit(id, x, y, u, v, w, h, tw, th)` | `graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, tw, th)`（第一参数必须传 `RenderPipeline`） |
| **5** | **Screen 渲染方法** | `public void render(GuiGraphics g, int mx, int my, float d)`<br/>`public void renderBackground(...)` | `public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float d)`<br/>`public void extractBackground(...)` |
| **6** | **Widget 渲染方法** | `protected void renderWidget(GuiGraphics g, int mx, int my, float d)` | `protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float d)` |
| **7** | **打开/获取当前 Screen** | `Minecraft.getInstance().setScreen(screen)`<br/>`Minecraft.getInstance().screen` | `Minecraft.getInstance().gui.setScreen(screen)`<br/>`Minecraft.getInstance().gui.screen()`（26.2 起移入 `Gui` 类） |
| **8** | **HUD 渲染与接管** | `HudRenderCallback.EVENT.register(...)`<br/>Mixin `Gui#render(GuiGraphics, DeltaTracker)` | `HudElementRegistry`（`HudRenderCallback` 已删除！）<br/>Mixin 目标为 `Hud#extractRenderState(GuiGraphicsExtractor, DeltaTracker)`（26.2 起从 `Gui` 拆分到 `Hud` 类） |
| **9** | **穿墙渲染（禁用深度测试）** | `RenderSystem.disableDepthTest();` | **全局 `disableDepthTest()` 已删除**！必须在构建 `RenderPipeline` 时声明 `.withDepthStencilState(Optional.empty())` |
| **10** | **Screen 鼠标/键盘事件** | `mouseClicked(double x, double y, int btn)`<br/>`keyPressed(int key, int scan, int mod)`<br/>`Screen.hasShiftDown()` | `mouseClicked(MouseButtonEvent event, boolean doubleClick)`<br/>`keyPressed(KeyEvent event)`<br/>`event.hasShiftDown()` 或 `Minecraft.getInstance().hasShiftDown()`（`Screen.hasShiftDown()` 在 26.3 已删除） |
| **11** | **自定义输入框焦点（SDL3）** | 仅调用 `super.setFocused(focused)` | **26.3 换用 SDL3 替代 GLFW**！自定义文本输入组件必须在 `setFocused` 中调用 `Minecraft.getInstance().onTextInputFocusChange(this, focused)`，否则 SDL 输入法失步导致无法打字！ |
| **12** | **按键绑定注册** | `KeyBindingHelper.registerKeyBinding(new KeyMapping(name, type, key, "category.str"))` | `KeyMappingHelper.registerKeyMapping(new KeyMapping(name, type, key, KeyMapping.Category.register(id)))`（分类改为 `KeyMapping.Category` 对象，Helper 改名） |
| **13** | **帧缓冲截图（巡礼相机）** | `NativeImage img = Screenshot.takeScreenshot(renderTarget);` | **同步方法在 26.3 已移除**！必须使用异步回调：<br/>`Screenshot.takeScreenshot(renderTarget, nativeImage -> { ... });` |
| **14** | **矩阵旋转（PoseStack）** | `poseStack.mulPose(Axis.YP.rotationDegrees(90f))` | **26.3 已将 `mulPose` 重命名为 `rotate`**：<br/>`poseStack.rotate(Axis.YP.rotationDegrees(90f))` 或直接 `poseStack.rotateDegrees(Axis.YP, 90f)` |
| **15** | **读取告示牌文字** | `signEntity.getFrontText().getMessages(false)` (返回 `Component[]`) | **26.3 重构为 `SignTextSlot`**：<br/>`signEntity.getText(SignTextSlot.FRONT).getMessages(false)`（且返回值改为 `List<Component>`！） |
| **16** | **世界持久化 SavedData** | 重写 `save(CompoundTag, Provider)` + `SavedData.Factory` | **彻底改为 `Codec` + `SavedDataType`**：<br/>`new SavedDataType<>(id, MyData::new, CODEC, null)`，通过 `server.getDataStorage().computeIfAbsent(TYPE)` 获取 |
| **17** | **3D 世界绘制文字** | `font.drawInBatch(...)` | **`Font#drawInBatch` 在 26.2 已删除**！使用 `Display.TextDisplay` 实体（支持 `seeThrough`），或 `OrderedSubmitNodeCollector#submitText` / `Font#prepareText` |

---

## 3. 本项目（CraftGuessr）核心功能在 26.3 下的标准代码模板

### 3.1 GUI 屏幕、自定义地图缩放组件与 SDL3 输入事件（26.3 规范）

在编写大厅 GUI、题库相册 GUI 以及 2D 缩放扎针地图组件时，必须使用 `GuiGraphicsExtractor` 与新的事件 Record（`MouseButtonEvent` / `KeyEvent` / `CharacterEvent`）：

```java
package com.craftguessr.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class GuessMapScreen extends Screen {
    private static final Identifier PIN_ICON = Identifier.fromNamespaceAndPath("craftguessr", "textures/gui/pin.png");
    private final Screen parent;

    public GuessMapScreen(Screen parent) {
        super(Component.literal("CraftGuessr Map"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Button submitBtn = Button.builder(Component.literal("Submit Guess"), btn -> {
            // 提交扎针
        }).bounds(this.width - 130, this.height - 30, 120, 20).build();
        this.addRenderableWidget(submitBtn);
    }

    // ✅ 26.x 中 render 已重命名为 extractRenderState，参数为 GuiGraphicsExtractor
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        // 绘制半透明底框 (x1, y1, x2, y2, ARGB)
        graphics.fill(10, 10, 210, 210, 0xCC1E1E2E);
        // 绘制描边 (x, y, width, height, ARGB) —— 注意已由 renderOutline 改名为 outline
        graphics.outline(10, 10, 200, 200, 0xFFFFFFFF);

        // 绘制纹理必须传 RenderPipelines.GUI_TEXTURED
        graphics.blit(RenderPipelines.GUI_TEXTURED, PIN_ICON, 100, 100, 0, 0, 16, 16, 16, 16);

        // 绘制文字 (font, text, x, y, ARGB, shadow) —— 注意必须带 0xFF 不透明度
        graphics.centeredText(this.font, "Click to place pin", 110, 22, 0xFFFFFFFF);
    }

    // ✅ 26.3 鼠标点击签名：接收 MouseButtonEvent 与 boolean doubleClick
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (event.hasShiftDown()) {
            // Shift 快捷键逻辑（严禁调用已删除的 Screen.hasShiftDown()）
        }
        return super.mouseClicked(event, doubleClick);
    }

    // ✅ 26.3 鼠标拖拽签名
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return super.mouseDragged(event, dragX, dragY);
    }

    // ✅ 26.3 键盘按键签名：接收 KeyEvent
    // 注意：SDL3 下 event.key() 是物理扫描码 (SDLScancode)，event.keycode() 是逻辑键值 (SDLKeycode)
    @Override
    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        // ✅ 26.2+ 必须通过 this.minecraft.gui.setScreen 切换界面
        this.minecraft.gui.setScreen(this.parent);
    }
}
```

> [!WARNING]
> **自定义文本输入框必看（26.3 SDL3 焦点同步）**：
> 如果不使用原版 `EditBox` 而是自己实现接收 `charTyped(CharacterEvent event)` 的组件，**必须**在焦点变化时通知 `Minecraft#onTextInputFocusChange`，否则 SDL3 后端不会触发 `SDL_StartTextInput`，玩家将完全无法输入任何字符：
> ```java
> @Override
> public void setFocused(boolean focused) {
>     super.setFocused(focused);
>     Minecraft.getInstance().onTextInputFocusChange(this, focused);
> }
> ```

---

### 3.2 HUD 注册与「一键净空所有第三方坐标 HUD + F3」（26.3 `Hud` 架构）

1. **`HudRenderCallback` 已在 26.1 彻底删除**！本 Mod 自己的猜谜倒计时 HUD 必须注册到 `net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry`：
   ```java
   HudElementRegistry.attachElementBefore(
       VanillaHudElements.CHAT,
       Identifier.fromNamespaceAndPath("craftguessr", "round_timer_hud"),
       CraftGuessrHud::extractRoundHud
   );
   ```
2. **在答题观察期一键屏蔽 F3 与所有第三方坐标 HUD（MiniHUD / Sodium Extra / Xaero 等）**：
   - ❌ **切勿**去 Mixin 早已不存在的 `Gui#render` 或 `HudRenderCallback`！
   - ✅ **26.3 正确做法**：在 26.2+ 中，HUD 提取独立到了 `net.minecraft.client.gui.Hud` 类的 `extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker)`。通过 Mixin 拦截 `Hud#extractDebugOverlay`（屏蔽 F3），并在严格防作弊模式下于 `Hud#extractRenderState` 中只允许执行本 Mod 的 `CraftGuessrHud.extractRoundHud(graphics, deltaTracker)`，直接跳过第三方 HUD 层：
   ```java
   package com.craftguessr.client.mixin;

   import net.minecraft.client.DeltaTracker;
   import net.minecraft.client.gui.GuiGraphicsExtractor;
   import net.minecraft.client.gui.Hud;
   import org.spongepowered.asm.mixin.Mixin;
   import org.spongepowered.asm.mixin.injection.At;
   import org.spongepowered.asm.mixin.injection.Inject;
   import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
   import com.craftguessr.client.ClientGameState;
   import com.craftguessr.client.gui.CraftGuessrHud;

   @Mixin(Hud.class)
   public class HudMixin {
       // 1. 屏蔽 F3 调试界面
       @Inject(method = "extractDebugOverlay", at = @At("HEAD"), cancellable = true)
       private void onExtractDebugOverlay(GuiGraphicsExtractor graphics, CallbackInfo ci) {
           if (ClientGameState.isInActiveObservation()) {
               ci.cancel();
           }
       }

       // 2. 严格防作弊模式下：直接接管 HUD 提取，一键屏蔽所有挂载在 Hud 上的第三方坐标/小地图 Mod！
       @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
       private void onExtractHudRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
           if (ClientGameState.isInStrictAntiCheatObservation()) {
               CraftGuessrHud.extractRoundHud(graphics, deltaTracker);
               ci.cancel();
           }
       }
   }
   ```

---

### 3.3 自动驾驶巡礼状态机、区块编译检测与 1024x1024 水印拼图（26.3 Renderpearl & 异步截图）

1. **Renderpearl 包路径迁移（26.3 核心变化）**：
   26.3 将原先 `com.mojang.blaze3d` 下的大量底层 GPU 类拆分到了 `com.mojang.renderpearl`：
   - `GpuFormat` ➔ `com.mojang.renderpearl.api.GpuFormat`
   - `PrimitiveTopology` ➔ `com.mojang.renderpearl.api.pipeline.PrimitiveTopology`
   - `IndexType` ➔ `com.mojang.renderpearl.api.pipeline.IndexType`
   - `GpuBuffer` / `GpuBufferSlice` ➔ `com.mojang.renderpearl.api.buffers.*`
   - `GpuTexture` / `GpuTextureView` / `GpuSampler` ➔ `com.mojang.renderpearl.api.textures.*`（且均已由 class 改为 interface）
   - `CommandEncoder` / `RenderPass` / `GpuFence` ➔ `com.mojang.renderpearl.api.commands.*`
   - `GpuDevice` ➔ `com.mojang.renderpearl.api.device.GpuDevice`
2. **等待远端区块网格（Mesh）编译完成再抓拍**：
   客户端 TP 到候选点后，必须等待周围区块数据到达且 `LevelRenderer` 完成 Section 编译（否则拍出来是空天空或半截建筑）：
   - 在 `ClientTickEvents.END_CLIENT_TICK` 状态机中检查周围区块是否已加载（`client.level.getChunkSource().hasChunk(cx, cz)`），并调用 26.3 `client.levelRenderer.isSectionCompiledAndVisible(blockPos, 0L)`（注意 26.3 新增了 `long chunkFadeDuration` 参数）确认视野内 Section 已编译完毕后再触发快门。
3. **异步截图读取（`Screenshot#takeScreenshot`）与离散方位换算**：
   26.3 删除了同步返回 `NativeImage` 的 `Screenshot.takeScreenshot(RenderTarget)`，必须使用异步回调：
   ```java
   import net.minecraft.client.Screenshot;
   import com.mojang.blaze3d.pipeline.RenderTarget;
   import com.mojang.blaze3d.platform.NativeImage;

   // ✅ 26.3 正确写法：传入 Consumer<NativeImage>
   Screenshot.takeScreenshot(renderTarget, (NativeImage frameImage) -> {
       // 依次收集 NORTH (Yaw -180), EAST (Yaw -90), SOUTH (Yaw 0), WEST (Yaw 90) 四张 512x512 图像
       // 拼接为 1024x1024，并在各象限左上角烙印 [View A: NORTH] 等水印后交由虚拟线程发送给 AI
   });
   ```
   **离散方位转精确 MC Yaw/Pitch 换算公式**：
   ```java
   public static float computeYaw(String bestView, String pan) {
       float baseYaw = switch (bestView) {
           case "A" -> -180.0f; // NORTH
           case "B" -> -90.0f;  // EAST
           case "C" -> 0.0f;    // SOUTH
           case "D" -> 90.0f;   // WEST
           default -> 0.0f;
       };
       float panOffset = switch (pan) {
           case "LEFT_30" -> -30.0f;
           case "RIGHT_30" -> 30.0f;
           default -> 0.0f;
       };
       return baseYaw + panOffset;
   }
   ```
4. **动态瓦片纹理注册（`DynamicTexture`）**：
   `DynamicTexture` 构造函数必须传入 `Supplier<String>` 或 `String` 调试标签（Label）：
   ```java
   DynamicTexture tileTexture = new DynamicTexture(() -> "craftguessr_tile_" + tileX + "_" + tileZ, nativeImage);
   Identifier tileId = Identifier.fromNamespaceAndPath("craftguessr", "map_tile/" + tileX + "_" + tileZ);
   Minecraft.getInstance().getTextureManager().register(tileId, tileTexture);
   ```

---

### 3.4 3D 穿墙揭晓光束与 3 米球面穿墙全息字（Extraction / Drawing 双阶段）

- ❌ **切勿调用已删除的 `RenderSystem.disableDepthTest()` 或 `PoseStack#mulPose(...)`！**
- ✅ **26.3 穿墙光束标准写法**：
  1. 注册声明式无深度测试管线（`.withDepthStencilState(Optional.empty())`）：
     ```java
     private static final RenderPipeline BEAM_THROUGH_WALLS = RenderPipelines.register(
         RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
             .withLocation(Identifier.fromNamespaceAndPath("craftguessr", "pipeline/reveal_beam_through_walls"))
             .withDepthStencilState(Optional.empty()) // 穿墙显示，无视墙壁遮挡！
             .build()
     );
     ```
  2. 在 `LevelExtractionEvents.END_EXTRACTION` 提取揭晓光束数据存入不可变 Record，在 `LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN` 中使用 `StagedVertexBuffer` 绘制；旋转矩阵时使用 **`matrices.rotate(...)` 或 `matrices.rotateDegrees(Axis.YP, deg)`**。
  3. **3 米球面穿墙浮空名牌**：在距离靶心 3 米的猜测方向单位向量位置（`targetPos.add(dirVec.scale(3.0))`），生成开启了 `seeThrough`（穿墙属性 `setFlags((byte) (flags | Display.TextDisplay.FLAG_SEE_THROUGH))`）与 `BillboardConstraints.CENTER` 的 `Display.TextDisplay` 实体（或在客户端提交至 `SubmitNodeCollection#seeThrough` 阶段），实现无视地下室/窄巷墙体遮挡的 3D 全息指示。

---

### 3.5 服务端区块扫描、告示牌读取、玩家传送与 SavedData

1. **读取告示牌文本（26.3 `SignBlockEntity` 与 `SignTextSlot`）**：
   ```java
   import net.minecraft.world.level.block.entity.SignBlockEntity;
   import net.minecraft.world.level.block.entity.SignTextSlot;
   import net.minecraft.network.chat.Component;
   import java.util.List;

   if (blockEntity instanceof SignBlockEntity sign) {
       // ✅ 26.3 必须传入 SignTextSlot.FRONT / SignTextSlot.BACK，且返回 List<Component>
       List<Component> frontLines = sign.getText(SignTextSlot.FRONT).getMessages(false);
       List<Component> backLines = sign.getText(SignTextSlot.BACK).getMessages(false);
   }
   ```
2. **跨维度/同维度安全传送玩家（`ServerPlayer#teleportTo`）**：
   ```java
   import java.util.Set;
   import net.minecraft.server.level.ServerLevel;
   import net.minecraft.server.level.ServerPlayer;
   import net.minecraft.world.entity.Relative;

   // ✅ 最后一个 boolean 参数表示 setCamera (是否同步重置相机视角)
   serverPlayer.teleportTo(
       targetServerLevel,
       targetX, targetY, targetZ,
       Set.<Relative>of(),
       targetYaw, targetPitch,
       true
   );
   ```
3. **持久化保存原位快照与题库（`SavedDataType` + `Codec`）**：
   ```java
   public class PlayerSnapshotSavedData extends SavedData {
       public static final Codec<PlayerSnapshotSavedData> CODEC = RecordCodecBuilder.create(instance -> ...);

       public static final SavedDataType<PlayerSnapshotSavedData> TYPE = new SavedDataType<>(
           Identifier.fromNamespaceAndPath("craftguessr", "player_snapshots"),
           PlayerSnapshotSavedData::new,
           CODEC,
           null
       );

       public static PlayerSnapshotSavedData get(MinecraftServer server) {
           return server.getDataStorage().computeIfAbsent(TYPE);
       }
   }
   ```
