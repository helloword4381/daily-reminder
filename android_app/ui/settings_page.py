"""
日常提醒工作记录 — Android 设置页（Flet UI）

提醒时间设置 + 同步状态显示
"""

import flet as ft
from android_app import db


class SettingsPage:
    """设置页面"""

    def __init__(self, page: ft.Page, on_sync_now=None):
        self.page = page
        self.on_sync_now = on_sync_now
        # 默认值
        self.morning_time = "09:00"
        self.noon_time = "12:30"
        self.morning_on = True
        self.noon_on = True
        self.sync_status = "未连接"
        self._build()

    def _build(self):
        # ── 提醒设置 ──
        self.morning_time_field = ft.TextField(
            label="☀️ 早上提醒时间", value=self.morning_time,
            hint_text="HH:MM", width=120, text_size=14,
        )
        self.morning_switch = ft.Switch(value=self.morning_on, on_change=self._on_change)
        self.noon_time_field = ft.TextField(
            label="🌤 中午提醒时间", value=self.noon_time,
            hint_text="HH:MM", width=120, text_size=14,
        )
        self.noon_switch = ft.Switch(value=self.noon_on, on_change=self._on_change)

        # ── 同步状态 ──
        self.sync_status_text = ft.Text(f"📡 同步状态: {self.sync_status}", size=14)
        self.sync_btn = ft.ElevatedButton("🔄 立即同步", on_click=self._sync_now)
        self.device_id_text = ft.Text(
            f"📱 设备 ID: {db.get_device_id()}",
            size=11, color=ft.Colors.GREY_500,
        )

        # ── 设备信息 ──
        self.info_text = ft.Text(
            "💡 确保手机和电脑在同一 Wi-Fi 网络\n"
            "Windows 端启动后，Android 端会自动发现并同步",
            size=12, color=ft.Colors.GREY_600,
        )

        self.view = ft.Container(
            content=ft.Column(
                [
                    ft.Text("⏰ 提醒设置", size=18, weight=ft.FontWeight.BOLD),
                    ft.Row([self.morning_time_field, self.morning_switch], alignment=ft.MainAxisAlignment.SPACE_BETWEEN),
                    ft.Row([self.noon_time_field, self.noon_switch], alignment=ft.MainAxisAlignment.SPACE_BETWEEN),
                    ft.Divider(),
                    ft.Text("📡 同步", size=18, weight=ft.FontWeight.BOLD),
                    self.sync_status_text,
                    self.sync_btn,
                    self.device_id_text,
                    ft.Divider(),
                    self.info_text,
                ],
                spacing=12,
                scroll=ft.ScrollMode.AUTO,
            ),
            padding=16,
            expand=True,
        )

    def load(self):
        """加载当前设置"""
        # 提醒设置存本地 config
        self.device_id_text.value = f"📱 设备 ID: {db.get_device_id()}"
        self.device_id_text.update()

    def set_sync_status(self, connected: bool, msg: str = ""):
        """更新同步状态显示"""
        status = "🟢 已连接" if connected else "🔴 未连接"
        if msg and msg != "__REFRESH__":
            status += f" ({msg})"
        self.sync_status_text.value = f"📡 同步状态: {status}"
        self.sync_status_text.update()

    def _on_change(self, e=None):
        pass  # 时间设置暂存本地

    def _sync_now(self, e=None):
        if self.on_sync_now:
            self.on_sync_now()
