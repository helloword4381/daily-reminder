package com.dailyreminder.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tower_calc")
data class TowerCalcEntity(
    @PrimaryKey val id: String,
    val standingPosition: String = "小里程",
    val number: String,
    val data1: Double,
    val data2: Double,
    val data3: Double,
    val data4: Double,
    val resultLeftRight: String,
    val resultForwardBack: String,
    val createdAt: String = ""
) {
    fun toModel() = com.dailyreminder.data.model.TowerCalc(
        id, standingPosition, number, data1, data2, data3, data4, resultLeftRight, resultForwardBack, createdAt
    )
    companion object {
        fun fromModel(m: com.dailyreminder.data.model.TowerCalc) = TowerCalcEntity(
            id = m.id, standingPosition = m.standingPosition, number = m.number,
            data1 = m.data1, data2 = m.data2, data3 = m.data3, data4 = m.data4,
            resultLeftRight = m.resultLeftRight, resultForwardBack = m.resultForwardBack,
            createdAt = m.createdAt
        )
    }
}
