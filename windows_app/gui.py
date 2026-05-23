"""
日常提醒工作记录 — Windows 桌面 GUI (CustomTkinter)

主窗口：任务列表 + 添加/编辑/删除 + 状态切换 + 同步状态指示
"""

import tkinter as tk
import threading
from datetime import datetime
from tkinter import messagebox
from typing import Optional
import customtkinter as ctk

from common.models import Task
from windows_app import db

# ── 主题色 ──
ctk.set_appearance_mode("system")
ctk.set_default_color_theme("blue")

COMPLETED_COLOR = "#2e7d32"
PENDING_COLOR = "#e65100"


class WindowsApp(ctk.CTk):
    """Windows 桌面客户端主窗口"""

    def __init__(self, on_sync_callback=None):
        super().__init__()
        self.on_sync_callback = on_sync_callback  # 由 main.py 注入

        # 窗口设置
        self.title("📋 日常提醒工作记录")
        self.geometry("600+700")
        self.minsize(400, 500)

        # 状态
        self.current_date = datetime.now().strftime("%Y-%m-%d")
        self.tasks: list[Task] = []
        self.editing_task_id: Optional[str] = None
        self.sync_status = "未连接"
        self._ui_lock = threading.Lock()

        # 构建 UI
        self._build_header()
        self._build_toolbar()
        self._build_task_lists()
        self._build_status_bar()

        # 加载数据
        self.load_tasks()

        # 退出确认
        self.protocol("WM_DELETE_WINDOW", self._on_close)

    # ── 头部 ──
    def _build_header(self):
        self.header_frame = ctk.CTkFrame(self, height=60, corner_radius=0)
        self.header_frame.pack(fill="x")

        ctk.CTkLabel(
            self.header_frame,
            text="📋 日常提醒工作记录",
            font=ctk.CTkFont(size=20, weight="bold"),
        ).pack(side="left", padx=20, pady=14)

        self.sync_indicator = ctk.CTkLabel(
            self.header_frame,
            text="● 未连接",
            font=ctk.CTkFont(size=12),
            text_color="gray",
        )
        self.sync_indicator.pack(side="right", padx=20, pady=14)

    # ── 工具栏 ──
    def _build_toolbar(self):
        self.toolbar = ctk.CTkFrame(self, height=50, corner_radius=0)
        self.toolbar.pack(fill="x", padx=0, pady=0)

        # 日期选择
        ctk.CTkLabel(self.toolbar, text="📅 日期:", font=ctk.CTkFont(size=13)).pack(side="left", padx=(16, 4), pady=10)
        self.date_var = tk.StringVar(value=self.current_date)
        self.date_entry = ctk.CTkEntry(
            self.toolbar, textvariable=self.date_var, width=120,
            placeholder_text="YYYY-MM-DD"
        )
        self.date_entry.pack(side="left", padx=4, pady=10)
        self.date_entry.bind("<Return>", lambda e: self.load_tasks())

        ctk.CTkButton(
            self.toolbar, text="今天", width=60,
            command=self._go_today,
            font=ctk.CTkFont(size=12),
        ).pack(side="left", padx=4, pady=10)

        ctk.CTkButton(
            self.toolbar, text="← 前一天", width=80,
            command=self._prev_day,
            font=ctk.CTkFont(size=12),
        ).pack(side="left", padx=4, pady=10)

        ctk.CTkButton(
            self.toolbar, text="后一天 →", width=80,
            command=self._next_day,
            font=ctk.CTkFont(size=12),
        ).pack(side="left", padx=4, pady=10)

        # 统计
        self.stat_label = ctk.CTkLabel(
            self.toolbar, text="", font=ctk.CTkFont(size=12)
        )
        self.stat_label.pack(side="right", padx=16, pady=10)

    # ── 任务列表（已完成 / 未完成） ──
    def _build_task_lists(self):
        self.main_frame = ctk.CTkScrollableFrame(self)
        self.main_frame.pack(fill="both", expand=True, padx=8, pady=(4, 0))

        # 添加任务栏
        self.add_frame = ctk.CTkFrame(self.main_frame, height=45)
        self.add_frame.pack(fill="x", padx=8, pady=4)

        self.task_input = ctk.CTkEntry(
            self.add_frame, placeholder_text="添加新工作...",
            font=ctk.CTkFont(size=14),
        )
        self.task_input.pack(side="left", fill="x", expand=True, padx=(4, 4), pady=6)
        self.task_input.bind("<Return>", lambda e: self.add_task())

        ctk.CTkButton(
            self.add_frame, text="➕ 添加", width=80,
            command=self.add_task,
            font=ctk.CTkFont(size=12),
        ).pack(side="right", padx=4, pady=6)

        # "已完成" 分区
        self.completed_header = ctk.CTkLabel(
            self.main_frame, text="✅ 已完成",
            font=ctk.CTkFont(size=15, weight="bold"),
            text_color=COMPLETED_COLOR,
        )
        self.completed_header.pack(anchor="w", padx=12, pady=(12, 2))

        self.completed_frame = ctk.CTkFrame(self.main_frame, fg_color="transparent")
        self.completed_frame.pack(fill="x", padx=4, pady=2)

        # "未完成" 分区
        self.pending_header = ctk.CTkLabel(
            self.main_frame, text="⏳ 未完成",
            font=ctk.CTkFont(size=15, weight="bold"),
            text_color=PENDING_COLOR,
        )
        self.pending_header.pack(anchor="w", padx=12, pady=(16, 2))

        self.pending_frame = ctk.CTkFrame(self.main_frame, fg_color="transparent")
        self.pending_frame.pack(fill="x", padx=4, pady=2)

        # 底部留白
        ctk.CTkLabel(self.main_frame, text="").pack(pady=10)

    # ── 底部状态栏 ──
    def _build_status_bar(self):
        self.status_frame = ctk.CTkFrame(self, height=30, corner_radius=0)
        self.status_frame.pack(fill="x", side="bottom")

        self.status_label = ctk.CTkLabel(
            self.status_frame, text="就绪",
            font=ctk.CTkFont(size=11),
            text_color="gray",
        )
        self.status_label.pack(side="left", padx=12, pady=4)

    # ── 数据加载 ──
    def load_tasks(self):
        """加载指定日期的任务"""
        date_str = self.date_var.get().strip()
        if not date_str:
            return
        try:
            datetime.strptime(date_str, "%Y-%m-%d")
        except ValueError:
            self.set_status("⚠️ 日期格式错误，应为 YYYY-MM-DD")
            return

        self.tasks = db.get_tasks(date_str)
        self._render_tasks()
        self._update_stat()

    def _render_tasks(self):
        """渲染任务列表"""
        # 清空
        for w in self.completed_frame.winfo_children():
            w.destroy()
        for w in self.pending_frame.winfo_children():
            w.destroy()

        completed = [t for t in self.tasks if t.status == "completed"]
        pending = [t for t in self.tasks if t.status == "unfinished"]

        if not completed:
            ctk.CTkLabel(
                self.completed_frame, text="  暂无已完成",
                font=ctk.CTkFont(size=12), text_color="gray"
            ).pack(anchor="w", padx=16, pady=4)

        if not pending:
            ctk.CTkLabel(
                self.pending_frame, text="  暂无未完成",
                font=ctk.CTkFont(size=12), text_color="gray"
            ).pack(anchor="w", padx=16, pady=4)

        for task in completed:
            self._render_task_item(self.completed_frame, task, is_done=True)
        for task in pending:
            self._render_task_item(self.pending_frame, task, is_done=False)

    def _render_task_item(self, parent, task: Task, is_done: bool):
        """渲染一条任务"""
        frame = ctk.CTkFrame(parent, height=40)
        frame.pack(fill="x", padx=4, pady=1)

        bg_color = "#e8f5e9" if is_done else "#fff3e0"
        frame.configure(fg_color=bg_color)

        # 复选框（切换状态）
        check_text = "✅" if is_done else "⬜"
        btn_check = ctk.CTkButton(
            frame, text=check_text, width=32, height=28,
            fg_color="transparent", hover_color="#ddd",
            font=ctk.CTkFont(size=14),
            command=lambda t=task: self._toggle_task(t),
        )
        btn_check.pack(side="left", padx=(4, 0))

        # 任务内容
        content_text = task.content
        if is_done:
            content_text = f"~~{content_text}~~"  # 视觉标记

        lbl = ctk.CTkLabel(
            frame, text=content_text,
            font=ctk.CTkFont(size=13),
            anchor="w",
        )
        lbl.pack(side="left", fill="x", expand=True, padx=6, pady=4)
        # 点击编辑
        lbl.bind("<Button-1>", lambda e, t=task: self._edit_task(t))
        frame.bind("<Button-1>", lambda e, t=task: self._edit_task(t))

        # 时间标签
        time_str = task.updated_at[11:16] if task.updated_at else ""
        ctk.CTkLabel(
            frame, text=time_str,
            font=ctk.CTkFont(size=10), text_color="gray",
        ).pack(side="right", padx=8)

        # 删除按钮
        btn_del = ctk.CTkButton(
            frame, text="✕", width=24, height=24,
            fg_color="transparent", hover_color="#ffcdd2",
            text_color="#c62828", font=ctk.CTkFont(size=12),
            command=lambda t=task: self._delete_task(t),
        )
        btn_del.pack(side="right", padx=(0, 4))

    # ── 操作 ──
    def add_task(self):
        content = self.task_input.get().strip()
        if not content:
            return
        db.create_task(content, self.date_var.get().strip(), device_id="windows")
        self.task_input.delete(0, "end")
        self.load_tasks()
        self.set_status(f"✅ 已添加: {content[:20]}...")

    def _toggle_task(self, task: Task):
        new_status = "unfinished" if task.status == "completed" else "completed"
        db.update_task_status(task.id, new_status)
        self.load_tasks()
        self.set_status(f"🔄 已切换状态: {task.content[:20]}...")

    def _edit_task(self, task: Task):
        """打开编辑对话框"""
        dialog = ctk.CTkInputDialog(
            text="编辑任务内容:",
            title="✏️ 编辑任务",
        )
        dialog.input.delete(0, "end")
        dialog.input.insert(0, task.content)
        self.wait_window(dialog)
        new_content = dialog.input.get().strip()
        if new_content and new_content != task.content:
            db.update_task_content(task.id, new_content)
            self.load_tasks()
            self.set_status(f"✏️ 已更新: {new_content[:20]}...")

    def _delete_task(self, task: Task):
        if messagebox.askyesno("确认删除", f"确定删除「{task.content[:30]}」？"):
            db.delete_task(task.id)
            self.load_tasks()
            self.set_status(f"🗑 已删除: {task.content[:20]}...")

    def _go_today(self):
        self.date_var.set(datetime.now().strftime("%Y-%m-%d"))
        self.load_tasks()

    def _prev_day(self):
        try:
            d = datetime.strptime(self.date_var.get().strip(), "%Y-%m-%d")
            d = d.replace(day=d.day - 1)
            self.date_var.set(d.strftime("%Y-%m-%d"))
            self.load_tasks()
        except ValueError:
            pass

    def _next_day(self):
        try:
            d = datetime.strptime(self.date_var.get().strip(), "%Y-%m-%d")
            d = d.replace(day=d.day + 1)
            self.date_var.set(d.strftime("%Y-%m-%d"))
            self.load_tasks()
        except ValueError:
            pass

    def _update_stat(self):
        summary = db.get_today_summary()
        self.stat_label.configure(
            text=f"✅ {summary['completed']} 项完成  ⏳ {summary['unfinished']} 项待办"
        )

    # ── 同步状态更新（从其他线程调用） ──
    def update_sync_status(self, connected: bool, message: str = ""):
        """由同步引擎线程调用"""
        if connected:
            self.sync_indicator.configure(
                text=f"🟢 已连接 {message}",
                text_color="#2e7d32",
            )
        else:
            self.sync_indicator.configure(
                text="🔴 未连接",
                text_color="#c62828",
            )

    def set_status(self, msg: str):
        self.status_label.configure(text=msg)

    def _on_close(self):
        """退出确认"""
        self.withdraw()
        if messagebox.askokcancel("退出", "确定退出日常提醒工作记录？\n后台同步将停止。"):
            self.quit()
            self.destroy()
        else:
            self.deiconify()
