#!/system/bin/sh
# Lucifer Kitchen - Disable Secure Screenshot restriction
TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT_DIR="$1"
ANDROID_VER="${2:-14}"
ROM_TYPE="${3:-aosp}"

[ -z "$PROJECT_DIR" ] && echo "ERROR: Usage: jar_patch_secure_ss.sh <project_dir> <android_ver> <rom_type>" && exit 1

BAKSMALI="$TOOLS_DIR/bin/baksmali.jar"
SMALI="$TOOLS_DIR/bin/smali.jar"
FRAMEWORK_DIR="$PROJECT_DIR/system/framework"
SERVICES_JAR="$FRAMEWORK_DIR/services.jar"
TMPDIR="$TOOLS_DIR/tmp/secure_ss_$$"

[ ! -f "$SERVICES_JAR" ] && echo "ERROR: services.jar not found at $SERVICES_JAR" && exit 1

echo "INFO: Disable Secure Screenshot patch"
echo "INFO: Android $ANDROID_VER, ROM type: $ROM_TYPE"

mkdir -p "$TMPDIR"
cd "$TMPDIR"
unzip -o "$SERVICES_JAR" "classes*.dex" -d "$TMPDIR" > /dev/null 2>&1

# Find dex containing WindowManagerService
TARGET_DEX=""
for dex in "$TMPDIR"/classes*.dex; do
    java -jar "$BAKSMALI" disassemble "$dex" -o "$TMPDIR/smali_check" 2>/dev/null
    if find "$TMPDIR/smali_check" -path "*WindowManagerService*.smali" 2>/dev/null | grep -q .; then
        TARGET_DEX="$dex"
        break
    fi
    rm -rf "$TMPDIR/smali_check"
done

# Also check for WindowState/WindowSurfaceController
if [ -z "$TARGET_DEX" ]; then
    for dex in "$TMPDIR"/classes*.dex; do
        java -jar "$BAKSMALI" disassemble "$dex" -o "$TMPDIR/smali_check" 2>/dev/null
        if find "$TMPDIR/smali_check" -name "WindowState*.smali" 2>/dev/null | grep -q .; then
            TARGET_DEX="$dex"
            break
        fi
        rm -rf "$TMPDIR/smali_check"
    done
fi

[ -z "$TARGET_DEX" ] && echo "ERROR: Could not find WindowManagerService in any dex" && exit 1
echo "INFO: Found target in $(basename "$TARGET_DEX")"

SMALI_DIR="$TMPDIR/smali_out"
java -jar "$BAKSMALI" disassemble "$TARGET_DEX" -o "$SMALI_DIR" 2>/dev/null

# Patch FLAG_SECURE checks
# FLAG_SECURE = 0x2000 (8192)
PATCHED=0
find "$SMALI_DIR" -name "*.smali" | while read smali_file; do
    if grep -q "0x2000\|FLAG_SECURE\|isSecureLocked\|setSecureLocked" "$smali_file"; then
        echo "INFO: Patching $(basename "$smali_file")"

        # Patch isSecureLocked to always return false
        sed -i '/.method.*isSecureLocked/,/.end method/{
            /\.method/!{/\.end method/!{
                /const\/4.*0x1/s/0x1/0x0/
            }}
        }' "$smali_file"

        # Patch setSecureLocked to be no-op
        sed -i '/.method.*setSecureLocked/,/.end method/{
            /\.method/!{/\.end method/!{
                /iput-boolean/s/^/# /
            }}
        }' "$smali_file"

        # Remove FLAG_SECURE (0x2000) bit from flag checks
        # Replace and-int with 0x2000 to always produce 0
        sed -i '/and-int.*0x2000/{
            s/and-int/const\/4/
            s/, v[0-9]*, 0x2000/, 0x0/
        }' "$smali_file" 2>/dev/null

        PATCHED=$((PATCHED + 1))
    fi
done

# Also patch WindowState for screenshot restriction
find "$SMALI_DIR" -name "WindowState*.smali" | while read smali_file; do
    if grep -q "0x2000" "$smali_file"; then
        echo "INFO: Patching WindowState: $(basename "$smali_file")"
        sed -i 's/const\/16 v\([0-9]*\), 0x2000/const\/4 v\1, 0x0/g' "$smali_file"
    fi
done

# Recompile
java -jar "$SMALI" assemble "$SMALI_DIR" -o "$TARGET_DEX" 2>/dev/null
[ $? -ne 0 ] && echo "ERROR: Smali assembly failed" && exit 1

# Replace in jar
cp "$SERVICES_JAR" "$SERVICES_JAR.bak"
cd "$TMPDIR"
zip -j "$SERVICES_JAR" "$TARGET_DEX" > /dev/null 2>&1

rm -rf "$TMPDIR"
echo "INFO: Secure Screenshot patch applied"
exit 0
