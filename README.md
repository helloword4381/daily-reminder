# 日常提醒工作记录 📋

**双客户端架构** — Windows 桌面端 + Android 移动端，局域网自动发现 + 10 秒心跳双向同步。

## 架构

```
┌───────────────────────┐          UDP 发现 (8898)          ┌───────────────────────┐
│   Windows 桌面客户端    │ ◄──────────────────────────────► │   Android 移动客户端   │
│   (CustomTkinter)      │          TCP 同步 (8899)          │   (Flet)              │
│                        │ ◄────── 每 10 秒心跳 ──────────► │                       │
│   ┌─────────────────┐  │                                   │  ┌─────────────────┐  │
│   │  SQLite 本地数据库│  │   双向：按 updated_at 时间戳合并   │  │  SQLite 本地数据库│  │
│   └─────────────────┘  │                                   │  └─────────────────┘  │
│                        │   离线也能独立使用，连上 Wi-Fi 自动同步 │                       │
│   UDP 发现监听         │                                   │  UDP 广播发现          │
│   TCP 同步服务端       │                                   │  TCP 同步客户端        │
└───────────────────────┘                                   └───────────────────────┘
```

## 快速开始

### 1. 安装依赖

```
setup.bat
```

自动创建虚拟环境 + 安装所有依赖（阿里云镜像加速）。

### 2. 启动 Windows 客户端

```
start.bat
```

或手动：

```
.venv\Scripts\python windows_app\main.py
```

启动后窗口显示任务列表，右下角系统托盘可最小化。

### 3. 在手机上运行（推荐：Termux）

不需要编译 APK，用 Termux 在手机上直接跑 Python：

**Step 1** 手机安装 Termux  
从 [F-Droid](https://f-droid.org/packages/com.termux/) 下载安装

**Step 2** 把代码传到手机  
USB 线 / QQ 文件 / 微信文件传输助手 → 传到 Android 的 Download 目录

**Step 3** 在 Termux 中执行：

```bash
# 更新并安装 Python
pkg update -y && pkg install python -y

# 安装 flet
pip install flet

# 进入代码目录（假设传到了 Download）
cd ~/storage/downloads/daily-reminder

# 启动
python android_app/main.py
```

手机屏幕就会显示工作记录界面，自动发现同一 Wi-Fi 的 Windows 电脑。  
手机横屏或竖屏均可正常使用。

### 4. 构建 APK（安装到真机）

#### 方式一：GitHub Actions 自动编译（推荐）

将项目推送到 GitHub，每次推送自动构建 APK：

```bash
git init
git add .
git commit -m "init"
git remote add origin https://github.com/你的用户名/daily-reminder.git
git push -u origin main
```

去 GitHub 仓库 → Actions → Build Android APK → Run workflow → 下载 APK

#### 方式二：本地 Docker 构建

```bash
docker run --rm -it -v %cd%/android_app:/app -v %cd%/common:/common ghcr.io/flet-dev/build-apk:latest
```

> 💡 推荐用 GitHub Actions。本地构建需要 Docker Desktop 或 Android SDK（几十 GB），门槛较高。

### 5. 自动同步

| 条件 | 行为 |
|------|------|
| 手机和电脑在同一 Wi-Fi | 10 秒内自动发现，开始同步 |
| 任意一端离线操作 | 数据存本地，连上后自动合并 |
| 两端同时修改同一条 | 以最后修改的时间戳为准 |
| Wi-Fi 断开 | 标记离线，继续本地使用 |

## 项目结构

```
daily-reminder/
├── common/                    # 🔗 双端共享代码
│   ├── models.py              #   数据模型（UUID 主键）
│   └── sync_protocol.py       #   同步协议（UDP 发现 + TCP 同步）
│
├── windows_app/               # 🪟 Windows 桌面客户端
│   ├── main.py                #   入口
│   ├── db.py                  #   本地 SQLite 数据库
│   ├── gui.py                 #   CustomTkinter GUI
│   └── sync_engine.py         #   UDP 监听 + TCP 同步服务端
│
├── android_app/               # 📱 Android 移动客户端
│   ├── main.py                #   入口（Flet）
│   ├── db.py                  #   本地 SQLite 数据库
│   ├── sync_engine.py         #   UDP 发现 + TCP 同步客户端
│   └── ui/
│       ├── home_page.py       #   主页：今日任务
│       ├── settings_page.py   #   设置页：同步状态
│       └── history_page.py    #   历史页：查看任意日期
│
├── data/                      # 📁 数据库目录（自动创建）
├── requirements.txt           # 📦 依赖清单
├── setup.bat                  # 🛠 安装脚本
├── start.bat                  # ▶️ 启动 Windows 端
├── build_windows.bat          # 📦 打包 Windows .exe
└── build_android.bat          # 📦 构建 Android APK
```

## 同步协议

```
┌─────────────────────────────────────────────────────────┐
│  1. Android 广播 UDP (端口 8898)                         │
│     → "REMINDER_DISCOVERY"                               │
│                                                          │
│  2. Windows 收到广播，回复本机信息                          │
│     → "REMINDER_RESPONSE" + hostname + IP + port         │
│                                                          │
│  3. Android 连接 Windows (TCP 8899)                      │
│     → 发送本机变更（since last sync_token）                │
│     ← 接收 Windows 端变更                                 │
│                                                          │
│  4. 双方按 updated_at 时间戳合并数据                       │
│     → 最新的胜出                                          │
│                                                          │
│  5. 每 10 秒重复步骤 1（心跳检测）                         │
│     → 发现则同步，超时 30 秒标记离线                       │
└─────────────────────────────────────────────────────────┘
```

## 技术栈

| 组件 | 技术 |
|------|------|
| Windows GUI | CustomTkinter (+系统托盘 pystray) |
| Android UI | Flet (Flutter-based Python 框架) |
| 本地数据库 | SQLite (双端结构一致) |
| 同步传输 | TCP + JSON (长度前缀协议) |
| 设备发现 | UDP 广播 (端口 8898) |
| 同步端口 | TCP 8899 |
| 心跳间隔 | 10 秒 |
| 冲突解决 | Last-write-wins (updated_at) |
