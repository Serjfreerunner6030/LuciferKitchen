#!/system/bin/sh
# Lucifer Kitchen - Repack boot/recovery/vendor_boot image
TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$TOOLS_DIR/bin"
WORK_DIR="$1"
OUTPUT_IMG="${2:-}"

[ -z "$WORK_DIR" ] && echo "ERROR: Usage: boot_repack.sh <unpacked_dir> [output.img]" && exit 1
[ ! -d "$WORK_DIR" ] && echo "ERROR: Directory not found: $WORK_DIR" && exit 1

export PATH="$BIN:$PATH"
export LD_LIBRARY_PATH="$BIN/lib:$LD_LIBRARY_PATH"
bb="$BIN/busybox"
[ ! -f "$bb" ] && bb="busybox"

[ -z "$OUTPUT_IMG" ] && OUTPUT_IMG="$WORK_DIR/boot-new.img"

echo "INFO: Repacking boot image from: $WORK_DIR"
echo "INFO: Output: $OUTPUT_IMG"

cd "$WORK_DIR"

# Detect ramdisk compression from original
RAMDISK_ORIG=$(find split_img -name "*ramdisk*" -type f | head -1)
COMP="$($BIN/file -m $BIN/magic "$RAMDISK_ORIG" 2>/dev/null | $bb cut -d: -f2 | $bb awk '{ print $1 }')"
echo "INFO: Using compression: $COMP"

# Pack ramdisk
echo "INFO: Packing ramdisk..."
cd ramdisk

case "$COMP" in
    gzip)   $bb find . | cpio -H newc -o 2>/dev/null | $bb gzip -9 > ../ramdisk-new.cpio.gz ;;
    lz4)    $bb find . | cpio -H newc -o 2>/dev/null | $BIN/lz4 -l -9 > ../ramdisk-new.cpio.lz4 ;;
    xz)     $bb find . | cpio -H newc -o 2>/dev/null | $BIN/xz -1 -Ccrc32 > ../ramdisk-new.cpio.xz ;;
    lzma)   $bb find . | cpio -H newc -o 2>/dev/null | $BIN/xz -Flzma > ../ramdisk-new.cpio.lzma ;;
    bzip2)  $bb find . | cpio -H newc -o 2>/dev/null | $bb bzip2 -9 > ../ramdisk-new.cpio.bz2 ;;
    *)      $bb find . | cpio -H newc -o 2>/dev/null | $bb gzip -9 > ../ramdisk-new.cpio.gz; COMP="gzip" ;;
esac

cd "$WORK_DIR"
RAMDISK_NEW=$(find . -maxdepth 1 -name "ramdisk-new.*" -type f | head -1)
echo "INFO: Ramdisk packed: $RAMDISK_NEW"

# Find kernel and other components
KERNEL=$(find split_img -name "*kernel" -type f | head -1)
DTB=$(find split_img -name "*dtb" -type f | head -1)
CMDLINE=""
[ -f split_img/*-cmdline ] && CMDLINE="$(cat split_img/*-cmdline 2>/dev/null)"
BASE=""
[ -f split_img/*-base ] && BASE="$(cat split_img/*-base 2>/dev/null)"
PAGESIZE=""
[ -f split_img/*-pagesize ] && PAGESIZE="$(cat split_img/*-pagesize 2>/dev/null)"
KERNELOFF=""
[ -f split_img/*-kernel_offset ] && KERNELOFF="$(cat split_img/*-kernel_offset 2>/dev/null)"
RAMDISKOFF=""
[ -f split_img/*-ramdisk_offset ] && RAMDISKOFF="$(cat split_img/*-ramdisk_offset 2>/dev/null)"
TAGSOFF=""
[ -f split_img/*-tags_offset ] && TAGSOFF="$(cat split_img/*-tags_offset 2>/dev/null)"
DTBOFF=""
[ -f split_img/*-dtb_offset ] && DTBOFF="$(cat split_img/*-dtb_offset 2>/dev/null)"
HDRVER=""
[ -f split_img/*-header_version ] && HDRVER="$(cat split_img/*-header_version 2>/dev/null)"
OSVER=""
[ -f split_img/*-os_version ] && OSVER="$(cat split_img/*-os_version 2>/dev/null)"
OSLVL=""
[ -f split_img/*-os_patch_level ] && OSLVL="$(cat split_img/*-os_patch_level 2>/dev/null)"

# Build mkbootimg command
echo "INFO: Building image..."
CMD="$BIN/mkbootimg"
[ -n "$KERNEL" ] && CMD="$CMD --kernel $KERNEL"
CMD="$CMD --ramdisk $RAMDISK_NEW"
[ -n "$CMDLINE" ] && CMD="$CMD --cmdline \"$CMDLINE\""
[ -n "$BASE" ] && CMD="$CMD --base $BASE"
[ -n "$PAGESIZE" ] && CMD="$CMD --pagesize $PAGESIZE"
[ -n "$KERNELOFF" ] && CMD="$CMD --kernel_offset $KERNELOFF"
[ -n "$RAMDISKOFF" ] && CMD="$CMD --ramdisk_offset $RAMDISKOFF"
[ -n "$TAGSOFF" ] && CMD="$CMD --tags_offset $TAGSOFF"
[ -n "$DTBOFF" ] && CMD="$CMD --dtb_offset $DTBOFF"
[ -n "$DTB" ] && CMD="$CMD --dtb $DTB"
[ -n "$HDRVER" ] && CMD="$CMD --header_version $HDRVER"
[ -n "$OSVER" ] && CMD="$CMD --os_version $OSVER"
[ -n "$OSLVL" ] && CMD="$CMD --os_patch_level $OSLVL"
CMD="$CMD -o $OUTPUT_IMG"

eval $CMD 2>&1
EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    SIZE=$(ls -lh "$OUTPUT_IMG" | awk '{print $5}')
    echo "INFO: Boot image repacked: $OUTPUT_IMG ($SIZE)"

    # Pad to original size if original boot image exists
    ORIG_BOOT=""
    PARENT_DIR="$(dirname "$WORK_DIR")"
    for candidate in "$PARENT_DIR/boot.img" "$PARENT_DIR/boot_orig.img" \
                     "$WORK_DIR/../boot.img"; do
        if [ -f "$candidate" ]; then
            ORIG_BOOT="$(readlink -f "$candidate")"
            break
        fi
    done

    # Also check split_img for original size record
    ORIG_SIZE=""
    if [ -f split_img/boot-origsize ]; then
        ORIG_SIZE="$(cat split_img/boot-origsize 2>/dev/null)"
    elif [ -f split_img/*-origsize ]; then
        ORIG_SIZE="$(cat split_img/*-origsize 2>/dev/null)"
    fi

    if [ -z "$ORIG_SIZE" ] && [ -n "$ORIG_BOOT" ]; then
        ORIG_SIZE=$(wc -c < "$ORIG_BOOT" 2>/dev/null)
    fi

    if [ -n "$ORIG_SIZE" ] && [ "$ORIG_SIZE" -gt 0 ] 2>/dev/null; then
        CURRENT_SIZE=$(wc -c < "$OUTPUT_IMG" 2>/dev/null)
        if [ "$CURRENT_SIZE" -lt "$ORIG_SIZE" ]; then
            echo "INFO: Padding image to original size: ${ORIG_SIZE} bytes (was ${CURRENT_SIZE} bytes)"
            dd if=/dev/zero bs=1 count=$(( ORIG_SIZE - CURRENT_SIZE )) >> "$OUTPUT_IMG" 2>/dev/null
            NEW_SIZE=$(ls -lh "$OUTPUT_IMG" | awk '{print $5}')
            echo "INFO: Final size: $NEW_SIZE"
        else
            echo "INFO: Image size OK (${CURRENT_SIZE} bytes, original ${ORIG_SIZE} bytes)"
        fi
    else
        echo "WARN: Original boot image not found — cannot pad to original size"
        echo "WARN: Some devices may reject a boot image with different size"
        echo "WARN: If flashing fails, place the original boot.img next to the unpacked dir"
    fi

    # Clean up temp ramdisk
    rm -f ramdisk-new.cpio.* 2>/dev/null
else
    echo "WARN: mkbootimg failed, trying magiskboot..."
    cp "$KERNEL" kernel 2>/dev/null
    cp "$RAMDISK_NEW" ramdisk.cpio 2>/dev/null
    [ -n "$DTB" ] && cp "$DTB" dtb 2>/dev/null
    $BIN/magiskboot repack "$WORK_DIR/split_img/"*-origsize 2>/dev/null || \
    $BIN/magiskboot repack boot.img 2>/dev/null
    [ -f new-boot.img ] && mv new-boot.img "$OUTPUT_IMG"
    rm -f kernel ramdisk.cpio dtb 2>/dev/null
fi

echo "INFO: Done"
exit $EXIT_CODE
