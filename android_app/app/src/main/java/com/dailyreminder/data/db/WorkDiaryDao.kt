package com.dailyreminder.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDiaryDao {
    @Query("SELECT * FROM work_diary ORDER BY date DESC, createdAt DESC")
    fun getAll(): Flow<List<WorkDiaryEntity>>

    @Query("SELECT * FROM work_diary WHERE date = :date ORDER BY createdAt DESC")
    fun getByDate(date: String): Flow<List<WorkDiaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(diary: WorkDiaryEntity)

    @Delete
    suspend fun delete(diary: WorkDiaryEntity)
}
