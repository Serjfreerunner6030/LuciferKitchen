#!/system/bin/sh
# patch_vbmeta.sh - Patches vbmeta.img to disable AVB verification
# Args: $1=project_dir

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$TOOLS_DIR/bin"

PROJECT_DIR="$1"

if [ -z "$PROJECT_DIR" ]; then
    echo "ERROR: project_dir argument required"
    exit 1
fi

if [ ! -d "$PROJECT_DIR" ]; then
    echo "ERROR: Project directory not found: $PROJECT_DIR"
    exit 1
fi

# Find vbmeta.img files
VBMETA_FILES=""
for candidate in \
    "$PROJECT_DIR/vbmeta.img" \
    "$PROJECT_DIR/vbmeta_system.img" \
    "$PROJECT_DIR/vbmeta_vendor.img" \
    "$PROJECT_DIR/extracted/vbmeta.img" \
    "$PROJECT_DIR/output/vbmeta.img"; do
    if [ -f "$candidate" ]; then
        VBMETA_FILES="$VBMETA_FILES $candidate"
    fi
done

if [ -z "$VBMETA_FILES" ]; then
    VBMETA_FILES="$(find "$PROJECT_DIR" -maxdepth 4 -name "vbmeta*.img" 2>/dev/null | tr '\n' ' ')"
fi

if [ -z "$VBMETA_FILES" ]; then
    echo "ERROR: No vbmeta.img files found in $PROJECT_DIR"
    exit 1
fi

patch_vbmeta() {
    local VBMETA="$1"
    echo "INFO: Patching: $VBMETA"

    # Verify AVB magic: "AVB0" at offset 0
    MAGIC="$(dd if="$VBMETA" bs=1 count=4 2>/dev/null)"
    if [ "$MAGIC" != "AVB0" ]; then
        echo "WARN: $VBMETA does not have AVB0 magic, skipping"
        return 1
    fi

    # Patch flags at offset 123 (0x7B):
    # Set bit 0 = disable verification
    # Set bit 1 = disable vbmeta verification
    # Value 0x03 disables both flags
    printf '\x03' | dd of="$VBMETA" bs=1 seek=123 conv=notrunc 2>/dev/null
    if [ $? -ne 0 ]; then
        echo "ERROR: Failed to patch flags byte at offset 123"
        return 1
    fi

    # Also patch offset 120 (0x78) - hashtree_error_mode to 2 (ignore)
    printf '\x02' | dd of="$VBMETA" bs=1 seek=120 conv=notrunc 2>/dev/null

    echo "INFO: Patched vbmeta flags: offset 0x7B = 0x03 (verification disabled)"
    return 0
}

PATCHED=0
for VBMETA in $VBMETA_FILES; do
    patch_vbmeta "$VBMETA"
    [ $? -eq 0 ] && PATCHED=$(( PATCHED + 1 ))
done

if [ $PATCHED -eq 0 ]; then
    echo "ERROR: No vbmeta images were successfully patched"
    exit 1
fi

echo "INFO: Successfully patched $PATCHED vbmeta image(s)"
exit 0
