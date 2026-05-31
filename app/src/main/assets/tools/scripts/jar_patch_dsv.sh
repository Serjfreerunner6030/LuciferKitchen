#!/system/bin/sh
# jar_patch_dsv.sh — Core patch: Disable Signature Verification
# Args: $1=project_dir, $2=android_version(10-16), $3=rom_type(aosp|miui|hyperos)

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BAKSMALI="$TOOLS_DIR/bin/baksmali.jar"
SMALI="$TOOLS_DIR/bin/smali.jar"
PROJECT="$1"
ANDROID_VER="$2"
ROM_TYPE="$3"

[ -z "$PROJECT" ] && echo "ERROR: No project dir" && exit 1
[ -z "$ANDROID_VER" ] && ANDROID_VER="14"
[ -z "$ROM_TYPE" ] && ROM_TYPE="aosp"

# Find java/dalvikvm
JAVA=""
for j in dalvikvm java; do
    if command -v "$j" >/dev/null 2>&1; then JAVA="$j"; break; fi
done
[ -z "$JAVA" ] && echo "ERROR: No java/dalvikvm found" && exit 1

# Find services.jar and framework.jar
SERVICES_JAR=""
FRAMEWORK_JAR=""
for part in system system_ext; do
    [ -f "$PROJECT/$part/framework/services.jar" ] && SERVICES_JAR="$PROJECT/$part/framework/services.jar"
    [ -f "$PROJECT/$part/framework/framework.jar" ] && FRAMEWORK_JAR="$PROJECT/$part/framework/framework.jar"
done

[ -z "$SERVICES_JAR" ] && echo "ERROR: services.jar not found" && exit 1

echo "INFO: Patching DSV for Android $ANDROID_VER ($ROM_TYPE)..."

WORK_DIR="$PROJECT/.tmp_dsv"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"

# Decompile services.jar
echo "INFO: Decompiling services.jar..."
$JAVA -jar "$BAKSMALI" d "$SERVICES_JAR" -o "$WORK_DIR/smali"

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to decompile services.jar"
    rm -rf "$WORK_DIR"
    exit 1
fi

# Find PackageManagerService smali
PMS_SMALI=""
PMS_CANDIDATES="
com/android/server/pm/PackageManagerService.smali
com/android/server/pm/PackageManagerServiceUtils.smali
com/android/server/pm/InstallPackageHelper.smali
com/android/server/pm/verification/PackageVerificationService.smali
"

for candidate in $PMS_CANDIDATES; do
    if [ -f "$WORK_DIR/smali/$candidate" ]; then
        PMS_SMALI="$WORK_DIR/smali/$candidate"
        echo "INFO: Found PMS at $candidate"
        break
    fi
done

# For Android 14+, check additional locations
if [ -z "$PMS_SMALI" ] && [ "$ANDROID_VER" -ge 14 ] 2>/dev/null; then
    PMS_SMALI=$(find "$WORK_DIR/smali" -name "PackageManagerService*.smali" -type f | head -1)
fi

if [ -z "$PMS_SMALI" ]; then
    echo "ERROR: PackageManagerService smali not found"
    rm -rf "$WORK_DIR"
    exit 1
fi

echo "INFO: Patching signature verification..."

# Patch: compareSignatures method — return 0 (MATCH)
# Look for method and replace return value
sed -i '/\.method.*compareSignatures/,/\.end method/{
    s/const\/4 v0, 0x1/const\/4 v0, 0x0/g
    s/const\/4 v0, -0x1/const\/4 v0, 0x0/g
    s/const\/4 v0, -0x2/const\/4 v0, 0x0/g
    s/const\/4 v0, -0x3/const\/4 v0, 0x0/g
    s/const\/4 v0, -0x4/const\/4 v0, 0x0/g
}' "$PMS_SMALI"

# Patch: checkSignatures — return SIGNATURE_MATCH (0)
sed -i '/\.method.*checkSignatures/,/\.end method/{
    s/const\/4 v0, 0x1/const\/4 v0, 0x0/g
    s/const\/4 v0, -0x1/const\/4 v0, 0x0/g
}' "$PMS_SMALI"

# For MIUI/HyperOS — additional patches
if [ "$ROM_TYPE" = "miui" ] || [ "$ROM_TYPE" = "hyperos" ]; then
    echo "INFO: Applying MIUI/HyperOS specific patches..."
    # Patch MiuiPackageManagerService if present
    MIUI_PMS=$(find "$WORK_DIR/smali" -name "MiuiPackageManager*.smali" -type f 2>/dev/null)
    for f in $MIUI_PMS; do
        sed -i '/\.method.*checkSignatures\|\.method.*compareSignatures/,/\.end method/{
            s/const\/4 v0, 0x1/const\/4 v0, 0x0/g
            s/const\/4 v0, -0x1/const\/4 v0, 0x0/g
        }' "$f"
    done
fi

# Recompile
echo "INFO: Recompiling services.jar..."
cp "$SERVICES_JAR" "$SERVICES_JAR.bak"
$JAVA -jar "$SMALI" a "$WORK_DIR/smali" -o "$WORK_DIR/classes.dex"

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to recompile"
    rm -rf "$WORK_DIR"
    exit 1
fi

# Replace classes.dex in jar
cd "$WORK_DIR"
cp "$SERVICES_JAR" services_new.jar
zip -j services_new.jar classes.dex
cp services_new.jar "$SERVICES_JAR"
cd "$PROJECT"

rm -rf "$WORK_DIR"
echo "INFO: DSV patch applied successfully"
exit 0
