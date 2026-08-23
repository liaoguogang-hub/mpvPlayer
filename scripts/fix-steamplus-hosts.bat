@echo off
chcp 65001 >nul
title W31.23 Fix Steam++ Hosts Hijack

:: Check if admin (S-1-16-12288 = High Mandatory Level)
whoami /groups 2>nul | findstr "S-1-16-12288" >nul
if %errorlevel% neq 0 (
    echo [W31.23] Not admin. Self-elevating via UAC...
    powershell -Command "Start-Process cmd -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs"
    exit /b
)

echo [W31.23] Running as admin. Patching hosts file...

:: Simple inline powershell replace (single line, no line continuations)
powershell -NoProfile -ExecutionPolicy Bypass -Command "(Get-Content 'C:\Windows\System32\drivers\etc\hosts' -Encoding UTF8) -replace '^127\.0\.0\.1\s+api\.github\.com\s*$', '#127.0.0.1 api.github.com # W31.23 temp' | Set-Content 'C:\Windows\System32\drivers\etc\hosts' -Encoding UTF8"

echo.
echo [W31.23] === Remaining api.github.com entries (should all start with #) === :
findstr "api.github.com" "C:\Windows\System32\drivers\etc\hosts"
echo === End ===
echo.
echo [W31.23] Done. Close this window and tell Claude to retry gh release create.
pause