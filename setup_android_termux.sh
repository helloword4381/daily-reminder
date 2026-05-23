#!/bin/bash
# 📱 日常提醒工作记录 - Android 一键安装脚本 (Termux)
# 
# 使用方法：
# 1. 在手机安装 Termux (从 F-Droid 下载: https://f-droid.org/packages/com.termux/)
# 2. 安装好后打开 Termux，执行：
#    curl -O https://你的服务器/setup_android.sh && bash setup_android.sh
# 3. 等待安装完成，自动启动

set -e

echo "============================================"
echo "  日常提醒工作记录 - Android 安装脚本"
echo "============================================"
echo

# 更新包管理器
echo "[1/6] 更新 Termux 包管理器..."
pkg update -y && pkg upgrade -y

# 安装 Python
echo "[2/6] 安装 Python + 依赖工具..."
pkg install -y python python-pip git openssl

# 安装 Flet 需要的依赖
echo "[3/6] 安装 Flet 系统依赖..."
pkg install -y libjpeg-turbo libpng libwebp libxml2 libxslt

# 克隆代码
echo "[4/6] 下载代码..."
cd ~
if [ -d "daily-reminder" ]; then
    echo "  已存在，更新代码..."
    cd daily-reminder && git pull
else
    # 如果没 git 仓库，手动创建目录结构
    echo "  请在电脑上把 android_app/ 和 common/ 复制到手机"
    echo "  或者使用文件传输工具传过来"
    echo
    echo "  传入后放在 ~/daily-reminder/ 目录"
    mkdir -p ~/daily-reminder
fi

# 安装 Python 依赖
echo "[5/6] 安装 Python 依赖..."
cd ~/daily-reminder
pip install --upgrade pip
pip install flet sqlite3

# 启动
echo "[6/6] 启动 Android 客户端..."
echo
echo "============================================"
echo "  安装完成！"
echo "  正在启动 Flet Android 客户端..."
echo "  - 手机端会显示工作记录界面"
echo "  - 确保手机和电脑在同一 Wi-Fi"
echo "  - Windows 端需先运行 start.bat"
echo "============================================"
echo

# 启动 Flet 客户端
export FLET_ANDROID=1
python android_app/main.py
