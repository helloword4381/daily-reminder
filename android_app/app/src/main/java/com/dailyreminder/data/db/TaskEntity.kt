package com.dailyreminder.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val content: String,
    val done: Boolean = false,
    val priority: String = "日常",
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    fun toTask() = com.dailyreminder.data.model.Task(id, content, done, priority, createdAt, updatedAt)

    companion object {
        fun fromTask(task: com.dailyreminder.data.model.Task) = TaskEntity(
            id = task.id,
            content = task.content,
            done = task.done,
            priority = task.priority,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt
        )
    }
}
