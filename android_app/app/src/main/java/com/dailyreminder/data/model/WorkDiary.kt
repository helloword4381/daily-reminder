package com.dailyreminder.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkDiary(
    val id: String = java.util.UUID.randomUUID().toString(),
    val date: String = "",          // yyyy-MM-dd
    val title: String = "",
    val content: String = "",
    val imagePath: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    companion object {
        fun now(): String {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            return sdf.format(java.util.Date())
        }
        fun today(): String {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            return sdf.format(java.util.Date())
        }
    }
}
