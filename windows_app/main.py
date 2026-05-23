"""
日常提醒工作记录 — Windows 桌面客户端入口

启动 GUI + 后台同步引擎 + 系统托盘。
"""

import sys
import os
import logging
from pathlib import Path

# 确保项目根目录在 sys.path 中
sys.path.insert(0, str(Path(__file__).parent.parent.resolve()))

from windows_app import db
from windows_app.gui import WindowsApp
from windows_app.sync_engine import WindowsSyncEngine

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("windows-app")


def main():
    # 初始化本地数据库
    db.init_db()
    logger.info("数据库已初始化")

    # 创建 GUI
    app = WindowsApp()

    # 创建同步引擎，注入 GUI 回调
    sync_engine = WindowsSyncEngine(on_sync_status=lambda connected, msg:
        _handle_sync_status(app, connected, msg))

    # 启动同步引擎
    sync_engine.start()

    # 存储引用以便退出时清理
    app.sync_engine = sync_engine

    # 运行 GUI 主循环
    try:
        app.mainloop()
    finally:
        sync_engine.stop()
        logger.info("应用已退出")


def _handle_sync_status(app: WindowsApp, connected: bool, message: str):
    """同步引擎回调 — 用 after() 安全更新 GUI"""
    try:
        if message == "__REFRESH__":
            # 数据已变更，刷新任务列表
            app.after(0, app.load_tasks)
            app.after(0, lambda: app.set_status("🔄 已从 Android 同步数据"))
        else:
            app.after(0, lambda: app.update_sync_status(connected, message))
    except Exception as e:
        logger.warning("更新 GUI 状态异常: %s", e)


if __name__ == "__main__":
    main()
