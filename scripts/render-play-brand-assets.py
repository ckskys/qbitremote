"""Render the Play Store icon (512x512) and feature graphic (1024x500)."""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

FONT_DIR = Path(sys.argv[1])
RAW_HOME = Path(sys.argv[2])
OUT = Path(sys.argv[3])
OUT.mkdir(parents=True, exist_ok=True)

BG = (11, 13, 16)
BG_TOP = (21, 25, 31)
TEXT = (236, 239, 243)
MUTED = (139, 148, 161)
ACCENT = (95, 211, 199)

# Mark geometry in a 32-unit box (same paths as the launcher vector).
UP = [[(11, 21), (11, 10)], [(7, 14), (11, 10), (15, 14)]]
DOWN = [[(21, 11), (21, 22)], [(17, 18), (21, 22), (25, 18)]]
STROKE = 2.4


def draw_mark(img, cx, cy, scale, up_color, down_color):
    """Draw the two arrows centred on (cx, cy); 1 unit = `scale` px, round caps/joins."""
    d = ImageDraw.Draw(img)
    w = STROKE * scale
    r = w / 2
    for paths, color in ((UP, up_color), (DOWN, down_color)):
        for pts in paths:
            xy = [(cx + (x - 16) * scale, cy + (y - 16) * scale) for x, y in pts]
            d.line(xy, fill=color, width=round(w), joint="curve")
            for x, y in xy:
                d.ellipse((x - r, y - r, x + r, y + r), fill=color)


def vertical_gradient(size, top, bottom):
    w, h = size
    g = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / max(h - 1, 1)
        g.putpixel((0, y), tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return g.resize((w, h))


def geist(size, weight, mono=False):
    f = ImageFont.truetype(str(FONT_DIR / ("geist_mono.ttf" if mono else "geist.ttf")), size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


# ---------- 512 icon (full-bleed square; Play applies its own corner mask) ----------
SS = 4
N = 512 * SS
icon = vertical_gradient((N, N), BG_TOP, BG)
# Mark spans 18 units wide; ~56% of the icon width, matching the adaptive icon.
draw_mark(icon, N / 2, N / 2, 0.56 * N / 18, ACCENT, TEXT)
icon = icon.resize((512, 512), Image.LANCZOS)
icon_path = OUT / "torrentremote-icon-512.png"
icon.save(icon_path, optimize=True)

# ---------- 1024x500 feature graphic ----------
FW, FH = 1024, 500
fg = vertical_gradient((FW * SS, FH * SS), BG_TOP, BG)
glow = Image.new("RGB", fg.size, BG)
ImageDraw.Draw(glow).ellipse((560 * SS, 40 * SS, 1060 * SS, 540 * SS), fill=(22, 52, 52))
glow = glow.filter(ImageFilter.GaussianBlur(120 * SS))
fg = Image.blend(fg, glow, 0.55)

# App tile + mark on the left.
tile = 112 * SS
tx, ty = 72 * SS, 120 * SS
tile_img = Image.new("RGB", (tile, tile), (0, 0, 0))
tile_img.paste(vertical_gradient((tile, tile), (28, 33, 40), (17, 20, 25)))
draw_mark(tile_img, tile / 2, tile / 2, 0.56 * tile / 18, ACCENT, TEXT)
mask = Image.new("L", (tile, tile), 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, tile - 1, tile - 1), radius=int(tile * 0.26), fill=255)
fg.paste(tile_img, (tx, ty), mask)
fg = fg.resize((FW, FH), Image.LANCZOS)

d = ImageDraw.Draw(fg)
d.text((72, 260), "TorrentRemote", font=geist(58, 600), fill=TEXT)
d.text((74, 336), "Remote control for qBittorrent", font=geist(26, 400), fill=MUTED)
d.text((74, 370), "and Transmission", font=geist(26, 400), fill=MUTED)

# Real home screenshot on the right, bleeding off the bottom edge.
shot = Image.open(RAW_HOME).convert("RGB")
sw = 300
sh = round(shot.height * sw / shot.width)
shot = shot.resize((sw, sh), Image.LANCZOS)
top = 56
vis = FH - top
shot = shot.crop((0, 0, sw, vis))
radius = 26
sx = 640
frame = Image.new("RGBA", (FW, FH), (0, 0, 0, 0))
ImageDraw.Draw(frame).rounded_rectangle((sx - 2, top - 2, sx + sw + 1, FH + radius), radius=radius + 2,
                                        fill=(255, 255, 255, 30))
fg.paste(frame, (0, 0), frame)
smask = Image.new("L", (sw, vis), 0)
ImageDraw.Draw(smask).rounded_rectangle((0, 0, sw - 1, vis + radius), radius=radius, fill=255)
fg.paste(shot, (sx, top), smask)

fg_path = OUT / "feature-graphic-1024x500.png"
fg.save(fg_path, optimize=True)
for p in (icon_path, fg_path):
    print(p.name, Image.open(p).size, p.stat().st_size)
