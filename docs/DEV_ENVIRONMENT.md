# Minecraft 26.3 · Fabric Mod 开发环境（全 Docker 隔离）

整条工具链（JDK 25 / Gradle / Fabric Loom / 游戏客户端）都跑在容器里，宿主机**不装** java、gradle，
也不往 `~/.gradle`、`~/.m2` 写任何东西。删掉容器和卷，虚拟机就回到原样。

- 源码与构建产物：绑定挂载到当前目录（`./`）
- Gradle 依赖缓存：命名卷 `gradle-home`
- 游戏客户端：直接画在虚拟机**已有**的 X 显示 `:1` 上（就是你平时 VNC 看到的那个桌面），不用额外开桌面

## 快速开始

```bash
./dev.sh up        # 构建镜像 + 起常驻工具箱容器（第一次约 2-3 分钟）
./dev.sh build     # ./gradlew build -> build/libs/*.jar
./dev.sh client    # 启动带 mod 的游戏客户端（画面出现在 VNC 桌面里）
./dev.sh shot      # 给正在运行的客户端截图 -> run/screenshot.png
./dev.sh shell     # 进容器 bash，随便折腾
./dev.sh stop      # 停容器（保留缓存卷）
./dev.sh destroy   # 连镜像和卷一起删，磁盘全部回收
```

`./dev.sh` 无参数时会打印全部子命令。

## 版本矩阵

| 组件 | 版本 | 备注 |
| --- | --- | --- |
| Minecraft | 26.3 | 2026-09-15 发布的正式版 |
| Java | 25 | 26.3 的 `javaVersion.majorVersion = 25` |
| Fabric Loader | 0.20.1 | 稳定版，支持 26.3 |
| Fabric API | 0.165.2+26.3 | 适配 26.3 的最新版 |
| Fabric Loom | 1.19.4 | 钉死版本保证可复现（模板默认是 `1.19-SNAPSHOT`） |
| Gradle | 9.8.2 | 与官方模板 wrapper 一致 |

## 目录

```
compose.yaml            # dev（工具箱）/ client（宿主 X）/ client-vnc（自带 Xvfb + noVNC）
dev.sh                  # 日常操作入口
docker/Dockerfile       # 基础镜像：eclipse-temurin:25-jdk-noble + Gradle + X11/Mesa/Vulkan
docker/start-client.sh  # client-vnc 入口：Xvfb + x11vnc + noVNC + gradlew runClient
.devcontainer/          # VSCode Dev Containers 直接复用 compose 的 dev 服务
src/main/  src/client/  # 模板 mod（modid / com.example.ExampleMod）
```

## 两种客户端跑法

- **`./dev.sh client`** —— 默认。容器共享宿主 `/tmp/.X11-unix`，直接画在 `:1` 上，
  你在已有的 VNC 桌面里就能看到窗口。想换显示号：`HOST_DISPLAY=:0 ./dev.sh client`。
- **`./dev.sh clientvnc`** —— 宿主没有 X 时用。容器内自建 Xvfb，浏览器打开
  `http://<虚拟机IP>:6083/vnc.html`（裸 VNC 5907）。这两个端口是刻意避开宿主已占用的 6080/5900。

## 渲染：为什么必须走 Vulkan

MC 26.x 的渲染层 `renderpearl` 支持 OpenGL 与 Vulkan 两种后端。在 Xvfb 这类无 GPU 的 X server 上
OpenGL 建不出上下文（`Couldn't find matching GLX visual`），所以镜像里装了 `mesa-vulkan-drivers`
（lavapipe 软件 Vulkan）+ `libvulkan1`，日志里会看到：

```
Using graphics backend Vulkan, using drivers: 1.4.318 llvmpipe Mesa 25.2.8
```

软件渲染吃 CPU：这台 2 核 VM 能跑，但会比较卡；`runClient` 首帧前还会有一段资源加载。
宿主机如果真有 GPU，可以给 `client` 服务加 `- /dev/dri:/dev/dri` 并装对应 Vulkan/GL 驱动换成硬件渲染。

**声音**：容器默认拿不到声卡，`ALSOFT_DRIVERS=null` 让 OpenAL 用空设备，避免刷
`Failed to open OpenAL device`。要真声音：挂 `/dev/snd` 并设 `ALSOFT_DRIVERS=alsa`。
`Narrator ... Failed to load library flite` 也是同类无害告警，可以忽略。

## 改成本自己的 mod

1. `gradle.properties`：`maven_group`、`archives_base_name`
2. `src/main/resources/fabric.mod.json`：`id` / `name` / `version`、`entrypoints`、mixins 配置名
3. 把 `com.example` 包名和 `modid` 全量替换（含 `src/client/resources/modid.client.mixins.json`）
4. `./dev.sh build` 后产物是 `build/libs/<archives_base_name>-<version>.jar`

> 26.x 起 Loom 不再生成 `remapJar` 任务：游戏本体已经是可读的中间名（如 `net/minecraft/resources/Identifier`），
> 构建出来的 jar 直接可用，不需要再 remap。

## IDE

- **VSCode**：装 `Dev Containers` 扩展，直接「Reopen in Container」，会用 `compose.yaml` 的 `dev` 服务。
- 其他 IDE：`./dev.sh gen` 生成反编译源码，然后把项目按普通 Gradle 工程导入（JDK 选容器里的 25）。

## 磁盘占用

镜像约 2-3 GB（JDK + Mesa + Vulkan + Gradle），缓存卷另外几百 MB。全部集中在 Docker 里，
不想要了执行：

```bash
./dev.sh destroy
docker system df      # 看看还剩多少
```
