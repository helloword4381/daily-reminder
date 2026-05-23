"""
日常提醒工作记录 — 双端共享数据模型

所有同步相关的数据结构，Windows 和 Android 共用。
任务使用 UUID 确保全局唯一。
"""

import uuid
from datetime import datetime
from dataclasses import dataclass, field, asdict
from typing import Optional


def new_uuid() -> str:
    return str(uuid.uuid4())

def now_iso() -> str:
    return datetime.now().strftime("%Y-%m-%dT%H:%M:%S")


@dataclass
class Task:
    """一条工作记录（同步版本，UUID 主键）"""
    id: str = field(default_factory=new_uuid)
    content: str = ""
    status: str = "unfinished"          # completed / unfinished
    task_date: str = ""                 # YYYY-MM-DD
    sort_order: int = 0
    created_at: str = field(default_factory=now_iso)
    updated_at: str = field(default_factory=now_iso)
    device_id: str = ""                 # 创建/最后修改的设备 ID
    deleted: bool = False               # 软删除标记

    def to_dict(self) -> dict:
        return asdict(self)

    @classmethod
    def from_dict(cls, d: dict) -> "Task":
        return cls(**{k: v for k, v in d.items() if k in cls.__dataclass_fields__})

    @classmethod
    def from_old_format(cls, d: dict, device_id: str) -> "Task":
        """从旧版 web 格式转换"""
        return cls(
            id=new_uuid(),
            content=d.get("content", ""),
            status=d.get("status", "unfinished"),
            task_date=d.get("task_date", ""),
            sort_order=d.get("sort_order", 0),
            created_at=d.get("created_at", "").replace(" ", "T"),
            updated_at=d.get("updated_at", "").replace(" ", "T"),
            device_id=device_id,
        )


@dataclass
class DeviceInfo:
    """设备信息"""
    device_id: str
    device_name: str          # 主机名/设备名
    device_type: str          # "windows" / "android"
    version: str = "1.0"


@dataclass
class SyncMessage:
    """
    同步报文
    type: "sync_request" | "sync_response" | "sync_ack"
    """
    type: str
    device: DeviceInfo
    since_token: str = ""                # 上次同步的时间戳
    changes: list[dict] = field(default_factory=list)  # Task dict 列表
    deleted_ids: list[str] = field(default_factory=list)
    sync_token: str = field(default_factory=now_iso)


# ── 冲突解决 ──────────────────────────────────────────────

def resolve_conflict(local: Task, remote: Task, local_wins: bool = False) -> Task:
    """
    冲突解决：按 updated_at 决定胜出方。
    如果 local_wins=True，本地优先（在本地编辑但还没同步时）。
    """
    if local_wins or local.updated_at >= remote.updated_at:
        return local
    return remote
