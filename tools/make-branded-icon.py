#!/usr/bin/env python3
"""Compose the shared GameBeacon badge over a consumer app's RGBA launcher art.

Usage:
  python tools/make-branded-icon.py <app-icon.png> <brand-mark.png> <output.png>

The consumer icon is kept intact except where the badge overlaps it. PNG handling
uses the Python standard library and intentionally accepts Android's common
8-bit, non-interlaced RGBA PNG resources.
"""
import math
import os
import struct
import sys
import zlib


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
NAVY = (5, 14, 48)
BADGE_SIZE = 512
BADGE_ICON_SCALE = 0.84
BADGE_ART_SCALE = 0.24


def read_rgba(path):
    data = open(path, "rb").read()
    if not data.startswith(PNG_SIGNATURE):
        raise ValueError(f"{path}: not a PNG")
    pos = len(PNG_SIGNATURE)
    idat = bytearray()
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", body)
            if (depth, color, compression, filtering, interlace) != (8, 6, 0, 0, 0):
                raise ValueError(f"{path}: expected a non-interlaced 8-bit RGBA PNG")
        elif kind == b"IDAT":
            idat.extend(body)
        elif kind == b"IEND":
            break
        pos += length + 12

    stride = width * 4
    raw = zlib.decompress(idat)
    if len(raw) != height * (stride + 1):
        raise ValueError(f"{path}: invalid RGBA scanline data")
    pixels = bytearray(width * height * 4)
    prev = bytearray(stride)
    cursor = 0
    for y in range(height):
        filter_type = raw[cursor]
        row = bytearray(raw[cursor + 1:cursor + 1 + stride])
        cursor += stride + 1
        for x in range(stride):
            left = row[x - 4] if x >= 4 else 0
            up = prev[x]
            upper_left = prev[x - 4] if x >= 4 else 0
            if filter_type == 1:
                row[x] = (row[x] + left) & 255
            elif filter_type == 2:
                row[x] = (row[x] + up) & 255
            elif filter_type == 3:
                row[x] = (row[x] + ((left + up) >> 1)) & 255
            elif filter_type == 4:
                p = left + up - upper_left
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - upper_left)
                predictor = left if pa <= pb and pa <= pc else up if pb <= pc else upper_left
                row[x] = (row[x] + predictor) & 255
            elif filter_type != 0:
                raise ValueError(f"{path}: unsupported PNG filter {filter_type}")
        start = y * stride
        pixels[start:start + stride] = row
        prev = row
    return width, height, pixels


def write_rgba(path, width, height, pixels):
    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body))

    stride = width * 4
    raw = b"".join(b"\x00" + pixels[y * stride:(y + 1) * stride] for y in range(height))
    payload = (PNG_SIGNATURE
               + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
               + chunk(b"IDAT", zlib.compress(raw, 9))
               + chunk(b"IEND", b""))
    parent = os.path.dirname(os.path.abspath(path))
    os.makedirs(parent, exist_ok=True)
    with open(path, "wb") as output:
        output.write(payload)


def alpha_bounds(image):
    width, height, pixels = image
    min_x, min_y, max_x, max_y = width, height, -1, -1
    for y in range(height):
        row = y * width * 4
        for x in range(width):
            if pixels[row + x * 4 + 3]:
                min_x, min_y = min(min_x, x), min(min_y, y)
                max_x, max_y = max(max_x, x), max(max_y, y)
    if max_x < min_x:
        raise ValueError("icon/brand image has no visible pixels")
    return min_x, min_y, max_x + 1, max_y + 1


def sample(image, x, y, bounds=None):
    width, height, pixels = image
    x0, y0, x1, y1 = bounds or (0, 0, width, height)
    x = min(max(x, x0), x1 - 1)
    y = min(max(y, y0), y1 - 1)
    xlo, ylo = int(math.floor(x)), int(math.floor(y))
    xhi, yhi = min(xlo + 1, x1 - 1), min(ylo + 1, y1 - 1)
    fx, fy = x - xlo, y - ylo
    points = ((xlo, ylo, (1 - fx) * (1 - fy)),
              (xhi, ylo, fx * (1 - fy)),
              (xlo, yhi, (1 - fx) * fy),
              (xhi, yhi, fx * fy))
    alpha = 0.0
    premul = [0.0, 0.0, 0.0]
    for px, py, weight in points:
        offset = (py * width + px) * 4
        a = pixels[offset + 3] / 255.0
        alpha += a * weight
        for channel in range(3):
            premul[channel] += pixels[offset + channel] * a * weight
    if alpha <= 0.0:
        return 0, 0, 0, 0
    return tuple(round(c / alpha) for c in premul) + (round(alpha * 255),)


def blend_pixel(destination, offset, color, alpha):
    if alpha <= 0:
        return
    dest_alpha = destination[offset + 3] / 255.0
    src_alpha = alpha / 255.0
    out_alpha = src_alpha + dest_alpha * (1.0 - src_alpha)
    for channel in range(3):
        src = color[channel] * src_alpha
        dst = destination[offset + channel] * dest_alpha * (1.0 - src_alpha)
        destination[offset + channel] = round((src + dst) / out_alpha)
    destination[offset + 3] = round(out_alpha * 255)


def make_badge(mark):
    width, height, _ = mark
    bounds = alpha_bounds(mark)
    x0, y0, x1, y1 = bounds
    content_scale = BADGE_SIZE * BADGE_ICON_SCALE / max(x1 - x0, y1 - y0)
    content_w, content_h = (x1 - x0) * content_scale, (y1 - y0) * content_scale
    content_left = (BADGE_SIZE - content_w) / 2
    content_top = (BADGE_SIZE - content_h) / 2
    pixels = bytearray(BADGE_SIZE * BADGE_SIZE * 4)
    center = (BADGE_SIZE - 1) / 2
    radius = BADGE_SIZE / 2
    for y in range(BADGE_SIZE):
        for x in range(BADGE_SIZE):
            edge = radius + 0.5 - math.hypot(x - center, y - center)
            circle_alpha = round(max(0.0, min(1.0, edge)) * 255)
            if not circle_alpha:
                continue
            offset = (y * BADGE_SIZE + x) * 4
            pixels[offset:offset + 4] = bytes((*NAVY, circle_alpha))
            if not (content_left <= x + 0.5 < content_left + content_w
                    and content_top <= y + 0.5 < content_top + content_h):
                continue
            sx = x0 + (x + 0.5 - content_left) / content_scale - 0.5
            sy = y0 + (y + 0.5 - content_top) / content_scale - 0.5
            red, green, blue, mark_alpha = sample(mark, sx, sy, bounds)
            if not mark_alpha:
                continue
            a = mark_alpha / 255.0
            pixels[offset] = round(red * a + NAVY[0] * (1 - a))
            pixels[offset + 1] = round(green * a + NAVY[1] * (1 - a))
            pixels[offset + 2] = round(blue * a + NAVY[2] * (1 - a))
    return BADGE_SIZE, BADGE_SIZE, pixels


def compose(icon, mark):
    width, height, base = icon
    bounds = alpha_bounds(icon)
    x0, y0, x1, y1 = bounds
    art_w, art_h = x1 - x0, y1 - y0
    if art_w < width * 0.75:
        # Adaptive foregrounds are padded for the launcher mask. Keep a small
        # badge centered below the controller, within the central safe zone.
        diameter = min(width, height) * 0.14
        center_x = width * 0.50
        center_y = height * 0.69
    else:
        # Standalone launcher PNGs place the same mark in the lower-middle,
        # just below the control's center area.
        diameter = min(width, height) * 0.22
        center_x = width * 0.50
        center_y = height * 0.80
    badge = make_badge(mark)
    radius = diameter / 2
    left, top = center_x - radius, center_y - radius
    min_x, max_x = max(0, math.floor(left)), min(width, math.ceil(left + diameter))
    min_y, max_y = max(0, math.floor(top)), min(height, math.ceil(top + diameter))
    for y in range(min_y, max_y):
        by = (y + 0.5 - top) * BADGE_SIZE / diameter - 0.5
        for x in range(min_x, max_x):
            bx = (x + 0.5 - left) * BADGE_SIZE / diameter - 0.5
            red, green, blue, alpha = sample(badge, bx, by)
            blend_pixel(base, (y * width + x) * 4, (red, green, blue), alpha)
    return width, height, base


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    icon_path, mark_path, output_path = sys.argv[1:]
    icon = read_rgba(icon_path)
    mark = read_rgba(mark_path)
    write_rgba(output_path, *compose(icon, mark))
    print(f"{output_path}: {icon[0]}x{icon[1]} with GameBeacon badge")


if __name__ == "__main__":
    main()
