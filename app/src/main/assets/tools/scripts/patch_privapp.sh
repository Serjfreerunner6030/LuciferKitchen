#!/system/bin/sh
# patch_privapp.sh - Patches priv-app permissions XML
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

get_package_name() {
    local APK="$1"
    local AAPT="$TOOLS_DIR/bin/aapt"

    if [ -f "$AAPT" ]; then
        "$AAPT" dump badging "$APK" 2>/dev/null | grep "^package:" | sed "s/.*name='\([^']*\)'.*/\1/"
    else
        # Fallback: try to read from META-INF or guess from filename
        basename "$(dirname "$APK")"
    fi
}

create_permissions_xml() {
    local PERMISSIONS_FILE="$1"
    local PARTITION="$2"

    echo "INFO: Creating permissions file: $PERMISSIONS_FILE"
    mkdir -p "$(dirname "$PERMISSIONS_FILE")"

    cat > "$PERMISSIONS_FILE" << 'XMLEOF'
<?xml version="1.0" encoding="utf-8"?>
<permissions>
XMLEOF

    # Scan priv-app directories
    for BASE in \
        "$PROJECT_DIR/${PARTITION}" \
        "$PROJECT_DIR/${PARTITION}_extracted" \
        "$PROJECT_DIR/super_extracted/${PARTITION}"; do
        PRIVAPP_DIR="$BASE/priv-app"
        if [ ! -d "$PRIVAPP_DIR" ]; then
            continue
        fi

        echo "INFO: Scanning $PRIVAPP_DIR..."

        find "$PRIVAPP_DIR" -name "*.apk" 2>/dev/null | while read APK; do
            APP_DIR="$(dirname "$APK")"
            APP_NAME="$(basename "$APP_DIR")"

            PKG=$(get_package_name "$APK")
            if [ -z "$PKG" ]; then
                PKG="$APP_NAME"
            fi

            echo "INFO:   Adding permissions for: $PKG"
            cat >> "$PERMISSIONS_FILE" << ENTRYEOF

    <privapp-permissions package="$PKG">
        <permission name="android.permission.INSTALL_PACKAGES"/>
        <permission name="android.permission.DELETE_PACKAGES"/>
        <permission name="android.permission.INTERACT_ACROSS_USERS"/>
        <permission name="android.permission.INTERACT_ACROSS_USERS_FULL"/>
        <permission name="android.permission.MANAGE_USERS"/>
        <permission name="android.permission.CHANGE_COMPONENT_ENABLED_STATE"/>
        <permission name="android.permission.BROADCAST_PACKAGE_REMOVED"/>
        <permission name="android.permission.FORCE_STOP_PACKAGES"/>
    </privapp-permissions>
ENTRYEOF
        done
    done

    echo '</permissions>' >> "$PERMISSIONS_FILE"
}

# Process system partition
for PARTITION in system product system_ext; do
    for BASE in \
        "$PROJECT_DIR/${PARTITION}" \
        "$PROJECT_DIR/${PARTITION}_extracted" \
        "$PROJECT_DIR/super_extracted/${PARTITION}"; do
        if [ ! -d "$BASE" ]; then
            continue
        fi

        PERMS_DIR="$BASE/etc/permissions"
        mkdir -p "$PERMS_DIR"
        PERMS_FILE="$PERMS_DIR/privapp-permissions-lucifer.xml"

        create_permissions_xml "$PERMS_FILE" "$PARTITION"

        # Verify the file is valid XML
        if [ -f "$PERMS_FILE" ] && [ -s "$PERMS_FILE" ]; then
            echo "INFO: Permissions file written: $PERMS_FILE"
        fi
        break
    done
done

# Fix existing privapp-permissions files that may have DENY entries causing issues
find "$PROJECT_DIR" -maxdepth 6 -name "privapp-permissions*.xml" 2>/dev/null | while read PFILE; do
    if grep -q "<deny-permission" "$PFILE" 2>/dev/null; then
        echo "INFO: Removing deny-permission entries from: $PFILE"
        sed -i '/<deny-permission/d' "$PFILE"
    fi
done

echo "INFO: Priv-app permissions patching complete"
exit 0
