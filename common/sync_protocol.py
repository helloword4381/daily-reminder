"""
日常提醒工作记录 — 双端同步协议

定义 UDP 发现 + TCP 同步的完整协议。
"""

import json
import socket
import struct
import logging
from dataclasses import dataclass
from typing import Optional

from common.models import DeviceInfo, SyncMessage, Task, now_iso

logger = logging.getLogger(__name__)

# ── 常量 ──
DISCOVERY_PORT = 8898          # UDP 广播端口
SYNC_PORT = 8899               # TCP 同步端口
DISCOVERY_MAGIC = b"REMINDER_SYNC_v1"
HEARTBEAT_INTERVAL = 10        # 心跳间隔（秒）
SYNC_TIMEOUT = 30              # 判定离线（秒）


# ── UDP 发现协议 ──────────────────────────────────────────

def encode_discovery_request() -> bytes:
    """编码发现请求报文"""
    payload = json.dumps({"type": "discovery", "version": "1.0"}).encode("utf-8")
    return DISCOVERY_MAGIC + struct.pack("!I", len(payload)) + payload


def encode_discovery_response(device: DeviceInfo) -> bytes:
    """编码发现响应报文"""
    payload = json.dumps({
        "type": "response",
        "device_id": device.device_id,
        "device_name": device.device_name,
        "device_type": device.device_type,
        "version": device.version,
        "sync_port": SYNC_PORT,
    }).encode("utf-8")
    return DISCOVERY_MAGIC + struct.pack("!I", len(payload)) + payload


def decode_discovery_packet(data: bytes) -> Optional[dict]:
    """解码发现报文，返回 None 表示无效"""
    if not data.startswith(DISCOVERY_MAGIC):
        return None
    header_len = len(DISCOVERY_MAGIC)
    if len(data) < header_len + 4:
        return None
    payload_len = struct.unpack("!I", data[header_len:header_len + 4])[0]
    payload_data = data[header_len + 4:header_len + 4 + payload_len]
    try:
        return json.loads(payload_data.decode("utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError):
        return None


# ── 发现监听（服务端 — Windows） ──────────────────────────

def start_discovery_listener(device: DeviceInfo, stop_event=None):
    """
    在 Windows 端启动 UDP 广播监听。
    收到 Android 的发现请求后回复本机信息。
    """
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.settimeout(1.0)  # 每秒醒来检查 stop_event
    try:
        sock.bind(("0.0.0.0", DISCOVERY_PORT))
    except OSError as e:
        logger.warning("发现监听端口 %d 被占用: %s", DISCOVERY_PORT, e)
        return

    logger.info("🔍 发现监听已启动 (UDP %d)", DISCOVERY_PORT)

    while not (stop_event and stop_event.is_set()):
        try:
            data, addr = sock.recvfrom(4096)
            msg = decode_discovery_packet(data)
            if msg and msg.get("type") == "discovery":
                logger.info("收到发现请求来自 %s:%d", addr[0], addr[1])
                resp = encode_discovery_response(device)
                sock.sendto(resp, addr)
                logger.info("已响应发现请求 -> %s", addr[0])
        except socket.timeout:
            continue
        except Exception as e:
            logger.error("发现监听错误: %s", e)

    sock.close()
    logger.info("发现监听已停止")


# ── 发现客户端（Android） ─────────────────────────────────

def discover_windows(timeout: float = 2.0) -> Optional[dict]:
    """
    Android 端在局域网广播发现请求，等待 Windows 响应。
    返回 Windows 设备信息或 None。
    """
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.settimeout(timeout)

    try:
        # 发送广播
        req = encode_discovery_request()
        sock.sendto(req, ("255.255.255.255", DISCOVERY_PORT))
        logger.info("已发送发现广播")

        # 等待响应
        try:
            data, addr = sock.recvfrom(4096)
            msg = decode_discovery_packet(data)
            if msg and msg.get("type") == "response":
                logger.info("发现 Windows 设备: %s (%s)", msg.get("device_name"), addr[0])
                msg["ip"] = addr[0]
                return msg
        except socket.timeout:
            logger.info("未发现 Windows 设备 (超时)")
            return None
    finally:
        sock.close()

    return None


# ── TCP 同步协议 ──────────────────────────────────────────

def encode_sync_message(msg: SyncMessage) -> bytes:
    """编码同步报文 → 长度前缀 + JSON"""
    payload = json.dumps({
        "type": msg.type,
        "device": {
            "device_id": msg.device.device_id,
            "device_name": msg.device.device_name,
            "device_type": msg.device.device_type,
            "version": msg.device.version,
        },
        "since_token": msg.since_token,
        "changes": msg.changes,
        "deleted_ids": msg.deleted_ids,
        "sync_token": msg.sync_token,
    }).encode("utf-8")
    return struct.pack("!I", len(payload)) + payload


def decode_sync_message(data: bytes) -> Optional[SyncMessage]:
    """解码同步报文
    
    支持两种输入：
    - 完整帧（4 字节长度前缀 + JSON）
    - 纯 JSON payload
    """
    # 检测并去除长度前缀
    if len(data) > 4:
        try:
            # 检查前 4 字节是否像长度前缀
            payload_len = struct.unpack("!I", data[:4])[0]
            if 0 < payload_len <= len(data) - 4:
                # 尝试把剩余部分作为 JSON 解析
                candidate = data[4:4 + payload_len]
                try:
                    payload = json.loads(candidate.decode("utf-8"))
                    return _parse_sync_message(payload)
                except (json.JSONDecodeError, UnicodeDecodeError):
                    pass  # 不是长度前缀格式，继续当纯 JSON 试
        except struct.error:
            pass  # 不是长度前缀，继续当纯 JSON 试

    # 当作纯 JSON payload
    try:
        payload = json.loads(data.decode("utf-8"))
        return _parse_sync_message(payload)
    except (json.JSONDecodeError, UnicodeDecodeError):
        return None


def _parse_sync_message(payload: dict) -> SyncMessage:
    """从解析后的 dict 构造 SyncMessage"""
    device_info = DeviceInfo(
        device_id=payload.get("device", {}).get("device_id", ""),
        device_name=payload.get("device", {}).get("device_name", ""),
        device_type=payload.get("device", {}).get("device_type", ""),
        version=payload.get("device", {}).get("version", "1.0"),
    )
    return SyncMessage(
        type=payload.get("type", ""),
        device=device_info,
        since_token=payload.get("since_token", ""),
        changes=payload.get("changes", []),
        deleted_ids=payload.get("deleted_ids", []),
        sync_token=payload.get("sync_token", now_iso()),
    )



def recv_exact(sock: socket.socket, n: int) -> bytes:
    """精确接收 n 字节"""
    chunks = []
    received = 0
    while received < n:
        chunk = sock.recv(n - received)
        if not chunk:
            raise ConnectionError("连接断开")
        chunks.append(chunk)
        received += len(chunk)
    return b"".join(chunks)


def recv_sync_message(sock: socket.socket) -> Optional[SyncMessage]:
    """从 socket 接收一条同步报文"""
    header = recv_exact(sock, 4)
    payload_len = struct.unpack("!I", header)[0]
    if payload_len == 0:
        return None
    payload = recv_exact(sock, payload_len)
    return decode_sync_message(payload)


def send_sync_message(sock: socket.socket, msg: SyncMessage):
    """发送一条同步报文"""
    data = encode_sync_message(msg)
    sock.sendall(data)


# ── 同步服务端（Windows） ─────────────────────────────────

def start_sync_server(device: DeviceInfo, get_changes_fn, apply_changes_fn, stop_event=None):
    """
    在 Windows 端启动 TCP 同步服务器。
    get_changes_fn(since_token) -> (changes, deleted_ids, new_token)
    apply_changes_fn(changes, deleted_ids) -> None
    """
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.settimeout(1.0)
    try:
        sock.bind(("0.0.0.0", SYNC_PORT))
        sock.listen(5)
    except OSError as e:
        logger.warning("同步端口 %d 被占用: %s", SYNC_PORT, e)
        return

    logger.info("🔄 同步服务器已启动 (TCP %d)", SYNC_PORT)

    while not (stop_event and stop_event.is_set()):
        try:
            conn, addr = sock.accept()
            logger.info("同步连接来自 %s:%d", addr[0], addr[1])
            _handle_sync_client(conn, device, get_changes_fn, apply_changes_fn)
        except socket.timeout:
            continue
        except Exception as e:
            logger.error("同步服务器错误: %s", e)

    sock.close()
    logger.info("同步服务器已停止")


def _handle_sync_client(conn, server_device, get_changes_fn, apply_changes_fn):
    """处理一个同步客户端的连接"""
    try:
        # 接收客户端请求
        req = recv_sync_message(conn)
        if not req:
            return

        logger.info("客户端 %s 请求同步 (since: %s)", req.device.device_id, req.since_token)

        # 应用客户端的数据
        if req.changes or req.deleted_ids:
            apply_changes_fn(req.changes, req.deleted_ids, req.device.device_id)
            logger.info("已应用 %d 条变更, %d 条删除", len(req.changes), len(req.deleted_ids))

        # 获取本机变更
        changes, deleted_ids, new_token = get_changes_fn(req.since_token)
        logger.info("返回 %d 条变更, %d 条删除", len(changes), len(deleted_ids))

        # 发送响应
        resp = SyncMessage(
            type="sync_response",
            device=server_device,
            changes=changes,
            deleted_ids=deleted_ids,
            sync_token=new_token,
        )
        send_sync_message(conn, resp)

    except ConnectionError:
        logger.warning("同步客户端断开")
    except Exception as e:
        logger.error("处理同步客户端异常: %s", e)
    finally:
        try:
            conn.close()
        except Exception:
            pass


# ── 同步客户端（Android） ─────────────────────────────────

def sync_with_windows(windows_ip: str, device: DeviceInfo,
                      get_changes_fn, apply_changes_fn,
                      since_token: str = "") -> Optional[str]:
    """
    Android 端连接 Windows 执行一次同步。
    返回新的 sync_token，失败返回 None。
    """
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(10)

    try:
        sock.connect((windows_ip, SYNC_PORT))
        logger.info("已连接到 Windows @ %s:%d", windows_ip, SYNC_PORT)

        # 获取本机变更
        changes, deleted_ids, _ = get_changes_fn(since_token)

        # 发送同步请求
        req = SyncMessage(
            type="sync_request",
            device=device,
            since_token=since_token,
            changes=changes,
            deleted_ids=deleted_ids,
        )
        send_sync_message(sock, req)
        logger.info("已发送 %d 条本地变更, %d 条删除", len(changes), len(deleted_ids))

        # 接收响应
        resp = recv_sync_message(sock)
        if resp and resp.type == "sync_response":
            if resp.changes or resp.deleted_ids:
                apply_changes_fn(resp.changes, resp.deleted_ids, resp.device.device_id)
                logger.info("已应用 %d 条远程变更, %d 条删除",
                          len(resp.changes), len(resp.deleted_ids))
            return resp.sync_token

        logger.warning("同步响应格式异常: %s", resp.type if resp else "None")
        return None

    except (ConnectionError, socket.timeout, OSError) as e:
        logger.warning("同步失败: %s", e)
        return None
    finally:
        try:
            sock.close()
        except Exception:
            pass
