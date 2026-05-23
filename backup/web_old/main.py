"""日常提醒工作记录 — 主入口

启动 FastAPI 服务 + 定时提醒调度器。
浏览器访问 http://localhost:8899 即可使用。
"""

import sys
import os
import webbrowser
import logging
from contextlib import asynccontextmanager
from pathlib import Path

# 确保项目根在 sys.path 中
sys.path.insert(0, str(Path(__file__).parent.resolve()))

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse

import config
from routers import tasks as tasks_router, reminders as reminders_router
from services import scheduler
from database import async_init_db, get_or_create_settings

# ── 日志 ──────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("daily-reminder")


# ── 生命周期 ──────────────────────────────────────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用启动/关闭事件"""
    # ── 启动 ──
    logger.info("正在初始化数据库...")
    await async_init_db()
    logger.info("数据库就绪")

    logger.info("正在启动提醒调度器...")
    settings = await get_or_create_settings()
    scheduler.configure(settings)
    scheduler.start()
    logger.info("提醒调度器已启动")

    # 打印访问地址
    logger.info("─" * 45)
    logger.info("  🌐 本地访问:  http://localhost:%s", config.PORT)
    # 尝试获取局域网 IP
    try:
        import socket
        hostname = socket.gethostname()
        local_ip = socket.gethostbyname(hostname)
        logger.info("  📱 局域网访问: http://%s:%s", local_ip, config.PORT)
        logger.info("      （Android 手机在浏览器打开上面的地址即可同步使用）")
    except Exception:
        pass
    logger.info("─" * 45)

    yield

    # ── 关闭 ──
    logger.info("正在停止调度器...")
    scheduler.stop()
    logger.info("应用已关闭")


# ── 创建应用 ──────────────────────────────────────────────
app = FastAPI(
    title="日常提醒工作记录",
    description="Windows + Android 双端工作记录提醒工具",
    version="1.0.0",
    lifespan=lifespan,
)

# 注册路由
app.include_router(tasks_router.router)
app.include_router(reminders_router.router)

# 挂载静态文件
static_dir = Path(__file__).parent / "static"
static_dir.mkdir(exist_ok=True)
app.mount("/static", StaticFiles(directory=str(static_dir)), name="static")


# ── 根路径 ────────────────────────────────────────────────
@app.get("/")
async def index():
    return FileResponse(str(static_dir / "index.html"))


@app.get("/api/health")
async def health():
    return {"status": "ok", "version": "1.0.0"}


# ── 直接运行 ──────────────────────────────────────────────
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host=config.HOST,
        port=config.PORT,
        reload=False,
        log_level="info",
    )
