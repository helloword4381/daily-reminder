@echo off
chcp 65001 >nul
title Daily Reminder Setup

echo ============================================
echo   Daily Reminder - Setup
echo ============================================
echo.

cd /d "%~dp0"

:: --- Check Python ---
where py >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python not found. Please install Python 3.11+ first.
    pause
    exit /b 1
)

:: --- Create virtual environment if needed ---
if exist ".venv\Scripts\python.exe" (
    echo [1/2] Virtual env already exists, skipping.
) else (
    echo [1/2] Creating virtual environment...
    py -m venv .venv
    if errorlevel 1 (
        echo [ERROR] Failed to create virtual env.
        pause
        exit /b 1
    )
    echo [1/2] Virtual env created.
)

:: --- Install dependencies ---
echo [2/2] Installing dependencies (mirror: aliyun)...
.venv\Scripts\pip.exe install -i https://mirrors.aliyun.com/pypi/simple/ -r requirements.txt
if errorlevel 1 (
    echo [WARN] Aliyun mirror failed, trying default PyPI...
    .venv\Scripts\pip.exe install -r requirements.txt
    if errorlevel 1 (
        echo [ERROR] Dependency installation failed.
        pause
        exit /b 1
    )
)

echo.
echo ============================================
echo   Setup complete!
echo.
echo   Run start.bat to launch the Windows client
echo ============================================
echo.
pause
