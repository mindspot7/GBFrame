#!/usr/bin/env python3
"""Plain launcher icon for Android 13+ (docs/APP_INTEGRATION_STANDARD.md): the adaptive foreground PNG trimmed to its
visible pixels, centered on a square transparent canvas with ~2% margin. Standard library only.

Usage: make-legacy-icon.py <ic_launcher_foreground.png> <drawable-nodpi/<name>_legacy.png>
"""
import os
import struct
import sys
import zlib


def read_rgba(path):
    data = open(path, 'rb').read()
    pos, idat = 8, b''
    while pos < len(data):
        length = struct.unpack('>I', data[pos:pos + 4])[0]
        kind, body = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + length]
        if kind == b'IHDR':
            width, height, depth, color = struct.unpack('>IIBB', body[:10])
            if depth != 8 or color != 6:
                sys.exit('expected an 8-bit RGBA PNG')
        elif kind == b'IDAT':
            idat += body
        pos += 12 + length
    raw, stride, rows, prev, p = zlib.decompress(idat), width * 4, [], bytearray(width * 4), 0
    for _ in range(height):
        kind = raw[p]
        line = bytearray(raw[p + 1:p + 1 + stride])
        p += 1 + stride
        for x in range(stride):
            a = line[x - 4] if x >= 4 else 0
            b = prev[x]
            c = prev[x - 4] if x >= 4 else 0
            if kind == 1:
                line[x] = (line[x] + a) & 255
            elif kind == 2:
                line[x] = (line[x] + b) & 255
            elif kind == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif kind == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line)
        prev = line
    return width, height, rows


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    width, height, rows = read_rgba(sys.argv[1])
    xs = [x for x in range(width) if any(rows[y][x * 4 + 3] for y in range(height))]
    ys = [y for y in range(height) if any(rows[y][x * 4 + 3] for x in range(width))]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    side = int((max(x1 - x0, y1 - y0) + 1) * 1.04)
    ox, oy = (x0 + x1) // 2 - side // 2, (y0 + y1) // 2 - side // 2
    out = b''
    for y in range(oy, oy + side):
        line = bytearray(b'\x00')
        for x in range(ox, ox + side):
            line += rows[y][x * 4:x * 4 + 4] if 0 <= x < width and 0 <= y < height else b'\x00\x00\x00\x00'
        out += bytes(line)

    def chunk(kind, body):
        return struct.pack('>I', len(body)) + kind + body + struct.pack('>I', zlib.crc32(kind + body))

    os.makedirs(os.path.dirname(os.path.abspath(sys.argv[2])), exist_ok=True)
    with open(sys.argv[2], 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', side, side, 8, 6, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(out, 9)) + chunk(b'IEND', b''))
    print(f'{sys.argv[2]}: {side}x{side}')


if __name__ == '__main__':
    main()
