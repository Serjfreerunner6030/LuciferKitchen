#!/system/bin/sh
# unpack_super.sh - Unpacks super.img using lpunpack
# Args: $1=project_dir, $2=super.img (optional, auto-detected)

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$TOOLS_DIR/bin"

PROJECT_DIR="$1"
SUPER_IMG_ARG="$2"

if [ -z "$PROJECT_DIR" ]; then
    echo "ERROR: project_dir argument required"
    exit 1
fi

if [ ! -d "$PROJECT_DIR" ]; then
    echo "ERROR: Project directory not found: $PROJECT_DIR"
    exit 1
fi

LPUNPACK="$BIN/lpunpack"
SIMG2IMG="$BIN/simg2img"

if [ ! -f "$LPUNPACK" ]; then
    echo "ERROR: lpunpack binary not found: $LPUNPACK"
    exit 1
fi

# Find super.img
SUPER_IMG=""
if [ -n "$SUPER_IMG_ARG" ]; then
    if [ -f "$SUPER_IMG_ARG" ]; then
        SUPER_IMG="$SUPER_IMG_ARG"
    elif [ -f "$PROJECT_DIR/$SUPER_IMG_ARG" ]; then
        SUPER_IMG="$PROJECT_DIR/$SUPER_IMG_ARG"
    fi
fi

if [ -z "$SUPER_IMG" ]; then
    for candidate in "$PROJECT_DIR/super.img" \
                     "$PROJECT_DIR/extracted/super.img" \
                     "$PROJECT_DIR/images/super.img"; do
        if [ -f "$candidate" ]; then
            SUPER_IMG="$candidate"
            break
        fi
    done
fi

if [ -z "$SUPER_IMG" ]; then
    SUPER_IMG="$(find "$PROJECT_DIR" -maxdepth 4 -name "super.img" 2>/dev/null | head -n 1)"
fi

if [ -z "$SUPER_IMG" ] || [ ! -f "$SUPER_IMG" ]; then
    echo "ERROR: super.img not found in $PROJECT_DIR"
    exit 1
fi

echo "INFO: Found super.img: $SUPER_IMG"

# Check if sparse image (magic: 3aff26ed)
SPARSE_MAGIC="$(dd if="$SUPER_IMG" bs=1 count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')"
WORK_IMG="$SUPER_IMG"

if [ "$SPARSE_MAGIC" = "3aff26ed" ]; then
    echo "INFO: Detected sparse image format, converting to raw..."
    if [ ! -f "$SIMG2IMG" ]; then
        echo "ERROR: simg2img binary not found: $SIMG2IMG"
        exit 1
    fi
    RAW_IMG="${SUPER_IMG%.img}_raw.img"
    "$SIMG2IMG" "$SUPER_IMG" "$RAW_IMG"
    if [ $? -ne 0 ]; then
        echo "ERROR: simg2img conversion failed"
        exit 1
    fi
    echo "INFO: Converted sparse to raw: $RAW_IMG"
    WORK_IMG="$RAW_IMG"
else
    echo "INFO: Image is already in raw format"
fi

OUTPUT_DIR="$PROJECT_DIR/super_extracted"
mkdir -p "$OUTPUT_DIR"

echo "INFO: Running lpunpack..."
"$LPUNPACK" "$WORK_IMG" "$OUTPUT_DIR"
RET=$?

if [ $RET -ne 0 ]; then
    echo "ERROR: lpunpack failed with exit code $RET"
    exit 1
fi

echo "INFO: Super image unpacked to: $OUTPUT_DIR"
ls -lh "$OUTPUT_DIR" 2>/dev/null
exit 0
