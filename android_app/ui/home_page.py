"""
日常提醒工作记录 — Android 主页（Flet UI）

任务列表、添加、切换状态、编辑、删除
"""

import flet as ft
from datetime import datetime

from common.models import Task
from android_app import db


class HomePage:
    """主页：今日任务列表"""

    def __init__(self, page: ft.Page, on_edit_task=None):
        self.page = page
        self.on_edit_task = on_edit_task   # 回调：编辑任务
        self.current_date = datetime.now().strftime("%Y-%m-%d")
        self.tasks: list[Task] = []
        self._build()

    def _build(self):
        # ── 日期选择行 ──
        self.date_row = ft.Row(
            [
                ft.IconButton(ft.icons.CHEVRON_LEFT, on_click=self._prev_day),
                ft.Text(self.current_date, size=16, weight=ft.FontWeight.BOLD, text_align=ft.TextAlign.CENTER),
                ft.IconButton(ft.icons.CHEVRON_RIGHT, on_click=self._next_day),
                ft.Container(expand=True),
                ft.TextButton("今天", on_click=self._go_today),
            ],
            alignment=ft.MainAxisAlignment.CENTER,
        )

        # ── 统计行 ──
        self.stat_text = ft.Text("", size=13, color=ft.Colors.GREY_600)

        # ── 添加任务 ──
        self.task_input = ft.TextField(
            hint_text="添加新工作...",
            expand=True,
            height=48,
            text_size=14,
            on_submit=self._add_task,
        )
        self.add_btn = ft.FloatingActionButton(
            icon=ft.icons.ADD,
            on_click=self._add_task,
            height=40,
            width=40,
        )

        # ── 任务列表（已完成 / 未完成） ──
        self.completed_header = ft.Text("✅ 已完成", size=15, weight=ft.FontWeight.BOLD,
                                        color=ft.Colors.GREEN_700)
        self.completed_list = ft.Column(spacing=4, scroll=ft.ScrollMode.AUTO)
        self.pending_header = ft.Text("⏳ 未完成", size=15, weight=ft.FontWeight.BOLD,
                                      color=ft.Colors.ORANGE_900)
        self.pending_list = ft.Column(spacing=4, scroll=ft.ScrollMode.AUTO)

        # ── 整体布局 ──
        self.view = ft.Container(
            content=ft.Column(
                [
                    self.date_row,
                    self.stat_text,
                    ft.Divider(height=4, thickness=0),
                    ft.Row([self.task_input, self.add_btn], spacing=8, vertical_alignment=ft.CrossAxisAlignment.CENTER),
                    ft.Divider(height=4, thickness=0),
                    self.completed_header,
                    self.completed_list,
                    ft.Divider(height=8, thickness=0),
                    self.pending_header,
                    self.pending_list,
                ],
                spacing=6,
                scroll=ft.ScrollMode.AUTO,
            ),
            padding=12,
            expand=True,
        )

    # ── 加载数据 ──

    def load(self):
        """从数据库加载并刷新 UI"""
        self.tasks = db.get_tasks(self.current_date)
        self._refresh()

    def _refresh(self):
        completed = [t for t in self.tasks if t.status == "completed"]
        pending = [t for t in self.tasks if t.status == "unfinished"]

        # 更新统计
        summary = db.get_today_summary()
        self.stat_text.value = f"✅ {summary['completed']}   ⏳ {summary['unfinished']}   📋 共 {summary['total']} 项"
        self.stat_text.update()

        # 刷新列表
        self.completed_list.controls.clear()
        if completed:
            for t in completed:
                self.completed_list.controls.append(self._make_task_row(t, done=True))
        else:
            self.completed_list.controls.append(ft.Text("  暂无已完成", size=12, color=ft.Colors.GREY_400))

        self.pending_list.controls.clear()
        if pending:
            for t in pending:
                self.pending_list.controls.append(self._make_task_row(t, done=False))
        else:
            self.pending_list.controls.append(ft.Text("  暂无未完成", size=12, color=ft.Colors.GREY_400))

        self.completed_list.update()
        self.pending_list.update()

    def _make_task_row(self, task: Task, done: bool) -> ft.Container:
        """创建一条任务的 UI 行"""
        check_icon = ft.icons.CHECK_CIRCLE if done else ft.icons.RADIO_BUTTON_UNCHECKED
        check_color = ft.Colors.GREEN if done else ft.Colors.GREY_400

        content_style = ft.TextStyle(
            size=14,
            decoration=ft.TextDecoration.LINE_THROUGH if done else None,
            color=ft.Colors.GREY_400 if done else ft.Colors.BLACK,
        )

        time_str = task.updated_at[11:16] if task.updated_at else ""

        row = ft.Container(
            content=ft.Row(
                [
                    ft.IconButton(
                        icon=check_icon,
                        icon_color=check_color,
                        icon_size=22,
                        on_click=lambda _, t=task: self._toggle(t),
                    ),
                    ft.Column(
                        [
                            ft.Text(task.content, style=content_style, expand=True),
                            ft.Text(time_str, size=10, color=ft.Colors.GREY_400),
                        ],
                        spacing=0,
                        expand=True,
                        on_click=lambda _, t=task: self._edit(t),
                    ),
                    ft.IconButton(
                        icon=ft.icons.DELETE_OUTLINE,
                        icon_color=ft.Colors.RED_300,
                        icon_size=18,
                        on_click=lambda _, t=task: self._delete(t),
                    ),
                ],
                spacing=4,
                vertical_alignment=ft.CrossAxisAlignment.CENTER,
            ),
            padding=ft.padding.only(left=4, right=4, top=2, bottom=2),
        )
        return row

    # ── 操作 ──

    def _add_task(self, e=None):
        content = self.task_input.value.strip()
        if not content:
            return
        db.create_task(content, self.current_date)
        self.task_input.value = ""
        self.task_input.update()
        self.load()

    def _toggle(self, task: Task):
        new_status = "unfinished" if task.status == "completed" else "completed"
        db.update_task_status(task.id, new_status)
        self.load()

    def _edit(self, task: Task):
        """弹出编辑对话框"""
        if self.on_edit_task:
            self.on_edit_task(task)

    def _delete(self, task: Task):
        db.delete_task(task.id)
        self.load()

    def _go_today(self, e=None):
        self.current_date = datetime.now().strftime("%Y-%m-%d")
        self.date_row.controls[1] = ft.Text(self.current_date, size=16, weight=ft.FontWeight.BOLD)
        self.date_row.update()
        self.load()

    def _prev_day(self, e=None):
        d = datetime.strptime(self.current_date, "%Y-%m-%d")
        d = d.replace(day=d.day - 1)
        self.current_date = d.strftime("%Y-%m-%d")
        self.date_row.controls[1] = ft.Text(self.current_date, size=16, weight=ft.FontWeight.BOLD)
        self.date_row.update()
        self.load()

    def _next_day(self, e=None):
        d = datetime.strptime(self.current_date, "%Y-%m-%d")
        d = d.replace(day=d.day + 1)
        self.current_date = d.strftime("%Y-%m-%d")
        self.date_row.controls[1] = ft.Text(self.current_date, size=16, weight=ft.FontWeight.BOLD)
        self.date_row.update()
        self.load()
