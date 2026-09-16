---
name: "pico-spatial-run"
description: "Run a PICO Spatial 0.10.x app offline: build, launch emulator without black screen, install, verify. Invoke when deploying PICO VR apps or hitting Gradle JDK, Unknown AVD, black-screen, or GLB entity-invalid errors."
---

# PICO Spatial 一键运行与排错

在 **PICO Spatial SDK 0.10.x**（SpaceOS / Swan 模拟器，Apple Silicon macOS）上，离线构建、无黑屏启动模拟器、安装并验证一个空间应用。本技能内置了实战中确认的全部环境坑与 GLB 加载规范。

## 何时触发
- 用户要在 PICO 0.10 模拟器上运行 / 部署 / 调试空间应用。
- 遇到：Gradle 只报 `26.0.1` 失败、`Unknown AVD name [Pico]`、模拟器中间视口全黑、点进沉浸场景 app 退出、`entity invalid`、模型加载失败 / `You should call this method in Android Main Thread`。

## 一键执行（优先）
脚本位于本技能目录 `scripts/pico-spatial-run.sh`。把它复制到目标工程根目录或直接用环境变量调用：

```bash
PKG=com.example.myapp \
PROJECT_DIR=/abs/path/to/project \
bash scripts/pico-spatial-run.sh --launch
```

参数：`--launch` 安装后拉起；`--no-build` 仅安装已有 APK；`--emu-only` 只启动模拟器。
可覆盖变量：`JBR`、`ADB`、`PICO_EMU_DIR`、`PICO_AVD_HOME`(默认 `~/.pico/avd`)、`PICO_AVD_NAME`(默认 `PICO_0.10`)、`APK_REL`、`PKG`。

执行前若模拟器窗口已由 Android Studio 正常打开，脚本会自动复用，不会重复启动。

## 关键事实（默认值，先核实再用）
- Gradle wrapper 8.10.2、AGP 8.7.3、Kotlin 1.8.10、Spatial BOM 0.10.7。
- JBR：`/Applications/Android Studio*.app/Contents/jbr/Contents/Home`（JDK 21）。
- adb：`~/Library/Android/sdk/platform-tools/adb`。
- 模拟器：`~/Library/PICO/sdk/0.10/emulator`；AVD：`~/.pico/avd/PICO_0.10.avd`。
- AVD 的 `emu-launch-params.txt` 记录了上次成功启动参数，可作为 ground truth。

## 三个环境坑（脚本已内置，手动排错时照此）

### 坑 1：Gradle 报 `26.0.1` 即失败
shell 默认 `JAVA_HOME` 可能指向过新的 JDK（26+），Gradle 8.10.2 不支持。用 JBR 21：
```bash
JAVA_HOME="/Applications/Android Studio Preview.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleDebug --offline
```

### 坑 2：`Unknown AVD name [Pico]`
PICO AVD 不在 `~/.android/avd`，名字也不是 `Pico`。正确：`ANDROID_AVD_HOME=~/.pico` 下的 `PICO_0.10`。
核对：`ANDROID_AVD_HOME=~/.pico/avd <emu>/emulator -list-avds`。

### 坑 3：模拟器外壳正常但中间 3D 视口全黑
裸跑 qemu 缺少 Android Studio 注入的环境变量，Qt 外壳误走 macOS Darwin/Metal 后端，日志特征：
`destroyWindowSurface ... Darwin backend`、`PxrCompositor right eye cpu cost abnormal ~950ms`。
必须用下方整套环境（最关键 `QT_OPENGL=software`、`ANDROID_SDK_HOME=~/.pico`），可执行文件用 `qemu/darwin-aarch64/qemu-system-aarch64`：

```bash
EMU=~/Library/PICO/sdk/0.10/emulator
ANDROID_AVD_HOME=~/.pico/avd ANDROID_SDK_HOME=~/.pico \
ANDROID_HOME=$EMU/system-images ANDROID_SDK_ROOT=$EMU/system-images \
ANDROID_EMULATOR_LAUNCHER_DIR=$EMU \
QT_OPENGL=software QT_AUTO_SCREEN_SCALE_FACTOR=none QT_SCALE_FACTOR=none \
QT_SCREEN_SCALE_FACTORS=none QT_LOGGING_RULES=default.warning=false \
QTWEBENGINE_CHROMIUM_FLAGS=--disable-gpu \
QT_QPA_PLATFORM_PLUGIN_PATH=$EMU/lib64/qt/plugins \
DYLD_LIBRARY_PATH=$EMU/lib64/qt/lib:$EMU/lib64/vulkan:$EMU/lib64/gles_angle11:$EMU/lib64/gles_angle9:$EMU/lib64/gles_angle:$EMU/lib64 \
  $EMU/qemu/darwin-aarch64/qemu-system-aarch64 \
  -netdelay none -netspeed full -avd PICO_0.10 -gpu angle_indirect
```
等 `adb shell getprop sys.boot_completed` 返回 `1`。

## 加载 3D 模型（GLB）的正确写法（否则进 Stage 崩溃）
对照官方示例 `animation` / `welcomespace`（0.10.7）：
1. GLB 放 `app/src/main/assets/` **根目录**，用 URI 字符串加载：`Entity.load("asset://name.glb")`。
   **不要**用 `assets.open()` 的 `Entity.load(inputStream, ModelFormat.GLTF)` 重载——返回实体渲染资源注册不全，`SpatialView` 会报 `createSpatialView batch, entity invalid`，渲染进程随后被 signal 9 杀掉。
2. **线程规则**：`Entity.load(...)` 放 `Dispatchers.IO`；但访问 / 修改 `entity.components` **必须在主线程** `Dispatchers.Main`，否则抛
   `IllegalStateException: You should call this method in Android Main Thread`，导致模型全失败、room 为空。
3. 用模型自带 TransformComponent 调位姿，不要新建覆盖：
   `entity.components[TransformComponent::class.java]?.apply { setPosition(...) }`。
4. 可抓取：`entity.components.set(InteractableComponent())`。
5. Compose 里在实体就绪后再进 `SpatialView(initial = { content, _ -> content.addEntity(entity) })`；可参考官方 `Deferred.await()` 模式。

## 运行与验证
- 安装：`adb install -r app/build/outputs/apk/debug/app-debug.apk`。
- 拉起：`adb shell monkey -p <PKG> -c android.intent.category.LAUNCHER 1`。
  注意：仅 `adb shell am start` 对 PICO 空间应用常常无效（stub activity，进入 pending launch）。优先用 monkey 或在空间界面点卡片。
- 进程存活：`adb shell pidof <PKG>`。
- 模型加载：日志 tag 由 app 自定（如 `adb logcat -s HotlineChamber *:S`），确认每个模型「已加载」且无 `Main Thread` 异常。
- 持续渲染：`SpatialRuntimeService Watchdog ... lastRenderTime=` 每秒更新即正常；Stage 容器 `name=<STAGE_ID> life=foreground`。
- PICO 空间 surface 受保护，`screencap` 常只得到 50 字节黑图，**不代表故障**，以 Watchdog/容器日志和模拟器窗口为准。

## 排错速查
| 现象 | 根因 | 处理 |
|---|---|---|
| 构建仅输出 `26.0.1` | JDK 过新 | 用 JBR 21 的 JAVA_HOME |
| Unknown AVD [Pico] | AVD 目录/名错误 | `ANDROID_AVD_HOME=~/.pico/avd`，名 `PICO_0.10` |
| 模拟器视口全黑、右眼 ~950ms | 缺 QT/ANDROID 环境变量 | 用坑 3 整套环境启动 |
| 点进入房间 app 退出 / entity invalid | InputStream 加载或实体无效 | 改 `asset://` + assets 根目录 |
| 加载失败 Main Thread | 组件操作在 IO 线程 | 组件操作切回主线程 |
| am start 无反应 | 空间应用 stub 机制 | 用 monkey 或空间界面点卡片 |
