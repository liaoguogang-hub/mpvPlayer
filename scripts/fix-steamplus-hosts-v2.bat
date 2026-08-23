@echo off
chcp 65001 >nul
title W31.23 Fix Steam++ Hosts Hijack v2

:: Force admin via mshta (bypasses Git Bash / PowerShell elevation quirks)
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [W31.23] Re-launching via UAC (mshta)...
    mshta vbscript:CreateObject("Shell.Application").ShellExecute "%~f0", "", "", "runas", 1
    exit /b
)

echo [W31.23] Running as admin. Patching hosts...
powershell -NoProfile -ExecutionPolicy Bypass -Command "(Get-Content 'C:\Windows\System32\drivers\etc\hosts' -Encoding UTF8) -replace '^127\.0\.0\.1\s+api\.github\.com\s*$', '#127.0.0.1 api.github.com  # W31.23 temp disabled by Claude for gh release' | Set-Content 'C:\Windows\System32\drivers\etc\hosts' -Encoding UTF8"

echo.
echo [W31.23] Patched. Remaining api.github.com entries:
findstr "api.github.com" "C:\Windows\System32\drivers\etc\hosts"
echo.
echo [W31.23] Done. Close this window.
pause