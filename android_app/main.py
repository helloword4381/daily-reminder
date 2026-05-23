"""
日常提醒工作记录 — Android 客户端入口 (Flet)

启动 Flet UI + 后台同步引擎。
"""

import sys
import os
import logging
from pathlib import Path

# 确保项目根目录在 sys.path 中
sys.path.insert(0, str(Path(__file__).parent.parent.resolve()))

import flet as ft

from android_app import db
from android_app.ui.home_page import HomePage
from android_app.ui.settings_page import SettingsPage
from android_app.ui.history_page import HistoryPage
from android_app.sync_engine import AndroidSyncEngine

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("android-app")


def main(page: ft.Page):
    # ── 页面基本设置 ──
    page.title = "日常提醒工作记录"
    page.theme_mode = ft.ThemeMode.SYSTEM
    page.padding = 0
    page.window.width = 400
    page.window.height = 700

    # ── 初始化数据库 ──
    db.init_db()
    logger.info("数据库已初始化")

    # ── 创建页面组件 ──
    def on_edit_task(task):
        """编辑任务回调 — 弹出底部对话框"""
        tf = ft.TextField(value=task.content, multiline=True, min_lines=2, max_lines=4)
        dlg = ft.AlertDialog(
            title=ft.Text("✏️ 编辑任务"),
            content=tf,
            actions=[
                ft.TextButton("删除", on_click=lambda _: _delete_and_close(task, dlg)),
                ft.TextButton("取消", on_click=lambda _: close_dlg(dlg)),
                ft.FilledButton("保存", on_click=lambda _: _save_edit(task, tf, dlg)),
            ],
        )
        page.overlay.append(dlg)
        dlg.open = True
        page.update()

    def close_dlg(dlg):
        dlg.open = False
        page.update()

    def _save_edit(task, tf, dlg):
        content = tf.value.strip()
        if content:
            db.update_task_content(task.id, content)
        dlg.open = False
        page.update()
        home_page.load()

    def _delete_and_close(task, dlg):
        db.delete_task(task.id)
        dlg.open = False
        page.update()
        home_page.load()

    # 创建各页面
    home_page = HomePage(page, on_edit_task=on_edit_task)
    settings_page = SettingsPage(page, on_sync_now=lambda: sync_now())
    history_page = HistoryPage(page)

    # ── 同步引擎 ──
    def on_sync_status(connected: bool, message: str):
        if message == "__REFRESH__":
            # 数据有变更，刷新主页
            home_page.load()
            return
        # 更新设置页的同步状态
        settings_page.set_sync_status(connected, message)

    sync_engine = AndroidSyncEngine(on_sync_status=on_sync_status)

    def sync_now():
        sync_engine.sync_now()
        # 稍等一会再刷新
        import time
        time.sleep(0.5)
        home_page.load()

    # ── 导航控制 ──
    def navigate_to(page_name: str):
        if page_name == "home":
            page.views.clear()
            page.views.append(home_view)
            home_page.load()
        elif page_name == "settings":
            page.views.clear()
            page.views.append(settings_view)
            settings_page.load()
        elif page_name == "history":
            page.views.clear()
            page.views.append(history_view)
            history_page.load()
        page.update()

    # ── 底部导航栏 ──
    def build_nav_bar(active: str) -> ft.NavigationBar:
        return ft.NavigationBar(
            destinations=[
                ft.NavigationDestination(icon=ft.icons.TODAY_OUTLINED,
                                         selected_icon=ft.icons.TODAY, label="今日"),
                ft.NavigationDestination(icon=ft.icons.HISTORY_OUTLINED,
                                         selected_icon=ft.icons.HISTORY, label="历史"),
                ft.NavigationDestination(icon=ft.icons.SETTINGS_OUTLINED,
                                         selected_icon=ft.icons.SETTINGS, label="设置"),
            ],
            on_change=lambda e: navigate_to(
                ["home", "history", "settings"][e.control.selected_index]
            ),
        )

    # ── 构建各视图 ──
    home_view = ft.View(
        route="/",
        controls=[home_page.view],
        navigation_bar=build_nav_bar("home"),
    )

    history_view = ft.View(
        route="/history",
        controls=[history_page.view],
        navigation_bar=build_nav_bar("history"),
    )

    settings_view = ft.View(
        route="/settings",
        controls=[settings_page.view],
        navigation_bar=build_nav_bar("settings"),
    )

    # ── 初始显示 ──
    page.views.append(home_view)
    home_page.load()
    page.update()

    # ── 启动同步引擎 ──
    sync_engine.start()

    # ── 页面关闭时停止同步 ──
    page.on_close = lambda: sync_engine.stop()


# ── Flet 入口 ──
if __name__ == "__main__":
    ft.app(target=main)
