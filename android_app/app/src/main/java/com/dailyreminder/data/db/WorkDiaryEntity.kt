package com.dailyreminder.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_diary")
data class WorkDiaryEntity(
    @PrimaryKey val id: String,
    val date: String,
    val title: String,
    val content: String,
    val imagePath: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    fun toModel() = com.dailyreminder.data.model.WorkDiary(id, date, title, content, imagePath, createdAt, updatedAt)
    companion object {
        fun fromModel(m: com.dailyreminder.data.model.WorkDiary) = WorkDiaryEntity(
            id = m.id, date = m.date, title = m.title, content = m.content, imagePath = m.imagePath,
            createdAt = m.createdAt, updatedAt = m.updatedAt
        )
    }
}
