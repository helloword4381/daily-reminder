# 📦 旧版网页端备份

此目录是 **第一版** 日常提醒工作记录的备份，采用 **FastAPI + Web 前端** 架构。

## 内容

| 文件/目录 | 说明 |
|-----------|------|
| `main.py` | FastAPI 入口 |
| `config.py` | 配置 |
| `database.py` | SQLAlchemy 数据库层 |
| `models.py` | Pydantic 数据模型 |
| `routers/` | API 路由 |
| `services/` | 通知 + 调度服务 |
| `static/` | 前端页面（HTML/CSS/JS） |
| `daily_reminder.db` | 旧版数据库（可能含测试数据） |

## 为什么备份

第二版重构为 **Windows + Android 双客户端** 架构（UDP 发现 + TCP 同步），
网页版不再使用，但保留作为参考。

## 恢复方式

```bash
# 将备份文件移回根目录即可
move backup\web_old\* .
```
