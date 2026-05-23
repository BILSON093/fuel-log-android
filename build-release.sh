#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home}"
BUILD="$ROOT/build/release"
KEY_DIR="$ROOT/release/keystore"
KEYSTORE="$KEY_DIR/chehaoji-upload.jks"
PROPS="$KEY_DIR/keystore.properties"
APP_NAME="车耗记"
VERSION_CODE="${VERSION_CODE:-2}"
VERSION_NAME="${VERSION_NAME:-1.0.1}"

rm -rf "$BUILD"
mkdir -p "$BUILD/res" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$BUILD/aar" "$BUILD/native" "$ROOT/dist" "$KEY_DIR"

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

if [ ! -f "$PROPS" ]; then
  STORE_PASSWORD="$(python3 - <<'PY'
import secrets,string
alphabet=string.ascii_letters+string.digits
print(''.join(secrets.choice(alphabet) for _ in range(24)))
PY
)"
  KEY_PASSWORD="$STORE_PASSWORD"
  cat > "$PROPS" <<EOF
storePassword=$STORE_PASSWORD
keyPassword=$KEY_PASSWORD
keyAlias=chehaoji
storeFile=$KEYSTORE
EOF
  chmod 600 "$PROPS"
fi

set -a
# shellcheck disable=SC1090
source "$PROPS"
set +a

if [ ! -f "$KEYSTORE" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -v \
    -keystore "$KEYSTORE" \
    -storepass "$storePassword" \
    -keypass "$keyPassword" \
    -alias "$keyAlias" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "CN=CheHaoJi,O=CheHaoJi,C=CN" >/dev/null
fi

"$ANDROID_HOME/build-tools/35.0.0/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$BUILD/res/resources.zip"
prepare_aar_deps
LINK_ARGS=(
  -o "$BUILD/app-unsigned.apk"
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

(cd "$BUILD/dex" && zip -q "$BUILD/app-unsigned.apk" classes.dex)
if [ -d "$BUILD/native/lib" ]; then
  (cd "$BUILD/native" && zip -qr "$BUILD/app-unsigned.apk" lib)
fi
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -f -p 4 "$BUILD/app-unsigned.apk" "$BUILD/app-aligned.apk"

"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass "pass:$storePassword" \
  --key-pass "pass:$keyPassword" \
  --out "$BUILD/$APP_NAME-release.apk" \
  "$BUILD/app-aligned.apk"

"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify "$BUILD/$APP_NAME-release.apk"
cp "$BUILD/$APP_NAME-release.apk" "$ROOT/dist/$APP_NAME-release.apk"
ls -lh "$ROOT/dist/$APP_NAME-release.apk"
