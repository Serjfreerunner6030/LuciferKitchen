#!/system/bin/sh
# Lucifer Kitchen - Remove APK Protection from services.jar
TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT_DIR="$1"
ANDROID_VER="${2:-14}"
ROM_TYPE="${3:-aosp}"

[ -z "$PROJECT_DIR" ] && echo "ERROR: Usage: jar_patch_apk_prot.sh <project_dir> <android_ver> <rom_type>" && exit 1

BAKSMALI="$TOOLS_DIR/bin/baksmali.jar"
SMALI="$TOOLS_DIR/bin/smali.jar"
FRAMEWORK_DIR="$PROJECT_DIR/system/framework"
SERVICES_JAR="$FRAMEWORK_DIR/services.jar"
TMPDIR="$TOOLS_DIR/tmp/apk_prot_$$"

[ ! -f "$SERVICES_JAR" ] && echo "ERROR: services.jar not found at $SERVICES_JAR" && exit 1

echo "INFO: Remove APK Protection patch"
echo "INFO: Android $ANDROID_VER, ROM type: $ROM_TYPE"

mkdir -p "$TMPDIR"

# Extract classes.dex from services.jar
cd "$TMPDIR"
unzip -o "$SERVICES_JAR" "classes*.dex" -d "$TMPDIR" > /dev/null 2>&1
[ $? -ne 0 ] && echo "ERROR: Failed to extract services.jar" && exit 1

# Find the right dex with PackageInstallerService
TARGET_DEX=""
for dex in "$TMPDIR"/classes*.dex; do
    java -jar "$BAKSMALI" disassemble "$dex" -o "$TMPDIR/smali_check" 2>/dev/null
    if find "$TMPDIR/smali_check" -name "PackageInstallerService*.smali" 2>/dev/null | grep -q .; then
        TARGET_DEX="$dex"
        break
    fi
    rm -rf "$TMPDIR/smali_check"
done

if [ -z "$TARGET_DEX" ]; then
    # Try PackageManagerServiceUtils
    for dex in "$TMPDIR"/classes*.dex; do
        java -jar "$BAKSMALI" disassemble "$dex" -o "$TMPDIR/smali_check" 2>/dev/null
        if find "$TMPDIR/smali_check" -name "PackageManagerServiceUtils*.smali" 2>/dev/null | grep -q .; then
            TARGET_DEX="$dex"
            break
        fi
        rm -rf "$TMPDIR/smali_check"
    done
fi

[ -z "$TARGET_DEX" ] && echo "ERROR: Could not find target class in any dex" && exit 1
echo "INFO: Found target in $(basename "$TARGET_DEX")"

# Decompile
SMALI_DIR="$TMPDIR/smali_out"
java -jar "$BAKSMALI" disassemble "$TARGET_DEX" -o "$SMALI_DIR" 2>/dev/null
[ $? -ne 0 ] && echo "ERROR: Baksmali failed" && exit 1

# Patch: disable APK integrity verification
# Find isVerificationEnabled / isIntegrityVerificationEnabled methods
PATCHED=0
find "$SMALI_DIR" -name "*.smali" | while read smali_file; do
    # Patch isVerificationEnabled to return false
    if grep -q "isVerificationEnabled\|isIntegrityVerificationEnabled\|verifyPackage\|checkPackageIntegrity" "$smali_file"; then
        echo "INFO: Patching $(basename "$smali_file")"
        
        # Replace verification methods to return false/0
        sed -i '/.method.*isVerificationEnabled/,/.end method/{
            /\.method/!{/\.end method/!{
                /const\/4/s/0x1/0x0/
            }}
        }' "$smali_file"

        sed -i '/.method.*isIntegrityVerificationEnabled/,/.end method/{
            /\.method/!{/\.end method/!{
                /const\/4/s/0x1/0x0/
            }}
        }' "$smali_file"

        # Patch verify return values
        sed -i '/.method.*verifyPackage/,/.end method/{
            /\.method/!{/\.end method/!{
                /const\/4.*0x1/s/0x1/0x0/
            }}
        }' "$smali_file"

        PATCHED=$((PATCHED + 1))
    fi
done

# Recompile
java -jar "$SMALI" assemble "$SMALI_DIR" -o "$TARGET_DEX" 2>/dev/null
[ $? -ne 0 ] && echo "ERROR: Smali assembly failed" && exit 1

# Replace in jar
cp "$SERVICES_JAR" "$SERVICES_JAR.bak"
cd "$TMPDIR"
zip -j "$SERVICES_JAR" "$TARGET_DEX" > /dev/null 2>&1

# Cleanup
rm -rf "$TMPDIR"

echo "INFO: APK Protection patch applied"
exit 0
