#!/usr/bin/env bash
# 一键：确保 PICO 0.10 模拟器在运行 -> 离线构建 -> 安装（不额外下载任何依赖）。
#
# 两个本机环境坑（已在此脚本内规避）：
#  1) shell 默认 JAVA_HOME 可能指向过新的 JDK（如 JDK 26），Gradle 8.10.2 不支持，
#     会只报一个 “26.0.1” 然后失败。这里强制改用 Android Studio 自带的 JBR 21。
#  2) PICO 模拟器的 AVD 不在标准 ~/.android/avd，而在 ~/.pico/avd，
#     AVD 名是 PICO_0.10（不是 Pico）。裸跑 emulator 必须带 ANDROID_AVD_HOME。
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JBR="/Applications/Android Studio Preview.app/Contents/jbr/Contents/Home"
[ -x "$JBR/bin/java" ] || JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
PICO_EMU="$HOME/Library/PICO/sdk/0.10/emulator/emulator"
PICO_AVD_HOME="$HOME/.pico/avd"
PICO_AVD_NAME="PICO_0.10"

# 1) 确保模拟器在线；不在线则后台拉起并等待开机完成
#    关键：必须复刻 Android Studio / SpatialPlugin 的启动环境，否则 Qt 窗口走 Darwin/Metal
#    后端会黑屏（右眼合成卡死 ~950ms）。核心是 QT_OPENGL=software + ANDROID_SDK_HOME=~/.pico。
if ! "$ADB" devices | grep -qE "emulator-[0-9]+[[:space:]]+device"; then
  echo ">> 未发现运行中的模拟器，启动 PICO 0.10（$PICO_AVD_NAME）..."
  PICO_EMU_DIR="$HOME/Library/PICO/sdk/0.10/emulator"
  QEMU="$PICO_EMU_DIR/qemu/darwin-aarch64/qemu-system-aarch64"
  SYSIMG="$PICO_EMU_DIR/system-images"
  [ -x "$QEMU" ] || QEMU="$PICO_EMU"   # 回退到 wrapper

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
    nohup "$QEMU" \
      -netdelay none -netspeed full -avd "$PICO_AVD_NAME" -gpu angle_indirect \
      >/tmp/pico-emulator.log 2>&1 &
  echo ">> 等待开机（最多约 2 分钟）..."
  for _ in $(seq 1 40); do
    sleep 3
    [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  done
fi
echo ">> 设备状态："; "$ADB" devices

# 2) 用 JBR 21 离线构建
echo ">> 使用 JDK: $JBR"
cd "$PROJECT_DIR"
JAVA_HOME="$JBR" ./gradlew :app:assembleDebug --offline --console=plain

# 3) 安装
APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
echo ">> 安装 $APK"
"$ADB" install -r "$APK"

echo ">> 完成。请在 PICO 模拟器空间界面点击 “Hotline Chamber” 卡片进入。"
