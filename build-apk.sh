#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home}"
BUILD="$ROOT/build/manual"

rm -rf "$BUILD"
mkdir -p "$BUILD/res" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$BUILD/aar" "$BUILD/native" "$ROOT/dist"

AAR_JARS=()
RES_INPUTS=()
EXTRA_PACKAGES=()

prepare_aar_deps() {
  local aar name work pkg compiled
  while IFS= read -r aar; do
    name="$(basename "$aar" .aar)"
    work="$BUILD/aar/$name"
    mkdir -p "$work"
    unzip -q "$aar" -d "$work"
    if [ -f "$work/classes.jar" ]; then
      AAR_JARS+=("$work/classes.jar")
    fi
    if [ -d "$work/res" ]; then
      compiled="$BUILD/res/$name.zip"
      "$ANDROID_HOME/build-tools/35.0.0/aapt2" compile --dir "$work/res" -o "$compiled"
      RES_INPUTS+=("$compiled")
    fi
    if [ -d "$work/jni" ]; then
      mkdir -p "$BUILD/native/lib"
      cp -R "$work/jni/." "$BUILD/native/lib/"
    fi
    pkg="$(strings "$work/AndroidManifest.xml" | sed -n 's/.*package="\([^"]*\)".*/\1/p' | head -1 || true)"
    if [ -n "$pkg" ]; then
      EXTRA_PACKAGES+=("$pkg")
    fi
  done < <(find "$ROOT/app/libs" -name '*.aar' -type f 2>/dev/null | sort)
}

"$ANDROID_HOME/build-tools/35.0.0/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$BUILD/res/resources.zip"
prepare_aar_deps
LINK_ARGS=(
  -o "$BUILD/app-unsigned.apk"
  -I "$ANDROID_HOME/platforms/android-35/android.jar"
  --manifest "$ROOT/app/src/main/AndroidManifest.xml"
  --min-sdk-version 26
  --target-sdk-version 35
  --version-code 2
  --version-name 1.0.1
  --java "$BUILD/gen"
  --auto-add-overlay
)
if [ ${#EXTRA_PACKAGES[@]} -gt 0 ]; then
  EXTRA_PACKAGES_JOINED="$(IFS=:; echo "${EXTRA_PACKAGES[*]}")"
  LINK_ARGS+=(--extra-packages "$EXTRA_PACKAGES_JOINED")
fi
for res in "${RES_INPUTS[@]}"; do
  LINK_ARGS+=(-R "$res")
done
LINK_ARGS+=("$BUILD/res/resources.zip")
"$ANDROID_HOME/build-tools/35.0.0/aapt2" link "${LINK_ARGS[@]}"

find "$BUILD/gen" "$ROOT/app/src/main/java" -name '*.java' > "$BUILD/sources.txt"
CLASSPATH="$ANDROID_HOME/platforms/android-35/android.jar"
for jar in "${AAR_JARS[@]}"; do
  CLASSPATH="$CLASSPATH:$jar"
done
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 17 -target 17 \
  -classpath "$CLASSPATH" \
  -d "$BUILD/classes" \
  @"$BUILD/sources.txt"

"$ANDROID_HOME/build-tools/35.0.0/d8" \
  --lib "$ANDROID_HOME/platforms/android-35/android.jar" \
  --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name '*.class') \
  "${AAR_JARS[@]}"

(cd "$BUILD/dex" && zip -q "$BUILD/app-unsigned.apk" classes.dex)
if [ -d "$BUILD/native/lib" ]; then
  (cd "$BUILD/native" && zip -qr "$BUILD/app-unsigned.apk" lib)
fi
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
  --out "$BUILD/车耗记-debug.apk" \
  "$BUILD/app-aligned.apk"

"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify "$BUILD/车耗记-debug.apk"
cp "$BUILD/车耗记-debug.apk" "$ROOT/dist/车耗记.apk"
ls -lh "$ROOT/dist/车耗记.apk"
