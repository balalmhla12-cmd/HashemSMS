#!/data/data/com.termux/files/usr/bin/bash
set -e
pkg update -y
pkg install -y openjdk-17 wget unzip git aapt2
export JAVA_HOME=$PREFIX/opt/openjdk
if [ ! -d "$JAVA_HOME" ]; then export JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk; fi
export PATH="$JAVA_HOME/bin:$PATH"
if [ ! -d "$HOME/android-sdk" ]; then mkdir -p "$HOME/android-sdk/cmdline-tools"; fi
if [ ! -x "$HOME/android-sdk/cmdline-tools/latest/bin/sdkmanager" ]; then
  cd "$HOME/android-sdk/cmdline-tools"
  wget -q https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip -O tools.zip
  rm -rf latest temp
  mkdir temp
  unzip -q tools.zip -d temp
  mv temp/cmdline-tools latest
  rm -rf temp tools.zip
fi
export ANDROID_HOME=$HOME/android-sdk
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --licenses >/dev/null || true
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
cd "$(dirname "$0")"
if [ ! -f gradlew ]; then
  if command -v gradle >/dev/null 2>&1; then
    gradle wrapper --gradle-version 9.5.0
  else
    wget -q https://services.gradle.org/distributions/gradle-9.5.0-bin.zip -O /tmp/gradle.zip
    mkdir -p "$HOME/.gradle/wrapper/dists/manual"
    unzip -q /tmp/gradle.zip -d "$HOME/.gradle/wrapper/dists/manual"
    "$HOME/.gradle/wrapper/dists/manual/gradle-9.5.0/bin/gradle" wrapper --gradle-version 9.5.0
  fi
fi
chmod +x gradlew
./gradlew --no-daemon assembleDebug
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/HashemSMS-debug.apk
echo "APK: $(pwd)/dist/HashemSMS-debug.apk"
