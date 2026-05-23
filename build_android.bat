@echo off
chcp 65001 >nul
title Android APK Build (Alternative)

echo ============================================
echo   构建 Android APK - 实用方案
echo ============================================
echo.
echo 本地编译 APK 需要 Flutter SDK 1.7GB + Android SDK，
echo 网络环境从 Google 下载太慢。
echo.
echo 以下三种方式任选：
echo.
echo ─── 方式 1: 用 Termux 在手机上直接运行（推荐） ───
echo.
echo   Step 1: 手机安装 Termux
echo           https://f-droid.org/packages/com.termux/
echo.
echo   Step 2: 把 daily-reminder 文件夹传到手机
echo           用 USB 线 / QQ 文件传输 / 微信 传到手机
echo.
echo   Step 3: 在 Termux 中执行：
echo.
echo           pkg install python -y
echo           pip install flet
echo           cd ~/storage/downloads/daily-reminder
echo           python android_app/main.py
echo.
echo   手机就会显示工作记录界面，自动发现同一 Wi-Fi 的 Windows 端
echo.
echo ─── 方式 2: 推送到 GitHub Actions 自动编译 ──────────
echo.
echo   git add .
echo   git commit -m "init"
echo   git push
echo   然后去 GitHub -> Actions -> Build Android APK -> Run workflow
echo   等几分钟下载 APK
echo.
echo ─── 方式 3: 用 Pydroid 在手机上运行 ─────────────────
echo.
echo   手机安装 Pydroid（应用商店搜）
echo   把代码传进去，点运行即可
echo.
pause
