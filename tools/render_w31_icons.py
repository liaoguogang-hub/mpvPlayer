"""W31.8 渲染 PlexiPlay 风格 legacy 图标 (mipmap-mdpi 等 .webp)。
深紫底 + 青圆角方块 + 白播放三角。Android 8+ 走 adaptive icon,这个
只给 Android 5-7 用。
"""
import os
from PIL import Image, ImageDraw

DENSITIES = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192,
}

BG = (30, 10, 60, 255)        # #1E0A3C 深紫
CYAN = (34, 211, 238, 255)    # #22D3EE 青
WHITE = (255, 255, 255, 255)


def render_icon(size: int) -> Image.Image:
    """渲染 size x size 图标。设计按 108 viewport,缩放到 size。"""
    img = Image.new('RGBA', (size, size), BG)
    draw = ImageDraw.Draw(img)
    s = size / 108.0  # scale factor

    # 青圆角方块 (M30,30 L78,30 A12,12 → 90,42 → 90,66 → 78,78 → 30,78 → 18,66 → 18,42 → 30,30)
    box = [
        (round(30 * s), round(30 * s)),
        (round(78 * s), round(30 * s)),
        (round(90 * s), round(42 * s)),
        (round(90 * s), round(66 * s)),
        (round(78 * s), round(78 * s)),
        (round(30 * s), round(78 * s)),
        (round(18 * s), round(66 * s)),
        (round(18 * s), round(42 * s)),
    ]
    radius = round(12 * s)
    draw.rounded_rectangle(
        [(round(18 * s), round(30 * s)), (round(90 * s), round(78 * s))],
        radius=radius,
        fill=CYAN,
    )

    # 白播放三角 (48,40 → 48,68 → 72,54)
    triangle = [
        (round(48 * s), round(40 * s)),
        (round(48 * s), round(68 * s)),
        (round(72 * s), round(54 * s)),
    ]
    draw.polygon(triangle, fill=WHITE)

    return img


def main():
    out_dir = r'D:\study\mpvKt\app\src\main\res'
    for name, size in DENSITIES.items():
        sub = os.path.join(out_dir, f'mipmap-{name}')
        os.makedirs(sub, exist_ok=True)
        img = render_icon(size)
        # Android 同时要 ic_launcher.webp 和 ic_launcher_round.webp。
        # 我们的图标本来就是圆角矩形,round 版用同一个图。
        for fname in ('ic_launcher.webp', 'ic_launcher_round.webp'):
            img.save(os.path.join(sub, fname), 'WEBP', quality=90)
            print(f'wrote {sub}/{fname} ({size}x{size})')


if __name__ == '__main__':
    main()
