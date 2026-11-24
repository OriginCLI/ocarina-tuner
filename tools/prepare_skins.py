#!/usr/bin/env python3
"""Turn hole-free ocarina PNGs into app skins.

For every PNG in a source folder this script:
  1. cleans near-transparent fringe pixels and crops to the body,
  2. scales to 1200 px wide and saves a WebP with alpha into the app's assets,
  3. places the 12 holes by following the top edge of that skin's silhouette, so
     slightly different outlines still get sensible hole positions,
  4. writes/updates skins.json with the per-skin hole coordinates (0..1 of the image).

The coordinates in skins.json are plain data: hand-edit any of them afterwards if a hole
sits a little off on a particular skin. Re-running the script regenerates them.

Usage:
  python3 tools/prepare_skins.py SRC_DIR ASSET_DIR [--preview OUT.png]
SRC_DIR holds <skin_id>.png files; ASSET_DIR is e.g. app/src/main/assets/skins.
"""
import argparse
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

TARGET_WIDTH = 1200

# Hole layout for a 12-hole ocarina, top-down, wide end (left hand) on the left,
# mouthpiece at the lower left.
#   u: position along the body, 0 = left end, 1 = tip
#   d: distance below the top edge of the silhouette at that u, in units of image height
#   r: radius in units of image height
# Thumb holes are on the back and are drawn by the app beside the body, not here.
LAYOUT = {
    "L_PINKY":  dict(u=0.095, d=0.45, r=0.030),
    "L_RING":   dict(u=0.185, d=0.25, r=0.050),
    "L_MIDDLE": dict(u=0.295, d=0.22, r=0.054),
    "L_INDEX":  dict(u=0.405, d=0.21, r=0.050),
    "L_SUB":    dict(u=0.300, d=0.42, r=0.024),
    "R_INDEX":  dict(u=0.520, d=0.20, r=0.048),
    "R_MIDDLE": dict(u=0.630, d=0.185, r=0.044),
    "R_RING":   dict(u=0.735, d=0.165, r=0.038),
    "R_PINKY":  dict(u=0.835, d=0.145, r=0.030),
    "R_SUB":    dict(u=0.635, d=0.355, r=0.022),
}


def title_from_id(skin_id: str) -> str:
    return " ".join(w.capitalize() for w in skin_id.split("_"))


def process(path: str):
    im = Image.open(path).convert("RGBA")
    a = np.array(im)
    alpha = a[..., 3]
    alpha[alpha < 8] = 0
    a[..., 3] = alpha
    im = Image.fromarray(a)
    bbox = Image.fromarray(alpha).point(lambda v: 255 if v > 32 else 0).getbbox()
    im = im.crop(bbox)
    w, h = im.size
    scale = TARGET_WIDTH / w
    im = im.resize((TARGET_WIDTH, max(1, round(h * scale))), Image.LANCZOS)
    return im


def place_holes(im: Image.Image):
    alpha = np.array(im)[..., 3] > 128
    H, W = alpha.shape
    tops = np.full(W, H, dtype=float)
    for x in range(W):
        ys = np.nonzero(alpha[:, x])[0]
        if len(ys):
            tops[x] = ys[0]
    holes = {}
    problems = []
    for hid, p in LAYOUT.items():
        x = p["u"] * W
        xi = int(round(x))
        # Average the top edge over a small span to ignore glaze highlights at the rim.
        span = tops[max(0, xi - 6): xi + 7]
        top = float(np.median(span))
        y = top + p["d"] * H
        r = p["r"] * H
        # Check the hole (plus a small margin) sits fully on the body.
        margin = r * 1.25
        inside = True
        for ang in np.linspace(0, 2 * np.pi, 24, endpoint=False):
            px = int(round(x + margin * np.cos(ang)))
            py = int(round(y + margin * np.sin(ang)))
            if not (0 <= px < W and 0 <= py < H and alpha[py, px]):
                inside = False
                break
        if not inside:
            problems.append(hid)
        holes[hid] = dict(x=round(x / W, 4), y=round(y / H, 4), r=round(r / H, 4))
    return holes, problems


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("dst")
    ap.add_argument("--preview")
    ap.add_argument("--group", default="original",
                    help="label stored with each skin, e.g. 'fan' for fan-art skins")
    args = ap.parse_args()

    os.makedirs(args.dst, exist_ok=True)
    manifest_path = os.path.join(args.dst, "skins.json")
    manifest = {"skins": []}
    if os.path.exists(manifest_path):
        with open(manifest_path) as f:
            manifest = json.load(f)
    by_id = {s["id"]: s for s in manifest["skins"]}

    previews = []
    for name in sorted(os.listdir(args.src)):
        if not name.lower().endswith(".png"):
            continue
        skin_id = os.path.splitext(name)[0]
        im = process(os.path.join(args.src, name))
        out_name = f"{skin_id}.webp"
        im.save(os.path.join(args.dst, out_name), "WEBP", quality=90, method=6)
        holes, problems = place_holes(im)
        entry = by_id.get(skin_id, {"id": skin_id, "name": title_from_id(skin_id)})
        entry.update(image=out_name, aspect=round(im.size[0] / im.size[1], 4),
                     group=args.group, holes=holes)
        by_id[skin_id] = entry
        status = "ok" if not problems else "CHECK " + ",".join(problems)
        print(f"{skin_id:24s} {im.size[0]}x{im.size[1]}  {status}")
        previews.append((skin_id, im, holes))

    manifest["skins"] = sorted(by_id.values(), key=lambda s: s.get("order", 999))
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")

    if args.preview and previews:
        cols = 2
        tw = 600
        rows = (len(previews) + cols - 1) // cols
        th = max(int(tw / (im.size[0] / im.size[1])) for _, im, _ in previews) + 10
        sheet = Image.new("RGB", (tw * cols, th * rows), (24, 22, 32))
        d = ImageDraw.Draw(sheet)
        for i, (sid, im, holes) in enumerate(previews):
            t = im.resize((tw, int(tw * im.size[1] / im.size[0])))
            ox, oy = (i % cols) * tw, (i // cols) * th
            sheet.paste(t, (ox, oy), t)
            for hid, h in holes.items():
                cx, cy, r = ox + h["x"] * t.size[0], oy + h["y"] * t.size[1], h["r"] * t.size[1]
                d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(40, 30, 60), outline=(255, 255, 255))
            d.text((ox + 6, oy + 4), sid, fill=(255, 255, 0))
        sheet.save(args.preview)


if __name__ == "__main__":
    sys.exit(main())
