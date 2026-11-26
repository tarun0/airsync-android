#!/bin/sh
# Builds tools/clipbridge/ClipWatch.java into app/src/main/assets/clipwatch.dex
set -e
cd "$(dirname "$0")/../.."

AJ="${ANDROID_JAR:-$(ls -d "${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"/platforms/android-*/android.jar 2>/dev/null | tail -1)}"
[ -f "$AJ" ] || { echo "android.jar not found; set ANDROID_JAR" >&2; exit 1; }
echo "android.jar: $AJ"

OUT=app/build/clipbridge
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"

javac -source 11 -target 11 -cp "$AJ" -d "$OUT/classes" tools/clipbridge/ClipWatch.java 2>&1 | grep -v "^warning" || true
d8 --min-api 30 --lib "$AJ" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

mkdir -p app/src/main/assets
cp "$OUT/dex/classes.dex" app/src/main/assets/clipwatch.dex
ls -l app/src/main/assets/clipwatch.dex
