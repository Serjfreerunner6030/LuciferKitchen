#!/system/bin/sh
# Lucifer Kitchen - Download and setup all binary tools
# Supports: Termux packages, system binaries, GitHub releases
TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BIN_DIR="$TOOLS_DIR/bin"
TMPDIR="$TOOLS_DIR/tmp"

mkdir -p "$BIN_DIR" "$TMPDIR"

echo "INFO: Setting up Lucifer Kitchen tools..."

# Detect architecture
ARCH="$(uname -m)"
case "$ARCH" in
    aarch64|arm64) ABI="arm64-v8a"; ARCH_TAG="aarch64"; TERMUX_ARCH="aarch64" ;;
    armv7*|armv8l) ABI="armeabi-v7a"; ARCH_TAG="arm"; TERMUX_ARCH="arm" ;;
    x86_64) ABI="x86_64"; ARCH_TAG="x86_64"; TERMUX_ARCH="x86_64" ;;
    i*86) ABI="x86"; ARCH_TAG="i686"; TERMUX_ARCH="i686" ;;
    *) echo "ERROR: Unsupported architecture: $ARCH"; exit 1 ;;
esac
echo "INFO: Architecture: $ABI ($ARCH_TAG)"

# =====================================================================
# Helper functions
# =====================================================================
download() {
    local url="$1" output="$2"
    echo "INFO: Downloading $(basename "$output")..."
    if command -v curl > /dev/null 2>&1; then
        curl -fSL --connect-timeout 15 --max-time 120 -o "$output" "$url" 2>/dev/null
    elif command -v wget > /dev/null 2>&1; then
        wget -q --timeout=15 -O "$output" "$url" 2>/dev/null
    else
        echo "ERROR: No download tool (curl/wget)"
        return 1
    fi
    [ -f "$output" ] && [ "$(wc -c < "$output")" -gt 100 ] && return 0
    rm -f "$output" 2>/dev/null
    return 1
}

# Extract a single binary from a .deb package
extract_deb_bin() {
    local deb="$1" binname="$2" outpath="$3"
    local tmpex="$TMPDIR/deb_$$"
    mkdir -p "$tmpex"
    cd "$tmpex"
    ar x "$deb" 2>/dev/null
    # data.tar may be .xz, .gz, .zst
    if [ -f data.tar.xz ]; then
        xz -d data.tar.xz 2>/dev/null && tar xf data.tar 2>/dev/null
    elif [ -f data.tar.gz ]; then
        gzip -d data.tar.gz 2>/dev/null && tar xf data.tar 2>/dev/null
    elif [ -f data.tar.zst ]; then
        zstd -d data.tar.zst -o data.tar 2>/dev/null && tar xf data.tar 2>/dev/null
    elif [ -f data.tar ]; then
        tar xf data.tar 2>/dev/null
    fi
    local found=$(find "$tmpex" -name "$binname" -type f 2>/dev/null | head -1)
    if [ -n "$found" ]; then
        cp "$found" "$outpath"
        chmod 755 "$outpath"
        echo "INFO: Extracted $binname"
    else
        echo "WARN: $binname not found in deb"
    fi
    rm -rf "$tmpex"
    cd "$TOOLS_DIR"
}

# Try to get a tool: 1) bundled, 2) system PATH, 3) /system/bin, 4) Termux pkg
get_native_tool() {
    local toolname="$1"
    local termux_pkg="$2"    # Termux package name (optional)
    local termux_bin="$3"    # binary name inside Termux pkg (optional, defaults to toolname)
    [ -z "$termux_bin" ] && termux_bin="$toolname"

    # Already have it
    [ -f "$BIN_DIR/$toolname" ] && [ "$(wc -c < "$BIN_DIR/$toolname")" -gt 100 ] && return 0

    # Try system PATH
    local sys_path=$(command -v "$toolname" 2>/dev/null)
    if [ -n "$sys_path" ]; then
        cp "$sys_path" "$BIN_DIR/$toolname" 2>/dev/null && chmod 755 "$BIN_DIR/$toolname"
        echo "INFO: Copied $toolname from PATH"
        return 0
    fi

    # Try /system/bin (Android)
    if [ -f "/system/bin/$toolname" ]; then
        cp "/system/bin/$toolname" "$BIN_DIR/$toolname" 2>/dev/null && chmod 755 "$BIN_DIR/$toolname"
        echo "INFO: Copied $toolname from /system/bin"
        return 0
    fi

    # Try Termux prefix
    if [ -f "/data/data/com.termux/files/usr/bin/$toolname" ]; then
        cp "/data/data/com.termux/files/usr/bin/$toolname" "$BIN_DIR/$toolname" 2>/dev/null && chmod 755 "$BIN_DIR/$toolname"
        echo "INFO: Copied $toolname from Termux"
        return 0
    fi

    # Try downloading Termux .deb package
    if [ -n "$termux_pkg" ]; then
        local TERMUX_REPO="https://packages.termux.dev/apt/termux-main"
        local deb_url="$TERMUX_REPO/pool/main/${termux_pkg:0:1}/${termux_pkg}/${termux_pkg}_${TERMUX_ARCH}.deb"
        local deb_file="$TMPDIR/${termux_pkg}.deb"

        # Try multiple version patterns from Termux repo
        for try_url in \
            "$TERMUX_REPO/pool/main/${termux_pkg:0:1}/${termux_pkg}/" \
            ; do
            # Download package index first to find exact filename
            local idx_html="$TMPDIR/pkg_idx.html"
            download "$try_url" "$idx_html" 2>/dev/null
            if [ -f "$idx_html" ]; then
                local deb_name=$(grep -oE "${termux_pkg}[^\"]*${TERMUX_ARCH}\.deb" "$idx_html" 2>/dev/null | tail -1)
                if [ -n "$deb_name" ]; then
                    download "${try_url}${deb_name}" "$deb_file"
                    if [ -f "$deb_file" ] && [ "$(wc -c < "$deb_file")" -gt 1000 ]; then
                        extract_deb_bin "$deb_file" "$termux_bin" "$BIN_DIR/$toolname"
                        rm -f "$deb_file" "$idx_html"
                        [ -f "$BIN_DIR/$toolname" ] && return 0
                    fi
                fi
            fi
            rm -f "$idx_html"
        done
        rm -f "$deb_file" 2>/dev/null
    fi

    echo "WARN: $toolname not available. Install manually or via Termux: pkg install $termux_pkg"
    return 1
}

# =====================================================================
# Busybox setup (bundled in APK)
# =====================================================================
echo ""
echo "=== Busybox ==="
if [ -f "$BIN_DIR/busybox" ]; then
    chmod 755 "$BIN_DIR/busybox"
    # Install busybox applets we need
    for applet in ar tar gzip xz unzip zip find sed grep awk cp mv rm mkdir chmod cat wc dd mount umount stat; do
        if [ ! -f "$BIN_DIR/$applet" ]; then
            ln -sf "$BIN_DIR/busybox" "$BIN_DIR/$applet" 2>/dev/null
        fi
    done
    echo "INFO: Busybox applets installed"
else
    echo "WARN: Busybox not found in $BIN_DIR"
fi

# =====================================================================
# Java tools (bundled in APK - just verify)
# =====================================================================
echo ""
echo "=== Java Tools (bundled) ==="
for jar in apktool.jar baksmali.jar smali.jar uber-apk-signer.jar apksigner.jar; do
    if [ -f "$BIN_DIR/$jar" ]; then
        echo "  OK: $jar ($(wc -c < "$BIN_DIR/$jar") bytes)"
    else
        echo "  MISSING: $jar"
    fi
done

# =====================================================================
# Native binaries - payload-dumper-go (bundled)
# =====================================================================
echo ""
echo "=== Native Tools (bundled) ==="
if [ -f "$BIN_DIR/payload-dumper-go" ]; then
    chmod 755 "$BIN_DIR/payload-dumper-go"
    echo "  OK: payload-dumper-go"
else
    echo "  MISSING: payload-dumper-go"
fi

if [ -f "$BIN_DIR/magiskboot" ]; then
    chmod 755 "$BIN_DIR/magiskboot"
    echo "  OK: magiskboot"
fi

# =====================================================================
# Native binaries - downloaded from system/Termux
# =====================================================================
echo ""
echo "=== Native Tools (system/Termux) ==="

# Compression tools
get_native_tool "brotli"      "brotli"         "brotli"
get_native_tool "zstd"        "zstd"           "zstd"

# Android image tools
get_native_tool "simg2img"    "android-tools"  "simg2img"
get_native_tool "img2simg"    "android-tools"  "img2simg"
get_native_tool "lpunpack"    "android-tools"  "lpunpack"
get_native_tool "lpmake"      "android-tools"  "lpmake"
get_native_tool "lpdump"      "android-tools"  "lpdump"

# Filesystem tools
get_native_tool "make_ext4fs" "android-tools"  "make_ext4fs"
get_native_tool "mke2fs"      "e2fsprogs"      "mke2fs"
get_native_tool "e2fsdroid"   "android-tools"  "e2fsdroid"
get_native_tool "mkfs.erofs"  "erofs-utils"    "mkfs.erofs"
get_native_tool "fsck.erofs"  "erofs-utils"    "fsck.erofs"
get_native_tool "make_f2fs"   "f2fs-tools"     "mkfs.f2fs"
get_native_tool "sload_f2fs"  "f2fs-tools"     "sload.f2fs"

# APK tools
get_native_tool "zipalign"    "android-tools"  "zipalign"
get_native_tool "aapt2"       "android-tools"  "aapt2"

# General utilities
get_native_tool "python3"     "python"         "python3"
get_native_tool "java"        "openjdk-21"     "java"

# =====================================================================
# Set permissions on everything
# =====================================================================
echo ""
echo "=== Setting Permissions ==="
chmod 755 "$BIN_DIR"/* 2>/dev/null
chmod 755 "$TOOLS_DIR/scripts"/*.sh 2>/dev/null
chmod 755 "$TOOLS_DIR/scripts"/*.py 2>/dev/null

# =====================================================================
# Status report
# =====================================================================
echo ""
echo "=== Final Status ==="
echo "Tools directory: $TOOLS_DIR"
echo ""

TOTAL=0
FOUND=0
MISSING_LIST=""

for tool in busybox magiskboot payload-dumper-go \
            apktool.jar baksmali.jar smali.jar uber-apk-signer.jar apksigner.jar \
            brotli zstd simg2img img2simg lpunpack lpmake \
            make_ext4fs mke2fs e2fsdroid mkfs.erofs fsck.erofs zipalign; do
    TOTAL=$((TOTAL + 1))
    if [ -f "$BIN_DIR/$tool" ] && [ "$(wc -c < "$BIN_DIR/$tool")" -gt 100 ]; then
        FOUND=$((FOUND + 1))
    else
        MISSING_LIST="$MISSING_LIST $tool"
    fi
done

echo "Ready: $FOUND / $TOTAL tools"
if [ -n "$MISSING_LIST" ]; then
    echo ""
    echo "Missing:$MISSING_LIST"
    echo ""
    echo "To install missing tools on a rooted Android device:"
    echo "  1. Install Termux from F-Droid"
    echo "  2. Run: pkg install android-tools brotli zstd e2fsprogs erofs-utils f2fs-tools"
    echo "  3. Run this script again"
    echo ""
    echo "Or place ARM64 binaries manually in: $BIN_DIR"
fi

echo ""
echo "INFO: Setup complete"
rm -rf "$TMPDIR" 2>/dev/null
exit 0
