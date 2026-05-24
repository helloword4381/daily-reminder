package com.dailyreminder.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, WorkDiaryEntity::class, TowerCalcEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun workDiaryDao(): WorkDiaryDao
    abstract fun towerCalcDao(): TowerCalcDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v3 → v4: 给 tasks 表加 priority 字段
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN priority TEXT NOT NULL DEFAULT '日常'")
            }
        }

        // v4 → v5: 占位，后续实际迁移在此追加
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 暂无变更
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "daily_reminder.db"
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
