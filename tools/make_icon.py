#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 irvan-putra
"""Draws the launcher icons with the version on the badge. Usage: make_icon.py v0.2.1
Needs Pillow. Run from the repo root; keep the version equal to versionName in app/build.gradle."""
import os, sys
from PIL import Image, ImageDraw, ImageFont

V = sys.argv[1]
OUT = "android/app/src/main/res"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def icon(sz):
    im = Image.new("RGBA", (sz, sz), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, sz - 1, sz - 1], radius=sz // 5, fill=(38, 50, 56, 255))
    c, g, p = (255, 102, 0, 255), (42, 157, 143, 255), (123, 97, 255, 255)
    w = max(3, sz // 24); x0 = sz * 0.3
    d.line([(x0, sz * 0.18), (x0, sz * 0.62)], fill=c, width=w)
    d.line([(x0, sz * 0.36), (sz * 0.62, sz * 0.36)], fill=g, width=w)
    d.line([(x0, sz * 0.5), (sz * 0.74, sz * 0.5)], fill=p, width=w)
    r = sz * 0.055
    for x, y, col in [(x0, sz * 0.18, c), (sz * 0.62, sz * 0.36, g), (sz * 0.74, sz * 0.5, p), (x0, sz * 0.62, c)]:
        d.ellipse([x - r, y - r, x + r, y + r], fill=col)
    f = ImageFont.truetype(FONT, int(sz * 0.17))
    bw, bh = sz * 0.84, sz * 0.24; bx, by = (sz - bw) / 2, sz * 0.7
    d.rounded_rectangle([bx, by, bx + bw, by + bh], radius=bh / 3, fill=(255, 255, 255, 255))
    d.text(((sz - d.textlength(V, font=f)) / 2, by + bh * 0.12), V, fill=(20, 20, 20, 255), font=f)
    return im


for name, sz in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    p = f"{OUT}/mipmap-{name}"; os.makedirs(p, exist_ok=True); icon(sz).save(p + "/ic_launcher.png")
