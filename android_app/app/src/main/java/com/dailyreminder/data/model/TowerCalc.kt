package com.dailyreminder.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TowerCalc(
    val id: String = java.util.UUID.randomUUID().toString(),
    val standingPosition: String = "小里程",  // 站位：小里程/大里程/左幅/右幅
    val number: String = "",         // 墩柱（扣塔）编号
    val data1: Double = 0.0,         // 距墩柱（扣塔）顶平距 (米)
    val data2: Double = 0.0,         // 距墩柱（扣塔）底平距 (米)
    val data3: Double = 0.0,         // 墩柱顶实测方位角
    val data4: Double = 0.0,         // 墩柱底实测方位角
    val resultLeftRight: String = "", // 左右偏位结果
    val resultForwardBack: String = "", // 前后偏位结果
    val createdAt: String = ""
) {
    companion object {
        fun now(): String {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            return sdf.format(java.util.Date())
        }
    }
}
