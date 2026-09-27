"""Draws the launcher icons (older Android), the 512px Play Store icon and the feature graphic.
Run: python3 scripts/make_icons.py   (needs Pillow)"""
from PIL import Image, ImageDraw, ImageFont
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")
STORE = os.path.join(ROOT, "store")
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

def gradient(w, h, a=(0x6D, 0x4A, 0xFF), b=(0xC2, 0x3C, 0xFF)):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x / w + y / h) / 2
            px[x, y] = tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
    return img

def logo(draw, ox, oy, s):
    """Draws the stopwatch logo; (ox, oy) is the top-left of a 108-unit canvas scaled by s."""
    P = lambda x, y: (ox + x * s, oy + y * s)
    white, gold = (255, 255, 255), (0xFF, 0xC8, 0x57)
    draw.rectangle([P(49, 29), P(59, 34)], fill=white)
    draw.rectangle([P(52, 33), P(56, 38)], fill=white)
    draw.polygon([P(71.5, 37.5), P(74.5, 34.5), P(78.5, 38.5), P(75.5, 41.5)], fill=white)
    draw.ellipse([P(32, 38), P(76, 82)], outline=white, width=max(1, int(5 * s)))
    draw.polygon([P(49, 53.5), P(56, 48.5), P(60, 48.5), P(60, 69.5), P(64, 69.5), P(64, 73.5),
                  P(50, 73.5), P(50, 69.5), P(55, 69.5), P(55, 55), P(51, 57.6)], fill=gold)

def icon(size, rounded=True):
    big = size * 4
    img = gradient(big, big)
    d = ImageDraw.Draw(img)
    # show the centre 72 units of the 108 canvas (like the adaptive icon safe zone)
    s = big / 72.0
    logo(d, -18 * s, -18 * s, s)
    img = img.resize((size, size), Image.LANCZOS)
    if rounded:
        mask = Image.new("L", (size * 4, size * 4), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, size * 4 - 1, size * 4 - 1], radius=size * 4 // 5, fill=255)
        mask = mask.resize((size, size), Image.LANCZOS)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(img, (0, 0), mask)
        return out
    return img

for folder, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    path = os.path.join(RES, f"mipmap-{folder}")
    os.makedirs(path, exist_ok=True)
    icon(px).save(os.path.join(path, "ic_launcher.png"))

os.makedirs(STORE, exist_ok=True)
icon(512, rounded=False).save(os.path.join(STORE, "play-store-icon-512.png"))

# Feature graphic 1024x500
fg = gradient(1024, 500, (0x3B, 0x22, 0xC9), (0xE0, 0x3C, 0x8A))
d = ImageDraw.Draw(fg)
logo(d, 20, 60, 3.6)
d.text((430, 150), "1 Minute", font=ImageFont.truetype(BOLD, 86), fill="white")
d.text((430, 250), "Mind Game", font=ImageFont.truetype(BOLD, 86), fill=(0xFF, 0xC8, 0x57))
d.text((434, 368), "Train your brain, 1 minute a day", font=ImageFont.truetype(BOLD, 30), fill=(235, 230, 255))
fg.save(os.path.join(STORE, "feature-graphic-1024x500.png"))
print("done")
