#!/usr/bin/env python3
# img2sdat - Convert raw ext4 image to Android sparse data format
# Usage: img2sdat.py <input_img> [output_prefix] [version]

import sys
import os
import hashlib

BLOCK_SIZE = 4096

def main():
    if len(sys.argv) < 2:
        print("Usage: img2sdat.py <input_img> [output_prefix] [version]")
        sys.exit(1)

    input_img = sys.argv[1]
    output_prefix = sys.argv[2] if len(sys.argv) > 2 else os.path.splitext(input_img)[0]
    version = int(sys.argv[3]) if len(sys.argv) > 3 else 4

    if not os.path.isfile(input_img):
        print("ERROR: Input image not found: " + input_img)
        sys.exit(1)

    img_size = os.path.getsize(input_img)
    total_blocks = (img_size + BLOCK_SIZE - 1) // BLOCK_SIZE
    print("INFO: Input: %s (%.1f MB, %d blocks)" % (input_img, img_size / 1024.0 / 1024.0, total_blocks))
    print("INFO: Version: %d" % version)

    transfer_list = output_prefix + ".transfer.list"
    new_dat = output_prefix + ".new.dat"

    # Read image and find non-zero blocks
    non_zero_ranges = []
    zero_ranges = []

    with open(input_img, 'rb') as f:
        block_idx = 0
        in_nonzero = False
        range_start = 0

        while True:
            data = f.read(BLOCK_SIZE)
            if not data:
                break

            is_zero = (data == b'\x00' * len(data))

            if not is_zero:
                if not in_nonzero:
                    range_start = block_idx
                    in_nonzero = True
            else:
                if in_nonzero:
                    non_zero_ranges.append((range_start, block_idx))
                    in_nonzero = False

            block_idx += 1

        if in_nonzero:
            non_zero_ranges.append((range_start, block_idx))

    # Build zero ranges
    prev_end = 0
    for start, end in non_zero_ranges:
        if start > prev_end:
            zero_ranges.append((prev_end, start))
        prev_end = end
    if prev_end < total_blocks:
        zero_ranges.append((prev_end, total_blocks))

    # Write new.dat - only non-zero blocks
    with open(input_img, 'rb') as f_in:
        with open(new_dat, 'wb') as f_out:
            for start, end in non_zero_ranges:
                f_in.seek(start * BLOCK_SIZE)
                for i in range(end - start):
                    data = f_in.read(BLOCK_SIZE)
                    f_out.write(data)

    # Write transfer list
    with open(transfer_list, 'w') as f:
        f.write("%d\n" % version)
        f.write("%d\n" % total_blocks)

        if version >= 3:
            f.write("0\n")  # stash entries
            f.write("0\n")  # max stash blocks

        # Erase all first
        if zero_ranges:
            f.write("erase %s\n" % format_rangeset(zero_ranges))

        # New data blocks
        if non_zero_ranges:
            f.write("new %s\n" % format_rangeset(non_zero_ranges))

    dat_size = os.path.getsize(new_dat)
    print("INFO: Transfer list: %s" % transfer_list)
    print("INFO: Data file: %s (%.1f MB)" % (new_dat, dat_size / 1024.0 / 1024.0))
    print("INFO: Compression ratio: %.1f%%" % (100.0 * dat_size / img_size if img_size > 0 else 0))
    print("INFO: Done")


def format_rangeset(ranges):
    """Format ranges as Android rangeset: count,start1,end1,start2,end2,..."""
    parts = [str(len(ranges) * 2)]
    for start, end in ranges:
        parts.append(str(start))
        parts.append(str(end))
    return ','.join(parts)


if __name__ == '__main__':
    main()
