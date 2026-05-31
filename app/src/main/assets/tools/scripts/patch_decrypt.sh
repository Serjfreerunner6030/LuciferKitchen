#!/system/bin/sh
# patch_decrypt.sh — Disable forced encryption
# Args: $1=project_dir

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT="$1"

[ -z "$PROJECT" ] && echo "ERROR: No project dir" && exit 1
[ ! -d "$PROJECT" ] && echo "ERROR: Project dir not found" && exit 1

echo "INFO: Disabling forced encryption..."

# Patch fstab files
FSTAB_FILES=$(find "$PROJECT" -name "fstab.*" -o -name "fstab" 2>/dev/null)
for fstab in $FSTAB_FILES; do
    echo "INFO: Patching $fstab"
    # Remove encryption flags
    sed -i 's/,fileencryption=[^,\s]*//g' "$fstab"
    sed -i 's/fileencryption=[^,\s]*,//g' "$fstab"
    sed -i 's/,forceencrypt=[^,\s]*//g' "$fstab"
    sed -i 's/forceencrypt=[^,\s]*,//g' "$fstab"
    sed -i 's/,forcefdeorfbe=[^,\s]*//g' "$fstab"
    sed -i 's/forcefdeorfbe=[^,\s]*,//g' "$fstab"
    sed -i 's/,encryptable=[^,\s]*//g' "$fstab"
    sed -i 's/encryptable=[^,\s]*,//g' "$fstab"
    sed -i 's/,metadata_encryption=[^,\s]*//g' "$fstab"
    sed -i 's/metadata_encryption=[^,\s]*,//g' "$fstab"
    sed -i 's/,keydirectory=[^,\s]*//g' "$fstab"
    sed -i 's/keydirectory=[^,\s]*,//g' "$fstab"
    sed -i 's/,wrappedkey//g' "$fstab"
done

# Patch vendor init scripts
INIT_FILES=$(find "$PROJECT/vendor" -name "*.rc" 2>/dev/null)
for rc in $INIT_FILES; do
    if grep -q "installkey\|encrypt\|vold" "$rc" 2>/dev/null; then
        echo "INFO: Checking $rc"
        sed -i 's/forceencrypt/encryptable/g' "$rc"
    fi
done

# Remove encryption props
for prop_file in $(find "$PROJECT" -name "build.prop" -o -name "default.prop" 2>/dev/null); do
    sed -i '/ro.crypto.state/d' "$prop_file"
    echo "ro.crypto.state=unencrypted" >> "$prop_file"
done

echo "INFO: Encryption disabled"
exit 0
