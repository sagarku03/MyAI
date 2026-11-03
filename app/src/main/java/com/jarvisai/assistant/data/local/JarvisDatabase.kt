package com.jarvisai.assistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        MemoryEntity::class,
        ReminderEntity::class,
        WorkflowEntity::class,
        NotificationLogEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class JarvisDatabase : RoomDatabase() {

    abstract fun jarvisDao(): JarvisDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to chat_sessions
                db.execSQL("ALTER TABLE chat_sessions ADD COLUMN folder TEXT NOT NULL DEFAULT 'General'")
                db.execSQL("ALTER TABLE chat_sessions ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_sessions ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")

                // Add new columns to memories
                db.execSQL("ALTER TABLE memories ADD COLUMN importance TEXT NOT NULL DEFAULT 'NORMAL'")
                db.execSQL("ALTER TABLE memories ADD COLUMN expiresAtMillis INTEGER")

                // Create workflows table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS workflows (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        trigger TEXT NOT NULL DEFAULT 'MANUAL',
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        actionsJson TEXT NOT NULL DEFAULT '[]',
                        createdAt INTEGER NOT NULL,
                        lastRunAt INTEGER
                    )
                """.trimIndent())

                // Create notification_logs table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS notification_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        packageName TEXT NOT NULL,
                        appName TEXT NOT NULL,
                        title TEXT NOT NULL,
                        text TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isSensitive INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_hud_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
