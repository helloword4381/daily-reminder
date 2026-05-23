package com.dailyreminder.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE date(createdAt) = :date ORDER BY updatedAt DESC")
    fun getByDate(date: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE done = 0 ORDER BY updatedAt DESC")
    fun getPending(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE done = 0 ORDER BY updatedAt DESC")
    suspend fun getPendingList(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE updatedAt > :since")
    suspend fun getUpdatedSince(since: String): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE tasks SET done = :done, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDone(id: String, done: Boolean, updatedAt: String)

    @Query("SELECT updatedAt FROM tasks ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLastUpdated(): String?
}
