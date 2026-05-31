#!/system/bin/sh
# Lucifer Kitchen - Zipalign APK
TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APK_PATH="$1"

[ -z "$APK_PATH" ] && echo "ERROR: Usage: apk_zipalign.sh <apk_path>" && exit 1
[ ! -f "$APK_PATH" ] && echo "ERROR: APK not found: $APK_PATH" && exit 1

ZIPALIGN="$TOOLS_DIR/bin/zipalign"
[ ! -f "$ZIPALIGN" ] && ZIPALIGN="$(which zipalign 2>/dev/null)"
[ -z "$ZIPALIGN" ] && echo "ERROR: zipalign not found" && exit 1

ALIGNED_APK="${APK_PATH%.apk}-aligned.apk"

echo "INFO: Zipaligning $APK_PATH"

"$ZIPALIGN" -f -v 4 "$APK_PATH" "$ALIGNED_APK" 2>&1
EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    mv "$ALIGNED_APK" "$APK_PATH"
    echo "INFO: Zipalign completed"
else
    [ -f "$ALIGNED_APK" ] && rm -f "$ALIGNED_APK"
    echo "ERROR: Zipalign failed (exit code: $EXIT_CODE)"
fi
exit $EXIT_CODE
