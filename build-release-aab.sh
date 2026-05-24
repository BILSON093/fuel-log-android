#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home}"
BUILD="$ROOT/build/aab"
KEY_DIR="$ROOT/release/keystore"
PROPS="$KEY_DIR/keystore.properties"
BUNDLETOOL="$ROOT/tools/bundletool-all.jar"
VERSION_CODE="${VERSION_CODE:-2}"
VERSION_NAME="${VERSION_NAME:-1.0.1}"

if [ ! -f "$BUNDLETOOL" ]; then
  echo "Missing bundletool: $BUNDLETOOL" >&2
  echo "Download from https://github.com/google/bundletool/releases and save as tools/bundletool-all.jar" >&2
  exit 1
fi

if [ ! -f "$PROPS" ]; then
  echo "Missing release keystore properties. Run ./build-release.sh first." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$PROPS"
set +a

rm -rf "$BUILD"
mkdir -p "$BUILD/res" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$BUILD/aar" "$BUILD/native" "$BUILD/module/manifest" "$BUILD/module/dex" "$ROOT/dist"

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
  --proto-format
  -o "$BUILD/base.apk"
  -I "$ANDROID_HOME/platforms/android-35/android.jar"
  --manifest "$ROOT/app/src/main/AndroidManifest.xml"
  --min-sdk-version 26
  --target-sdk-version 35
  --version-code "$VERSION_CODE"
  --version-name "$VERSION_NAME"
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

mkdir -p "$BUILD/base-apk"
(cd "$BUILD/base-apk" && unzip -q ../base.apk)
cp -R "$BUILD/base-apk/res" "$BUILD/module/res"
cp "$BUILD/base-apk/resources.pb" "$BUILD/module/resources.pb"
cp "$BUILD/base-apk/AndroidManifest.xml" "$BUILD/module/manifest/AndroidManifest.xml"
cp "$BUILD/dex/classes.dex" "$BUILD/module/dex/classes.dex"
if [ -d "$BUILD/native/lib" ]; then
  cp -R "$BUILD/native/lib" "$BUILD/module/lib"
fi

(cd "$BUILD/module" && zip -qr "$BUILD/base.zip" .)
java -jar "$BUNDLETOOL" build-bundle --modules="$BUILD/base.zip" --output="$BUILD/车耗记-release-unsigned.aab"

"$JAVA_HOME/bin/jarsigner" \
  -keystore "$storeFile" \
  -storepass "$storePassword" \
  -signedjar "$ROOT/dist/车耗记-release.aab" \
  "$BUILD/车耗记-release-unsigned.aab" \
  "$keyAlias" >/dev/null

"$JAVA_HOME/bin/jarsigner" -verify "$ROOT/dist/车耗记-release.aab" >/dev/null
ls -lh "$ROOT/dist/车耗记-release.aab"
