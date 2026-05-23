"""日常提醒工作记录 — 配置文件"""

import os
import sys
from pathlib import Path

# 项目根目录
BASE_DIR = Path(__file__).parent.resolve()

# 数据库路径
DB_DIR = BASE_DIR / "data"
DB_PATH = DB_DIR / "daily_reminder.db"

# 默认提醒时间（24小时制）
DEFAULT_MORNING_TIME = "09:00"   # 早上提醒：记录昨日未完成 + 今日计划
DEFAULT_NOON_TIME = "12:30"      # 中午提醒：记录上午已完成和未完成

# 服务器配置
HOST = os.getenv("REMINDER_HOST", "0.0.0.0")
PORT = int(os.getenv("REMINDER_PORT", "8899"))

# 确保数据目录存在
DB_DIR.mkdir(parents=True, exist_ok=True)
