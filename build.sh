#!/usr/bin/env bash
# Compila il launcher senza Gradle: javac + d8 + aapt2 + apksigner. Output: build/home.apk
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-37.0/android.jar"
JAVAC="${JAVAC:-/opt/android-studio/jbr/bin/javac}"
rm -rf build && mkdir -p build/classes build/dex build/gen
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$JAR" --manifest AndroidManifest.xml \
  --min-sdk-version 24 --target-sdk-version 25 --version-code 1 --version-name 0.1 --java build/gen build/res.zip
"$JAVAC" --release 8 -Xlint:-options -cp "$JAR" -d build/classes $(find src build/gen -name '*.java')
"$BT/d8" --release --min-api 24 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=homelauncher" >/dev/null 2>&1
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out build/home.apk build/aligned.apk
ls -l build/home.apk
