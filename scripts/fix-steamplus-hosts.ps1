# Comment out BOTH api.github.com 127.0.0.1 lines in hosts file.
# Run from PowerShell launched with "Run as administrator".
$hostsPath = "$env:SystemRoot\System32\drivers\etc\hosts"
$content = Get-Content $hostsPath
$changed = 0
$new = foreach ($line in $content) {
    if ($line -match '^\s*127\.0\.0\.1\s+api\.github\.com\s*$') {
        $changed++
        "#$line   # W31.23 Claude: temp disabled for gh release"
    } else { $line }
}
if ($changed -gt 0) {
    Set-Content -Path $hostsPath -Value $new -Encoding UTF8
    Write-Host "OK: commented $changed lines"
} else {
    Write-Host "Nothing to change - already disabled or Steam++ not active"
}
Write-Host "---remaining api.github.com entries---"
Select-String -Path $hostsPath -Pattern "api.github.com" | ForEach-Object { Write-Host $_.Line }