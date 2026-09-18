---
name: pico-local-dev
description: "整理、修复和迁移 macOS 上的 Android Studio 与 PICO 模拟器开发环境，构建并验证现有 Spatial 项目。适用于 JDK 不匹配、AVD/模拟器版本迁移、CLI 体检及安装启动排错；不自动升级业务 SDK。"
---

# PICO 本地开发环境与模拟器迁移

以项目文件、当前 CLI help 和运行证据为准。若项目附带官方 `pico-env-doctor`、
`spatial-emulator-usage`，先读取它们并沿用官方工作流；本技能补充本地排坑和验证要求。
没有这些技能时，使用当前 CLI help 发现命令，不套用旧版脚本。

## 确定范围与版本

- 从 AGENTS.md、版本目录、Gradle wrapper、manifest 获取项目根目录、SDK/BOM、JDK、包名和入口。
  在多工程工作区不要对错工程操作。
- 分开记录业务 BOM、知识库、模拟器 bundle、guest ROM 和 Android API；它们不是同一个版本。
  `sdk/6.1` 目录可能只有 agent-vault，不能作为 6.1 模拟器已安装的证据。
- 用户要求模拟器迁移时执行明确的目标版本；用户已授权的环境安装、配置与启动无需反复询问。
  业务 SDK 升级只在用户要求或已证实的兼容性故障需要时进行，不能用环境体检触发无关迁移。

## 配置与体检

- 检查 Android Studio 的实际 product-info/版本、自带 JBR、SDK platforms，以及 Gradle 支持的 JDK。
  优先项目级配置，避免为单个项目改变全局 Java 默认值。
- JDK 路径包含空格时，shell 参数必须引用。机器路径优先放本机配置；若既有项目
  `org.gradle.java.home` 写了绝对路径，记录换机时需调整，不能把这条路径推广为通用模板。
- 先发现 `pico-cli --version`、命令 help，再执行该版本支持的 doctor/update 检查。
  把 JSON 内的具体错误映射到本任务：必要组件错误需修复；可选 Editor/perf/MCP 的错误
  独立报告，不能声称完整环境健康，也不要无关扩装。
- CLI JSON 可能混有子命令日志；保存原始输出与退出码，不直接把整份 stdout 当 JSON 解析。
  root doctor 显示 no-project/unknown 时，用项目声明及模块体检交叉核验。
- 持久化 PICO_HOME 后验证新 shell 实际读取值；配置值与目录内容分别核对。
  `.zshrc` 仅覆盖交互 zsh，自动化非交互命令应显式传入已核实的 PICO_HOME；不要反复运行
  doctor --fix 来处理同一个未导出的变量。
  证书报错时沿用系统信任链，禁止关闭 TLS 校验。

## 迁移模拟器

1. 列出所有 AVD 和在线设备，再单独看 managed AVD。非受管实例不会出现在 managed-only 列表。
2. 检查磁盘空间；迁移时默认保留旧 AVD 与数据作为回退。用户明确要求清理后，先核对运行进程、
   项目依赖与符号链接目标，再精确删除已废弃的工具、AVD 和下载缓存；同步撤销文档中的回退承诺。
   业务 BOM 版本旧不代表依赖无用，不能据此删除 Maven/Gradle 依赖。
3. 按当前 help 使用官方 install/create/start，显式传目标 bundle 版本。下载期间报告真实进度，
   不把长下载认定失败，也不把元数据请求成功当作安装成功。
4. 如果工具返回版本回退、缺少当前 host artifact 或复用了旧 AVD，先核对结果；不能改名或
   重写版本字段冒充迁移。缺少目标包时查官方发布信息或工具返回的 nextAction，并报告阻塞。
   若目标已安装但 create 又触发 install，停止重复下载并检查发现路径；CLI 0.5.0 在旧 bundle
   占据 PICO_HOME 根目录时可能停止向子目录扫描。实测修复是设置 `PICO_EMULATOR_HOME` 为
   已核实的新 bundle 根目录，先用 list 验证发现，再 create/start；保留 PICO_HOME 指向总根目录。
   此修复只针对已证实的布局问题，不能修改版本元数据来绕过核验。
5. macOS 保留官方 emulator/QEMU 签名和 Hypervisor entitlement。不要套用旧 Qt/DYLD 参数，
   不重签名、不全局清除安全保护。HV_DENIED 按官方技能切换获授权的宿主执行通道。
6. 启动后同时核对 bundle/AVD、实际进程路径、设备 serial 与 guest 属性。
   `adbOnline=true` 之外，还要确认同一设备 `sys.boot_completed=1`。
   所有后续安装、启动、日志都显式指定该 serial，防止验证到了旧模拟器。

## 分层验证

- 构建：记录任务和结果；缓存命中或 offline 构建不等于新机器完整依赖下载通过。
- 安装：官方 app install 返回成功，保留 APK 路径。
- 启动：官方 app launch（必要时指定 Activity）成功；再检查进程存活、应用/系统 crash 日志。
  进程已死时 PID 过滤可能失效，必须检查按包名和时间段定位的系统崩溃信息。
- 业务场景：按项目冒烟判据操作 UI，确认目标 Stage、资源加载、持续渲染。
  不用 Android 2D `input tap` 伪造空间手势；可用模拟器宿主 UI 控件时实际操作，
  否则明确说明尚未验证的交互。保护表面黑截图不能单独证明渲染失败或成功。
- 只为测试启动自动开 Stage 的临时修改不能冒充原始入口验证；若确需测试入口，限定 debug，
  记录差异并恢复原逻辑后重新构建。

## 固化修复

将通用决策修复写入本技能，把项目路径、版本、问题证据与本次结果放入
[references/local-findings.md](references/local-findings.md)。修复官方生成技能时优先维护个人
补充层，避免插件更新覆盖；项目 AGENTS.md 只加清晰的技能导航，不修改业务逻辑来掩盖环境问题。
最终分别报告：完成的迁移、验证层级、仍受阻的功能、变更路径和回退方法。
