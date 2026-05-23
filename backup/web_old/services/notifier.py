"""日常提醒工作记录 — 桌面通知服务

Windows 下使用 plyer 发送系统通知。
Android 端通过浏览器 Notification API 推送（前端实现）。
"""

import sys
import threading
import logging

logger = logging.getLogger(__name__)

# 尝试导入 plyer（桌面通知）
try:
    from plyer import notification as plyer_notification
    HAS_PLYER = True
except Exception as e:
    HAS_PLYER = False
    logger.warning("plyer 不可用，桌面通知将降级：%s", e)


def _send(title: str, message: str):
    """发送桌面通知（在后台线程执行，不阻塞主流程）"""
    def _notify():
        if not HAS_PLYER:
            logger.info("[通知] %s — %s", title, message)
            return
        try:
            plyer_notification.notify(
                title=title,
                message=message,
                app_name="日常提醒",
                timeout=10,
            )
        except Exception as e:
            logger.error("发送通知失败: %s", e)

    threading.Thread(target=_notify, daemon=True).start()


def remind_morning(unfinished_count: int):
    """早上提醒：回顾昨日未完成 + 规划今日"""
    if unfinished_count > 0:
        msg = f"昨日还有 {unfinished_count} 项未完成，记得跟进！"
    else:
        msg = "昨日所有工作已完成，今天继续加油！"
    _send("☀️ 早上好 — 工作回顾", msg)


def remind_noon(completed_count: int, unfinished_count: int):
    """中午提醒：记录上午已完成/未完成"""
    msg = f"已完成 {completed_count} 项，还有 {unfinished_count} 项未完成"
    _send("🌤 中午提醒 — 工作进度", msg)
