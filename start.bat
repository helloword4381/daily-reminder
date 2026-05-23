@echo off
chcp 65001 >nul
title Daily Reminder

echo ============================================
echo   Daily Reminder - Windows Client
echo ============================================
echo.

cd /d "%~dp0"

:: --- Check venv ---
if not exist ".venv\Scripts\python.exe" (
    echo [ERROR] Virtual env not found. Run setup.bat first.
    pause
    exit /b 1
)

:: --- Check requirements (quick sanity) ---
.venv\Scripts\python.exe -c "import customtkinter" >nul 2>&1
if errorlevel 1 (
    echo [WARN] Dependencies missing. Run setup.bat first.
    pause
    exit /b 1
)

echo Starting Windows client...
echo.
echo   Windows: desktop GUI (system tray)
echo   Android: auto-discover on same WiFi
echo   Close window to exit
echo ============================================
echo.

.venv\Scripts\python.exe windows_app\main.py

echo.
echo App exited.
pause
