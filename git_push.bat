@echo off
title Push to GitHub

:: 添加 Git 到路径（双击运行时可能没有）
set PATH=C:\Program Files\Git\cmd;%PATH%

echo.
echo ============================================
echo   Push code to GitHub
echo ============================================
echo.

cd /d "%~dp0"
echo Current directory: %cd%
echo.

:: Init git
if exist ".git" (
    echo [1/7] Git already initialized.
) else (
    echo [1/7] Initializing git...
    git init
)

:: Set user identity
echo [2/7] Setting user identity...
git config user.email "jiangflow@foxmail.com"
git config user.name "helloword4381"

:: Add files
echo [3/7] Adding all files...
git add .

:: Commit
echo [4/7] Committing...
git commit -m "v1.0 daily-reminder"

:: Rename branch to main
echo [5/7] Renaming branch to main...
git branch -M main

:: Add remote
echo [6/7] Setting remote origin...
git remote add origin https://github.com/helloword4381/daily-reminder.git 2>nul
echo        Remote: https://github.com/helloword4381/daily-reminder.git

:: Push
echo [7/7] Pushing to GitHub...
echo.
echo IMPORTANT: A login window will pop up.
echo Sign in with your browser.
echo.
pause

git push -u origin main

if errorlevel 1 (
    echo.
    echo [ERROR] Push failed.
    echo.
    echo Make sure you created the repo on GitHub first:
    echo   1. Open https://github.com/helloword4381
    echo   2. Click Repositories -^> New
    echo   3. Name: daily-reminder, Private, Create (no options checked)
    echo.
) else (
    echo.
    echo ============================================
    echo   SUCCESS!
    echo
    echo   Now go build APK:
    echo   1. https://github.com/helloword4381/daily-reminder
    echo   2. Click Actions tab
    echo   3. Click "Build Android APK" on the left
    echo   4. Click "Run workflow" -^> green "Run workflow"
    echo   5. Wait 5 min, download APK from Artifacts
    echo ============================================
)

echo.
pause
