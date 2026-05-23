"""日常提醒工作记录 — 数据库模型与操作"""

import asyncio
from datetime import date, datetime
from typing import Optional

from sqlalchemy import (
    Column, Integer, String, Text, Boolean, Date, DateTime, Float,
    create_engine, func, select, delete as sa_delete, and_
)
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, Session
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker

from config import DB_PATH


# ── 异步引擎 ──────────────────────────────────────────────
async_engine = create_async_engine(
    f"sqlite+aiosqlite:///{DB_PATH}",
    echo=False,
    connect_args={"check_same_thread": False},
)
AsyncSessionLocal = async_sessionmaker(async_engine, expire_on_commit=False)


# ── 同步引擎（给 APScheduler 用，它不跑在 async 上下文） ──
sync_engine = create_engine(
    f"sqlite:///{DB_PATH}",
    echo=False,
    connect_args={"check_same_thread": False},
)
SyncSession = Session(sync_engine)


# ── 基类 ──────────────────────────────────────────────────
class Base(DeclarativeBase):
    pass


# ── 工作表 ────────────────────────────────────────────────
class Task(Base):
    """一条工作记录"""
    __tablename__ = "tasks"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="unfinished")  # completed / unfinished
    task_date: Mapped[str] = mapped_column(String(10), nullable=False)     # YYYY-MM-DD
    sort_order: Mapped[int] = mapped_column(Integer, default=0)           # 排序
    created_at: Mapped[str] = mapped_column(
        String(19), default=lambda: datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    )
    updated_at: Mapped[str] = mapped_column(
        String(19), default=lambda: datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        onupdate=lambda: datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    )

    def to_dict(self):
        return {
            "id": self.id,
            "content": self.content,
            "status": self.status,
            "task_date": self.task_date,
            "sort_order": self.sort_order,
            "created_at": self.created_at,
            "updated_at": self.updated_at,
        }


# ── 提醒设置表 ────────────────────────────────────────────
class ReminderSetting(Base):
    """提醒时间设置"""
    __tablename__ = "reminder_settings"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    morning_time: Mapped[str] = mapped_column(String(5), default="09:00")   # HH:MM
    noon_time: Mapped[str] = mapped_column(String(5), default="12:30")      # HH:MM
    morning_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    noon_enabled: Mapped[bool] = mapped_column(Boolean, default=True)

    def to_dict(self):
        return {
            "id": self.id,
            "morning_time": self.morning_time,
            "noon_time": self.noon_time,
            "morning_enabled": self.morning_enabled,
            "noon_enabled": self.noon_enabled,
        }


# ── 数据库初始化 ──────────────────────────────────────────
def init_db():
    """同步初始化（建表 + 插入默认设置）"""
    Base.metadata.create_all(sync_engine)
    # 检查是否有提醒设置，没有则创建默认
    with SyncSession as session:
        existing = session.execute(select(ReminderSetting)).scalar_one_or_none()
        if existing is None:
            session.add(ReminderSetting())
            session.commit()


async def async_init_db():
    """异步初始化"""
    async with async_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    # 检查默认设置
    async with AsyncSessionLocal() as session:
        existing = await session.execute(select(ReminderSetting))
        if existing.scalar_one_or_none() is None:
            session.add(ReminderSetting())
            await session.commit()


# ── 任务 CRUD ─────────────────────────────────────────────
async def get_tasks(task_date: str) -> list[dict]:
    """获取某天的所有任务"""
    async with AsyncSessionLocal() as session:
        stmt = (
            select(Task)
            .where(Task.task_date == task_date)
            .order_by(Task.sort_order, Task.id)
        )
        result = await session.execute(stmt)
        tasks = result.scalars().all()
        return [t.to_dict() for t in tasks]


async def get_task_by_id(task_id: int) -> Optional[dict]:
    """根据 ID 获取单个任务"""
    async with AsyncSessionLocal() as session:
        stmt = select(Task).where(Task.id == task_id)
        result = await session.execute(stmt)
        task = result.scalar_one_or_none()
        return task.to_dict() if task else None


async def create_task(content: str, task_date: str, status: str = "unfinished") -> dict:
    """创建任务"""
    async with AsyncSessionLocal() as session:
        # 获取当前最大排序值
        stmt = (
            select(func.coalesce(func.max(Task.sort_order), -1))
            .where(Task.task_date == task_date)
        )
        result = await session.execute(stmt)
        max_order = result.scalar() or -1

        task = Task(
            content=content,
            status=status,
            task_date=task_date,
            sort_order=max_order + 1,
        )
        session.add(task)
        await session.commit()
        await session.refresh(task)
        return task.to_dict()


async def update_task_status(task_id: int, status: str) -> Optional[dict]:
    """更新任务状态"""
    async with AsyncSessionLocal() as session:
        stmt = select(Task).where(Task.id == task_id)
        result = await session.execute(stmt)
        task = result.scalar_one_or_none()
        if not task:
            return None
        task.status = status
        task.updated_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        await session.commit()
        await session.refresh(task)
        return task.to_dict()


async def update_task_content(task_id: int, content: str) -> Optional[dict]:
    """更新任务内容"""
    async with AsyncSessionLocal() as session:
        stmt = select(Task).where(Task.id == task_id)
        result = await session.execute(stmt)
        task = result.scalar_one_or_none()
        if not task:
            return None
        task.content = content
        task.updated_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        await session.commit()
        await session.refresh(task)
        return task.to_dict()


async def delete_task(task_id: int) -> bool:
    """删除任务"""
    async with AsyncSessionLocal() as session:
        stmt = select(Task).where(Task.id == task_id)
        result = await session.execute(stmt)
        task = result.scalar_one_or_none()
        if not task:
            return False
        await session.delete(task)
        await session.commit()
        return True


async def get_or_create_settings() -> dict:
    """获取提醒设置，不存在则创建"""
    async with AsyncSessionLocal() as session:
        stmt = select(ReminderSetting)
        result = await session.execute(stmt)
        setting = result.scalar_one_or_none()
        if setting is None:
            setting = ReminderSetting()
            session.add(setting)
            await session.commit()
            await session.refresh(setting)
        return setting.to_dict()


async def update_settings(
    morning_time: Optional[str] = None,
    noon_time: Optional[str] = None,
    morning_enabled: Optional[bool] = None,
    noon_enabled: Optional[bool] = None,
) -> dict:
    """更新提醒设置"""
    async with AsyncSessionLocal() as session:
        stmt = select(ReminderSetting)
        result = await session.execute(stmt)
        setting = result.scalar_one_or_none()
        if setting is None:
            setting = ReminderSetting()
            session.add(setting)
        if morning_time is not None:
            setting.morning_time = morning_time
        if noon_time is not None:
            setting.noon_time = noon_time
        if morning_enabled is not None:
            setting.morning_enabled = morning_enabled
        if noon_enabled is not None:
            setting.noon_enabled = noon_enabled
        await session.commit()
        await session.refresh(setting)
        return setting.to_dict()


async def get_recent_dates(limit: int = 7) -> list[str]:
    """获取最近的日期列表（有任务记录的）"""
    async with AsyncSessionLocal() as session:
        stmt = (
            select(Task.task_date)
            .distinct()
            .order_by(Task.task_date.desc())
            .limit(limit)
        )
        result = await session.execute(stmt)
        return [row[0] for row in result.all()]


async def get_today_summary() -> dict:
    """获取今日统计摘要"""
    today = date.today().isoformat()
    async with AsyncSessionLocal() as session:
        stmt = select(Task).where(Task.task_date == today)
        result = await session.execute(stmt)
        tasks = result.scalars().all()
        completed = sum(1 for t in tasks if t.status == "completed")
        unfinished = sum(1 for t in tasks if t.status == "unfinished")
        return {
            "date": today,
            "total": len(tasks),
            "completed": completed,
            "unfinished": unfinished,
        }
