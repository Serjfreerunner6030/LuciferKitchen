#!/system/bin/sh
# patch_ota.sh — Disable OTA updates
# Args: $1=project_dir

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT="$1"

[ -z "$PROJECT" ] && echo "ERROR: No project dir" && exit 1
[ ! -d "$PROJECT" ] && echo "ERROR: Project dir not found" && exit 1

echo "INFO: Disabling OTA updates..."

# Remove OTA updater apps
OTA_APPS="Updater MiuiUpdater HybridAccessory OTAUpdater SystemUpdate FWUpdate FWUpgrade FotaClient"
for part in system product system_ext; do
    for app in $OTA_APPS; do
        for subdir in app priv-app; do
            APP_DIR="$PROJECT/$part/$subdir/$app"
            if [ -d "$APP_DIR" ]; then
                echo "INFO: Removing $part/$subdir/$app"
                rm -rf "$APP_DIR"
            fi
        done
    done
done

# Block OTA URLs in hosts
HOSTS_FILE="$PROJECT/system/etc/hosts"
if [ -f "$HOSTS_FILE" ]; then
    echo "INFO: Blocking OTA domains in hosts file"
    for domain in ota.miui.com update.miui.com flash.sec.miui.com bigota.d.miui.com hugeota.d.miui.com; do
        grep -q "$domain" "$HOSTS_FILE" 2>/dev/null || echo "127.0.0.1 $domain" >> "$HOSTS_FILE"
    done
else
    mkdir -p "$(dirname "$HOSTS_FILE")"
    echo "127.0.0.1 localhost" > "$HOSTS_FILE"
    for domain in ota.miui.com update.miui.com flash.sec.miui.com bigota.d.miui.com hugeota.d.miui.com; do
        echo "127.0.0.1 $domain" >> "$HOSTS_FILE"
    done
fi

# Disable OTA in build.prop
for prop_file in $(find "$PROJECT" -name "build.prop" 2>/dev/null); do
    sed -i '/ro.build.ab_update/d' "$prop_file"
    echo "ro.build.ab_update=false" >> "$prop_file"
    sed -i '/ro.ota.allow_downgrade/d' "$prop_file"
    echo "ro.ota.allow_downgrade=false" >> "$prop_file"
done

echo "INFO: OTA updates disabled"
exit 0
