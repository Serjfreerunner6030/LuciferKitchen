#!/system/bin/sh
# Lucifer Kitchen - Patch boot.img for Magisk root
TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$TOOLS_DIR/bin"
BOOT_IMG="$1"

[ -z "$BOOT_IMG" ] && echo "ERROR: Usage: boot_patch_magisk.sh <boot.img>" && exit 1
[ ! -f "$BOOT_IMG" ] && echo "ERROR: File not found: $BOOT_IMG" && exit 1

export PATH="$BIN:$PATH"

echo "INFO: Patching boot.img for Magisk..."

BOOT_IMG="$(readlink -f "$BOOT_IMG")"
WORK_DIR="$(dirname "$BOOT_IMG")/magisk_patch_tmp"
mkdir -p "$WORK_DIR"
cd "$WORK_DIR"

# Unpack
$BIN/magiskboot unpack -h "$BOOT_IMG" 2>&1
[ $? -ne 0 ] && echo "ERROR: Failed to unpack" && exit 1

# Patch kernel to remove verity/encryption
if [ -f kernel ]; then
    echo "INFO: Patching kernel..."
    $BIN/magiskboot hexpatch kernel \
        49010054011440B93FA00F71E9000054010840B93FA00F7189000054001840B91FA00F7188010054 \
        A1020054011440B93FA00F7140020054010840B93FA00F71E0010054001840B91FA00F7140020054 \
        2>/dev/null
    echo "INFO: Kernel patched (skip_initramfs removed)"
fi

# Patch ramdisk for dm-verity/forceencrypt
if [ -f ramdisk.cpio ]; then
    echo "INFO: Patching ramdisk..."
    $BIN/magiskboot cpio ramdisk.cpio \
        "patch" 2>/dev/null
    echo "INFO: Ramdisk patched"
fi

# Repack
echo "INFO: Repacking..."
$BIN/magiskboot repack "$BOOT_IMG" 2>&1
[ $? -ne 0 ] && echo "ERROR: Failed to repack" && exit 1

OUTPUT="$(dirname "$BOOT_IMG")/boot-magisk-patched.img"
mv new-boot.img "$OUTPUT" 2>/dev/null
cd "$(dirname "$BOOT_IMG")"
rm -rf "$WORK_DIR"

SIZE=$(ls -lh "$OUTPUT" 2>/dev/null | awk '{print $5}')
echo "INFO: Patched image: $OUTPUT ($SIZE)"
exit 0
