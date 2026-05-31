#!/system/bin/sh
# patch_integrity.sh - Fixes Play Integrity by modifying build.prop files
# Args: $1=project_dir

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"

PROJECT_DIR="$1"

if [ -z "$PROJECT_DIR" ]; then
    echo "ERROR: project_dir argument required"
    exit 1
fi

if [ ! -d "$PROJECT_DIR" ]; then
    echo "ERROR: Project directory not found: $PROJECT_DIR"
    exit 1
fi

set_prop() {
    local FILE="$1"
    local KEY="$2"
    local VALUE="$3"

    if [ ! -f "$FILE" ]; then
        return
    fi

    if grep -q "^${KEY}=" "$FILE" 2>/dev/null; then
        # Replace existing key
        sed -i "s|^${KEY}=.*|${KEY}=${VALUE}|g" "$FILE"
        echo "INFO:   Set $KEY=$VALUE"
    else
        # Append if not present
        echo "${KEY}=${VALUE}" >> "$FILE"
        echo "INFO:   Added $KEY=$VALUE"
    fi
}

remove_prop() {
    local FILE="$1"
    local PATTERN="$2"

    if [ ! -f "$FILE" ]; then
        return
    fi

    if grep -q "$PATTERN" "$FILE" 2>/dev/null; then
        sed -i "/$PATTERN/d" "$FILE"
        echo "INFO:   Removed lines matching: $PATTERN"
    fi
}

patch_build_prop() {
    local FILE="$1"
    local LABEL="$2"

    if [ ! -f "$FILE" ]; then
        echo "INFO: Skipping $LABEL (not found)"
        return
    fi

    echo "INFO: Patching $LABEL: $FILE"

    # Core integrity properties
    set_prop "$FILE" "ro.build.type" "user"
    set_prop "$FILE" "ro.debuggable" "0"
    set_prop "$FILE" "ro.secure" "1"
    set_prop "$FILE" "ro.build.tags" "release-keys"

    # Remove test-keys references
    remove_prop "$FILE" "test-keys"

    # Additional properties for Play Integrity
    set_prop "$FILE" "ro.build.selinux" "1"

    # Fix build keys in description and fingerprint lines
    sed -i 's/test-keys/release-keys/g' "$FILE"
    sed -i 's/dev-keys/release-keys/g' "$FILE"
    sed -i 's/userdebug/user/g' "$FILE"

    echo "INFO:   Done patching $LABEL"
}

# Patch all relevant build.prop locations
for PARTITION in system vendor product system_ext odm; do
    for BASE in \
        "$PROJECT_DIR/${PARTITION}" \
        "$PROJECT_DIR/${PARTITION}_extracted" \
        "$PROJECT_DIR/super_extracted/${PARTITION}"; do
        if [ -d "$BASE" ]; then
            BUILD_PROP="$BASE/build.prop"
            patch_build_prop "$BUILD_PROP" "${PARTITION}/build.prop"

            # Also check etc/build.prop
            if [ -f "$BASE/etc/build.prop" ]; then
                patch_build_prop "$BASE/etc/build.prop" "${PARTITION}/etc/build.prop"
            fi
        fi
    done
done

# Handle direct build.prop in root project dir
if [ -f "$PROJECT_DIR/build.prop" ]; then
    patch_build_prop "$PROJECT_DIR/build.prop" "root/build.prop"
fi

echo "INFO: Play Integrity patching complete"
exit 0
