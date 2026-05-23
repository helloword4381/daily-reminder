"""日常提醒工作记录 — 定时任务调度器

使用 APScheduler 管理早晚定时提醒。
由 main.py 在异步上下文中初始化后传入设置。
"""

import asyncio
import logging
from datetime import date

from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger

import database as db
from services.notifier import remind_morning, remind_noon

logger = logging.getLogger(__name__)

scheduler = BackgroundScheduler()
_jobs: dict[str, any] = {}


# ── 异步提醒逻辑 ──────────────────────────────────────────

async def _morning_job():
    """早上提醒逻辑"""
    today = date.today().isoformat()
    tasks = await db.get_tasks(today)
    unfinished_count = sum(1 for t in tasks if t["status"] == "unfinished")
    remind_morning(unfinished_count)


async def _noon_job():
    """中午提醒逻辑"""
    today = date.today().isoformat()
    tasks = await db.get_tasks(today)
    completed_count = sum(1 for t in tasks if t["status"] == "completed")
    unfinished_count = sum(1 for t in tasks if t["status"] == "unfinished")
    remind_noon(completed_count, unfinished_count)


def _run_async_job(job_coro):
    """
    APScheduler job 的同步入口。
    把异步协程提交到当前运行的事件循环，或创建新循环执行。
    """
    try:
        loop = asyncio.get_running_loop()
        # 已有事件循环 → 创建 Future 并等待
        future = asyncio.run_coroutine_threadsafe(job_coro, loop)
        future.result(timeout=30)
    except RuntimeError:
        # 没有事件循环 → 创建临时循环
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            loop.run_until_complete(job_coro)
        finally:
            loop.close()


# ── 从主程序接收设置并注册 job ────────────────────────────

def configure(settings: dict):
    """
    根据数据库设置配置/刷新定时任务。
    由 main.py 在异步上下文中获取设置后调用。
    """
    morning_time = settings["morning_time"]   # "HH:MM"
    noon_time = settings["noon_time"]
    morning_enabled = settings["morning_enabled"]
    noon_enabled = settings["noon_enabled"]

    m_hour, m_min = morning_time.split(":")
    n_hour, n_min = noon_time.split(":")

    # ── 早上提醒 ──
    if morning_enabled:
        job = scheduler.add_job(
            _run_async_job,
            CronTrigger(hour=int(m_hour), minute=int(m_min), timezone="Asia/Shanghai"),
            args=[_morning_job()],
            id="morning_reminder",
            replace_existing=True,
        )
        _jobs["morning"] = job
        logger.info("☀️ 早上提醒已设定：%s:%s", m_hour, m_min)
    else:
        if "morning" in _jobs:
            _jobs["morning"].remove()
            del _jobs["morning"]
        logger.info("☀️ 早上提醒已关闭")

    # ── 中午提醒 ──
    if noon_enabled:
        job = scheduler.add_job(
            _run_async_job,
            CronTrigger(hour=int(n_hour), minute=int(n_min), timezone="Asia/Shanghai"),
            args=[_noon_job()],
            id="noon_reminder",
            replace_existing=True,
        )
        _jobs["noon"] = job
        logger.info("🌤 中午提醒已设定：%s:%s", n_hour, n_min)
    else:
        if "noon" in _jobs:
            _jobs["noon"].remove()
            del _jobs["noon"]
        logger.info("🌤 中午提醒已关闭")


def start():
    """启动调度器"""
    if scheduler.running:
        logger.warning("调度器已在运行")
        return
    scheduler.start()
    logger.info("提醒调度器已启动")


def stop():
    """停止调度器"""
    if scheduler.running:
        scheduler.shutdown(wait=False)
        logger.info("提醒调度器已停止")


def refresh(settings: dict):
    """刷新定时任务（设置变更后调用）"""
    configure(settings)
    logger.info("提醒时间已刷新")
