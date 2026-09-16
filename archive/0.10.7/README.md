# archive/0.10.7 — 历史版本专用产物（新栈勿用）

本目录存放 **PICO Spatial SDK 0.10.7 旧工具链**时期的自建脚本、Skill 与排坑文档。

自 2026-09 起，本项目已通过官方 `pico-cli setup`（traecli + local + spatial，插件
`pico-spatial-agentic-tools`）接入官方工作流。日常构建 / 启动模拟器 / 安装 / 崩溃排查
请优先使用官方命令与其下发到 `.trae/skills/` 的技能（见根目录 `AGENTS.md`）。

## 为什么归档而不是继续用
旧脚本里硬编码了一批只适用于当时本机 + 0.10.7 的 hack，新栈已被官方能力取代：

| 旧产物里的做法 | 状态 | 新栈替代 |
|---|---|---|
| 硬编码 JBR 21 规避 JDK 26 | 废弃 | 官方 Gradle / 插件链路 |
| 直接拼 `~/.pico/avd` 的 AVD 名启动 | 废弃 | `pico-cli emulator start` |
| 注入 `QT_OPENGL=software` 等环境变量修黑屏 | 废弃 | `pico-cli emulator start` 的 host 通道 |
| adb install / monkey 拉起 | 废弃 | `pico-cli app install` / `app launch` |
| `Entity.load(InputStream, ModelFormat)` 加载 GLB | 废弃 | `asset://` URI，见现网代码与 `spatial-sdk-scene-builder` |

## 内容
- `run-on-emulator.sh`：旧的一键构建+启动+安装脚本。
- `self-built-skill/pico-spatial-run/`：旧的自建 Trae Skill。
- `PICO模拟器运行排坑指南.md`：0.10.7 时期 5 个坑的完整排错记录，仅作历史参考。

> 若项目以后需要在没有安装官方 CLI / 插件的隔离机器上临时跑 0.10.7，可参考这些文件；
> 除此之外不要在新栈里复用其中的 QT/AVD/JDK 硬编码。
