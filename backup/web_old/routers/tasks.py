"""日常提醒工作记录 — 任务 CRUD API"""

from datetime import date
from fastapi import APIRouter, HTTPException

from models import TaskCreate, TaskUpdateStatus, TaskUpdateContent
import database as db

router = APIRouter(prefix="/api/tasks", tags=["tasks"])


@router.get("/today")
async def get_today():
    """获取今日任务列表"""
    today = date.today().isoformat()
    tasks = await db.get_tasks(today)
    return {"date": today, "tasks": tasks, "count": len(tasks)}


@router.get("/{task_date}")
async def get_by_date(task_date: str):
    """获取指定日期的任务"""
    tasks = await db.get_tasks(task_date)
    return {"date": task_date, "tasks": tasks, "count": len(tasks)}


@router.post("/")
async def create(body: TaskCreate):
    """创建新任务"""
    task = await db.create_task(
        content=body.content,
        task_date=body.task_date,
        status=body.status,
    )
    return {"message": "任务已创建", "task": task}


@router.put("/{task_id}/status")
async def update_status(task_id: int, body: TaskUpdateStatus):
    """更新任务状态（完成/未完成）"""
    task = await db.update_task_status(task_id, body.status)
    if task is None:
        raise HTTPException(status_code=404, detail="任务不存在")
    return {"message": "状态已更新", "task": task}


@router.put("/{task_id}/content")
async def update_content(task_id: int, body: TaskUpdateContent):
    """更新任务内容"""
    task = await db.update_task_content(task_id, body.content)
    if task is None:
        raise HTTPException(status_code=404, detail="任务不存在")
    return {"message": "内容已更新", "task": task}


@router.delete("/{task_id}")
async def delete(task_id: int):
    """删除任务"""
    ok = await db.delete_task(task_id)
    if not ok:
        raise HTTPException(status_code=404, detail="任务不存在")
    return {"message": "任务已删除"}


@router.get("/summary/today")
async def today_summary():
    """获取今日统计摘要"""
    summary = await db.get_today_summary()
    return summary


@router.get("/dates/recent")
async def recent_dates(limit: int = 7):
    """获取最近有记录的天数"""
    dates = await db.get_recent_dates(limit)
    return {"dates": dates}
