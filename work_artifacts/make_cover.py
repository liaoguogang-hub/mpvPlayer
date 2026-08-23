#!/usr/bin/env python3
"""Cover v5 — user-cropped photo, text on TOP (sky area), no HappyShrimp tag."""
import os
from PIL import Image, ImageDraw, ImageFont
from mutagen.id3 import ID3, ID3NoHeaderError, APIC, Encoding

SRC = 'C:/Users/guoga/Pictures/20260818-181906 V2.jpg'
OUT_PNG = 'D:/study/mpvKt/work_artifacts/cover_v5.png'
OUT_JPG = 'D:/study/mpvKt/work_artifacts/cover_v5.jpg'
MP3 = 'C:/Users/guoga/Downloads/寿星多多-要把寿星考一考.mp3'

ZH_BOLD = [
    'C:/Windows/Fonts/msyhbd.ttc',
    'C:/Windows/Fonts/msyh.ttc',
    'C:/Windows/Fonts/simhei.ttf',
]
EMOJI = [
    'C:/Windows/Fonts/seguiemj.ttf',
    'C:/Windows/Fonts/NotoColorEmoji.ttf',
]

def find_font(candidates, size):
    for p in candidates:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                continue
    return ImageFont.load_default()

zh_title = find_font(ZH_BOLD, 92)
zh_sub   = find_font(ZH_BOLD, 54)
emoji    = find_font(EMOJI, 64)

# === Build cover ===
im = Image.open(SRC).convert('RGB')
w, h = im.size
side = min(w, h)  # 1:1
# Source is already cropped — use as-is (no further centering offset)
left = (w - side) // 2
top = (h - side) // 2
crop = im.crop((left, top, left + side, top + side))
cover = crop.resize((1024, 1024), Image.LANCZOS)

# === No banner — just add a subtle dark gradient on TOP region for text legibility ===
cover_rgba = cover.convert('RGBA')
top_grad_h = int(1024 * 0.35)  # top 35%
for y in range(top_grad_h):
    # Strong at top, fading to nothing at y = top_grad_h
    a = int(170 * (1 - y / top_grad_h) ** 1.3)
    for x in range(1024):
        # Blend toward black
        r, g, b, _ = cover_rgba.getpixel((x, y))
        cover_rgba.putpixel((x, y), (int(r * (1 - a/255)), int(g * (1 - a/255)), int(b * (1 - a/255)), 255))

draw = ImageDraw.Draw(cover_rgba)
cx = 512

def draw_centered(text, y, font, fill, shadow=True):
    bbox = draw.textbbox((0, 0), text, font=font)
    tw = bbox[2] - bbox[0]
    x = cx - tw // 2
    if shadow:
        draw.text((x + 2, y + 3), text, font=font, fill=(0, 0, 0, 235))
    draw.text((x, y), text, font=font, fill=fill)
    return tw, bbox[3] - bbox[1]

# Emoji decoration (above title, in top sky area)
emoji_text = '🎂 🎈 🎉 🎁 🧁'
eb = draw.textbbox((0, 0), emoji_text, font=emoji)
ew, eh = eb[2] - eb[0], eb[3] - eb[1]
ex = cx - ew // 2
draw.text((ex + 2, ey + 3) if False else (ex + 2, 60 + 3), emoji_text, font=emoji, fill=(0, 0, 0, 180))
draw.text((ex, 60), emoji_text, font=emoji, fill=(255, 255, 255, 255))

# Title
draw_centered('要把寿星考一考', 150, zh_title, fill=(255, 255, 255, 255))

# Subtitle (golden)
draw_centered('寿星多多 · 生日快乐', 260, zh_sub, fill=(255, 225, 140, 255))

# NOTE: no HappyShrimp AI · 2026 tag (per user request)

out = cover_rgba.convert('RGB')
out.save(OUT_PNG, 'PNG')
out.save(OUT_JPG, 'JPEG', quality=92, optimize=True)
print(f'saved {OUT_PNG}')
print(f'saved {OUT_JPG}')

# === Write back to mp3 APIC ===
print()
print('=== updating mp3 cover ===')
try:
    tags = ID3(MP3)
except ID3NoHeaderError:
    tags = ID3()
for k in list(tags.keys()):
    if k.startswith('APIC'):
        del tags[k]
with open(OUT_JPG, 'rb') as f:
    cover_bytes = f.read()
tags.add(APIC(encoding=Encoding.LATIN1, mime='image/jpeg', type=3, desc='', data=cover_bytes))
tags.save(MP3, v2_version=3)
print(f'apic updated, {len(cover_bytes)} bytes')

# Verify
v = ID3(MP3)
for apic in v.getall('APIC'):
    print(f'  APIC type={apic.type} mime={apic.mime} size={len(apic.data)} bytes')