#!/system/bin/sh
# patch_rw.sh — Make ROM partitions read-write
# Args: $1=project_dir

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT="$1"

[ -z "$PROJECT" ] && echo "ERROR: No project dir" && exit 1
[ ! -d "$PROJECT" ] && echo "ERROR: Project dir not found" && exit 1

echo "INFO: Making ROM full read-write..."

# Patch fstab files — remove 'ro' mount option
FSTAB_FILES=$(find "$PROJECT" -name "fstab.*" -o -name "fstab" 2>/dev/null)
for fstab in $FSTAB_FILES; do
    echo "INFO: Patching $fstab"
    # Replace ',ro,' with ',rw,' and ' ro ' with ' rw '
    sed -i 's/,ro,/,rw,/g; s/,ro$/,rw/g; s/ ro / rw /g; s/ ro,/ rw,/g' "$fstab"
    # Remove read-only flag from ext4/erofs/f2fs entries
    sed -i 's/\bro\b/rw/g' "$fstab"
done

# Patch init rc files — remount as rw
INIT_FILES=$(find "$PROJECT" -name "init*.rc" -name "*.rc" 2>/dev/null)
for rc in $INIT_FILES; do
    if grep -q "mount.*\bro\b" "$rc" 2>/dev/null; then
        echo "INFO: Patching $rc"
        sed -i 's/mount ext4.*ro/mount ext4 rw/g' "$rc"
        sed -i 's/mount erofs.*ro/mount erofs rw/g' "$rc"
    fi
done

# Patch build.prop — enable rw
for prop_file in $(find "$PROJECT" -name "build.prop" 2>/dev/null); do
    if ! grep -q "ro.debuggable=1" "$prop_file" 2>/dev/null; then
        echo "ro.debuggable=1" >> "$prop_file"
    fi
done

# Remove verity from fstab
for fstab in $FSTAB_FILES; do
    sed -i 's/,verify//g; s/verify,//g; s/,avb//g; s/avb,//g' "$fstab"
    sed -i 's/,avb=.*,/,/g; s/,avb=[^ ]* / /g' "$fstab"
done

echo "INFO: ROM set to read-write mode"
exit 0
