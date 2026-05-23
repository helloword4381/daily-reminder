package com.dailyreminder

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailyreminder.data.db.*
import com.dailyreminder.data.model.WorkDiary
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    val todayPending: StateFlow<List<TaskEntity>> = allTasks.map { list ->
        val today = com.dailyreminder.data.model.Task.now().take(10)
        list.filter { !it.done && it.createdAt.take(10) == today }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTask(content: String) {
        viewModelScope.launch {
            val now = com.dailyreminder.data.model.Task.now()
            val task = com.dailyreminder.data.model.Task(content = content, createdAt = now, updatedAt = now)
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

    fun addDiary(date: String, title: String, content: String) {
        viewModelScope.launch {
            val now = WorkDiary.now()
            val entry = WorkDiary(date = date, title = title, content = content, createdAt = now, updatedAt = now)
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
        val version: String = "",
        val title: String = "",
        val notes: String = "",
        val downloadUrl: String = ""
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

    companion object {
        private const val APK_FILENAME = "update.apk"
    }

    /** 语义化版本比较，返回 true 如果 remote > current */
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

    /** 清除 Toast 消息（延迟） */
    private fun showToast(msg: String, durationMs: Long = 2500) {
        _toastMessage.value = msg
        viewModelScope.launch {
            kotlinx.coroutines.delay(durationMs)
            _toastMessage.value = ""
        }
    }

    /** 尝试从多个来源获取版本信息（按可用性排序，国内友好） */
    private suspend fun fetchVersionJson(): String? {
        val urls = listOf(
            "https://cdn.jsdelivr.net/gh/helloword4381/daily-reminder@main/version.json",
            "https://raw.githubusercontent.com/helloword4381/daily-reminder/main/version.json",
            "https://api.github.com/repos/helloword4381/daily-reminder/releases/latest"
        )
        for (url in urls) {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "DailyReminder")
                if (url.contains("api.github.com")) {
                    conn.setRequestProperty("Accept", "application/json")
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                if (text.isNotBlank()) return text
            } catch (_: Exception) { }
        }
        return null
    }

    private fun parseVersionJson(json: String, isGithubApi: Boolean): Triple<String, String, String> {
        fun extract(key: String) = json.lines().firstOrNull {
            it.trimStart().startsWith("\"$key\"")
        }?.substringAfter(":")?.trim()?.trim('"', ',', ' ') ?: ""

        if (isGithubApi) {
            val tag = extract("tag_name")
            val dlUrl = json.lines().firstOrNull {
                it.trimStart().startsWith("\"browser_download_url\"") && it.contains(".apk")
            }?.substringAfter(":")?.trim()?.trim('"', ',', ' ') ?: ""
            return Triple(tag.removePrefix("v").removePrefix("build-"), extract("body"), dlUrl)
        } else {
            return Triple(extract("version"), extract("notes"), extract("downloadUrl"))
        }
    }

    fun checkForUpdate() {
        _updateState.value = UpdateState.CHECKING
        viewModelScope.launch {
            try {
                val currentVer = getApplication<Application>().packageManager
                    .getPackageInfo(getApplication<Application>().packageName, 0)
                    .versionName ?: "0.0.0"

                val json = fetchVersionJson()
                if (json == null) {
                    _updateState.value = UpdateState.IDLE
                    showToast("检查更新失败，请检查网络")
                    return@launch
                }

                val isApi = json.contains("\"tag_name\"")
                val (remoteVer, notes, dlUrl) = parseVersionJson(json, isApi)
                val hasNew = isNewerVersion(remoteVer, currentVer)

                _updateInfo.value = UpdateInfo(
                    hasUpdate = hasNew,
                    version = remoteVer,
                    title = "日常助手 v$remoteVer",
                    notes = notes,
                    downloadUrl = dlUrl
                )
                if (hasNew) {
                    _updateState.value = UpdateState.AVAILABLE
                } else {
                    _updateState.value = UpdateState.IDLE
                    showToast("已是最新版本 v$currentVer")
                }
            } catch (_: Exception) {
                _updateInfo.value = UpdateInfo()
                _updateState.value = UpdateState.IDLE
                showToast("检查更新失败，请检查网络")
            }
        }
    }

    fun downloadApk() {
        val url = _updateInfo.value.downloadUrl ?: return
        if (url.isBlank()) return
        _updateState.value = UpdateState.DOWNLOADING
        _downloadProgress.value = 0

        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val file = File(context.cacheDir, APK_FILENAME)
                if (file.exists()) file.delete()

                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.connect()

                val totalSize = conn.contentLengthLong
                val input = conn.inputStream
                val output = file.outputStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalSize > 0) {
                        _downloadProgress.value = ((totalRead * 100) / totalSize).toInt()
                    }
                }
                output.close()
                input.close()
                conn.disconnect()

                _downloadFile.value = file
                _downloadProgress.value = 100
                _updateState.value = UpdateState.DOWNLOADED
            } catch (_: Exception) {
                _updateState.value = UpdateState.AVAILABLE
                _downloadProgress.value = 0
            }
        }
    }

    fun installApk() {
        val file = _downloadFile.value ?: return
        if (!file.exists()) return

        _updateState.value = UpdateState.INSTALLING
        val context = getApplication<Application>()
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) { }
        _updateState.value = UpdateState.IDLE
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
                appendLine("编号,距扣塔顶平距(m),距扣塔底平距(m),塔顶实测方位角,扣塔底实测方位角,左右偏位,前后偏位,日期")
                for (r in records) {
                    appendLine("${r.number},${r.data1},${r.data2},${r.data3},${r.data4},${r.resultLeftRight},${r.resultForwardBack},${r.createdAt.take(10)}")
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
