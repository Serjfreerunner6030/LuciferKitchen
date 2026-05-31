#!/system/bin/sh
# Lucifer Kitchen - Recompile APK from decompiled source
TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_DIR="$1"
OUTPUT_APK="$2"

[ -z "$SOURCE_DIR" ] && echo "ERROR: Usage: apk_recompile.sh <source_dir> [output_apk]" && exit 1
[ ! -d "$SOURCE_DIR" ] && echo "ERROR: Source dir not found: $SOURCE_DIR" && exit 1

APKTOOL="$TOOLS_DIR/bin/apktool.jar"
[ ! -f "$APKTOOL" ] && echo "ERROR: apktool.jar not found" && exit 1

DIR_NAME="$(basename "$SOURCE_DIR")"
[ -z "$OUTPUT_APK" ] && OUTPUT_APK="$(dirname "$SOURCE_DIR")/${DIR_NAME}.apk"

echo "INFO: Recompiling $SOURCE_DIR"
echo "INFO: Output: $OUTPUT_APK"

java -jar "$APKTOOL" b "$SOURCE_DIR" -o "$OUTPUT_APK" 2>&1
EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    SIZE=$(ls -lh "$OUTPUT_APK" 2>/dev/null | awk '{print $5}')
    echo "INFO: Recompiled successfully ($SIZE)"
else
    echo "ERROR: Recompilation failed (exit code: $EXIT_CODE)"
fi
exit $EXIT_CODE
