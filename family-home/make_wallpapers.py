"""Draws the created wallpapers for the personal home screen, 1920x1080 JPEG, from code (no photos):

    tools/venv/bin/python family-home/make_wallpapers.py family-home/art

writes family-home/art/<set>/<n>-<name>.jpg for the sets places, aurora, ruska and glass. family-home/push-sets.sh sends them
to the TV next to each theme's own picture. Every scene keeps its lower third dark, where the dock and the text sit.
Seeds are fixed, so a run always draws the same scenes (the grain aside).
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1920, 1080


# ---- building blocks ---------------------------------------------------------------------------

def rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


def sky(stops):
    """A vertical gradient from (offset, colour) stops, as a float HxWx3 array."""
    ys = np.linspace(0, 1, H)
    out = np.zeros((H, 3), np.float32)
    offs = [o for o, _ in stops]
    cols = [rgb(c) for _, c in stops]
    for ch in range(3):
        out[:, ch] = np.interp(ys, offs, [c[ch] for c in cols])
    return np.repeat(out[:, None, :], W, axis=1)


def glow(img, cx, cy, r, color, strength=1.0, sx=1.0, sy=1.0):
    """Adds a soft round light, squashed by sx/sy."""
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    d = np.sqrt(((xx - cx) / (r * sx)) ** 2 + ((yy - cy) / (r * sy)) ** 2)
    a = np.clip(1 - d, 0, 1) ** 2 * strength
    img += a[..., None] * (rgb(color) - img * 0.0) * 1.0 * (a[..., None] > 0)
    np.clip(img, 0, 255, out=img)


def blend(img, layer, alpha):
    """Mixes an RGBA PIL layer into the float image."""
    arr = np.asarray(layer, np.float32)
    a = arr[..., 3:4] / 255.0 * alpha
    img[:] = img * (1 - a) + arr[..., :3] * a


def layer():
    return Image.new("RGBA", (W, H), (0, 0, 0, 0))


def ridge(seed, base, amp, waves=3.0, rough=0.25, step=6):
    """A skyline of hills: y for every step px, base +/- amp."""
    rnd = random.Random(seed)
    ph = [rnd.random() * 6.28 for _ in range(4)]
    pts = []
    for x in range(0, W + step, step):
        t = x / W
        y = base - amp * (0.55 * math.sin(t * waves * 3.1 + ph[0]) + 0.3 * math.sin(t * waves * 7.3 + ph[1])
                          + rough * math.sin(t * waves * 19 + ph[2]) * 0.4 + rough * (rnd.random() - 0.5) * 0.3)
        pts.append((x, y))
    return pts


def fill_below(pts, color, alpha=1.0, img=None, blur=0):
    l = layer()
    ImageDraw.Draw(l).polygon(pts + [(W, H), (0, H)], fill=color + (255,) if isinstance(color, tuple) else color)
    if blur:
        l = l.filter(ImageFilter.GaussianBlur(blur))
    blend(img, l, alpha)


def stars(img, seed, n, ymax, rmax=2.2):
    rnd = random.Random(seed)
    l = layer()
    d = ImageDraw.Draw(l)
    for _ in range(n):
        x, y = rnd.random() * W, rnd.random() * ymax
        r = rnd.random() ** 3 * rmax + 0.6
        a = int(90 + rnd.random() * 165)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, a))
    blend(img, l, 1.0)


def reflect(img, y_water, depth=1.0, tint="#0a1020", strength=0.55, wobble=6):
    """Mirrors what is above the waterline into the water below it, darker and rippled."""
    top = img[:y_water].copy()
    flipped = top[::-1]
    n = H - y_water
    flipped = flipped[:n] if flipped.shape[0] >= n else np.concatenate(
        [flipped, np.repeat(flipped[-1:], n - flipped.shape[0], 0)])
    shifted = np.empty_like(flipped)
    for i in range(n):
        s = int(math.sin(i * 0.35) * wobble * (i / n + 0.2))
        shifted[i] = np.roll(flipped[i], s, axis=0)
    fade = np.linspace(strength, 0.1, n)[:, None, None]
    water = shifted * fade + rgb(tint) * (1 - fade)
    img[y_water:] = water


def darken_bottom(img, start=0.62, end=1.0, amount=0.75):
    """The lower third dark, where the dock and the text sit."""
    ys = np.linspace(0, 1, H)
    k = np.clip((ys - start) / (end - start), 0, 1) * amount
    img *= (1 - k)[:, None, None]


def save(img, path):
    noise = np.random.default_rng(1).normal(0, 2.2, img.shape)
    out = np.clip(img + noise, 0, 255).astype(np.uint8)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(out).save(path, "JPEG", quality=88, optimize=True)
    print(path, os.path.getsize(path) // 1024, "KB")


def windows(d, x0, x1, y0, y1, seed, color=(255, 214, 140), density=0.18, size=3):
    rnd = random.Random(seed)
    for y in range(int(y0) + 6, int(y1) - 4, 11):
        for x in range(int(x0) + 4, int(x1) - 4, 9):
            if rnd.random() < density:
                d.rectangle([x, y, x + size, y + size + 1], fill=color + (int(150 + rnd.random() * 100),))


# ---- places ------------------------------------------------------------------------------------

def doha_corniche():
    img = sky([(0, "#1b1036"), (0.35, "#4a2251"), (0.55, "#c4566a"), (0.66, "#f2a35e"), (1, "#f2a35e")])
    glow(img, 1300, 700, 520, "#ffcf8a", 0.55, 1.4, 0.6)
    water_y = 760
    l = layer()
    d = ImageDraw.Draw(l)
    sil = (16, 12, 28, 255)
    rnd = random.Random(7)
    x = 120
    while x < 1850:
        w = rnd.randint(45, 95)
        h = rnd.randint(120, 330)
        if 820 < x < 900:      # Burj Doha: a tall cylinder with a rounded crown and a spike
            d.rectangle([x, water_y - 420, x + 70, water_y], fill=sil)
            d.ellipse([x, water_y - 455, x + 70, water_y - 385], fill=sil)
            d.line([x + 35, water_y - 455, x + 35, water_y - 530], fill=sil, width=5)
            windows(d, x, x + 70, water_y - 410, water_y, 11)
            x += 90
            continue
        if 1180 < x < 1260:    # Tornado Tower: an hourglass
            pts = [(x, water_y - 360), (x + 80, water_y - 360), (x + 58, water_y - 180), (x + 80, water_y),
                   (x, water_y), (x + 22, water_y - 180)]
            d.polygon(pts, fill=sil)
            windows(d, x + 18, x + 62, water_y - 350, water_y, 12, (255, 120, 190))
            x += 100
            continue
        d.rectangle([x, water_y - h, x + w, water_y], fill=sil)
        if rnd.random() < 0.4:
            d.polygon([(x, water_y - h), (x + w, water_y - h), (x + w / 2, water_y - h - 40)], fill=sil)
        windows(d, x, x + w, water_y - h, water_y, x)
        x += w + rnd.randint(6, 30)
    blend(img, l, 1.0)
    reflect(img, water_y, tint="#1a1030", strength=0.6)
    # A dhow on the water.
    l = layer()
    d = ImageDraw.Draw(l)
    bx, by = 420, 860
    d.polygon([(bx - 150, by - 20), (bx + 170, by - 32), (bx + 120, by + 18), (bx - 110, by + 18)], fill=(10, 6, 16, 255))
    d.line([bx, by - 22, bx + 10, by - 250], fill=(10, 6, 16, 255), width=6)
    d.polygon([(bx + 10, by - 250), (bx - 160, by - 40), (bx + 120, by - 60)], fill=(30, 18, 34, 235))
    blend(img, l, 1.0)
    darken_bottom(img)
    return img


def toronto_night():
    img = sky([(0, "#03060f"), (0.45, "#0b1838"), (0.68, "#1c2d5a"), (1, "#1c2d5a")])
    stars(img, 3, 260, 600)
    glow(img, 960, 760, 700, "#3a5a9a", 0.35, 1.6, 0.5)
    water_y = 790
    l = layer()
    d = ImageDraw.Draw(l)
    sil = (6, 9, 20, 255)
    rnd = random.Random(5)
    x = 60
    while x < 1880:
        w, h = rnd.randint(40, 90), rnd.randint(90, 300)
        d.rectangle([x, water_y - h, x + w, water_y], fill=sil)
        windows(d, x, x + w, water_y - h, water_y, x, (255, 230, 170), 0.22)
        x += w + rnd.randint(4, 22)
    # The CN Tower.
    cx, base = 1150, water_y
    d.polygon([(cx - 34, base), (cx + 34, base), (cx + 9, base - 560), (cx - 9, base - 560)], fill=sil)
    d.ellipse([cx - 52, base - 600, cx + 52, base - 545], fill=sil)
    d.rectangle([cx - 10, base - 700, cx + 10, base - 590], fill=sil)
    d.ellipse([cx - 18, base - 715, cx + 18, base - 690], fill=sil)
    d.line([cx, base - 715, cx, base - 860], fill=sil, width=6)
    for k in range(8):
        d.rectangle([cx - 44 + k * 11, base - 578, cx - 40 + k * 11, base - 570], fill=(150, 200, 255, 230))
    d.ellipse([cx - 5, base - 868, cx + 5, base - 858], fill=(255, 60, 60, 255))
    # Rogers Centre dome.
    d.chord([cx + 120, base - 110, cx + 380, base + 90], 180, 360, fill=sil)
    blend(img, l, 1.0)
    glow(img, cx, base - 572, 60, "#8fc8ff", 0.5)
    reflect(img, water_y, tint="#050a18", strength=0.65, wobble=5)
    darken_bottom(img, 0.66)
    return img


def palm(d, x, y, h, lean, color):
    """A coconut palm: a gently curved trunk and drooping, leaf-shaped fronds."""
    pts = [(x + lean * (t / 10) ** 1.6, y - h * t / 10) for t in range(11)]
    for i in range(10):
        d.line([pts[i], pts[i + 1]], fill=color, width=int(16 - i * 0.7))
    tx, ty = pts[-1]
    for ang, length in [(-170, 170), (-140, 190), (-110, 150), (-70, 150), (-40, 190), (-10, 170), (200, 120), (-20, 120)]:
        r = math.radians(ang)
        spine = []
        for k in range(13):
            t = k / 12
            spine.append((tx + math.cos(r) * length * t, ty + math.sin(r) * length * t + 90 * t * t))
        # Each frond is a thin leaf: widest in the middle, a point at the tip.
        left, right = [], []
        for k, (px, py) in enumerate(spine):
            t = k / 12
            wdt = 16 * math.sin(math.pi * min(1, t * 1.1))
            left.append((px, py - wdt))
            right.append((px, py + wdt * 0.4))
        d.polygon(left + right[::-1], fill=color)
    d.ellipse([tx - 14, ty - 8, tx + 14, ty + 16], fill=color)


def chennai_marina():
    img = sky([(0, "#2a3b6e"), (0.4, "#c98a8f"), (0.56, "#ffc98c"), (0.6, "#ffe2b0"), (1, "#ffe2b0")])
    sea_y = 650
    glow(img, 760, sea_y - 10, 380, "#fff1c8", 0.9, 1.3, 1.0)
    l = layer()
    d = ImageDraw.Draw(l)
    d.ellipse([700, sea_y - 70, 820, sea_y + 50], fill=(255, 236, 196, 255))
    blend(img, l, 1.0)
    reflect(img, sea_y, tint="#3a3e62", strength=0.7, wobble=9)
    # Sand and the beach.
    fill_below([(0, 860), (600, 830), (1300, 850), (1920, 820)], (40, 30, 34), img=img)
    l = layer()
    d = ImageDraw.Draw(l)
    sil = (24, 18, 26, 255)
    # The Chennai lighthouse: a tall tower with a lit top.
    lx = 1560
    d.polygon([(lx - 40, 860), (lx + 40, 860), (lx + 22, 360), (lx - 22, 360)], fill=sil)
    d.rectangle([lx - 34, 330, lx + 34, 362], fill=sil)
    d.rectangle([lx - 20, 290, lx + 20, 330], fill=(255, 230, 150, 255))
    d.polygon([(lx - 26, 290), (lx + 26, 290), (lx, 255)], fill=sil)
    palm(d, 220, 880, 420, 60, sil)
    palm(d, 380, 900, 330, -40, sil)
    palm(d, 1800, 880, 380, -70, sil)
    blend(img, l, 1.0)
    glow(img, lx, 310, 120, "#ffe9a8", 0.6)
    darken_bottom(img, 0.7)
    return img


def vellore_fort():
    img = sky([(0, "#14123a"), (0.4, "#5a2f63"), (0.6, "#d0725a"), (0.7, "#f0a060"), (1, "#f0a060")])
    glow(img, 500, 620, 420, "#ffc27a", 0.5, 1.4, 0.7)
    water_y = 800
    l = layer()
    d = ImageDraw.Draw(l)
    sil = (22, 12, 26, 255)
    # The fort wall with battlements and round bastions.
    d.rectangle([0, 660, W, water_y], fill=sil)
    for x in range(0, W, 46):
        d.rectangle([x, 636, x + 26, 662], fill=sil)
    for bx in (160, 820, 1500):
        d.rectangle([bx, 600, bx + 150, water_y], fill=sil)
        for x in range(bx, bx + 150, 30):
            d.rectangle([x, 578, x + 18, 602], fill=sil)
    # The temple gopuram rising behind the wall: stacked tiers, a vaulted top and finials.
    gx, gy = 1150, 660
    w = 300
    for t in range(7):
        h = 52
        d.polygon([(gx - w / 2, gy), (gx + w / 2, gy), (gx + w / 2 - 16, gy - h), (gx - w / 2 + 16, gy - h)], fill=sil)
        gy -= h
        w -= 34
    d.chord([gx - w / 2 - 10, gy - 60, gx + w / 2 + 10, gy + 30], 180, 360, fill=sil)
    for k in range(-2, 3):
        d.ellipse([gx + k * 22 - 6, gy - 82, gx + k * 22 + 6, gy - 58], fill=sil)
    blend(img, l, 1.0)
    reflect(img, water_y, tint="#1a1028", strength=0.6, wobble=4)
    darken_bottom(img, 0.7)
    return img


def nilgiri_tea():
    img = sky([(0, "#5d7f9c"), (0.4, "#b8c6c4"), (0.6, "#d6d2bc"), (1, "#d6d2bc")])
    glow(img, 1450, 300, 260, "#ffe7b0", 0.45)
    layers = [(560, 90, "#7f9a8c", 0.9, 10), (640, 110, "#5f8070", 1.0, 4), (740, 120, "#3f6450", 1.0, 2),
              (860, 120, "#24402f", 1.0, 0), (980, 80, "#13241a", 1.0, 0)]
    for i, (base, amp, col, a, blur) in enumerate(layers):
        pts = ridge(40 + i, base, amp, 2.2, 0.15)
        fill_below(pts, tuple(int(v) for v in rgb(col)), a, img, blur)
        # Mist between the hills.
        mist = layer()
        ImageDraw.Draw(mist).rectangle([0, base - 40, W, base + 60], fill=(240, 240, 230, 120))
        blend(img, mist.filter(ImageFilter.GaussianBlur(40)), 0.6 - i * 0.1)
    # Tea rows on the nearest hill.
    l = layer()
    d = ImageDraw.Draw(l)
    for k in range(14):
        y = 880 + k * 16
        d.arc([-200, y - 120, W + 200, y + 160], 200, 340, fill=(60, 110, 70, 140), width=4)
    blend(img, l, 1.0)
    darken_bottom(img, 0.55, amount=0.85)
    return img


def desert_night():
    img = sky([(0, "#02030a"), (0.5, "#0c1230"), (0.72, "#2a2850"), (1, "#2a2850")])
    # The Milky Way: a soft tilted band of light with many small stars.
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    band = np.exp(-((yy - (650 - xx * 0.32)) / 120) ** 2) * 0.35
    img += band[..., None] * rgb("#8a86c0")
    stars(img, 9, 900, 760, 2.0)
    stars(img, 10, 300, 760, 1.2)
    glow(img, 1560, 200, 90, "#fffbe8", 1.0)
    l = layer()
    ImageDraw.Draw(l).ellipse([1530, 170, 1590, 230], fill=(255, 250, 230, 255))
    blend(img, l, 1.0)
    for i, (base, amp, col) in enumerate([(780, 60, "#3a2c3e"), (860, 80, "#241a28"), (960, 70, "#120c14")]):
        fill_below(ridge(70 + i, base, amp, 1.2, 0.05), tuple(int(v) for v in rgb(col)), 1.0, img)
    darken_bottom(img, 0.72, amount=0.6)
    return img


# ---- aurora ------------------------------------------------------------------------------------

def aurora_curtain(img, seed, y0, y1, colors=("#3cf0a0", "#7a5cff"), strength=0.8):
    rnd = random.Random(seed)
    xs = np.arange(W, dtype=np.float32)
    ph = [rnd.random() * 6.28 for _ in range(3)]
    top = y0 + 80 * np.sin(xs / 300 + ph[0]) + 40 * np.sin(xs / 97 + ph[1])
    intensity = 0.55 + 0.45 * np.sin(xs / 41 + ph[2]) ** 2
    yy = np.arange(H, dtype=np.float32)[:, None]
    t = (yy - top[None, :]) / (y1 - y0)
    a = np.where(t > 0, np.exp(-t * 3.2), np.exp(t * 25)) * intensity[None, :] * strength
    c0, c1 = rgb(colors[0]), rgb(colors[1])
    mix = np.clip(1 - t * 1.4, 0, 1)[..., None]
    col = c0 * (1 - mix) + c1 * mix
    img += a[..., None] * col * 0.9
    np.clip(img, 0, 255, out=img)


def aurora_cabin():
    img = sky([(0, "#020610"), (0.6, "#06142a"), (1, "#06142a")])
    stars(img, 21, 400, 700)
    aurora_curtain(img, 1, 120, 620)
    aurora_curtain(img, 2, 260, 700, ("#5cffc8", "#2ad0ff"), 0.5)
    water_y = 760
    fill_below(ridge(5, 720, 40, 6, 0.6, 4), (4, 10, 14), 1.0, img)
    l = layer()
    d = ImageDraw.Draw(l)
    rnd = random.Random(4)
    for x in range(0, W, 26):     # spruce tops on the far shore
        h = rnd.randint(30, 90)
        d.polygon([(x, water_y), (x + 13, water_y - h), (x + 26, water_y)], fill=(3, 8, 12, 255))
    # The cabin with a lit window.
    cx = 1380
    d.rectangle([cx, water_y - 70, cx + 120, water_y], fill=(3, 8, 12, 255))
    d.polygon([(cx - 14, water_y - 70), (cx + 134, water_y - 70), (cx + 60, water_y - 130)], fill=(3, 8, 12, 255))
    d.rectangle([cx + 40, water_y - 48, cx + 70, water_y - 22], fill=(255, 200, 120, 255))
    blend(img, l, 1.0)
    glow(img, cx + 55, water_y - 35, 70, "#ffb060", 0.5)
    reflect(img, water_y, tint="#020610", strength=0.7, wobble=3)
    darken_bottom(img, 0.72, amount=0.6)
    return img


def aurora_peaks():
    img = sky([(0, "#03040c"), (0.55, "#0a1530"), (1, "#0a1530")])
    stars(img, 31, 350, 650)
    aurora_curtain(img, 7, 80, 560, ("#4dff9a", "#d05cff"), 0.9)
    for i, (base, amp, col) in enumerate([(700, 220, "#b8c4dc"), (780, 160, "#5a6a88"), (900, 90, "#141a28")]):
        pts = ridge(90 + i, base, amp, 1.4 + i * 0.6, 0.12)
        fill_below(pts, tuple(int(v) for v in rgb(col)), 1.0, img)
        if i == 0:     # snow light on the far peaks
            glow(img, 900, 520, 500, "#c8ffe8", 0.12, 1.6, 0.5)
    darken_bottom(img, 0.7, amount=0.65)
    return img


# ---- ruska -------------------------------------------------------------------------------------

def autumn_tree(d, x, y, h, colors, rnd):
    d.line([x, y, x, y - h * 0.6], fill=(30, 18, 14, 255), width=8)
    for _ in range(26):
        rx = x + rnd.gauss(0, h * 0.18)
        ry = y - h * 0.6 - rnd.random() * h * 0.45
        r = rnd.uniform(h * 0.06, h * 0.12)
        c = colors[rnd.randrange(len(colors))]
        d.ellipse([rx - r, ry - r, rx + r, ry + r], fill=c + (230,))


def autumn_river():
    img = sky([(0, "#2a1a3a"), (0.4, "#8a3e4e"), (0.6, "#f08a4a"), (1, "#f08a4a")])
    glow(img, 960, 560, 420, "#ffd08a", 0.6, 1.5, 0.7)
    water_y = 720
    fill_below(ridge(12, 640, 50, 1.4, 0.2), (60, 30, 40), 1.0, img, 6)
    l = layer()
    d = ImageDraw.Draw(l)
    rnd = random.Random(14)
    warm = [(224, 96, 40), (240, 150, 50), (200, 60, 40), (250, 190, 80)]
    for x in list(range(-40, 700, 70)) + list(range(1250, 2000, 70)):
        autumn_tree(d, x + rnd.randint(-20, 20), water_y + 10, rnd.randint(220, 340), warm, rnd)
    blend(img, l, 1.0)
    reflect(img, water_y, tint="#1a1018", strength=0.6, wobble=7)
    darken_bottom(img, 0.7)
    return img


def birch_lake():
    img = sky([(0, "#3a4458"), (0.5, "#8a8a90"), (0.62, "#c8a88a"), (1, "#c8a88a")])
    water_y = 700
    fill_below(ridge(22, 650, 30, 2, 0.4), (70, 50, 46), 1.0, img, 8)
    mist = layer()
    ImageDraw.Draw(mist).rectangle([0, 600, W, 720], fill=(220, 210, 200, 160))
    blend(img, mist.filter(ImageFilter.GaussianBlur(50)), 0.8)
    l = layer()
    d = ImageDraw.Draw(l)
    rnd = random.Random(23)
    for x in (140, 260, 330, 1580, 1700, 1820):
        h = rnd.randint(520, 700)
        d.line([x, water_y + 20, x + rnd.randint(-15, 15), water_y - h], fill=(235, 232, 225, 255), width=12)
        for k in range(10):
            yk = water_y - rnd.random() * h
            d.line([x - 6, yk, x + 6, yk], fill=(30, 26, 26, 255), width=3)
        for _ in range(20):
            rx, ry = x + rnd.gauss(0, 60), water_y - h * rnd.uniform(0.55, 1.0)
            r = rnd.uniform(18, 34)
            d.ellipse([rx - r, ry - r, rx + r, ry + r], fill=(236, 170, 60, 210))
    blend(img, l, 1.0)
    reflect(img, water_y, tint="#2a2a30", strength=0.55, wobble=3)
    darken_bottom(img, 0.7)
    return img


# ---- glass -------------------------------------------------------------------------------------

def glass_blobs():
    img = sky([(0, "#0a1024"), (1, "#05070f")])
    rnd = random.Random(41)
    for _ in range(9):
        cx, cy = rnd.uniform(100, 1820), rnd.uniform(80, 700)
        r = rnd.uniform(160, 380)
        col = rnd.choice(["#2ad0c8", "#7a5cff", "#3a7aff", "#c05cff"])
        glow(img, cx, cy, r, col, rnd.uniform(0.25, 0.5), 1.2, 0.9)
    # Thin glass edges catching the light.
    l = layer()
    d = ImageDraw.Draw(l)
    for _ in range(6):
        cx, cy, r = rnd.uniform(200, 1700), rnd.uniform(150, 650), rnd.uniform(90, 220)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(255, 255, 255, 60), width=2)
    blend(img, l.filter(ImageFilter.GaussianBlur(1.2)), 1.0)
    darken_bottom(img, 0.6, amount=0.7)
    return img


def prism_beams():
    img = sky([(0, "#060912"), (1, "#03040a")])
    l = layer()
    d = ImageDraw.Draw(l)
    colors = [(255, 80, 120), (255, 170, 60), (240, 240, 90), (80, 230, 140), (60, 170, 255), (150, 90, 255)]
    for i, c in enumerate(colors):
        y = 260 + i * 34
        d.polygon([(860, 420), (W, y - 30), (W, y + 30)], fill=c + (110,))
    d.polygon([(780, 520), (960, 300), (1000, 520)], outline=(255, 255, 255, 150), fill=(255, 255, 255, 25), width=3)
    d.polygon([(0, 470), (870, 410), (870, 430), (0, 500)], fill=(255, 255, 255, 120))
    blend(img, l.filter(ImageFilter.GaussianBlur(6)), 1.0)
    glow(img, 900, 420, 260, "#ffffff", 0.18)
    darken_bottom(img, 0.6, amount=0.7)
    return img


SETS = {
    "places": [("1-doha-corniche", doha_corniche), ("2-toronto-night", toronto_night),
               ("3-chennai-marina", chennai_marina), ("4-vellore-fort", vellore_fort),
               ("5-nilgiri-tea", nilgiri_tea), ("6-desert-night", desert_night)],
    "aurora": [("aurora-cabin", aurora_cabin), ("aurora-peaks", aurora_peaks)],
    "ruska": [("autumn-river", autumn_river), ("birch-lake", birch_lake)],
    "glass": [("glass-blobs", glass_blobs), ("prism-beams", prism_beams)],
}

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "family-home/art"
    only = sys.argv[2:]
    for name, scenes in SETS.items():
        for file, fn in scenes:
            if only and file not in only:
                continue
            save(fn(), os.path.join(out, name, file + ".jpg"))
