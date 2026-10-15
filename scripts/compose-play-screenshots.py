"""Compose Google Play phone screenshots (1080x1920) from raw emulator captures."""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

RAW = Path(sys.argv[1])
OUT = Path(sys.argv[2])
FONT_DIR = Path(sys.argv[3])
OUT.mkdir(parents=True, exist_ok=True)

W, H = 1080, 1920
BG = (11, 13, 16)
TEXT = (236, 239, 243)
MUTED = (139, 148, 161)
ACCENT = (95, 211, 199)

SHOTS = [
    ("raw1_home.png", "All your servers,\nat a glance", "qBittorrent and Transmission with live speeds in one place"),
    ("raw2_list.png", "Every torrent,\nclearly laid out", "Swipe to pause or delete, long-press to select many"),
    ("raw3_detail.png", "Full control of\neach torrent", "Peers, trackers, files and per-file priority"),
    ("raw4_server.png", "See where your\nuploads go", "A daily upload map and per-server transfer totals"),
    ("raw5_charts.png", "Insights that\nstay simple", "Categories, sites and tags as clean, quiet charts"),
]


def geist(size, weight, mono=False):
    f = ImageFont.truetype(str(FONT_DIR / ("geist_mono.ttf" if mono else "geist.ttf")), size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


H1 = geist(78, 600)
SUB = geist(34, 400)
TAG = geist(26, 500, mono=True)


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] + radius), radius=radius, fill=255)
    return m


def compose(idx, raw_name, title, subtitle):
    canvas = Image.new("RGB", (W, H), BG)

    # Soft accent glow behind the device, very low intensity.
    glow = Image.new("RGB", (W, H), BG)
    gd = ImageDraw.Draw(glow)
    gd.ellipse((140, 700, 940, 1500), fill=(20, 44, 44))
    glow = glow.filter(ImageFilter.GaussianBlur(160))
    canvas = Image.blend(canvas, glow, 0.9)
    d = ImageDraw.Draw(canvas)

    x0 = 96
    d.text((x0, 132), f"0{idx}", font=TAG, fill=ACCENT)
    d.multiline_text((x0, 180), title, font=H1, fill=TEXT, spacing=10)
    d.text((x0, 392), subtitle, font=SUB, fill=MUTED)

    shot = Image.open(RAW / raw_name).convert("RGB")
    sw = 880
    sh = round(shot.height * sw / shot.width)
    shot = shot.resize((sw, sh), Image.LANCZOS)
    top = 500
    visible = H - top
    shot = shot.crop((0, 0, sw, visible))
    radius = 48
    sx = (W - sw) // 2

    # Hairline frame drawn as a slightly larger rounded rect behind the shot.
    frame = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle(
        (sx - 2, top - 2, sx + sw + 1, H + radius), radius=radius + 2, fill=(255, 255, 255, 28)
    )
    canvas.paste(frame, (0, 0), frame)
    canvas.paste(shot, (sx, top), rounded_mask((sw, visible), radius))

    out = OUT / f"{idx:02d}_{raw_name.split('_', 1)[1]}"
    canvas.save(out, optimize=True)
    return out


for i, (raw, title, sub) in enumerate(SHOTS, 1):
    p = compose(i, raw, title, sub)
    print(p.name, Image.open(p).size)
