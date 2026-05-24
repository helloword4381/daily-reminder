package com.dailyreminder.data.model

import kotlinx.serialization.Serializable

/**
 * 任务数据模型，与 Windows 端 common/models.py 一致
 */
@Serializable
data class Task(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String = "",
    val done: Boolean = false,
    val priority: String = "日常",
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    companion object {
        fun now(): String {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            return sdf.format(java.util.Date())
        }
    }
}
