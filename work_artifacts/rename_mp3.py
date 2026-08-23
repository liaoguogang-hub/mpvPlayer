"""Rename target mp3 to UTF-8 friendly name + flush thumbnail cache."""
import os, ctypes, ctypes.wintypes, glob, subprocess

downloads = r'C:\Users\guoga\Downloads'

# Find the target mp3 by:
# 1. Bash ls shows it as "要把寿星考一考.mp3" (UTF-8 display)
# 2. Python may see GBK bytes as mojibake
src_path = None
for path in glob.glob(os.path.join(downloads, '*.mp3')):
    name = os.path.basename(path)
    # Try multiple decoding strategies
    decoded_attempts = []
    for src_enc in ['utf-8', 'gbk', 'gb18030', 'cp936']:
        try:
            d = name.encode(src_enc).decode(src_enc) if False else name.encode('latin-1').decode(src_enc)
            decoded_attempts.append(f'{src_enc}={d!r}')
            if '寿星考一考' in d:
                src_path = path
                break
        except Exception:
            pass
    if '寿星' in name:
        src_path = path
    if src_path:
        break

# If still not found, use size match (4541821 bytes from earlier)
if not src_path:
    for path in glob.glob(os.path.join(downloads, '*.mp3')):
        if os.path.getsize(path) == 4541821:
            src_path = path
            break

if not src_path:
    print('ERROR: target mp3 not found')
    print('Listing all mp3 in Downloads:')
    for p in glob.glob(os.path.join(downloads, '*.mp3')):
        print(f'  {os.path.basename(p)} ({os.path.getsize(p)} bytes)')
    exit(1)

print(f'SRC: {src_path}')
print(f'     size: {os.path.getsize(src_path)} bytes')
print(f'     name bytes: {os.path.basename(src_path).encode("utf-8", errors="replace").hex()}')

# Target: UTF-8 friendly name
dst_name = '寿星多多-要把寿星考一考.mp3'
dst_path = os.path.join(downloads, dst_name)

if os.path.exists(dst_path):
    backup = dst_path.replace('.mp3', '-old.mp3')
    if os.path.exists(backup):
        os.remove(backup)
    print(f'backing up existing dst -> {backup}')
    os.rename(dst_path, backup)

# MoveFileW (Windows API) - handles UTF-16 paths correctly
MoveFileW = ctypes.windll.kernel32.MoveFileW
MoveFileW.argtypes = [ctypes.wintypes.LPCWSTR, ctypes.wintypes.LPCWSTR]
MoveFileW.restype = ctypes.wintypes.BOOL
ok = MoveFileW(src_path, dst_path)
if not ok:
    err = ctypes.GetLastError()
    print(f'MoveFileW failed: err={err}')
    exit(1)

print(f'\nRENAMED OK:')
print(f'  {src_path}')
print(f'-> {dst_path}')
print(f'  size: {os.path.getsize(dst_path)} bytes')
print()

# === Flush explorer thumbnail cache ===
print('=== flushing explorer thumbnail cache ===')
thumb_dir = os.path.join(os.environ['LOCALAPPDATA'], 'Microsoft', 'Windows', 'Explorer')
import glob as g
removed = 0
for p in g.glob(os.path.join(thumb_dir, 'thumbcache_*.db')):
    try:
        os.remove(p)
        removed += 1
        print(f'  removed: {os.path.basename(p)}')
    except Exception as e:
        print(f'  skip {os.path.basename(p)}: {e}')
print(f'removed {removed} thumbcache files')

# === Notify explorer + restart ===
print()
print('=== restarting explorer ===')
ps = subprocess.run(
    ['powershell', '-NoProfile', '-Command',
     'Add-Type -Namespace W -Name S -MemberDefinition "[DllImport(\"shell32.dll\")]public static extern void SHChangeNotify(int w,int u,System.IntPtr i1,System.IntPtr i2)";'
     '[W.S]::SHChangeNotify(0x00008000, 0x0005, 0, 0);'
     'Stop-Process -Name explorer -Force -ErrorAction SilentlyContinue;'
     'Start-Process explorer;'
     'Write-Host "explorer SHChangeNotify sent + restarted"'],
    capture_output=True, text=True, timeout=30,
)
print(ps.stdout.strip() or '(no stdout)')
if ps.stderr.strip():
    print(f'PS stderr: {ps.stderr.strip()}')

print()
print('DONE. Open Downloads in File Explorer; the file should be:')
print(f'  {dst_path}')
print('with thumbnail showing 多多 + 妈妈 birthday cover.')