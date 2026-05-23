"""
日常提醒工作记录 — Windows 本地数据库

使用 SQLite 存储任务，UUID 主键支持同步。
"""

import sqlite3
import threading
from datetime import datetime
from pathlib import Path
from typing import Optional

from common.models import Task, now_iso

DB_PATH = Path(__file__).parent.parent / "data" / "windows_tasks.db"


def get_conn() -> sqlite3.Connection:
    """获取线程安全的数据库连接"""
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(DB_PATH), check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn


# ── 初始化 ────────────────────────────────────────────────

def init_db():
    conn = get_conn()
    conn.executescript("""
        CREATE TABLE IF NOT EXISTS tasks (
            id          TEXT PRIMARY KEY,
            content     TEXT NOT NULL,
            status      TEXT NOT NULL DEFAULT 'unfinished',
            task_date   TEXT NOT NULL,
            sort_order  INTEGER NOT NULL DEFAULT 0,
            created_at  TEXT NOT NULL,
            updated_at  TEXT NOT NULL,
            device_id   TEXT NOT NULL DEFAULT '',
            deleted     INTEGER NOT NULL DEFAULT 0
        );
        CREATE INDEX IF NOT EXISTS idx_tasks_date ON tasks(task_date);
        CREATE INDEX IF NOT EXISTS idx_tasks_updated ON tasks(updated_at);
    """)
    conn.commit()
    conn.close()


# ── 任务 CRUD ─────────────────────────────────────────────

def _row_to_task(row) -> Task:
    return Task(
        id=row["id"],
        content=row["content"],
        status=row["status"],
        task_date=row["task_date"],
        sort_order=row["sort_order"],
        created_at=row["created_at"],
        updated_at=row["updated_at"],
        device_id=row["device_id"],
        deleted=bool(row["deleted"]),
    )


def get_tasks(task_date: str = None) -> list[Task]:
    """获取任务列表"""
    conn = get_conn()
    if task_date:
        rows = conn.execute(
            "SELECT * FROM tasks WHERE task_date = ? AND deleted = 0 ORDER BY sort_order",
            (task_date,)
        ).fetchall()
    else:
        rows = conn.execute(
            "SELECT * FROM tasks WHERE deleted = 0 ORDER BY task_date DESC, sort_order"
        ).fetchall()
    conn.close()
    return [_row_to_task(r) for r in rows]


def get_task_by_id(task_id: str) -> Optional[Task]:
    conn = get_conn()
    row = conn.execute("SELECT * FROM tasks WHERE id = ?", (task_id,)).fetchone()
    conn.close()
    return _row_to_task(row) if row else None


def create_task(content: str, task_date: str = None, device_id: str = "") -> Task:
    """创建新任务"""
    if task_date is None:
        task_date = datetime.now().strftime("%Y-%m-%d")
    task = Task(
        content=content,
        task_date=task_date,
        device_id=device_id or "windows-local",
    )
    conn = get_conn()
    conn.execute(
        """INSERT INTO tasks (id, content, status, task_date, sort_order, created_at, updated_at, device_id, deleted)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)""",
        (task.id, task.content, task.status, task.task_date, task.sort_order,
         task.created_at, task.updated_at, task.device_id)
    )
    conn.commit()
    conn.close()
    return task


def update_task_status(task_id: str, status: str) -> Optional[Task]:
    conn = get_conn()
    now = now_iso()
    conn.execute(
        "UPDATE tasks SET status = ?, updated_at = ? WHERE id = ?",
        (status, now, task_id)
    )
    conn.commit()
    conn.close()
    return get_task_by_id(task_id)


def update_task_content(task_id: str, content: str) -> Optional[Task]:
    conn = get_conn()
    now = now_iso()
    conn.execute(
        "UPDATE tasks SET content = ?, updated_at = ? WHERE id = ?",
        (content, now, task_id)
    )
    conn.commit()
    conn.close()
    return get_task_by_id(task_id)


def delete_task(task_id: str) -> bool:
    """软删除"""
    conn = get_conn()
    now = now_iso()
    conn.execute(
        "UPDATE tasks SET deleted = 1, updated_at = ? WHERE id = ?",
        (now, task_id)
    )
    conn.commit()
    conn.close()
    return True


def get_today_summary() -> dict:
    today = datetime.now().strftime("%Y-%m-%d")
    conn = get_conn()
    row = conn.execute(
        """SELECT
            COUNT(*) as total,
            SUM(CASE WHEN status='completed' THEN 1 ELSE 0 END) as done,
            SUM(CASE WHEN status='unfinished' THEN 1 ELSE 0 END) as pending
           FROM tasks WHERE task_date = ? AND deleted = 0""",
        (today,)
    ).fetchone()
    conn.close()
    return {
        "date": today,
        "total": row["total"] or 0,
        "completed": row["done"] or 0,
        "unfinished": row["pending"] or 0,
    }


# ── 同步用接口 ────────────────────────────────────────────

def get_changes_since(since_token: str) -> tuple[list[dict], list[str], str]:
    """
    获取自 since_token 以来的变更
    返回 (changes_dict_list, deleted_id_list, new_token)
    """
    conn = get_conn()
    rows = conn.execute(
        "SELECT * FROM tasks WHERE updated_at > ? ORDER BY updated_at",
        (since_token,)
    ).fetchall()
    conn.close()

    changes = []
    deleted_ids = []
    for r in rows:
        if r["deleted"]:
            deleted_ids.append(r["id"])
        else:
            changes.append(_row_to_task(r).to_dict())

    new_token = now_iso()
    return changes, deleted_ids, new_token


def apply_remote_changes(changes: list[dict], deleted_ids: list[str],
                         source_device_id: str):
    """
    应用远程同步过来的变更
    冲突解决：以 updated_at 较新的为准
    """
    conn = get_conn()
    now = now_iso()

    for task_dict in changes:
        remote = Task.from_dict(task_dict)
        existing = conn.execute(
            "SELECT * FROM tasks WHERE id = ?", (remote.id,)
        ).fetchone()

        if existing:
            # 冲突解决：时间戳较新的胜出
            if remote.updated_at > existing["updated_at"]:
                conn.execute(
                    """UPDATE tasks SET content=?, status=?, task_date=?, sort_order=?,
                       updated_at=?, device_id=? WHERE id=?""",
                    (remote.content, remote.status, remote.task_date, remote.sort_order,
                     remote.updated_at, remote.device_id, remote.id)
                )
        else:
            conn.execute(
                """INSERT INTO tasks (id, content, status, task_date, sort_order, created_at, updated_at, device_id, deleted)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)""",
                (remote.id, remote.content, remote.status, remote.task_date,
                 remote.sort_order, remote.created_at, remote.updated_at,
                 remote.device_id)
            )

    # 远程删除
    for tid in deleted_ids:
        conn.execute(
            "UPDATE tasks SET deleted = 1, updated_at = ? WHERE id = ?",
            (now, tid)
        )

    conn.commit()
    conn.close()


def get_recent_dates(limit: int = 14) -> list[str]:
    conn = get_conn()
    rows = conn.execute(
        "SELECT DISTINCT task_date FROM tasks WHERE deleted = 0 ORDER BY task_date DESC LIMIT ?",
        (limit,)
    ).fetchall()
    conn.close()
    return [r["task_date"] for r in rows]
