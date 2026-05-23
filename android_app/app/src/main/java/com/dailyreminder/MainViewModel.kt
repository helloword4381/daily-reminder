package com.dailyreminder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailyreminder.data.db.AppDatabase
import com.dailyreminder.data.db.TaskEntity
import com.dailyreminder.data.model.Task
import com.dailyreminder.sync.DiscoveryService
import com.dailyreminder.sync.SyncClient
import com.dailyreminder.sync.SyncProtocol
import com.dailyreminder.sync.SyncResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val taskDao = db.taskDao()
    private val discoveryService = DiscoveryService(application)

    // 所有任务
    val allTasks: StateFlow<List<TaskEntity>> = taskDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 今日未完成
    val todayPending: StateFlow<List<TaskEntity>> = allTasks.map { list ->
        val today = Task.now().take(10) // yyyy-MM-dd
        list.filter { !it.done && it.createdAt.take(10) == today }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 同步状态
    private val _syncStatus = MutableStateFlow("未连接")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _discoveredHost = MutableStateFlow<String?>(null)
    val discoveredHost: StateFlow<String?> = _discoveredHost.asStateFlow()

    private var syncClient: SyncClient? = null

    // 新增任务
    fun addTask(content: String) {
        viewModelScope.launch {
            val now = Task.now()
            val task = Task(content = content, createdAt = now, updatedAt = now)
            taskDao.upsert(TaskEntity.fromTask(task))
        }
    }

    // 切换完成状态
    fun toggleDone(id: String, currentDone: Boolean) {
        viewModelScope.launch {
            taskDao.setDone(id, !currentDone, Task.now())
        }
    }

    // 删除任务
    fun deleteTask(id: String) {
        viewModelScope.launch {
            taskDao.delete(id)
        }
    }

    // 按日期查询
    fun getTasksByDate(date: String) = allTasks.map { list ->
        list.filter { it.createdAt.take(10) == date }
    }

    // 开始发现
    fun startDiscovery() {
        discoveryService.start { info ->
            _discoveredHost.value = info.ip
            _syncStatus.value = "已发现: ${info.hostname}"
        }
        _syncStatus.value = "正在搜索..."
    }

    // 同步
    fun syncNow() {
        val host = _discoveredHost.value ?: return
        viewModelScope.launch {
            _syncStatus.value = "同步中..."
            syncClient = SyncClient(taskDao)
            val result = syncClient!!.sync(host)
            when (result) {
                is SyncResult.Success -> _syncStatus.value = "已同步 (+${result.merged}, -${result.skipped})"
                is SyncResult.Error -> _syncStatus.value = "同步失败: ${result.message}"
            }
        }
    }

    override fun onCleared() {
        discoveryService.stop()
        super.onCleared()
    }
}
