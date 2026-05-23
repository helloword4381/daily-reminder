"""
日常提醒工作记录 — Android 历史记录页（Flet UI）

查看任意一天的工作记录
"""

import flet as ft
from datetime import datetime

from android_app import db


class HistoryPage:
    """历史记录页面"""

    def __init__(self, page: ft.Page):
        self.page = page
        self.current_view_date = datetime.now().strftime("%Y-%m-%d")
        self._build()

    def _build(self):
        # ── 日期选择 ──
        self.date_picker = ft.DatePicker(
            on_change=self._on_date_selected,
            first_date=datetime(2024, 1, 1),
            last_date=datetime.now(),
        )
        self.date_btn = ft.ElevatedButton(
            text=f"📅 {self.current_view_date}",
            icon=ft.icons.CALENDAR_MONTH,
            on_click=lambda _: self.date_picker.pick_date(),
        )

        # ── 最近日期快速选择 ──
        self.chips_row = ft.Row(wrap=True, spacing=6)

        # ── 任务列表 ──
        self.task_list = ft.Column(spacing=4)
        self.empty_text = ft.Text("选择日期查看工作记录", size=14, color=ft.Colors.GREY_400)

        self.view = ft.Container(
            content=ft.Column(
                [
                    self.date_btn,
                    ft.Text("最近记录:", size=12, color=ft.Colors.GREY_600),
                    self.chips_row,
                    ft.Divider(),
                    self.task_list,
                    self.empty_text,
                ],
                spacing=8,
                scroll=ft.ScrollMode.AUTO,
            ),
            padding=16,
            expand=True,
        )

    def load(self):
        """加载最近日期和当前查看日期的任务"""
        self._load_date_chips()
        self._load_tasks_for_date()

    def _load_date_chips(self):
        dates = db.get_recent_dates(14)
        self.chips_row.controls.clear()
        if dates:
            for d in dates:
                label = d
                if d == datetime.now().strftime("%Y-%m-%d"):
                    label = "今天"
                elif d == datetime.now().replace(day=datetime.now().day - 1).strftime("%Y-%m-%d"):
                    label = "昨天"
                chip = ft.Chip(
                    label=ft.Text(label, size=12),
                    on_click=lambda _, date=d: self._select_date(date),
                    selected=d == self.current_view_date,
                )
                self.chips_row.controls.append(chip)
        self.chips_row.update()

    def _load_tasks_for_date(self):
        tasks = db.get_tasks(self.current_view_date)
        self.task_list.controls.clear()

        if not tasks:
            self.empty_text.visible = True
            self.empty_text.value = f"{self.current_view_date} 没有记录"
            self.empty_text.update()
            self.task_list.update()
            return

        self.empty_text.visible = False

        done_count = sum(1 for t in tasks if t.status == "completed")
        header = ft.Text(
            f"✅ {done_count} 完成  ⏳ {len(tasks) - done_count} 未完成  📋 共 {len(tasks)} 项",
            size=13, color=ft.Colors.GREY_600,
        )
        self.task_list.controls.append(header)

        for task in tasks:
            is_done = task.status == "completed"
            icon = ft.icons.CHECK_CIRCLE if is_done else ft.icons.RADIO_BUTTON_UNCHECKED
            color = ft.Colors.GREEN if is_done else ft.Colors.GREY_400
            self.task_list.controls.append(
                ft.Row(
                    [
                        ft.Icon(icon, color=color, size=18),
                        ft.Text(
                            task.content,
                            size=14,
                            decoration=ft.TextDecoration.LINE_THROUGH if is_done else None,
                            color=ft.Colors.GREY_400 if is_done else ft.Colors.BLACK,
                        ),
                    ],
                    spacing=8,
                )
            )
        self.task_list.update()

    def _on_date_selected(self, e):
        if self.date_picker.value:
            self.current_view_date = self.date_picker.value.strftime("%Y-%m-%d")
            self.date_btn.text = f"📅 {self.current_view_date}"
            self.date_btn.update()
            self._load_tasks_for_date()

    def _select_date(self, date_str: str):
        self.current_view_date = date_str
        self.date_btn.text = f"📅 {date_str}"
        self.date_btn.update()
        self._load_date_chips()
        self._load_tasks_for_date()
