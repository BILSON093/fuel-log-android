#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home}"
BUILD="$ROOT/build/manual"

rm -rf "$BUILD"
mkdir -p "$BUILD/res" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$ROOT/dist"

"$ANDROID_HOME/build-tools/35.0.0/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$BUILD/res/resources.zip"
"$ANDROID_HOME/build-tools/35.0.0/aapt2" link \
  -o "$BUILD/app-unsigned.apk" \
  -I "$ANDROID_HOME/platforms/android-35/android.jar" \
  --manifest "$ROOT/app/src/main/AndroidManifest.xml" \
  --min-sdk-version 26 \
  --target-sdk-version 35 \
  --version-code 1 \
  --version-name 1.0.0 \
  --java "$BUILD/gen" \
  "$BUILD/res/resources.zip"

find "$BUILD/gen" "$ROOT/app/src/main/java" -name '*.java' > "$BUILD/sources.txt"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 17 -target 17 \
  -classpath "$ANDROID_HOME/platforms/android-35/android.jar" \
  -d "$BUILD/classes" \
  @"$BUILD/sources.txt"

"$ANDROID_HOME/build-tools/35.0.0/d8" \
  --lib "$ANDROID_HOME/platforms/android-35/android.jar" \
  --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name '*.class')

(cd "$BUILD/dex" && zip -q "$BUILD/app-unsigned.apk" classes.dex)
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -f -p 4 "$BUILD/app-unsigned.apk" "$BUILD/app-aligned.apk"

if [ ! -f "$ROOT/debug.keystore" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -v \
    -keystore "$ROOT/debug.keystore" \
    -storepass android \
    -keypass android \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Codex,C=CN" >/dev/null
fi

"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign \
  --ks "$ROOT/debug.keystore" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$BUILD/油耗记录-debug.apk" \
  "$BUILD/app-aligned.apk"

"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify "$BUILD/油耗记录-debug.apk"
cp "$BUILD/油耗记录-debug.apk" "$ROOT/dist/油耗记录.apk"
ls -lh "$ROOT/dist/油耗记录.apk"
