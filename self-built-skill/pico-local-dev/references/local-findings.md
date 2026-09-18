# 本机项目记录（2026-09-16）

这些是 HotlineChamberSpatial 的实测记录，不是其他项目的默认版本或路径。

## 已验证的环境修复

- Android Studio Preview 2025.1，自带 JBR 21.0.8；SDK 在 `~/Library/Android/sdk`，API 35。
- Gradle 8.10.2、AGP 8.7.3、Kotlin 1.8.10、Spatial BOM 0.10.7。
- 项目固定 `org.gradle.java.home` 后，取消 shell JAVA_HOME 的 offline assembleDebug 成功。
- CLI 0.5.0 public。`PICO_HOME=~/Library/PICO/sdk` 已写入 `.zshrc`，且新交互 zsh 实测可读取；
  自动化非交互 shell 仍需显式设置，否则 doctor 返回 PARTIAL。
- `PICO_0.10` 非 managed AVD；managed-only 列表不足以检查本机是否已有运行实例。
- `sdk/6.1` 原本只有 agent-vault 6.1.2，不能据此声称 6.1 模拟器已安装。
- root doctor 的 Editor capabilities/MCP launcher 错误是编辑器问题；Gradle 构建与旧模拟器
  上的 APK 安装启动已实测成功。不能把这些结果提升为全链路体检通过。
- 实际入口代码使用 StageStyle.Full，旧 AGENTS.md 写 Mixed 已修正。

## 验证对象

- 包名：`com.pico.spatial.sample.hotlinechamber`
- Activity：`.platform.LaunchActivity`
- Stage：`HOTLINE_CHAMBER`
- 日志 tag：`HotlineChamber`
- 资源：`room.glb`、`chair.glb`、`sidetable.glb`、`phone.glb`
- 完整业务验证需要 Stage foreground/visible/focus、4 个模型加载成功、持续渲染，
  无 Main Thread 异常与反复 entity invalid。单有 PID 不足以判定。

## 版本与回退

旧模拟器 bundle 0.10.0 与 AVD `PICO_0.10` 保留。迁移时仅在新目标验证完成后更新默认
运行文档；未完成的下载或缺失 artifact 不得登记为已迁移。

## 6.1 安装时发现的问题

`pico-cli emulator install 6.1 --format json -y` 首先调用 primer，包名为
`pico_spatial_emulator_20260902_v6.1.0_mac.zip`。primer 已写入 6.1/emulator/package.xml
（revision 6.1.0）和二进制后，CLI 仍报告
`completed emulator installation, but no managed bundle was discovered`，随后自动进入 native
下载、校验和解压流程。此时不要额外启动第二个 install；跟踪原命令完成结果，并注意临时包、
旧目录和新解压目录并存会额外占用空间。该现象不能泛化为所有 CLI 版本均有此问题。

最终根因：CLI 0.5.0 的 bundle 扫描在 `PICO_HOME` 根目录识别旧兼容布局后直接返回，
不会继续遍历 `6.1/emulator`。把版本参数从 6.1 改为 6.1.0 并不能修复；两次触发重复
primer 安装的命令已取消，未修改旧 AVD。依据本机 CLI 源码及实际 list/create 结果，修复为：

```bash
export PICO_HOME="$HOME/Library/PICO/sdk"
export PICO_EMULATOR_HOME="$PICO_HOME/6.1/emulator"
pico-cli emulator list --format json
pico-cli emulator create --bundle-version 6.1.0 --avd Pico_Emulator_6_1 --format json -y
pico-cli emulator start --bundle-version 6.1.0 --avd Pico_Emulator_6_1 --format json -y
```

create 已返回 `bundleVersion=6.1.0`、二进制
`~/Library/PICO/sdk/6.1/emulator/emulator` 与 `actualSysdir=.../6.1/emulator/system-images/system-images`。
不要把 `PICO_HOME` 永久改为 `sdk/6.1`，否则其他工具的版本目录解析可能受影响。

## 新版运行验证

- 新 AVD `Pico_Emulator_6_1` 启动成功，`bundleVersion=6.1.0`、`bootCompleted=true`。
- QEMU PID 54340 的路径来自 `sdk/6.1/emulator/qemu/darwin-aarch64/`；guest
  `vendor.qemu.version=6.1.0`、Spatial Runtime `6.1.0.0-rls.24`。
- 新实例仍使用 `emulator-5554`：设备序列号会复用，不能凭同一个 serial 推断是旧实例或新版。
- 原 BOM 0.10.7 APK 安装启动成功，PID 4804，多次查询保持存活，设备 crash buffer 无记录。
- Watchdog 的 focus 为本应用，lastRenderTime 从 121795 增加到 203180，证实当前窗口持续渲染。
- 用户尚未确认点击“进入房间”，没有 4 个模型加载或 Stage foreground 的证据；该业务场景
  不能标为通过。当前 CUA 无法将裸 QEMU 可执行文件识别为可操控 App，空间点击保留为人工步骤。
- 旧 AVD 与 bundle 保留；新 bundle 选择变量已持久化至 `.zshrc`。
- 最终 emulator doctor 返回 SUCCESS，新 bundle 路径正确。但 primer 子报告仍把 6.1 模拟器
  列为缺失，说明不同安装流程的组件发现结果存在差异；不要反复安装或篡改版本字段消除报告。
  交付时保留此差异与二进制/guest 版本证据。

## 后续清理状态（优先于上面的历史回退记录）

用户随后明确要求清理旧环境：已删除 0.10 工具目录、PICO_0.10 AVD/ini、安装 zip 缓存，
以及指向旧镜像的根目录 system-images 符号链接。现在仅保留 6.1 bundle 和新 AVD。
旧版回退已不可用；未删除仍被项目使用的 BOM 0.10.7、Gradle/Maven 缓存及共享 Android SDK。
清理后 offline assembleDebug 和新版 bundle/AVD 发现验证通过。

## Spatial BOM 6.1.9 升级与全链路验证（2026-09-18）

用户要求后续以 6.1 为基准。从 BOM 0.10.7 升到 `6.1.9`（设备运行时 6.1.0），
Kotlin 2.0.21、compile/target SDK 36、AGP 8.7.3（Gradle 8.10.2），JBR 21。

- 工具链：启用 `org.jetbrains.kotlin.plugin.compose` 2.0.21，弃用 `composeOptions`
  的 compilerExtension 写法；AGP 8.7 对 SDK 36 的告警用
  `android.suppressUnsupportedCompileSdk=36`（gradle.properties，Kotlin DSL 无对应属性）。
- API 实测差异（以编译器/实际 AAR 为准，旧文档可能滞后）：
  - `com.pico.spatial.ui.design.Text` 已无 `vibrant=`，改用
    `Modifier.vibrantEffect(Vibrant.X)`。
  - `PicoTheme.colorScheme.accent` 已移除；字段直接返回 `Color`，用 `fillPrimary`。
  - 3D API 保持兼容：`Entity.load("asset://x.glb")`、`InteractableComponent()`、
    `SpatialView(initial = { content, _ -> })`、`openStage(id, StageStyle.Full)`。
- 世界锁定：Stage 用 `StageStyle.Full` + `Immersion(default=100,min=0,max=100)`；
  模拟器无真机空间追踪，Mixed 会让内容半跟随头部。
- WindowContainer：`worldscaletype` Fixed(2) 在 6.1 下又小又远，改 Dynamic(1) 解决；
  打开位置由系统决定，默认容器无法配置初始位置。
- 输入：空间投影截图的按钮位置不能直接当输入坐标；用
  `uiautomator dump` 取真实 bounds（本次按钮 `[1064,849][1336,945]`，中心 1200,897）。
- 验证：Stage `stageStyle=3, immersion=100, focus=true`，`space_status=fullspace`，
  4 个 GLB 全加载、可交互 3 个、失败 0，Watchdog 心跳递增，无 FATAL/entity invalid/signal 9；
  BACK 后 Stage destroyed、Home 恢复。Home 在 Stage 打开时保留属官方标准模式。
