"""
日常提醒工作记录 — Windows 同步引擎

后台线程：
1. UDP 发现监听 — 响应 Android 的发现请求
2. TCP 同步服务器 — 处理数据同步
3. 不主动发起同步，而是作为服务端等待 Android 连接
4. 每次同步后回调更新 GUI 状态
"""

import logging
import socket
import threading
from datetime import datetime
from typing import Optional, Callable

from common.models import DeviceInfo
from common.sync_protocol import (
    start_discovery_listener,
    start_sync_server,
)
from windows_app import db

logger = logging.getLogger(__name__)


class WindowsSyncEngine:
    """Windows 同步引擎"""

    def __init__(self, on_sync_status: Optional[Callable] = None):
        """
        on_sync_status(connected: bool, message: str = "")
        - 连接状态变化时回调，用于更新 GUI
        """
        self.device = DeviceInfo(
            device_id=self._get_device_id(),
            device_name=self._get_hostname(),
            device_type="windows",
        )
        self.on_sync_status = on_sync_status
        self._stop_event = threading.Event()
        self._threads: list[threading.Thread] = []
        self.running = False

    # ── 启动 / 停止 ──

    def start(self):
        """启动后台监听线程"""
        if self.running:
            return
        self._stop_event.clear()
        self.running = True

        # 线程 1: UDP 发现监听
        t1 = threading.Thread(
            target=start_discovery_listener,
            args=(self.device, self._stop_event),
            daemon=True,
            name="discovery-listener",
        )
        t1.start()
        self._threads.append(t1)

        # 线程 2: TCP 同步服务器
        t2 = threading.Thread(
            target=start_sync_server,
            args=(
                self.device,
                self._get_changes_fn,
                self._apply_changes_fn,
                self._stop_event,
            ),
            daemon=True,
            name="sync-server",
        )
        t2.start()
        self._threads.append(t2)

        logger.info("🟢 Windows 同步引擎已启动")
        self._notify_status(False, "等待 Android 连接...")

    def stop(self):
        """停止所有线程"""
        self._stop_event.set()
        self.running = False
        logger.info("🔴 Windows 同步引擎已停止")
        self._notify_status(False, "已停止")

    # ── 同步回调接口（给 sync server 用） ──

    def _get_changes_fn(self, since_token: str):
        """获取自上次同步以来的变更"""
        return db.get_changes_since(since_token)

    def _apply_changes_fn(self, changes, deleted_ids, source_device_id):
        """应用远程变更"""
        db.apply_remote_changes(changes, deleted_ids, source_device_id)
        # 变更后通知 GUI 刷新（主循环通过 after 调用）
        self._notify_sync_applied()

    # ── GUI 回调 ──

    def _notify_status(self, connected: bool, message: str = ""):
        """线程安全地通知 GUI 更新状态"""
        if self.on_sync_status:
            try:
                self.on_sync_status(connected, message)
            except Exception as e:
                logger.warning("GUI 状态回调异常: %s", e)

    def _notify_sync_applied(self):
        """通知 GUI 数据已变更，需要刷新"""
        if self.on_sync_status:
            try:
                # 用特殊标记表示需要刷新
                self.on_sync_status(True, "__REFRESH__")
            except Exception as e:
                logger.warning("GUI 刷新回调异常: %s", e)

    # ── 辅助 ──

    @staticmethod
    def _get_hostname() -> str:
        try:
            return socket.gethostname()
        except Exception:
            return "Windows-PC"

    @staticmethod
    def _get_device_id() -> str:
        """基于主机名生成稳定的设备 ID"""
        hostname = WindowsSyncEngine._get_hostname()
        return f"win-{hostname.replace(' ', '-').lower()}"
