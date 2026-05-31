#!/usr/bin/env python3
# sdat2img - Convert Android sparse data image to raw ext4 image
# Usage: sdat2img.py <transfer_list> <system_new_dat> <system_img>

import sys
import os

BLOCK_SIZE = 4096

def main():
    if len(sys.argv) < 4:
        print("Usage: sdat2img.py <transfer_list> <new_dat_file> <output_img>")
        sys.exit(1)

    transfer_list = sys.argv[1]
    new_dat_file = sys.argv[2]
    output_img = sys.argv[3]

    if not os.path.isfile(transfer_list):
        print("ERROR: Transfer list not found: " + transfer_list)
        sys.exit(1)
    if not os.path.isfile(new_dat_file):
        print("ERROR: Data file not found: " + new_dat_file)
        sys.exit(1)

    with open(transfer_list, 'r') as f:
        lines = f.readlines()

    version = int(lines[0].strip())
    print("INFO: Transfer list version: %d" % version)

    # Version 2+ has total blocks on line 2
    if version >= 2:
        total_blocks = int(lines[1].strip())
        print("INFO: Total blocks: %d (%.1f MB)" % (total_blocks, total_blocks * BLOCK_SIZE / 1024.0 / 1024.0))

    # Skip header lines based on version
    if version == 1:
        line_start = 1
    elif version == 2:
        line_start = 2
    elif version >= 3:
        # v3/v4: line 3 = stash entries needed, line 4 = max stash blocks
        line_start = 4
    else:
        line_start = 1

    commands = []
    for line in lines[line_start:]:
        line = line.strip()
        if not line:
            continue
        parts = line.split(' ')
        if len(parts) >= 2:
            commands.append((parts[0], parts[1]))
        elif len(parts) == 1:
            commands.append((parts[0], ''))

    with open(new_dat_file, 'rb') as dat_f:
        with open(output_img, 'wb') as out_f:
            for cmd, rangeset in commands:
                if cmd == 'new':
                    ranges = parse_rangeset(rangeset)
                    for begin, end in ranges:
                        block_count = end - begin
                        data = dat_f.read(block_count * BLOCK_SIZE)
                        if len(data) < block_count * BLOCK_SIZE:
                            data += b'\x00' * (block_count * BLOCK_SIZE - len(data))
                        out_f.seek(begin * BLOCK_SIZE)
                        out_f.write(data)
                        print("INFO: Writing %d blocks at offset %d" % (block_count, begin))

                elif cmd == 'zero':
                    ranges = parse_rangeset(rangeset)
                    for begin, end in ranges:
                        block_count = end - begin
                        out_f.seek(begin * BLOCK_SIZE)
                        out_f.write(b'\x00' * (block_count * BLOCK_SIZE))

                elif cmd == 'erase':
                    pass  # No action needed for erase

    output_size = os.path.getsize(output_img)
    print("INFO: Output image: %s (%.1f MB)" % (output_img, output_size / 1024.0 / 1024.0))
    print("INFO: Done")


def parse_rangeset(rangeset_str):
    """Parse Android rangeset format: count,start1,end1,start2,end2,..."""
    parts = rangeset_str.split(',')
    if len(parts) < 3:
        return []

    count = int(parts[0])
    ranges = []
    for i in range(1, len(parts), 2):
        if i + 1 < len(parts):
            begin = int(parts[i])
            end = int(parts[i + 1])
            ranges.append((begin, end))
    return ranges


if __name__ == '__main__':
    main()
