#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

echo "[هاشم SMS] بدء التجهيز والبناء..."
if ! command -v pkg >/dev/null 2>&1; then
  echo "هذا السكربت مخصص لـ Termux."; exit 1
fi
pkg update -y
pkg install -y openjdk-17 wget unzip git
export JAVA_HOME="$PREFIX/opt/openjdk"
[ -d "$JAVA_HOME" ] || export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
export PATH="$JAVA_HOME/bin:$PATH"

export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
mkdir -p "$ANDROID_HOME/cmdline-tools"
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  cd "$ANDROID_HOME/cmdline-tools"
  wget -q https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip -O tools.zip
  rm -rf latest tmp-tools
  mkdir tmp-tools
  unzip -q tools.zip -d tmp-tools
  mv tmp-tools/cmdline-tools latest
  rm -rf tmp-tools tools.zip
  cd "$ROOT"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

if [ ! -x "$ROOT/gradlew" ]; then
  wget -q https://services.gradle.org/distributions/gradle-9.5-bin.zip -O "$HOME/gradle-9.5.zip"
  rm -rf "$HOME/gradle-9.5"
  mkdir -p "$HOME/gradle-9.5"
  unzip -q "$HOME/gradle-9.5.zip" -d "$HOME/gradle-9.5"
  GRADLE_BIN="$HOME/gradle-9.5/gradle-9.5/bin/gradle"
  "$GRADLE_BIN" wrapper --gradle-version 9.5
fi
chmod +x "$ROOT/gradlew"
./gradlew --no-daemon assembleDebug
mkdir -p "$ROOT/dist"
cp "$ROOT/app/build/outputs/apk/debug/app-debug.apk" "$ROOT/dist/HashemSMS-debug.apk"

echo
echo "========================================"
echo "تم البناء بنجاح."
echo "APK: $ROOT/dist/HashemSMS-debug.apk"
echo "========================================"
