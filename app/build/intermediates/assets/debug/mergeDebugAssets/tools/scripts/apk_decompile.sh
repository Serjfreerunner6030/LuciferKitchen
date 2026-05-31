#!/system/bin/sh
# Lucifer Kitchen - Decompile APK using apktool
TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APK_PATH="$1"
OUTPUT_DIR="$2"

[ -z "$APK_PATH" ] && echo "ERROR: Usage: apk_decompile.sh <apk_path> [output_dir]" && exit 1
[ ! -f "$APK_PATH" ] && echo "ERROR: APK not found: $APK_PATH" && exit 1

APKTOOL="$TOOLS_DIR/bin/apktool.jar"
[ ! -f "$APKTOOL" ] && echo "ERROR: apktool.jar not found. Run setup_tools.sh first" && exit 1

APK_NAME="$(basename "$APK_PATH" .apk)"
[ -z "$OUTPUT_DIR" ] && OUTPUT_DIR="$(dirname "$APK_PATH")/${APK_NAME}_decompiled"

echo "INFO: Decompiling $APK_PATH"
echo "INFO: Output: $OUTPUT_DIR"

# Remove old output if exists
[ -d "$OUTPUT_DIR" ] && rm -rf "$OUTPUT_DIR"

java -jar "$APKTOOL" d "$APK_PATH" -o "$OUTPUT_DIR" -f 2>&1
EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    FILE_COUNT=$(find "$OUTPUT_DIR" -type f 2>/dev/null | wc -l)
    echo "INFO: Decompiled successfully ($FILE_COUNT files)"
else
    echo "ERROR: Decompilation failed (exit code: $EXIT_CODE)"
fi
exit $EXIT_CODE
