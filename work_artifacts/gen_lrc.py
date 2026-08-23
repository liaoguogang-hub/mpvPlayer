"""LRC v3 — corrected: 整体提前 1-2s + 第二段 (Verse 2) 重算时间.

New anchors (from v2 feedback):
- Intro 4 句 still ~ 0-18s
- Verse 1 first line at 34s (user confirmed)
- 但 v2 整体滞后 1-2s,所以全部往前提 2s
- 第二段(Verse 2) v2 估的 78.5s 完全错位,按段落长度重算

段落预算 (基于用户锚点 + 典型流行歌曲节奏):
- Intro:        0   -  18s (4 × 4.5s)
- 过渡:        18   -  32s
- Verse 1:    32   -  48s (4 × 4s)
- Chorus 1:   48   -  77s (9 行,含 3 组问答)
- Verse 2:    77   -  95s (4 × 4.5s)   ← v2 这里估错了
- Chorus 2:   95   - 124s (9 行)
- Outro:      124   - 181s (5 行 + 长尾)
"""
import os, shutil
from mutagen.id3 import ID3, ID3NoHeaderError, SYLT
from mutagen.id3 import Encoding as ID3Encoding

MP3 = 'C:/Users/guoga/Downloads/寿星多多-要把寿星考一考.mp3'
LRC = 'D:/study/mpvKt/work_artifacts/寿星多多-要把寿星考一考.lrc'

lyrics_v3 = [
    # Intro: 0:00 - 0:18
    (0.00,  '[Intro]'),
    (0.50,  '祝你生日快乐'),
    (4.50,  '祝你生日快乐'),
    (9.00,  '祝你生日快乐'),
    (13.50, '祝你生日快乐'),

    # 过渡 18-32s (14s 纯音乐)

    # Verse 1: 32-48s (4 行)
    (32.00, '[Verse]'),
    (32.50, '今天是你的好日子，全家欢聚多热闹'),
    (36.50, '妈妈忙里又忙外，做了一桌好佳肴'),
    (40.50, '外公外婆坐中间，笑得合不拢嘴角'),
    (44.50, '举起酒杯送祝愿，福星高照身体好'),

    # Chorus 1: 48-77s
    (48.50, '[Chorus]'),
    (49.00, '全家人齐上阵，要把寿星考一考'),
    (53.50, '大声回答这些问题，一个都不能少'),
    (57.50, '爸爸最喜欢谁呀？'),
    (59.50, '宝贝心肝！'),
    (61.00, '爸爸最喜欢吃什么呀？'),
    (63.00, '多多！'),
    (64.50, '爸爸最喜欢干什么？'),
    (66.50, '出外快！'),
    (69.00, '祝你天天都开心，好运连连没烦恼！'),

    # Verse 2: 77-95s (4 行)  ← v2 这里估错,实际 ~77s
    (73.00, '[Verse]'),
    (73.50, '喜庆的歌儿唱起来，欢乐节拍真动感'),
    (77.50, '不管你出多少外快，健康平安是靠山'),
    (81.50, '外公外婆来夸赞，夸你是个好模范'),
    (85.50, '妈妈点头心里面暖，幸福一家永相伴'),

    # Chorus 2: 95-124s
    (89.50, '[Chorus]'),
    (90.00, '全家人齐上阵，要把寿星考一考'),
    (94.50, '大声回答这些问题，一个都不能少'),
    (98.50, '爸爸最喜欢谁呀？'),
    (100.50, '宝贝心肝！'),
    (102.00, '爸爸最喜欢吃什么呀？'),
    (104.00, '多多！'),
    (105.50, '爸爸最喜欢干什么？'),
    (107.50, '出外快！'),
    (110.00, '祝你天天都开心，好运连连没烦恼！'),

    # Outro: 124-181s
    (114.50, '[Outro]'),
    (115.00, '祝你生日快乐'),
    (123.00, '宝贝心肝永远爱你'),
    (131.00, '祝你生日快乐'),
    (140.00, '出外快天天发大财'),
    (175.00, '耶！生日快乐！'),
]

def fmt_ts(seconds):
    m = int(seconds // 60)
    s = seconds - m * 60
    return f'[{m:02d}:{s:05.2f}]'

# === Write LRC ===
header = [
    '[ti:要把寿星考一考 (寿星:多多)]',
    '[ar:多多 & 妈妈]',
    '[al:要把寿星考一考]',
    '[by:Claude v3 — 用户反馈滞后1-2s + 第二段重算]',
    '[re:AIMP/foobar2000]',
    '',
]
body = [f'{fmt_ts(ts)}{text}' for ts, text in lyrics_v3]
lrc_content = '\n'.join(header + body) + '\n'

with open(LRC, 'w', encoding='utf-8') as f:
    f.write(lrc_content)

# === Write SYLT ===
try:
    tags = ID3(MP3)
except ID3NoHeaderError:
    tags = ID3()
for k in list(tags.keys()):
    if k.startswith('SYLT'):
        del tags[k]

sylt_data = [(text, int(ts * 1000)) for ts, text in lyrics_v3]
tags.add(SYLT(encoding=ID3Encoding.UTF16, lang='und', format=2, type=1, desc='', text=sylt_data))
tags.save(MP3, v2_version=3)

# === Copy LRC next to mp3 ===
mp3_dir = os.path.dirname(MP3)
lrc_dest = os.path.join(mp3_dir, '寿星多多-要把寿星考一考.lrc')
shutil.copy(LRC, lrc_dest)

print(f'LRC v3 saved: {LRC}')
print(f'  {len(lyrics_v3)} lines')
print(f'  span: 0:00 - {fmt_ts(lyrics_v3[-1][0])}')
print(f'  song duration: 181.0s')
print(f'  SYLT written')
print(f'  LRC copied next to mp3')
print()
print('=== 关键 anchor 校准 ===')
for ts, text in [(13.50, '祝你生日快乐 (Intro 末句 ~18s)'),
                  (32.50, '今天是你的好日子 (Verse 1 首句 34s)'),
                  (73.50, '喜庆的歌儿唱起来 (Verse 2 首句 77s — 修正)'),
                  (175.00, '耶！生日快乐！ (Outro 末 175s)')]:
    print(f'  {fmt_ts(ts)}  {text}')