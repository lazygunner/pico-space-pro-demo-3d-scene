# PICO Spatial SDK 0.10.7 模拟器运行排坑实录

> 适用环境：Apple Silicon（M1/M2/M3）macOS、Android Studio（自带 JBR 21）、PICO Spatial SDK **0.10.7**、PICO 模拟器 0.10（SpaceOS / Swan, arm64-v8a, Android API 36）、Gradle 8.10.2 / AGP 8.7.3 / Kotlin 1.8.10。
>
> 目标：在**不额外下载任何依赖**的前提下，命令行离线构建、无黑屏启动模拟器、安装并验证一个空间应用。本文记录实战中确认的 5 个坑、根因与解决方案，并提供可直接分发的一键脚本与 Trae Skill。

---

## 1. 坑位一览

| # | 现象 | 关键报错 / 特征 | 根因 | 解决 |
|---|------|----------------|------|------|
| 1 | Gradle 构建秒失败，只打印一个版本号 | `26.0.1` | shell 默认 `JAVA_HOME` 指向过新的 JDK 26，Gradle 8.10.2 不支持 | 改用 Android Studio 自带 **JBR 21** |
| 2 | 模拟器起不来 | `Unknown AVD name [Pico]` | PICO 的 AVD 不在 `~/.android/avd`，名字也不叫 `Pico` | `ANDROID_AVD_HOME=~/.pico/avd`，AVD 名 **`PICO_0.10`** |
| 3 | 模拟器外壳正常，中间 3D 视口**纯黑** | `right eye cpu cost abnormal ~950ms`、`destroyWindowSurface ... Darwin backend` | 裸跑 qemu 缺环境变量，Qt 外壳误走 macOS Metal 后端 | 复刻 Android Studio 的整套 `QT_*`/`ANDROID_*` 环境变量（核心 `QT_OPENGL=software`） |
| 4 | 点"进入房间"后 app 退出 | `createSpatialView batch, entity invalid`，渲染进程 signal 9 | 用 `assets.open()` 的 `InputStream + ModelFormat` 加载 GLB，实体渲染资源未注册 | 改为 `Entity.load("asset://x.glb")`，GLB 放 `assets/` 根目录 |
| 5 | 模型全部加载失败 | `You should call this method in Android Main Thread` | 在 IO 线程访问 `entity.components` | `load` 放 IO 线程，**组件操作切回主线程** |

---

## 2. 坑 1：Gradle 只报 `26.0.1` 就失败

**现象**：命令行 `./gradlew :app:assembleDebug` 立即失败，错误信息几乎只有一个 `26.0.1`。

**根因**：终端环境的 `JAVA_HOME` 指向了过新的 JDK（本机是 Trae 自带 JDK 26）。Gradle 8.10.2 不支持 JDK 26，于是异常退出。Android Studio 内置终端/构建默认用自带 JBR 21，所以在 IDE 里点 Run 不会遇到。

**解决**：显式指定 Android Studio 自带的 JBR：

```bash
JAVA_HOME="/Applications/Android Studio Preview.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleDebug --offline
```

> 正式版路径为 `/Applications/Android Studio.app/Contents/jbr/Contents/Home`。`--offline` 可验证所有依赖均已命中本机 Gradle 缓存（本工程与官方样例依赖版本完全一致，无需联网）。

---

## 3. 坑 2：`Unknown AVD name [Pico]`

**现象**：照 `emulatorParams.ini` 里的 `PARAMS="-avd Pico ..."` 启动，弹窗报错 `Unknown AVD name [Pico], use -list-avds ...`。

**根因**：
- PICO 的 AVD **不在**标准 `~/.android/avd`，而在 `~/.pico/avd`；
- 真实 AVD 目录名是 **`PICO_0.10.avd`**（名字 `PICO_0.10`），`emulatorParams.ini` 里的 `Pico` 已失效；
- 命令行裸跑时没有设置 `ANDROID_AVD_HOME`，日志可见 `avd folder is (null)`。

**确认方法**：

```bash
EMU=~/Library/PICO/sdk/0.10/emulator
ANDROID_AVD_HOME=~/.pico/avd "$EMU/emulator" -list-avds   # 输出: PICO_0.10
ls ~/.pico/avd        # PICO_0.10.avd / PICO_0.10.ini
cat ~/.pico/avd/PICO_0.10.avd/emu-launch-params.txt       # 上次成功启动参数(ground truth)
```

`emu-launch-params.txt` 记录了上次成功启动的命令（`-netdelay none -netspeed full -avd PICO_0.10 -gpu angle_indirect`），是最可靠的参考。

---

## 4. 坑 3：模拟器中间视口全黑（最隐蔽）

**现象**：模拟器窗口标题栏、左侧工具栏、底部控制条都正常，但中央 3D 视口纯黑，连 PICO 系统桌面/环境都看不到；不是 app 的问题。

**根因**：Android Studio 通过 **SpatialPlugin** 启动模拟器时注入了一整套环境变量；命令行裸跑缺失这些变量，模拟器外壳的 Qt 窗口走了 macOS Darwin(Metal) 后端，导致空间合成器右眼帧渲染卡死。

典型日志特征：

```
ERROR | destroyWindowSurface: destroyWindowSurface called on Darwin backend!
E PxrCompositor: ATW_CPU right eye cpu cost abnormal, cost 946ms
E PxrCompositor: ATW_CPU right flush too long, cost ~950ms
E libEGL : called unimplemented OpenGL ES API
```

> 排查技巧：抓取 Android Studio 启动模拟器时的真实进程，可一次性拿到准确的命令行与环境变量：
> ```bash
> ps -Axo pid=,ppid=,command= | grep -F "PICO/sdk/0.10"
> QPID=$(ps -Axo pid=,comm= | awk '$2=="qemu-system-aarch64"{print $1; exit}')
> ps eww -o command= -p "$QPID" | tr ' ' '\n' | grep -E '^(ANDROID|QT|DYLD)'
> ```

**解决**：直接调用 `qemu-system-aarch64` 并复刻整套环境（最关键是 `QT_OPENGL=software` 与 `ANDROID_SDK_HOME=~/.pico`）：

```bash
EMU=~/Library/PICO/sdk/0.10/emulator
ANDROID_AVD_HOME=~/.pico/avd \
ANDROID_SDK_HOME=~/.pico \
ANDROID_HOME=$EMU/system-images \
ANDROID_SDK_ROOT=$EMU/system-images \
ANDROID_EMULATOR_LAUNCHER_DIR=$EMU \
QT_OPENGL=software \
QT_AUTO_SCREEN_SCALE_FACTOR=none QT_SCALE_FACTOR=none QT_SCREEN_SCALE_FACTORS=none \
QT_LOGGING_RULES=default.warning=false \
QTWEBENGINE_CHROMIUM_FLAGS=--disable-gpu \
QT_QPA_PLATFORM_PLUGIN_PATH=$EMU/lib64/qt/plugins \
DYLD_LIBRARY_PATH=$EMU/lib64/qt/lib:$EMU/lib64/vulkan:$EMU/lib64/gles_angle11:$EMU/lib64/gles_angle9:$EMU/lib64/gles_angle:$EMU/lib64 \
  $EMU/qemu/darwin-aarch64/qemu-system-aarch64 \
  -netdelay none -netspeed full -avd PICO_0.10 -gpu angle_indirect
```

等待开机完成：`adb shell getprop sys.boot_completed` 返回 `1`。GPU 模式仍用 `angle_indirect`（与官方一致），不要用 `host`——`host` 在 macOS 上同样会报 Darwin 后端错误。

---

## 5. 坑 4 + 5：GLB 模型加载导致进入房间即崩溃

### 5.1 现象
首页悬浮窗正常；点击"进入房间"打开沉浸 Stage 后，渲染进程约 20 秒后被 `signal 9 (Killed)`，日志：

```
W ware-HandleRenderResultSystem: createSpatialView batch, entity invalid
```

第一版代码用的是：

```kotlin
// 错误示范
val entity = context.assets.open("models/room.glb").use {
    Entity.load(it, ModelFormat.GLTF)
}
```

### 5.2 根因与正确加载方式（对照官方 animation / welcomespace 0.10.7）
1. **用 URI 字符串加载，GLB 放 `assets/` 根目录**：官方一律 `Entity.load("asset://name.glb")`。`InputStream + ModelFormat` 重载返回的实体不会正确注册渲染资源，加入 `SpatialView` 后即被判为 `entity invalid`。
2. **线程规则**：`Entity.load(...)` 放 `Dispatchers.IO`；但访问 / 修改 `entity.components` **必须在主线程**，否则抛
   `IllegalStateException: You should call this method in Android Main Thread`，使所有模型进入 catch、room 为空。
3. 调整位姿时**修改模型自带的 TransformComponent**，不要新建覆盖，以保留模型内部层级变换。

### 5.3 正确写法

```kotlin
suspend fun load(context: Context): Chamber {
    for (spec in specs) {                       // spec.uri = "asset://room.glb" 等
        val entity = withContext(Dispatchers.IO) { Entity.load(spec.uri) }   // IO 解码
        withContext(Dispatchers.Main) {                                      // 主线程操作组件
            entity.components[TransformComponent::class.java]?.apply { setPosition(offset) }
            if (spec.movable) entity.components.set(InteractableComponent())
        }
    }
    ...
}
```

Compose 侧在实体就绪后再渲染：

```kotlin
SpatialView(initial = { content, _ ->
    content.addEntity(room)
    movables.forEach { content.addEntity(it) }
})
```

### 5.4 验证成功的标志
- 日志依次输出每个模型的「已加载模型: asset://xxx.glb」，无 `Main Thread` 异常；
- Stage 容器 `name=<STAGE_ID> ... life=foreground`、`visible=true focus=true`；
- 进程持续存活（`adb shell pidof <PKG>` 不消失）；
- `SpatialRuntimeService: Watchdog ... lastRenderTime=` 每秒递增，表示持续渲染。

---

## 6. 安装与拉起的注意点

- 安装：`adb install -r app/build/outputs/apk/debug/app-debug.apk`。
- 拉起优先用 **monkey**，不要只用 `am start`：
  ```bash
  adb shell monkey -p <PKG> -c android.intent.category.LAUNCHER 1
  ```
  PICO 空间应用的入口是 `SpatialStubActivity`/stub 机制，单纯 `am start` 常进入 pending launch、进程不驻留（官方样例也一样）。
- PICO 空间 surface 受保护，`adb exec-out screencap` 往往只得到 50 字节黑图，**这不是故障**；以 Watchdog / 容器生命周期日志和模拟器实际窗口为准。

---

## 7. 一键流程（脚本 + Skill）

仓库内提供两份等价资产：

```
trae-skills/pico-spatial-run/
├── SKILL.md                      # Trae Skill：工作流说明 + 排错速查
└── scripts/pico-spatial-run.sh   # 一键：启动模拟器(如需) + 离线构建 + 安装(可拉起)
```

用法：

```bash
# 在工程根目录
PKG=com.pico.spatial.sample.myapp bash scripts/pico-spatial-run.sh --launch
```

参数：`--launch` 安装后拉起；`--no-build` 仅安装已有 APK；`--emu-only` 只启动模拟器。
可覆盖变量：`PROJECT_DIR`、`JBR`、`ADB`、`PICO_EMU_DIR`、`PICO_AVD_HOME`、`PICO_AVD_NAME`、`APK_REL`、`PKG`。脚本会在模拟器已由 Android Studio 打开时自动复用，不会重复启动。

### 分发给其他人
1. 把 `trae-skills/pico-spatial-run/` 整个目录拷给对方。
2. **作为 Trae Skill 使用**：将该目录放到对方工作区的 `.trae/skills/pico-spatial-run/`（即 `SKILL.md` 位于 `.trae/skills/pico-spatial-run/SKILL.md`），之后在对话中描述"运行/调试 PICO 空间应用"即可自动触发。
3. **仅当脚本用**：保留 `scripts/pico-spatial-run.sh`，按需用环境变量覆盖路径（默认值已按 Apple Silicon + 标准安装位置设定）。
4. 前提：对方已安装 PICO 模拟器 0.10（`~/Library/PICO/sdk/0.10/emulator`）且 AVD 已创建（`~/.pico/avd/PICO_0.10.avd`），Android Studio 自带 JBR 21。

---

## 8. 关键路径速查

| 项 | 路径 / 值 |
|----|-----------|
| JBR 21 | `/Applications/Android Studio*.app/Contents/jbr/Contents/Home` |
| adb | `~/Library/Android/sdk/platform-tools/adb` |
| PICO 模拟器目录 | `~/Library/PICO/sdk/0.10/emulator` |
| qemu 可执行 | `…/emulator/qemu/darwin-aarch64/qemu-system-aarch64` |
| AVD 主目录 | `~/.pico/avd` |
| AVD 名称 | `PICO_0.10` |
| 成功启动参数记录 | `~/.pico/avd/PICO_0.10.avd/emu-launch-params.txt` |
| 模拟器日志 | `/tmp/android-<user>/qemu-log/`、`/tmp/pico-emulator.log` |
| 官方示例 | `welcomespace`（房间/Stage）、`animation`（GLB 加载）、`physics`、`spatialvideo` |
