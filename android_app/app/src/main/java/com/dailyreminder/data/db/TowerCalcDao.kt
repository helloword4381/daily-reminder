package com.dailyreminder.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TowerCalcDao {
    @Query("SELECT * FROM tower_calc ORDER BY createdAt DESC")
    fun getAll(): Flow<List<TowerCalcEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(calc: TowerCalcEntity)

    @Delete
    suspend fun delete(calc: TowerCalcEntity)

    @Query("DELETE FROM tower_calc WHERE id = :id")
    suspend fun deleteById(id: String)
}
