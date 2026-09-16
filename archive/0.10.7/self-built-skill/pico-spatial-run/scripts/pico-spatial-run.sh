#!/usr/bin/env bash
# =============================================================================
# pico-spatial-run.sh
# 一键离线构建 PICO Spatial (0.10.x) 应用 -> 确保模拟器在运行 -> 安装（可选拉起）。
#
# 本脚本把实战中踩过的三个本机环境坑全部内置规避，可直接分发给其他成员：
#   坑 1：命令行默认 JAVA_HOME 可能是过新的 JDK（如 JDK 26），Gradle 8.10.2 不支持，
#         构建会只报一个 “26.0.1” 就失败。这里强制改用 Android Studio 自带 JBR 21。
#   坑 2：PICO 模拟器 AVD 不在标准 ~/.android/avd，而在 ~/.pico/avd，
#         AVD 名通常是 PICO_0.10（不是 emulatorParams.ini 里写的 Pico）。
#   坑 3：裸跑 emulator/qemu 若缺少 QT_OPENGL=software 等环境变量，Qt 外壳会走
#         macOS Darwin(Metal) 后端，导致空间合成右眼卡死、中间 3D 视口纯黑。
#         必须复刻 Android Studio / SpatialPlugin 注入的整套环境变量。
#
# 用法：
#   ./pico-spatial-run.sh                 # 启动模拟器(如需) + 离线构建 + 安装
#   ./pico-spatial-run.sh --launch        # 安装后再用 monkey 拉起应用
#   ./pico-spatial-run.sh --no-build      # 跳过构建，只确保模拟器 + 安装已有 APK
#   ./pico-spatial-run.sh --emu-only      # 只启动并等待模拟器，不构建/安装
#
# 可用环境变量覆盖默认路径（默认值按 Apple Silicon + 标准安装位置设定）：
#   PROJECT_DIR   工程根目录（含 gradlew），默认当前目录
#   PKG           应用包名，提供 --launch 时必填
#   JBR           Android Studio JBR 路径
#   ADB           adb 路径
#   PICO_EMU_DIR  PICO 模拟器目录（含 qemu/darwin-aarch64/qemu-system-aarch64）
#   PICO_AVD_HOME AVD 主目录，默认 ~/.pico/avd
#   PICO_AVD_NAME AVD 名称，默认 PICO_0.10
# =============================================================================
set -euo pipefail

# ----------------------------- 可配置参数（默认值） -----------------------------
PROJECT_DIR="${PROJECT_DIR:-$(pwd)}"
JBR="${JBR:-/Applications/Android Studio Preview.app/Contents/jbr/Contents/Home}"
[ -x "$JBR/bin/java" ] || JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PICO_EMU_DIR="${PICO_EMU_DIR:-$HOME/Library/PICO/sdk/0.10/emulator}"
PICO_AVD_HOME="${PICO_AVD_HOME:-$HOME/.pico/avd}"
PICO_AVD_NAME="${PICO_AVD_NAME:-PICO_0.10}"
APK_REL="${APK_REL:-app/build/outputs/apk/debug/app-debug.apk}"
PKG="${PKG:-}"

DO_BUILD=1; DO_INSTALL=1; DO_LAUNCH=0
for arg in "$@"; do
  case "$arg" in
    --launch) DO_LAUNCH=1 ;;
    --no-build) DO_BUILD=0 ;;
    --emu-only) DO_BUILD=0; DO_INSTALL=0 ;;
    *) echo "未知参数: $arg" >&2; exit 2 ;;
  esac
done

log() { echo -e "\033[1;36m>>\033[0m $*"; }

# ----------------------------- 1) 确保模拟器在线 -----------------------------
device_online() { "$ADB" devices | grep -qE "emulator-[0-9]+[[:space:]]+device"; }

if ! device_online; then
  QEMU="$PICO_EMU_DIR/qemu/darwin-aarch64/qemu-system-aarch64"
  [ -x "$QEMU" ] || QEMU="$PICO_EMU_DIR/emulator"   # 回退到 wrapper
  SYSIMG="$PICO_EMU_DIR/system-images"
  log "未发现运行中的模拟器，启动 $PICO_AVD_NAME ..."
  log "（已复刻 Android Studio 的 QT/ANDROID 环境变量，避免黑屏）"
  ANDROID_AVD_HOME="$PICO_AVD_HOME" \
  ANDROID_SDK_HOME="$HOME/.pico" \
  ANDROID_HOME="$SYSIMG" \
  ANDROID_SDK_ROOT="$SYSIMG" \
  ANDROID_EMULATOR_LAUNCHER_DIR="$PICO_EMU_DIR" \
  QT_OPENGL=software \
  QT_AUTO_SCREEN_SCALE_FACTOR=none \
  QT_SCALE_FACTOR=none \
  QT_SCREEN_SCALE_FACTORS=none \
  QT_LOGGING_RULES=default.warning=false \
  QTWEBENGINE_CHROMIUM_FLAGS=--disable-gpu \
  QT_QPA_PLATFORM_PLUGIN_PATH="$PICO_EMU_DIR/lib64/qt/plugins" \
  DYLD_LIBRARY_PATH="$PICO_EMU_DIR/lib64/qt/lib:$PICO_EMU_DIR/lib64/vulkan:$PICO_EMU_DIR/lib64/gles_angle11:$PICO_EMU_DIR/lib64/gles_angle9:$PICO_EMU_DIR/lib64/gles_angle:$PICO_EMU_DIR/lib64" \
    nohup "$QEMU" -netdelay none -netspeed full -avd "$PICO_AVD_NAME" -gpu angle_indirect \
      >/tmp/pico-emulator.log 2>&1 &

  log "等待开机（最多约 3 分钟）..."
  ok=0
  for _ in $(seq 1 60); do
    sleep 3
    if [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then ok=1; break; fi
  done
  [ "$ok" = "1" ] || { echo "模拟器开机超时，请查看 /tmp/pico-emulator.log" >&2; exit 1; }
else
  log "模拟器已在线。"
fi
"$ADB" devices

[ "$DO_INSTALL" = "0" ] && { log "仅启动模拟器，结束。"; exit 0; }

# ----------------------------- 2) 离线构建 -----------------------------
if [ "$DO_BUILD" = "1" ]; then
  [ -x "$JBR/bin/java" ] || { echo "找不到 Android Studio JBR，请用 JBR=... 指定" >&2; exit 1; }
  log "使用 JDK: $JBR"
  ( cd "$PROJECT_DIR" && JAVA_HOME="$JBR" ./gradlew :app:assembleDebug --offline --console=plain )
fi

# ----------------------------- 3) 安装 -----------------------------
APK="$PROJECT_DIR/$APK_REL"
[ -f "$APK" ] || { echo "找不到 APK: $APK" >&2; exit 1; }
log "安装 $APK"
"$ADB" install -r "$APK"

# ----------------------------- 4) 可选拉起 -----------------------------
if [ "$DO_LAUNCH" = "1" ]; then
  [ -n "$PKG" ] || { echo "--launch 需要设置 PKG=<包名>" >&2; exit 1; }
  log "拉起 $PKG"
  "$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
fi

log "完成。若进入沉浸式 Stage，应用窗口由空间系统托管，属正常现象。"
