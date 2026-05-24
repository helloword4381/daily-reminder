# 日常助手 - 开发手册

> 存放于项目根目录，每次开发前阅读

---

## 一、版本号管理（最高优先级）

**每次推送代码到 main，必须同时递增版本号。**

```kotlin
// android_app/app/build.gradle.kts
versionCode = 28          // 每次 +1
versionName = "1.2.22"    // patch 位递增
```

同步更新 `version.json`（根目录）：

```json
{
  "versionName": "1.2.22",
  "versionCode": 28,
  "buildNumber": 28,
  "md5": "",
  "downloadUrl": "https://github.com/helloword4381/daily-reminder/releases/download/build-28/日常助手_v1.2.22_build28.apk"
}
```

> **不更新版本号 → 自动更新失效**。对比逻辑依赖 `isNewerVersion()` 语义比较。

---

## 二、更新检测架构

```
build.gradle.kts → versionName: "1.2.22"
     ↓
GitHub Actions 编译 APK
     ↓
workflow 自动生成 build-info.json（含 versionName + md5 + apkName）
     ↓
上传到 Release（tag: build-{run_number}）
     ↓
App 启动 → silentCheckForUpdate()
     ├─ ① GitHub releases/latest/download/build-info.json    ← 主源
     ├─ ② raw.githubusercontent.com/.../version.json          ← 备用
     ├─ 语义比较 isNewerVersion(remoteVer, currentVer)
     └─ 有新版本 → 设置页内联显示 "发现新版本 vX.X.X"
```

### 函数分工

| 函数 | 触发 | 行为 |
|------|------|------|
| `silentCheckForUpdate()` | App 启动 | 静默检测，不弹窗 |
| `checkForUpdate()` | 设置页"检查更新"按钮 | 显示 CHECKING 对话框 |

---

## 三、GitHub Actions Workflow

位置：`.github/workflows/build-apk.yml`

### 触发条件
- Push 到 main 分支，且路径包含 `android_app/**`
- 手动触发（workflow_dispatch）

### 编译流程
1. checkout → JDK 17 → Android SDK 34 → Gradle 8.7
2. `gradle assembleDebug --no-daemon --build-cache`
3. 查找生成的 APK
4. **sed 精确提取** versionName/versionCode（已修复 grep 泛匹配问题）
5. `md5sum` 计算 APK 指纹
6. `printf` 生成 `build-info.json`
7. 使用 `softprops/action-gh-release@v2` 创建 Release
8. 上传 APK + `build-info.json`

### Workflow 注意事项
- versionName 提取：`sed -n 's/.*versionName[[:space:]]*=[[:space:]]*"\(.*\)".*/\1/p'`
- versionCode 提取：`sed -n 's/.*versionCode[[:space:]]*=[[:space:]]*\([0-9]*\).*/\1/p'`
- buildNumber = `${{ github.run_number }}`（每次递增，与 versionCode 无关）

---

## 四、断点续传下载

```kotlin
viewModelScope.launch(Dispatchers.IO) {
    // 1. 检查 update.apk.tmp 是否存在 → 获取已下载字节数
    // 2. 发送 Range: bytes={downloadedBytes}- 请求
    // 3. RandomAccessFile seek + 写入
    // 4. 边下载边计算 MD5（MessageDigest）
    // 5. 下载完校验 MD5
    // 6. 不匹配 → 删除 tmp → 提示重试
    // 7. 匹配 → rename tmp → update.apk
}
```

### 文件路径
- `context.cacheDir/update.apk.tmp`（临时文件）
- `context.cacheDir/update.apk`（完整文件）

### 续传响应码
- `206 Partial Content` → 服务端支持续传
- 其他 → 不支持续传，删除 tmp 全量重下

---

## 五、权限清单

| 权限 | 用途 | API 级别 |
|------|------|---------|
| `INTERNET` | 联网检查更新、下载 | 全部 |
| `POST_NOTIFICATIONS` | 通知弹窗 | Android 13+ |
| `SCHEDULE_EXACT_ALARM` | 精确闹钟触发提醒 | Android 12+ |
| `FOREGROUND_SERVICE` | 前台服务常驻 | Android 9+ |
| `RECEIVE_BOOT_COMPLETED` | 开机重启服务 | 全部 |

### 权限请求位置
- `MainActivity.onCreate()` → `requestNotificationPermission()`
- `MainActivity.onCreate()` → `requestAlarmPermission()`
- 设置页 → **必要权限** 卡片（两个设置按钮）

---

## 六、编译常见错误

### 6.1 NetworkOnMainThreadException
**原因**：`viewModelScope.launch` 默认 `Dispatchers.Main`，网络请求跑在主线程。  
**修复**：加 `launch(Dispatchers.IO)`。

### 6.2 @Composable invocations only happen from @Composable function
**原因**：`LocalContext.current` 写在 `onClick` 等非 Composable lambda 里。  
**修复**：提到 composable 顶层捕获。

### 6.3 Unresolved reference 'withContext' / 'Alignment'
**原因**：缺少 import。  
**修复**：`import kotlinx.coroutines.withContext` / `import androidx.compose.ui.Alignment`。

### 6.4 headerField → getHeaderField
**原因**：Java 方法名是 `getHeaderField()` 不是 `headerField`。

### 6.5 死代码引用不存在变量
**原因**：旧版 `DailyReminderApp()` 函数未删除，引用 `updateInfo`/`updateState` 但不在作用域。  
**修复**：删除死代码。

---

## 七、国内网络适配

### 更新检测 URL（按优先级）
1. `https://github.com/.../releases/latest/download/build-info.json`
   - 自动重定向到最新 Release
   - instanceFollowRedirects = true 必须设置
2. `https://raw.githubusercontent.com/.../version.json`
   - 备用源，需要手动同步版本号

### jsDelivr CDN
- `https://cdn.jsdelivr.net/gh/helloword4381/daily-reminder@main/version.json`
- 修改后需要 purge：`https://purge.jsdelivr.net/gh/...@main/version.json`
- 缓存最长 12 小时（分支模式 `@main`）
- `@` 符号在 URL 中可能被 `java.net.URL` 误解析 → 优先用 raw.githubusercontent.com

---

## 八、数据模型

### Room Entity
- `TaskEntity` → taskDao
- `WorkDiaryEntity` → workDiaryDao
- `TowerCalcEntity` → towerCalcDao

### 数据库
- `AppDatabase`（Room，版本 1）
- 所有数据本地存储，无服务器

---

## 九、小工具（推送脚本）

位置：`小工具、插件等/日常助手推送.bat`

```bat
双击运行 → 输入提交说明 → 自动 add / commit / push
```
