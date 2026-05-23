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

    private val _updateInfo = MutableStateFlow(UpdateInfo())
    val updateInfo: StateFlow<UpdateInfo> = _updateInfo.asStateFlow()

    private val _checkingUpdate = MutableStateFlow(true)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            try {
                val currentVer = getApplication<Application>().packageManager
                    .getPackageInfo(getApplication<Application>().packageName, 0)
                    .versionName ?: "0.0.0"
                val url = java.net.URL("https://api.github.com/repos/helloword4381/daily-reminder/releases/latest")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/json")
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                // 极简 JSON 解析（不依赖第三方库）
                val tag = json.lines().firstOrNull { it.trimStart().startsWith("\"tag_name\"") }
                    ?.substringAfter(":")?.trim()?.trim('"', ',', ' ') ?: ""
                val relName = json.lines().firstOrNull { it.trimStart().startsWith("\"name\"") }
                    ?.substringAfter(":")?.trim()?.trim('"', ',', ' ') ?: ""
                val relBody = json.lines().firstOrNull { it.trimStart().startsWith("\"body\"") }
                    ?.substringAfter(":")?.trim()?.trim('"', ',', ' ') ?: ""
                // 提取第一个 APK 下载链接（browser_download_url）
                val dlUrl = json.lines().firstOrNull {
                    it.trimStart().startsWith("\"browser_download_url\"") &&
                    it.contains(".apk")
                }?.substringAfter(":")?.trim()?.trim('"', ',', ' ') ?: ""

                // 比较版本号
                val remoteVer = tag.removePrefix("v").removePrefix("build-")
                val hasNew = remoteVer > currentVer

                _updateInfo.value = UpdateInfo(
                    hasUpdate = hasNew,
                    version = remoteVer,
                    title = relName,
                    notes = relBody,
                    downloadUrl = dlUrl
                )
            } catch (_: Exception) {
                _updateInfo.value = UpdateInfo()
            } finally {
                _checkingUpdate.value = false
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
