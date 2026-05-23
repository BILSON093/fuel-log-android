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
mkdir -p "$BUILD/res" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$ROOT/dist" "$KEY_DIR"

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
"$ANDROID_HOME/build-tools/35.0.0/aapt2" link \
  -o "$BUILD/app-unsigned.apk" \
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

(cd "$BUILD/dex" && zip -q "$BUILD/app-unsigned.apk" classes.dex)
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
