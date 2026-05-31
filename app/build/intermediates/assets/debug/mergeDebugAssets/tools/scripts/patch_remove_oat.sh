#!/system/bin/sh
# patch_remove_oat.sh — Remove pre-compiled OAT/ODEX/VDEX files
# Args: $1=project_dir

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT="$1"

[ -z "$PROJECT" ] && echo "ERROR: No project dir" && exit 1
[ ! -d "$PROJECT" ] && echo "ERROR: Project dir not found" && exit 1

echo "INFO: Removing OAT/ODEX/VDEX/ART files..."

COUNT=0
for ext in oat odex vdex art; do
    FILES=$(find "$PROJECT" -name "*.$ext" -type f 2>/dev/null)
    for f in $FILES; do
        echo "INFO: Removing $f"
        rm -f "$f"
        COUNT=$((COUNT + 1))
    done
done

# Remove oat directories
OAT_DIRS=$(find "$PROJECT" -type d -name "oat" 2>/dev/null)
for d in $OAT_DIRS; do
    echo "INFO: Removing directory $d"
    rm -rf "$d"
    COUNT=$((COUNT + 1))
done

echo "INFO: Removed $COUNT OAT/ODEX/VDEX/ART files and directories"
exit 0
