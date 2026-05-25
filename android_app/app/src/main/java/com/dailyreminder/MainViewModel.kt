package com.dailyreminder

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailyreminder.data.db.*
import com.dailyreminder.data.model.WorkDiary
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val taskDao = db.taskDao()
    private val diaryDao = db.workDiaryDao()
    private val towerDao = db.towerCalcDao()
    val settings = SettingsManager(application)

    // === 任务 ===
    val allTasks: StateFlow<List<TaskEntity>> = taskDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingTasks: StateFlow<List<TaskEntity>> = allTasks.map { list ->
        list.filter { !it.done }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 兼容旧代码：仍暴露 todayPending（暂时保留以防其他引用） */
    val todayPending: StateFlow<List<TaskEntity>> = pendingTasks

    fun addTask(content: String, priority: String = "日常") {
        viewModelScope.launch {
            val now = com.dailyreminder.data.model.Task.now()
            val task = com.dailyreminder.data.model.Task(
                content = content, done = false, priority = priority,
                createdAt = now, updatedAt = now
            )
            taskDao.upsert(TaskEntity.fromTask(task))
        }
    }

    fun toggleDone(id: String, currentDone: Boolean) {
        viewModelScope.launch {
            taskDao.setDone(id, !currentDone, com.dailyreminder.data.model.Task.now())
        }
    }

    fun deleteTask(id: String) {
        viewModelScope.launch { taskDao.delete(id) }
    }

    fun getTasksByDate(date: String) = allTasks.map { list ->
        list.filter { it.createdAt.take(10) == date }
    }

    // === 工作日记 ===
    val diaryEntries: StateFlow<List<WorkDiaryEntity>> = diaryDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addDiary(date: String, title: String, content: String, imagePath: String = "") {
        viewModelScope.launch {
            val now = WorkDiary.now()
            val entry = WorkDiary(date = date, title = title, content = content, imagePath = imagePath, createdAt = now, updatedAt = now)
            diaryDao.upsert(WorkDiaryEntity.fromModel(entry))
        }
    }

    fun updateDiary(entity: WorkDiaryEntity) {
        viewModelScope.launch { diaryDao.upsert(entity) }
    }

    fun deleteDiary(entity: WorkDiaryEntity) {
        viewModelScope.launch { diaryDao.delete(entity) }
    }

    // === 扣塔计算 ===
    val towerRecords: StateFlow<List<TowerCalcEntity>> = towerDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveTowerRecord(entity: TowerCalcEntity) {
        viewModelScope.launch { towerDao.upsert(entity) }
    }

    fun deleteTowerRecord(id: String) {
        viewModelScope.launch { towerDao.deleteById(id) }
    }

    // === 更新信息 ===
    data class UpdateInfo(
        val hasUpdate: Boolean = false,
        val versionName: String = "",
        val versionCode: Int = 0,
        val buildNumber: Int = 0,
        val downloadUrl: String = "",
        val md5: String = ""
    )

    enum class UpdateState { IDLE, CHECKING, AVAILABLE, DOWNLOADING, DOWNLOADED, INSTALLING }

    private val _updateInfo = MutableStateFlow(UpdateInfo())
    val updateInfo: StateFlow<UpdateInfo> = _updateInfo.asStateFlow()

    private val _updateState = MutableStateFlow(UpdateState.IDLE)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _downloadFile = MutableStateFlow<File?>(null)
    val downloadFile: StateFlow<File?> = _downloadFile.asStateFlow()

    private val _toastMessage = MutableStateFlow("")
    val toastMessage: StateFlow<String> = _toastMessage.asStateFlow()

    /** 主题模式: 0=跟随系统, 1=浅色, 2=深色 */
    private val _themeMode = MutableStateFlow(settings.themeMode)
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    fun setThemeMode(mode: Int) {
        _themeMode.value = mode
        settings.themeMode = mode
    }

    /** 语义化版本比较 */
    private fun isNewerVersion(remote: String, current: String): Boolean {
        val rParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val cParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(rParts.size, cParts.size)) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    private fun showToast(msg: String, durationMs: Long = 2500) {
        _toastMessage.value = msg
        viewModelScope.launch {
            kotlinx.coroutines.delay(durationMs)
            _toastMessage.value = ""
        }
    }

    companion object {
        private const val APK_FILENAME = "update.apk"
        private const val APK_TMP = "update.apk.tmp"
        private const val USER_AGENT = "Mozilla/5.0 (Android 14; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0"
        private val VERSION_URLS = listOf(
            "https://ghfast.top/https://raw.githubusercontent.com/helloword4381/daily-reminder/main/release/version.json",
            "https://raw.githubusercontent.com/helloword4381/daily-reminder/main/release/version.json"
        )
        private val APK_URLS = listOf(
            "https://ghfast.top/https://raw.githubusercontent.com/helloword4381/daily-reminder/main/release/Daily-Aide.apk",
            "https://raw.githubusercontent.com/helloword4381/daily-reminder/main/release/Daily-Aide.apk"
        )
    }

    /** 获取版本信息：ghfast 主源，raw.githubusercontent 备用 */
    private suspend fun fetchVersionJson(): String? {
        for (urlStr in VERSION_URLS) {
            var conn: java.net.HttpURLConnection? = null
            try {
                conn = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.instanceFollowRedirects = true
                if (conn.responseCode in 200..299) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    if (text.trimStart().startsWith("{")) return text
                }
            } catch (_: Exception) { } finally { conn?.disconnect() }
        }
        return null
    }

    private fun extractJson(json: String, key: String): String {
        return try {
            org.json.JSONObject(json).optString(key, "")
        } catch (_: Exception) { "" }
    }

    // ==================== 更新检查 ====================
    /** 静默检查（启动时调用，不弹对话框） */
    fun silentCheckForUpdate() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val currentVer = getApplication<Application>().packageManager
                    .getPackageInfo(getApplication<Application>().packageName, 0)
                    .versionName ?: "0.0.0"
                val json = fetchVersionJson() ?: return@launch
                val remoteVer = extractJson(json, "versionName")
                if (isNewerVersion(remoteVer, currentVer)) {
                    _updateInfo.value = UpdateInfo(
                        hasUpdate = true, versionName = remoteVer,
                        downloadUrl = extractJson(json, "downloadUrl"),
                        md5 = extractJson(json, "md5")
                    )
                    _updateState.value = UpdateState.AVAILABLE
                }
            } catch (_: Exception) { }
        }
    }

    /** 手动检查（点击按钮时调用，显示对话框） */
    fun checkForUpdate() {
        _updateState.value = UpdateState.CHECKING
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pm = getApplication<Application>().packageManager
                val pi = pm.getPackageInfo(getApplication<Application>().packageName, 0)
                val currentVer = pi.versionName ?: "0.0.0"

                val json = fetchVersionJson() ?: run {
                    _updateState.value = UpdateState.IDLE
                    showToast("检查更新失败，请检查网络")
                    return@launch
                }

                val remoteVer = extractJson(json, "versionName")
                val hasNew = isNewerVersion(remoteVer, currentVer)

                _updateInfo.value = UpdateInfo(
                    hasUpdate = hasNew,
                    versionName = remoteVer,
                    downloadUrl = extractJson(json, "downloadUrl"),
                    md5 = extractJson(json, "md5")
                )
                _updateState.value = if (hasNew) UpdateState.AVAILABLE else UpdateState.IDLE
                if (!hasNew) showToast("已是最新版本 v$currentVer")
            } catch (_: Exception) {
                _updateInfo.value = UpdateInfo()
                _updateState.value = UpdateState.IDLE
                showToast("检查更新失败，请检查网络")
            }
        }
    }

    // ==================== 断点续传 + MD5（三级降级） ====================
    /** 从一个 URL 下载 APK，成功返回文件流 info，失败返回 null */
    private fun tryDownloadFrom(url: String, tmpFile: File, downloadedBytes: Long, expectedMd5: String): DownloadResult? {
        return try {
            var conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.instanceFollowRedirects = true

            if (downloadedBytes > 0) {
                conn.setRequestProperty("Range", "bytes=$downloadedBytes-")
            }
            conn.connect()

            val code = conn.responseCode
            val totalSize = if (downloadedBytes > 0 && code == 206) {
                val range = conn.getHeaderField("Content-Range")
                range?.substringAfter("/")?.trim()?.toLongOrNull() ?: conn.contentLengthLong
            } else {
                if (tmpFile.exists()) tmpFile.delete()
                conn.disconnect()
                conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.instanceFollowRedirects = true
                conn.connect()
                conn.contentLengthLong
            }
            DownloadResult(conn, totalSize)
        } catch (_: Exception) { null }
    }

    private data class DownloadResult(val conn: java.net.HttpURLConnection, val totalSize: Long)

    fun downloadApk() {
        val info = _updateInfo.value

        _updateState.value = UpdateState.DOWNLOADING
        _downloadProgress.value = 0
        val expectedMd5 = info.md5
        val ctx = getApplication<Application>()
        val tmpFile = File(ctx.cacheDir, APK_TMP)
        var downloadedBytes = tmpFile.length()
        val finalFile = File(ctx.cacheDir, APK_FILENAME)

        // 已有完整文件且 MD5 匹配 → 跳过下载
        if (finalFile.exists() && verifyMd5(finalFile, expectedMd5)) {
            _downloadFile.value = finalFile
            _downloadProgress.value = 100
            _updateState.value = UpdateState.DOWNLOADED
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 三级降级：ghfast → jsDelivr → raw（version.json 中的 downloadUrl 已经是最快的）
            val downloadUrls = mutableListOf(info.downloadUrl)
            for (fallback in APK_URLS) {
                if (fallback != info.downloadUrl) downloadUrls.add(fallback)
            }

            for (url in downloadUrls) {
                val result = tryDownloadFrom(url, tmpFile, downloadedBytes, expectedMd5) ?: continue
                val (conn, totalSize) = result

                try {
                    val input = conn.inputStream
                    val raf = java.io.RandomAccessFile(tmpFile, "rw")
                    raf.seek(downloadedBytes)

                    val digest = if (expectedMd5.isNotBlank())
                        java.security.MessageDigest.getInstance("MD5") else null
                    if (digest != null && downloadedBytes > 0) {
                        val existing = java.io.FileInputStream(tmpFile).use { it.readBytes() }
                        digest.update(existing)
                    }

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = downloadedBytes

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        digest?.update(buffer, 0, bytesRead)
                        if (totalSize > 0) {
                            _downloadProgress.value = ((totalRead * 100) / totalSize).toInt()
                        }
                    }
                    raf.close()
                    input.close()
                    conn.disconnect()

                    // MD5 校验
                    if (digest != null) {
                        val actualMd5 = digest.digest().joinToString("") { "%02x".format(it) }
                        if (!actualMd5.equals(expectedMd5, ignoreCase = true)) {
                            tmpFile.delete()
                            downloadedBytes = 0
                            _downloadProgress.value = 0
                            // 不清除状态，继续尝试下一个 URL
                            continue
                        }
                    }

                    // 成功！重命名为正式文件
                    if (finalFile.exists()) finalFile.delete()
                    tmpFile.renameTo(finalFile)
                    _downloadFile.value = finalFile
                    _downloadProgress.value = 100
                    _updateState.value = UpdateState.DOWNLOADED
                    showToast("新版本已下载，点击安装")
                    return@launch
                } catch (_: Exception) {
                    conn.disconnect()
                    // 尝试下一个 URL
                }
            }

            // 所有 URL 都失败
            _updateState.value = UpdateState.AVAILABLE
            _downloadProgress.value = 0
            showToast("下载失败，请在浏览器中手动下载")
        }
    }

    fun openDownloadInBrowser() {
        val url = _updateInfo.value.downloadUrl
        if (url.isBlank()) return
        val context = getApplication<Application>()
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    private fun verifyMd5(file: File, expected: String): Boolean {
        if (expected.isBlank() || !file.exists()) return false
        return try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            val actual = digest.digest(java.io.FileInputStream(file).use { it.readBytes() })
                .joinToString("") { "%02x".format(it) }
            actual.equals(expected, ignoreCase = true)
        } catch (_: Exception) { false }
    }

    fun installApk() {
        val file = _downloadFile.value ?: return
        if (!file.exists()) {
            showToast("安装文件不存在，请重新下载")
            return
        }

        val context = getApplication<Application>()

        // Android 8+ 需要 "安装未知应用" 权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                try {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:${context.packageName}")
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Exception) { }
                showToast("请先允许安装未知来源应用")
                return
            }
        }

        _updateState.value = UpdateState.IDLE

        try {
            // 方式1: ACTION_INSTALL_PACKAGE（标准安装，各ROM兼容性最好）
            val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(android.content.Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(android.content.Intent.EXTRA_RETURN_RESULT, false)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // 方式2: 回退到 ACTION_VIEW + 正确的 MIME type
            try {
                val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                showToast("安装失败，请在浏览器中手动安装")
            }
        }
    }

    fun cancelUpdate() {
        _downloadFile.value?.delete()
        _downloadFile.value = null
        _downloadProgress.value = 0
        _updateState.value = UpdateState.IDLE
    }

    fun postponeUpdate() {
        // 保存 APK 路径，下次启动时检查
        settings.pendingApkPath = _downloadFile.value?.absolutePath ?: ""
        _updateState.value = UpdateState.IDLE
    }

    fun checkPendingInstall() {
        val path = settings.pendingApkPath
        if (path.isNotBlank()) {
            val file = File(path)
            if (file.exists()) {
                _downloadFile.value = file
                _updateState.value = UpdateState.DOWNLOADED
            } else {
                settings.pendingApkPath = ""
            }
        }
    }

    // === 导出分享（CSV → Android SharePicker） ===
    fun shareTowerRecords(records: List<TowerCalcEntity>) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val csv = buildString {
                appendLine("编号,站位,顶平距(m),底平距(m),顶方位角,底方位角,左右偏位,前后偏位,日期")
                for (r in records) {
                    appendLine("${r.number},${r.standingPosition},${r.data1},${r.data2},${r.data3},${r.data4},${r.resultLeftRight},${r.resultForwardBack},${r.createdAt.take(10)}")
                }
            }

            val file = File(context.cacheDir, "tower_share.csv")
            if (file.exists()) file.delete()
            file.writeText(csv)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享扣塔记录").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
