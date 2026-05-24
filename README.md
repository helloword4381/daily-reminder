# 日常提醒工作记录 📋

**Android 原生 App（Kotlin + Jetpack Compose）** — 待办任务、工作日记、偏位计算器、自动更新。

## 功能

| 功能 | 说明 |
|------|------|
| ✅ 待办任务 | 紧急/日常双按钮，优先级标签，显示创建时间 |
| ✅ 工作日记 | 每日记录，支持编辑和删除 |
| ✅ 偏位计算器 | 墩柱（扣塔）偏位分析，历史记录可分享 |
| ✅ 暗色模式 | 跟随系统 / 浅色 / 深色 三档可选 |
| ✅ 自动更新 | ghfast.top + raw.githubusercontent.com 三级降级 |

## 下载

从 [GitHub Releases](https://github.com/helloword4381/daily-reminder/releases/latest) 下载最新 APK，或打开设置页检测更新。

## 构建

```bash
cd android_app
gradle assembleDebug
```

需要 Android SDK 34 + Gradle 8.7。

## 项目结构

```
daily-reminder/
├── android_app/                    # 📱 Android 源码
│   ├── app/
│   │   ├── build.gradle.kts        #   构建配置
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/dailyreminder/
│   │       │   ├── MainViewModel.kt          #   主 ViewModel
│   │       │   ├── DailyReminderApp.kt       #   导航+主题
│   │       │   ├── SettingsManager.kt        #   设置存储
│   │       │   ├── data/db/                  #   Room 数据库
│   │       │   └── ui/screens/               #   各页面
│   │       └── res/
│   └── build.gradle.kts
├── .github/workflows/              # 🔄 GitHub Actions
│   └── build-apk.yml               #   自动构建+推送
├── release/                        # 📦 发布目录（由 workflow 写入）
└── README.md
```

## 版本记录

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.4.6 | 2026-05 | 安装修复，权限补全 |
| 1.4.5 | 2026-05 | 偏位计算器键盘优化 |
| 1.4.4 | 2026-05 | 暗色模式、顶部高度缩减 |
| 1.4.3 | 2026-05 | JSON 解析修复（提取更新） |
| 1.4.2 | 2026-05 | 数据库保留迁移 |
| 1.4.1 | 2026-05 | workflow 提前推送 version.json |
| 1.4.0 | 2026-05 | 暗色模式+待办任务+紧急日常 |
| 1.3.9 | 2026-05 | 三级降级更新通道 |
| 1.3.8 | 2026-05 | jsDelivr CDN 迁移 |
