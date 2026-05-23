@echo off
chcp 65001 >nul
title Package Windows Client

echo ============================================
echo   Package Windows Desktop Client (.exe)
echo ============================================
echo.

cd /d "%~dp0"

:: --- Check venv ---
if not exist ".venv\Scripts\python.exe" (
    echo [ERROR] Virtual env not found. Run setup.bat first.
    pause
    exit /b 1
)

:: --- Install PyInstaller ---
echo [1/3] Installing PyInstaller...
.venv\Scripts\pip.exe install -i https://mirrors.aliyun.com/pypi/simple/ pyinstaller 2>&1
if errorlevel 1 (
    .venv\Scripts\pip.exe install pyinstaller
)

:: --- Build ---
echo [2/3] Building .exe ...
rmdir /s /q build 2>nul
rmdir /s /q dist 2>nul

.venv\Scripts\pyinstaller.exe --onefile --windowed ^
    --name "DailyReminder" ^
    --hidden-import windows_app ^
    --hidden-import windows_app.db ^
    --hidden-import windows_app.gui ^
    --hidden-import windows_app.sync_engine ^
    --hidden-import common ^
    --hidden-import common.models ^
    --hidden-import common.sync_protocol ^
    --hidden-import plyer ^
    --hidden-import plyer.platforms.win.notification ^
    --hidden-import customtkinter ^
    --add-data "common;common" ^
    windows_app\main.py

if errorlevel 1 (
    echo [ERROR] Build failed.
    pause
    exit /b 1
)

echo [3/3] Done!
echo.
echo   Output: dist\DailyReminder.exe
echo.
pause
