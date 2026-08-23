# Rename target mp3 to UTF-8 friendly name + clear explorer thumbnail cache
$ErrorActionPreference = 'Stop'

$src = 'C:\Users\guoga\Downloads\Ҫ�����ǿ�һ��.mp3'
$dst = 'C:\Users\guoga\Downloads\寿星多多-要把寿星考一考.mp3'

if (-not (Test-Path -LiteralPath $src)) {
    Write-Host "ERROR: source not found: $src"
    exit 1
}
$size = (Get-Item -LiteralPath $src).Length
Write-Host "[1] source exists, size=$size bytes"

if (Test-Path -LiteralPath $dst) {
    $backup = $dst -replace '\.mp3$', '-old.mp3'
    Move-Item -LiteralPath $dst $backup -Force
    Write-Host "[2] backed up existing dst to: $backup"
}

Rename-Item -LiteralPath $src $dst
Write-Host "[3] renamed to: $dst"

$item = Get-Item -LiteralPath $dst
Write-Host "[4] verify: $($item.Length) bytes"

# Clear thumbnail cache (force explorer to regenerate)
$thumbDir = "$env:LocalAppData\Microsoft\Windows\Explorer"
Get-ChildItem -Path $thumbDir -Filter 'thumbcache_*.db' -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "[5] removing thumbcache: $($_.Name)"
    Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue
}

# Notify explorer to refresh thumbnails (SHCNE_RENAMEITEM | SHCNE_UPDATEITEM)
Add-Type -Namespace Win32 -Name Shell32 -MemberDefinition @'
[DllImport("shell32.dll")]
public static extern void SHChangeNotify(int wEventId, int uFlags, IntPtr dwItem1, IntPtr dwItem2);
'@
[Win32.Shell32]::SHChangeNotify(0x00008000, 0x0005, 0, 0)  # SHCNE_UPDATEITEM
Write-Host "[6] SHChangeNotify UPDATEITEM broadcast sent"

# Kill + restart explorer to ensure cache rebuild
Stop-Process -Name explorer -Force -ErrorAction SilentlyContinue
Start-Process explorer
Write-Host "[7] explorer restarted"

Write-Host ""
Write-Host "DONE. Now in Explorer, navigate to Downloads, the file should be:"
Write-Host "  $dst"
Write-Host "with thumbnail = 多多 + 妈妈 birthday cover"