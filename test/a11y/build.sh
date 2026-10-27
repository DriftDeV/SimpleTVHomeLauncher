#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="$HOME/Android/Sdk"; BT="$SDK/build-tools/36.0.0"; JAR="$SDK/platforms/android-37.0/android.jar"
rm -rf build && mkdir -p build/classes build/dex
/opt/android-studio/jbr/bin/javac --release 8 -Xlint:-options -cp "$JAR" -d build/classes $(find src -name '*.java')
"$BT/d8" --release --min-api 24 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$JAR" --manifest AndroidManifest.xml --min-sdk-version 24 --target-sdk-version 28 build/res.zip
cp build/base.apk build/u.apk; (cd build/dex && zip -q -j ../u.apk classes.dex)
"$BT/zipalign" -f -p 4 build/u.apk build/a.apk
"$BT/apksigner" sign --ks ../../debug.keystore --ks-pass pass:android --key-pass pass:android --out build/a11ytest.apk build/a.apk 2>/dev/null
ls -l build/a11ytest.apk
