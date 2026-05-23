"""日常提醒工作记录 — 提醒设置 API"""

from fastapi import APIRouter

from models import SettingsUpdate
import database as db
from services import scheduler

router = APIRouter(prefix="/api/reminders", tags=["reminders"])


@router.get("/settings")
async def get_settings():
    """获取当前提醒设置"""
    settings = await db.get_or_create_settings()
    return settings


@router.put("/settings")
async def update_settings(body: SettingsUpdate):
    """更新提醒设置"""
    settings = await db.update_settings(
        morning_time=body.morning_time,
        noon_time=body.noon_time,
        morning_enabled=body.morning_enabled,
        noon_enabled=body.noon_enabled,
    )
    # 刷新调度器
    scheduler.refresh(settings)
    return {"message": "设置已更新", "settings": settings}
