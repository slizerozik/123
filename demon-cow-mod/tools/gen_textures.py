"""Generates all mod textures. Run: python tools/gen_textures.py (needs pillow + numpy)."""
import math, os, random
import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "demoncow", "textures")
os.makedirs(os.path.join(ROOT, "entity"), exist_ok=True)
os.makedirs(os.path.join(ROOT, "item"), exist_ok=True)
rnd = random.Random(666)

BLACK, GRAY, WHITE = (22, 20, 24), (150, 148, 142), (205, 203, 196)
RED, DARKRED = (230, 30, 25), (110, 14, 18)
MAUVE, MAUVE_D = (98, 62, 68), (74, 44, 52)
PINK, GOLD = (226, 150, 172), (242, 200, 48)
HORN, HORN_D = (196, 186, 164), (150, 140, 124)


def jitter(c, a=8):
    d = rnd.randint(-a, a)
    return tuple(max(0, min(255, v + d)) for v in c) + (255,)


def faces(u, v, w, h, d):
    """Minecraft box UV layout -> rects (x, y, w, h)."""
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def fill(px, rect, fn):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            c = fn(x, y, w, h)
            if c is not None:
                px[x0 + x, y0 + y] = c if len(c) == 4 else jitter(c)


def cow_textures():
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    glow = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    px, gx = img.load(), glow.load()

    def light():
        return WHITE if rnd.random() < 0.3 else GRAY

    def patchy(light_from):
        # black on top, jagged edge, light patches below `light_from` (fraction of height)
        edges = {}

        def fn(x, y, w, h):
            if x not in edges:
                edges[x] = light_from * h + rnd.uniform(-1.6, 1.6)
            return light() if y >= edges[x] else BLACK
        return fn

    def belly(x, y, w, h):
        if (x in (0, w - 1) and rnd.random() < 0.7) or (x in (1, w - 2) and rnd.random() < 0.3):
            return BLACK
        return light()

    # body 12x10x18 @ (0,0)
    f = faces(0, 0, 12, 10, 18)
    fill(px, f["top"], lambda x, y, w, h: BLACK)
    fill(px, f["bottom"], belly)
    fill(px, f["right"], patchy(0.5))
    fill(px, f["left"], patchy(0.5))
    fill(px, f["front"], patchy(0.55))
    fill(px, f["back"], patchy(0.75))
    # blood-red cracks on the back and flanks
    for name in ("top", "right", "left", "bottom"):
        x0, y0, w, h = f[name]
        for _ in range(3):
            x, y = x0 + rnd.randrange(w), y0 + rnd.randrange(h // 2)
            for i in range(rnd.randint(3, 6)):
                if x0 <= x < x0 + w and y0 <= y < y0 + h:
                    px[x, y] = jitter(DARKRED, 4)
                y += 1
                x += rnd.choice((-1, 0, 0, 1))

    # head 8x8x6 @ (64,0)
    f = faces(64, 0, 8, 8, 6)
    for name in ("top", "back"):
        fill(px, f[name], lambda x, y, w, h: BLACK)
    fill(px, f["bottom"], lambda x, y, w, h: MAUVE_D)
    # sides: black, the part next to the face is the mauve muzzle
    fill(px, f["right"], lambda x, y, w, h: MAUVE if (x >= w - 2 and y >= 2) else BLACK)
    fill(px, f["left"], lambda x, y, w, h: MAUVE if (x <= 1 and y >= 2) else BLACK)
    face = [
        "KKKKKKKK",
        "KRRKKRRK",
        "KRRKKRRK",
        "MMMMMMMM",
        "MNNMMNNM",
        "MNNMMNNM",
        "DTDTTDTD",
        "DDDDDDDD",
    ]
    cols = {"K": BLACK, "R": RED, "M": MAUVE, "N": (48, 10, 18), "T": (235, 232, 215), "D": (70, 8, 14)}
    x0, y0, _, _ = f["front"]
    for y, row in enumerate(face):
        for x, ch in enumerate(row):
            px[x0 + x, y0 + y] = jitter(cols[ch], 6 if ch == "M" else 3)
            if ch == "R":
                gx[x0 + x, y0 + y] = (255, 40, 30, 255)

    # hair cap 9x2x7 @ (64,16)
    for r in faces(64, 16, 9, 2, 7).values():
        fill(px, r, lambda x, y, w, h: (12, 10, 14))

    # nose ring pieces
    for r in list(faces(64, 28, 1, 3, 1).values()) + list(faces(70, 28, 5, 1, 1).values()):
        fill(px, r, lambda x, y, w, h: GOLD)

    # legs 4x12x4: front/arm @ (0,32), back @ (16,32)
    for u, rune_rows in ((0, (2, 3, 9, 10)), (16, (9, 10))):
        f = faces(u, 32, 4, 12, 4)
        fill(px, f["top"], lambda x, y, w, h: BLACK)
        fill(px, f["bottom"], lambda x, y, w, h: (40, 34, 34))
        for name in ("right", "front", "left", "back"):
            x0, y0, w, h = f[name]
            edge = [rnd.uniform(3.5, 7.5) for _ in range(w)]

            def leg(x, y, w, h):
                if u == 16:
                    return light() if 0 < y < edge[x] else BLACK
                return light() if edge[x] - 3 < y < edge[x] + 1 else BLACK
            fill(px, f[name], leg)
            # glowing "> <" runes
            rune = ("R..R", ".RR.") if name in ("front", "back") else ("....", ".RR.")
            for i, ry in enumerate(rune_rows):
                for x, ch in enumerate(rune[i % 2]):
                    if ch == "R":
                        px[x0 + x, y0 + ry] = RED + (255,)
                        gx[x0 + x, y0 + ry] = (255, 50, 30, 255)

    # udder 4x3x5 @ (32,32), teat 1x1x1 @ (32,42)
    for r in faces(32, 32, 4, 3, 5).values():
        fill(px, r, lambda x, y, w, h: PINK)
    for r in faces(32, 42, 1, 1, 1).values():
        fill(px, r, lambda x, y, w, h: (200, 110, 140))

    # horns: 3x2x2 @ (52,32), 2x5x2 @ (52,38)
    for r in list(faces(52, 32, 3, 2, 2).values()) + list(faces(52, 38, 2, 5, 2).values()):
        fill(px, r, lambda x, y, w, h: HORN if rnd.random() < 0.6 else HORN_D)

    img.save(os.path.join(ROOT, "entity", "demon_cow.png"))
    glow.save(os.path.join(ROOT, "entity", "demon_cow_glow.png"))


def portal_textures():
    n = 256
    ys, xs = np.mgrid[0:n, 0:n]
    x = (xs + 0.5) / n * 2 - 1
    y = (ys + 0.5) / n * 2 - 1
    r = np.sqrt(x * x + y * y)
    th = np.arctan2(y, x)

    # swirl
    arms = 0.5 + 0.5 * np.sin(5 * th + 9 * r)
    arms2 = 0.5 + 0.5 * np.sin(3 * th - 14 * r + 1.3)
    noise = np.random.default_rng(1).random((n // 8, n // 8))
    noise = np.kron(noise, np.ones((8, 8)))
    val = 0.18 + 0.5 * arms ** 2 * (0.4 + 0.6 * arms2)
    val *= 0.75 + 0.25 * noise
    val *= np.clip(0.35 + 0.9 * (1 - r), 0, 1) + 0.25
    core = np.exp(-(r / 0.22) ** 2)
    R = np.clip(val * 255 + core * 255, 0, 255)
    G = np.clip(val * 18 + core * 235, 0, 255)
    B = np.clip(val * 26 + core * 235, 0, 255)
    A = np.where(r <= 1.0, 255, 0)
    Image.fromarray(np.dstack([R, G, B, A]).astype(np.uint8)).save(os.path.join(ROOT, "entity", "portal_swirl.png"))

    # ornate ring
    n = 512
    ys, xs = np.mgrid[0:n, 0:n]
    x = (xs + 0.5) / n * 2 - 1
    y = (ys + 0.5) / n * 2 - 1
    r = np.sqrt(x * x + y * y)
    th = np.arctan2(y, x)
    out = np.zeros((n, n, 4), dtype=np.float64)

    def paint(mask, col, alpha=255):
        out[mask] = (*col, alpha)

    speck = np.random.default_rng(2).random((n // 4, n // 4))
    speck = np.kron(speck, np.ones((4, 4)))
    paint((r > 0.84) & (r <= 1.0), (214, 28, 30))                      # red band
    paint((r > 0.84) & (r <= 1.0) & (speck > 0.82), (150, 12, 18))
    paint((r > 0.885) & (r < 0.955), (238, 222, 222))                  # pale inner track
    paint((r > 0.885) & (r < 0.955) & (speck > 0.6), (226, 150, 150))
    paint((r > 0.815) & (r <= 0.845), (246, 190, 40))                  # thin golden hoop
    # golden curls and red runes alternating around the track
    k = 20
    seg = (th + math.pi) / (2 * math.pi) * k
    idx = np.floor(seg).astype(int)
    lu = (seg - idx) * 2 - 1                                           # -1..1 along the ring
    lv = (r - 0.92) / 0.05                                             # -1..1 across the ring
    curl_r = np.sqrt(lu * lu + lv * lv)
    curl_t = np.arctan2(lv, lu)
    curl = (np.abs(curl_r - (0.25 + 0.5 * ((curl_t + math.pi) / (2 * math.pi)))) < 0.13) & (curl_r < 0.85)
    paint(curl & (idx % 2 == 0), (250, 196, 36))
    rune = ((np.abs(np.abs(lu) - (0.55 - 0.45 * np.abs(lv))) < 0.1) | (np.abs(lu) < 0.07)) & (np.abs(lv) < 0.8)
    paint(rune & (idx % 2 == 1), (200, 20, 26))
    Image.fromarray(out.astype(np.uint8)).save(os.path.join(ROOT, "entity", "portal_ring.png"))


def item_texture():
    rows = [
        "................",
        ".......rr.......",
        "......rRRr......",
        "......rRRr......",
        "......rRRr......",
        ".......bb.......",
        "......gGGg......",
        ".....gGYYGg.....",
        ".....gGYGGg.....",
        "....gGYGGGGg....",
        "....gGYGGGGg....",
        "...gGGYGGRGGg...",
        "...gGGGGGGGGg...",
        "..bbbbbbbbbbbb..",
        "......dRRd......",
        "................",
    ]
    cols = {"r": (120, 10, 40), "R": (214, 24, 70), "b": (110, 70, 30), "g": (176, 120, 20), "G": (240, 196, 60),
            "Y": (255, 238, 150), "d": (90, 50, 20)}
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), cols[ch] + (255,))
    img.save(os.path.join(ROOT, "item", "cow_bell.png"))


cow_textures()
portal_textures()
item_texture()
print("textures written to", os.path.normpath(ROOT))
