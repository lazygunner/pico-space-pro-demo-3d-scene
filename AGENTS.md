<!-- pico-cli:plugin-context:pico-spatial-agentic-tools:start -->
## Plugin Context

Also read `./PICO-SPATIAL-AGENTIC-TOOLS.AGENTS.md` for PICO Spatial plugin guidance.
<!-- pico-cli:plugin-context:pico-spatial-agentic-tools:end -->

# Hotline Chamber（热线房间）项目导航

> 本文件是给 AI agent / 开发者的简明导航，不是教程。通用工作流以官方插件指引
> （`PICO-SPATIAL-AGENTIC-TOOLS.AGENTS.md` 与 `.trae/skills/`）为准。

## 应用事实
- 类型：PICO Spatial（SpaceOS）空间应用，Kotlin + Compose + Spatial SDK。
- 包名：`com.pico.spatial.sample.hotlinechamber`
- 启动 Activity：`.platform.LaunchActivity`（继承 `SpatialLaunchActivity`，是 stub；
  空间应用不能只靠 `am start` 拉起，优先 `pico-cli app launch`，必要时加 `--activity`）。
- Application：`.platform.SpatialApplication`，在 `onCreate` 调 `launch(::mainApp)`。
- 入口 DSL：`Main.kt` 的 `mainApp(scope)`
  - `DefaultWindowContainer { HomePanel() }`：启动悬浮窗（Home）。
  - `Stage(id = STAGE_ID) { ChamberStage() }`：全空间沉浸场景。
- 常量：`STAGE_ID = "HOTLINE_CHAMBER"`；进入方式 `openStage(STAGE_ID, StageStyle.Mixed)`。

## 3D 模型约定（重要）
- 4 个 PBR GLB 放在 `app/src/main/assets/` **根目录**（不是子目录）：
  `room.glb`（静态房间）、`chair.glb`、`sidetable.glb`、`phone.glb`。
- 加载方式必须是 `Entity.load("asset://<name>.glb")`；
  **不要**用 `assets.open()` 的 `Entity.load(inputStream, ModelFormat)`（会导致渲染层
  `entity invalid`、进 Stage 崩溃）。
- 线程规则：`Entity.load(...)` 在 `Dispatchers.IO`；访问 / 修改 `entity.components`
  必须在主线程 `Dispatchers.Main`，否则抛 “You should call this method in Android Main Thread”。
- 落地偏移：统一 `setPosition(Vector3(0f, -1.6f, 0f))`，修改模型自带 TransformComponent。
- 可抓取件：`chair` / `sidetable` / `phone` 设置 `InteractableComponent()`；`room` 静态。
- 相关代码：`ui/ChamberModels.kt`（加载）、`ui/ChamberStage.kt`（`SpatialView` + addEntity）、
  `ui/HomePanel.kt`（悬浮窗入口）。

## 构建 / 运行 / 验证
- 构建：`./gradlew :app:assembleDebug`（本机若命令行 JDK 过新会失败，用 Android Studio 自带
  JBR 21：`JAVA_HOME="/Applications/Android Studio Preview.app/Contents/jbr/Contents/Home"`）。
- APK：`app/build/outputs/apk/debug/app-debug.apk`。
- 模拟器 / 安装 / 拉起 / 日志：统一走官方 `pico-cli`（见 `spatial-emulator-usage` 技能），
  例如 `pico-cli emulator start`、`pico-cli app install <apk>`、
  `pico-cli app launch com.pico.spatial.sample.hotlinechamber --activity .platform.LaunchActivity`。
- 历史 0.10.7 手写脚本与排坑文档已移到 `archive/0.10.7/`，新栈勿用其中的 QT/AVD/JDK 硬编码。

## 本项目“进入房间成功”的冒烟判据
1. `./gradlew :app:assembleDebug` 通过，`pico-cli app install` 成功。
2. 点“进入房间”后进程不退出；Stage 容器 `name=HOTLINE_CHAMBER` 进入 `life=foreground`、
   `visible=true focus=true`。
3. 日志（tag `HotlineChamber`）依次出现 4 条“已加载模型: asset://{room,chair,sidetable,phone}.glb”，
   无 “Main Thread” 异常。
4. `SpatialRuntimeService Watchdog ... lastRenderTime=` 持续递增（持续渲染）。
5. 不反复出现 `createSpatialView batch, entity invalid`（首帧偶发一次可忽略）。

## 已知环境注意
- 官方 setup 选择的是 **traecli + local**：新 Skills/MCP 需在 Trae CLI 新会话才加载。
- 新栈目标为 PICO OS 6.1 + Android Studio 2025.1.x。
- 本机沙箱 npm 因 CA 问题，装 pico-cli 时需 `NODE_EXTRA_CA_CERTS=<系统钥匙串导出的根证书pem>`；
  setup 的 graphify/vault/profiler 辅助组件因本机 uv+Python 环境问题未装成，不影响构建与运行。
- PICO 空间 surface 受保护，`capture screenshot` 对空间容器可能是黑图；2D `input tap`
  不能驱动空间窗口/手势，空间内交互以日志判据 + 人工确认为准。
