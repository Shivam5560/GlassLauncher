#!/usr/bin/env python3
"""Renders the Glass wallpapers: 12 original abstract designs, each as a light and a dark version.

Everything is procedural (numpy + Pillow) and seeded, so re-running gives the same pixels:
mesh gradients, flowing ribbons and soft caustics, with enough structure that Liquid Glass
refraction is visible behind panels.

Output (committed):
  app/src/main/assets/wallpapers/<id>_<light|dark>.webp        1440x3200, full size (set as wallpaper)
  app/src/main/assets/wallpapers/<id>_<light|dark>_thumb.webp  270x600, picker previews
  app/src/main/assets/wallpapers/index.json                    ids, names, categories
  glassicons/src/main/assets/wallpapers/<id>_<light|dark>.webp 540x1200, Glass Icons preview backdrops

Usage: python3 tools/wallpapers/generate_wallpapers.py [--scale 0.25]   (scale is for quick previews)
Requires: numpy, Pillow (pip install numpy pillow)
"""
import json
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
APP_OUT = os.path.join(ROOT, "app", "src", "main", "assets", "wallpapers")
ICONS_OUT = os.path.join(ROOT, "glassicons", "src", "main", "assets", "wallpapers")
W, H = 1440, 3200


def hexrgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


def smooth_noise(rng, w, h, cells_x, cells_y):
    """Smooth value noise in [0, 1]: random grid upsampled with bicubic filtering."""
    g = rng.random((cells_y + 3, cells_x + 3)).astype(np.float32)
    img = Image.fromarray((g * 255).astype(np.uint8), "L").resize((w + w // cells_x * 3, h + h // cells_y * 3), Image.BICUBIC)
    a = np.asarray(img, dtype=np.float32)[:h, :w] / 255.0
    return a


def fbm(rng, w, h, base=3, octaves=4):
    out = np.zeros((h, w), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        c = base * (2 ** o)
        out += amp * smooth_noise(rng, w, h, c, max(1, int(c * h / w)))
        total += amp
        amp *= 0.5
    return out / total


class Canvas:
    def __init__(self, w, h, seed):
        self.w, self.h = w, h
        self.rng = np.random.default_rng(seed)
        ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
        self.x = xs / w            # 0..1
        self.y = ys / w            # 0..H/W (square units)
        self.aspect = h / w

    def warp(self, strength=0.12, base=2):
        nx = fbm(self.rng, self.w, self.h, base) - 0.5
        ny = fbm(self.rng, self.w, self.h, base) - 0.5
        return self.x + nx * strength * 2, self.y + ny * strength * 2


def mesh(c, points, warp=0.15, sharp=3.0):
    """Mesh gradient: colours at control points blended with inverse-distance weights."""
    x, y = c.warp(warp)
    acc = np.zeros((c.h, c.w, 3), np.float32)
    wsum = np.zeros((c.h, c.w), np.float32)
    for (px, py, col) in points:
        d2 = (x - px) ** 2 + (y - py * c.aspect) ** 2
        wt = 1.0 / (d2 ** (sharp / 2) + 1e-4)
        acc += wt[..., None] * hexrgb(col)
        wsum += wt
    return acc / wsum[..., None]


def ribbons(c, img, color, count=3, amp=0.18, freq=1.6, width=0.07, phase=0.0, glow=0.5, tilt=0.0, center=0.5):
    """Flowing glossy ribbons: bands around sine curves with a soft highlight along the crest."""
    x, y = c.warp(0.05, 2)
    out = img.copy()
    col = hexrgb(color)
    width *= 2.6
    freq *= 0.6
    for i in range(count):
        off = (i - (count - 1) / 2) * width * 1.15
        cy = (center * c.aspect + off + tilt * (x - 0.5)
              + amp * np.sin(2 * np.pi * (freq * x + phase + i * 0.17))
              + amp * 0.18 * np.sin(2 * np.pi * (freq * 1.9 * x + phase * 1.7 + i * 0.31)))
        d = (y - cy) / width
        band = np.exp(-d ** 4 * 1.6)
        # light along the upper side of the ribbon, shadow below: gives it a 3D glassy fold
        shade = 0.5 + 0.5 * np.tanh(-d * 1.8)
        depth = 0.45 * (1 - 0.65 * float(col.mean()))
        tone = col * (1 - depth + depth * shade[..., None]) + (1 - col) * (np.exp(-((d + 0.45) * 3.2) ** 2) * glow)[..., None]
        out = out * (1 - band[..., None] * 0.85) + tone * band[..., None] * 0.85
    return out


def caustics(c, img, strength=0.35, scale=7.0, tint="#FFFFFF"):
    """Soft caustic network: bright thin lines where warped cosine waves meet."""
    x, y = c.warp(0.08, 3)
    v = np.zeros_like(x)
    for k, (a, b) in enumerate([(1.0, 0.6), (-0.7, 1.0), (0.4, -1.1)]):
        v += np.cos(2 * np.pi * scale * (a * x + b * y) + k * 1.9 + 3 * fbm(c.rng, c.w, c.h, 2, 2))
    lines = np.exp(-np.abs(v) * 3.5) ** 1.4
    mask = fbm(c.rng, c.w, c.h, 2, 2)
    lines *= np.clip((mask - 0.3) * 2.2, 0, 1)
    return img + (hexrgb(tint) - img) * (lines * strength)[..., None]


def orbs(c, img, items):
    """Large soft light blooms."""
    out = img.copy()
    for (px, py, r, col, a) in items:
        d2 = ((c.x - px) ** 2 + (c.y - py * c.aspect) ** 2) / (r * r)
        out = out + (hexrgb(col) - out) * (np.exp(-d2) * a)[..., None]
    return out


def vignette(c, img, amount):
    d = np.sqrt((c.x - 0.5) ** 2 + ((c.y / c.aspect) - 0.5) ** 2 * 0.6)
    return img * (1 - amount * np.clip(d * 1.4, 0, 1) ** 2)[..., None]


def grain(c, img, amount=0.012):
    return img + (c.rng.random((c.h, c.w, 1)).astype(np.float32) - 0.5) * amount


# Each design returns a float RGB image for a given canvas and mode ("light" / "dark").
def lagoon(c, m):
    pts = [(0.1, 0.05, "#9FE7FF"), (0.9, 0.2, "#3DA9FC"), (0.2, 0.55, "#5EE0C8"), (0.85, 0.8, "#1D6FE0"), (0.4, 0.98, "#BFF3FF")] \
        if m == "light" else [(0.1, 0.05, "#073B5A"), (0.9, 0.2, "#0B5FA8"), (0.2, 0.55, "#06786A"), (0.85, 0.8, "#041A3D"), (0.4, 0.98, "#0A4F73")]
    img = mesh(c, pts)
    return caustics(c, img, 0.45 if m == "light" else 0.3, 6, "#FFFFFF" if m == "light" else "#7FE8FF")


def aurora(c, m):
    base = mesh(c, [(0.2, 0.1, "#E8F0FF"), (0.8, 0.4, "#D9E6FF"), (0.3, 0.9, "#F2E8FF")]) if m == "light" else \
        mesh(c, [(0.2, 0.1, "#050B1E"), (0.8, 0.4, "#0A1433"), (0.3, 0.9, "#120826")])
    img = ribbons(c, base, "#36D6A4" if m == "light" else "#20C997", 3, 0.12, 1.2, 0.06, 0.1, 0.6, -0.3, 0.38)
    return ribbons(c, img, "#7B6CFF" if m == "light" else "#6A5CFF", 2, 0.1, 1.5, 0.05, 0.55, 0.5, 0.2, 0.62)


def ember(c, m):
    pts = [(0.1, 0.1, "#FFD8A8"), (0.9, 0.3, "#FF8A5B"), (0.2, 0.7, "#FF5E7E"), (0.8, 0.95, "#FFC46B")] if m == "light" else \
        [(0.1, 0.1, "#2A0A06"), (0.9, 0.3, "#6E1A0E"), (0.2, 0.7, "#5A0F2A"), (0.8, 0.95, "#3A1606")]
    img = mesh(c, pts, 0.2)
    return ribbons(c, img, "#FFF1E0" if m == "light" else "#FF7A3D", 2, 0.16, 1.1, 0.08, 0.3, 0.7, 0.4, 0.55)


def orchid(c, m):
    pts = [(0.15, 0.1, "#F6D6FF"), (0.85, 0.35, "#C08BFF"), (0.25, 0.65, "#FF9AD5"), (0.75, 0.95, "#E3C4FF")] if m == "light" else \
        [(0.15, 0.1, "#1E0A2E"), (0.85, 0.35, "#3E1470"), (0.25, 0.65, "#5C0F45"), (0.75, 0.95, "#170824")]
    img = mesh(c, pts, 0.22, 2.5)
    return orbs(c, img, [(0.7, 0.25, 0.35, "#FFFFFF" if m == "light" else "#B57CFF", 0.35),
                         (0.25, 0.75, 0.4, "#FFE6F6" if m == "light" else "#FF4FB0", 0.3)])


def tide(c, m):
    base = mesh(c, [(0.5, 0.0, "#EAF6FF"), (0.5, 1.0, "#BFE0FF")]) if m == "light" else mesh(c, [(0.5, 0.0, "#020812"), (0.5, 1.0, "#06213F")])
    img = ribbons(c, base, "#2E8BFF" if m == "light" else "#1F6FEB", 4, 0.09, 1.8, 0.045, 0.0, 0.6, 0.0, 0.5)
    return ribbons(c, img, "#00C2D1" if m == "light" else "#00A5B5", 3, 0.07, 2.2, 0.035, 0.4, 0.6, 0.1, 0.72)


def dune(c, m):
    pts = [(0.2, 0.1, "#FFF3DC"), (0.8, 0.45, "#F2C994"), (0.3, 0.8, "#E8A877"), (0.7, 1.0, "#FFE2BF")] if m == "light" else \
        [(0.2, 0.1, "#1D140C"), (0.8, 0.45, "#4A3020"), (0.3, 0.8, "#5E3423"), (0.7, 1.0, "#24170D")]
    img = mesh(c, pts, 0.1)
    for i, cy in enumerate([0.35, 0.55, 0.75]):
        img = ribbons(c, img, "#FFFFFF" if m == "light" else "#C98A5A", 1, 0.06, 0.9, 0.11, i * 0.2, 0.25, 0.25, cy)
    return img


def glacier(c, m):
    pts = [(0.2, 0.1, "#FFFFFF"), (0.8, 0.3, "#CFE9FF"), (0.25, 0.7, "#A8D8F0"), (0.8, 0.95, "#E6F4FF")] if m == "light" else \
        [(0.2, 0.1, "#0E1A24"), (0.8, 0.3, "#16334A"), (0.25, 0.7, "#0B2A3C"), (0.8, 0.95, "#08121B")]
    img = mesh(c, pts, 0.12)
    return caustics(c, img, 0.55 if m == "light" else 0.35, 4.5, "#FFFFFF" if m == "light" else "#9ED8FF")


def nebula(c, m):
    base = mesh(c, [(0.3, 0.2, "#E9E4FF"), (0.7, 0.8, "#FFE4F1")]) if m == "light" else mesh(c, [(0.3, 0.2, "#07051A"), (0.7, 0.8, "#14051A")])
    img = orbs(c, base, [(0.3, 0.3, 0.32, "#8B7CFF", 0.55), (0.72, 0.5, 0.3, "#FF6FB5", 0.5),
                         (0.4, 0.78, 0.34, "#3EC7FF", 0.45)])
    return caustics(c, img, 0.18, 9, "#FFFFFF")


def citrus(c, m):
    pts = [(0.1, 0.1, "#FFF6B8"), (0.9, 0.3, "#FFD23F"), (0.2, 0.7, "#B8F06A"), (0.8, 0.95, "#FF9F3D")] if m == "light" else \
        [(0.1, 0.1, "#1F1A05"), (0.9, 0.3, "#4A3A06"), (0.2, 0.7, "#1F3A0A"), (0.8, 0.95, "#3A1F05")]
    img = mesh(c, pts, 0.18)
    return ribbons(c, img, "#FFFFFF" if m == "light" else "#E6C229", 2, 0.14, 1.3, 0.07, 0.65, 0.6, -0.5, 0.5)


def meadow(c, m):
    pts = [(0.15, 0.1, "#E8FFE0"), (0.85, 0.35, "#8EE3A0"), (0.2, 0.7, "#5CC9A8"), (0.8, 0.95, "#C6F2B0")] if m == "light" else \
        [(0.15, 0.1, "#05140A"), (0.85, 0.35, "#0D3A1E"), (0.2, 0.7, "#073528"), (0.8, 0.95, "#0A1F0E")]
    img = mesh(c, pts, 0.2)
    return caustics(c, img, 0.3 if m == "light" else 0.25, 5.5, "#FFFFFF" if m == "light" else "#7CF0A8")


def prism(c, m):
    base = mesh(c, [(0.5, 0.0, "#F7F7FA"), (0.5, 1.0, "#E6E8EF")]) if m == "light" else mesh(c, [(0.5, 0.0, "#08080B"), (0.5, 1.0, "#111217")])
    img = base
    for i, col in enumerate(["#FF5E5E", "#FFB23E", "#3EDC81", "#3E9BFF", "#9B6BFF"]):
        img = ribbons(c, img, col, 1, 0.1, 1.0, 0.045, 0.1 + i * 0.04, 0.55, 0.9, 0.3 + i * 0.09)
    return img


def dusk(c, m):
    pts = [(0.5, 0.0, "#FFD3B0"), (0.2, 0.4, "#FF9FB2"), (0.8, 0.6, "#A98BFF"), (0.5, 1.0, "#5E7BFF")] if m == "light" else \
        [(0.5, 0.0, "#2A1020"), (0.2, 0.4, "#3A0F2E"), (0.8, 0.6, "#1E1450"), (0.5, 1.0, "#060A26")]
    img = mesh(c, pts, 0.1, 2.0)
    return orbs(c, img, [(0.62, 0.42, 0.22, "#FFF0D6" if m == "light" else "#FF8A5B", 0.55)])


DESIGNS = [
    ("lagoon", "Lagoon", "Caustics", lagoon),
    ("aurora", "Aurora", "Ribbons", aurora),
    ("ember", "Ember", "Ribbons", ember),
    ("orchid", "Orchid", "Mesh", orchid),
    ("tide", "Tide", "Ribbons", tide),
    ("dune", "Dune", "Ribbons", dune),
    ("glacier", "Glacier", "Caustics", glacier),
    ("nebula", "Nebula", "Mesh", nebula),
    ("citrus", "Citrus", "Mesh", citrus),
    ("meadow", "Meadow", "Caustics", meadow),
    ("prism", "Prism", "Ribbons", prism),
    ("dusk", "Dusk", "Mesh", dusk),
]


def render(fn, seed, mode, w, h):
    c = Canvas(w, h, seed)
    img = fn(c, mode)
    img = vignette(c, img, 0.12 if mode == "light" else 0.25)
    img = grain(c, img)
    return Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB")


def main():
    scale = 1.0
    if "--scale" in sys.argv:
        scale = float(sys.argv[sys.argv.index("--scale") + 1])
    w, h = int(W * scale), int(H * scale)
    os.makedirs(APP_OUT, exist_ok=True)
    os.makedirs(ICONS_OUT, exist_ok=True)
    index = []
    total = 0
    for i, (wid, name, cat, fn) in enumerate(DESIGNS):
        for mode in ("light", "dark"):
            img = render(fn, 1000 + i * 7 + (0 if mode == "light" else 3), mode, w, h)
            path = os.path.join(APP_OUT, f"{wid}_{mode}.webp")
            img.save(path, "WEBP", quality=82, method=6)
            img.resize((270, 600), Image.LANCZOS).save(os.path.join(APP_OUT, f"{wid}_{mode}_thumb.webp"), "WEBP", quality=80, method=6)
            img.resize((540, 1200), Image.LANCZOS).save(os.path.join(ICONS_OUT, f"{wid}_{mode}.webp"), "WEBP", quality=78, method=6)
            total += os.path.getsize(path)
            print(f"{wid}_{mode}: {os.path.getsize(path) // 1024} KB", flush=True)
        index.append({"id": wid, "name": name, "category": cat})
    with open(os.path.join(APP_OUT, "index.json"), "w") as f:
        json.dump({"version": 1, "width": w, "height": h, "wallpapers": index}, f, indent=2)
    print(f"total full-size: {total // 1024} KB")


if __name__ == "__main__":
    main()
