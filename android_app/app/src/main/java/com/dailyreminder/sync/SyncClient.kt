package com.dailyreminder.sync

import android.util.Log
import com.dailyreminder.data.db.TaskDao
import com.dailyreminder.data.db.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * TCP 同步客户端
 * 连接到 Windows 端，拉取和推送变更
 */
class SyncClient(private val taskDao: TaskDao) {

    private val tag = "SyncClient"
    private var socket: Socket? = null

    suspend fun sync(host: String, port: Int = SyncProtocol.SYNC_PORT): SyncResult = withContext(Dispatchers.IO) {
        try {
            socket = Socket().apply {
                connect(InetSocketAddress(host, port), SyncProtocol.TIMEOUT_MS.toInt())
                soTimeout = SyncProtocol.TIMEOUT_MS.toInt()
            }

            val input = socket!!.getInputStream()
            val output = socket!!.getOutputStream()

            // 1. 发送本地变更
            val lastSync = taskDao.getLastUpdated() ?: ""
            val localTasks = taskDao.getUpdatedSince(lastSync)
            val syncOut = SyncProtocol.SyncMessage(
                type = "sync",
                tasks = localTasks.map { it.toSyncTask() },
                syncToken = lastSync
            )
            output.writeMessage(syncOut)

            // 2. 接收远端变更
            val syncIn = input.readMessage()
            val remoteTasks = syncIn.tasks

            // 3. 合并（按 updatedAt 时间戳，最新的胜出）
            var merged = 0
            var skipped = 0
            for (remote in remoteTasks) {
                val existing = localTasks.find { it.id == remote.id }
                if (existing == null || remote.updatedAt > existing.updatedAt) {
                    taskDao.upsert(TaskEntity.fromTask(remote.toTask()))
                    merged++
                } else {
                    skipped++
                }
            }

            // 4. 发送合并结果给服务器
            val response = SyncProtocol.SyncMessage(
                type = "sync_response",
                syncToken = taskDao.getLastUpdated() ?: ""
            )
            output.writeMessage(response)

            Log.i(tag, "Sync complete: $merged merged, $skipped skipped")
            SyncResult.Success(merged = merged, skipped = skipped)
        } catch (e: Exception) {
            Log.e(tag, "Sync failed", e)
            SyncResult.Error(e.message ?: "Unknown error")
        } finally {
            socket?.close()
            socket = null
        }
    }

    private fun SyncProtocol.SyncTask.toTask() = com.dailyreminder.data.model.Task(
        id = id, content = content, done = done,
        createdAt = createdAt, updatedAt = updatedAt
    )

    private fun TaskEntity.toSyncTask() = SyncProtocol.SyncTask(
        id = id, content = content, done = done,
        createdAt = createdAt, updatedAt = updatedAt
    )

    private fun OutputStream.writeMessage(msg: SyncProtocol.SyncMessage) {
        val data = SyncProtocol.encode(msg)
        this.write(data)
        this.flush()
    }

    private fun InputStream.readMessage(): SyncProtocol.SyncMessage {
        // 读取 4 字节长度前缀
        val lenBytes = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = this.read(lenBytes, read, 4 - read)
            if (n == -1) throw java.io.EOFException("Connection closed")
            read += n
        }
        val len = ((lenBytes[0].toInt() and 0xFF) shl 24) or
                ((lenBytes[1].toInt() and 0xFF) shl 16) or
                ((lenBytes[2].toInt() and 0xFF) shl 8) or
                (lenBytes[3].toInt() and 0xFF)

        // 读取消息体
        val data = ByteArray(len)
        read = 0
        while (read < len) {
            val n = this.read(data, read, len - read)
            if (n == -1) throw java.io.EOFException("Connection closed")
            read += n
        }
        return SyncProtocol.decode(data)
    }
}

sealed class SyncResult {
    data class Success(val merged: Int, val skipped: Int) : SyncResult()
    data class Error(val message: String) : SyncResult()
}
