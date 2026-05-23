"""
日常提醒工作记录 — Android 同步引擎

10 秒心跳检测：
1. UDP 广播发现 Windows 设备
2. 发现后通过 TCP 执行数据同步
3. 断开则标记离线，继续心跳检测
"""

import logging
import socket
import threading
import time
from typing import Optional, Callable

from common.models import DeviceInfo
from common.sync_protocol import discover_windows, sync_with_windows
from android_app import db

logger = logging.getLogger(__name__)

HEARTBEAT_INTERVAL = 10  # 秒


class AndroidSyncEngine:
    """Android 端同步引擎"""

    def __init__(self, on_sync_status: Optional[Callable] = None):
        """
        on_sync_status(connected: bool, message: str = "")
        状态变化时回调更新 GUI
        """
        self.device = DeviceInfo(
            device_id=db.get_device_id(),
            device_name=socket.gethostname(),
            device_type="android",
        )
        self.on_sync_status = on_sync_status
        self._stop_event = threading.Event()
        self._thread: Optional[threading.Thread] = None
        self.running = False

        # 状态
        self.connected = False
        self.windows_ip: Optional[str] = None
        self.windows_name: Optional[str] = None
        self.sync_token: str = db.get_sync_token()

    # ── 启动 / 停止 ──

    def start(self):
        if self.running:
            return
        self._stop_event.clear()
        self.running = True
        self._thread = threading.Thread(target=self._sync_loop, daemon=True, name="android-sync")
        self._thread.start()
        logger.info("🟢 Android 同步引擎已启动")

    def stop(self):
        self._stop_event.set()
        self.running = False
        logger.info("🔴 Android 同步引擎已停止")
        self._notify_status(False, "已停止")

    # ── 主循环（10s 心跳） ──

    def _sync_loop(self):
        """每 10 秒执行：发现 → 同步"""
        while not self._stop_event.is_set():
            try:
                self._do_discovery_and_sync()
            except Exception as e:
                logger.error("同步异常: %s", e)

            # 等待 10 秒（可中断）
            for _ in range(HEARTBEAT_INTERVAL):
                if self._stop_event.is_set():
                    return
                time.sleep(1)

    def _do_discovery_and_sync(self):
        """一次发现+同步周期"""
        # ── 发现 Windows ──
        windows = discover_windows(timeout=2.0)

        if windows is None:
            if self.connected:
                self.connected = False
                self.windows_ip = None
                self.windows_name = None
                self._notify_status(False, "已断开")
                logger.info("Windows 设备已断开")
            return

        # 发现成功
        ip = windows.get("ip", "")
        name = windows.get("device_name", "Windows-PC")

        if not self.connected:
            self.connected = True
            self.windows_ip = ip
            self.windows_name = name
            self._notify_status(True, name)
            logger.info("已发现 Windows: %s (%s)", name, ip)

        # 如果 IP 变了
        if ip != self.windows_ip:
            self.windows_ip = ip
            self._notify_status(True, name)

        # ── 执行同步 ──
        self._do_sync()

    def _do_sync(self):
        """与 Windows 执行一次数据同步"""
        if not self.windows_ip:
            return

        new_token = sync_with_windows(
            windows_ip=self.windows_ip,
            device=self.device,
            get_changes_fn=self._get_changes,
            apply_changes_fn=self._apply_changes,
            since_token=self.sync_token,
        )

        if new_token:
            # 更新 sync_token
            self.sync_token = new_token
            db.set_sync_token(new_token)
            logger.info("同步成功 (token: %s)", new_token[:16])
            self._notify_status(True, f"{self.windows_name} ✓")

            # 触发 UI 刷新（数据已变）
            self._notify_refresh()
        else:
            logger.warning("同步失败")
            self._notify_status(True, f"{self.windows_name} ⚠️")

    # ── 数据回调 ──

    def _get_changes(self, since_token: str):
        return db.get_changes_since(since_token)

    def _apply_changes(self, changes, deleted_ids, source_device_id):
        db.apply_remote_changes(changes, deleted_ids, source_device_id)

    # ── GUI 回调 ──

    def _notify_status(self, connected: bool, message: str = ""):
        if self.on_sync_status:
            try:
                self.on_sync_status(connected, message)
            except Exception as e:
                logger.warning("GUI 回调异常: %s", e)

    def _notify_refresh(self):
        """通知 GUI 刷新数据"""
        if self.on_sync_status:
            try:
                self.on_sync_status(True, "__REFRESH__")
            except Exception as e:
                logger.warning("GUI 刷新回调异常: %s", e)

    # ── 手动触发同步 ──

    def sync_now(self):
        """手动触发一次立即同步"""
        logger.info("手动触发同步")
        self._do_discovery_and_sync()
