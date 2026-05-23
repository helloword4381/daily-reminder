"""日常提醒工作记录 — Pydantic 数据模型（API 请求/响应校验）"""

from pydantic import BaseModel, Field
from typing import Optional


class TaskCreate(BaseModel):
    content: str = Field(..., min_length=1, max_length=500, description="任务内容")
    task_date: str = Field(..., pattern=r"^\d{4}-\d{2}-\d{2}$", description="日期 YYYY-MM-DD")
    status: str = Field(default="unfinished", pattern=r"^(completed|unfinished)$")


class TaskUpdateStatus(BaseModel):
    status: str = Field(..., pattern=r"^(completed|unfinished)$")


class TaskUpdateContent(BaseModel):
    content: str = Field(..., min_length=1, max_length=500)


class SettingsUpdate(BaseModel):
    morning_time: Optional[str] = Field(None, pattern=r"^\d{2}:\d{2}$")
    noon_time: Optional[str] = Field(None, pattern=r"^\d{2}:\d{2}$")
    morning_enabled: Optional[bool] = None
    noon_enabled: Optional[bool] = None
