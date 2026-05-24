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
mkdir -p "$BUILD/res" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$BUILD/module/manifest" "$BUILD/module/dex" "$ROOT/dist"

"$ANDROID_HOME/build-tools/35.0.0/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$BUILD/res/resources.zip"
"$ANDROID_HOME/build-tools/35.0.0/aapt2" link --proto-format \
  -o "$BUILD/base.apk" \
  -I "$ANDROID_HOME/platforms/android-35/android.jar" \
  --manifest "$ROOT/app/src/main/AndroidManifest.xml" \
  --min-sdk-version 26 \
  --target-sdk-version 35 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
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

mkdir -p "$BUILD/base-apk"
(cd "$BUILD/base-apk" && unzip -q ../base.apk)
cp -R "$BUILD/base-apk/res" "$BUILD/module/res"
cp "$BUILD/base-apk/resources.pb" "$BUILD/module/resources.pb"
cp "$BUILD/base-apk/AndroidManifest.xml" "$BUILD/module/manifest/AndroidManifest.xml"
cp "$BUILD/dex/classes.dex" "$BUILD/module/dex/classes.dex"

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
