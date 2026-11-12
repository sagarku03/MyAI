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
        WorkingMemoryTaskEntity::class,
        EpisodicEventEntity::class,
        SemanticTripleEntity::class,
        ReminderEntity::class,
        WorkflowEntity::class,
        NotificationLogEntity::class
    ],
    version = 4,
    exportSchema = false
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add accessCount and lastAccessedAt to memories
                db.execSQL("ALTER TABLE memories ADD COLUMN accessCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE memories ADD COLUMN lastAccessedAt INTEGER NOT NULL DEFAULT 0")

                // Create working_memory_tasks table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS working_memory_tasks (
                        taskId TEXT PRIMARY KEY NOT NULL,
                        rootGoal TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        stepsJson TEXT NOT NULL DEFAULT '[]',
                        scratchpadJson TEXT NOT NULL DEFAULT '{}',
                        currentStepIndex INTEGER NOT NULL DEFAULT 0,
                        obstacleRetryCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create episodic_memories table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS episodic_memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestampMs INTEGER NOT NULL,
                        timeBucket TEXT NOT NULL DEFAULT 'MORNING',
                        eventType TEXT NOT NULL DEFAULT 'CONVERSATION',
                        summary TEXT NOT NULL,
                        involvedApps TEXT NOT NULL DEFAULT '',
                        involvedEntities TEXT NOT NULL DEFAULT '',
                        outcomeStatus TEXT NOT NULL DEFAULT 'SUCCESS',
                        importanceValence INTEGER NOT NULL DEFAULT 2
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_episodic_memories_timestampMs ON episodic_memories(timestampMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_episodic_memories_timeBucket ON episodic_memories(timeBucket)")

                // Create semantic_triples table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS semantic_triples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        subject TEXT NOT NULL,
                        predicate TEXT NOT NULL,
                        objectValue TEXT NOT NULL,
                        category TEXT NOT NULL DEFAULT 'KNOWLEDGE',
                        confidence REAL NOT NULL DEFAULT 1.0,
                        source TEXT NOT NULL DEFAULT 'EXPLICIT',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_semantic_triples_subject_predicate ON semantic_triples(subject, predicate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_semantic_triples_category ON semantic_triples(category)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN thoughtText TEXT")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN thoughtDurationMs INTEGER")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN toolCallsJson TEXT")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN tokenUsageJson TEXT")
            }
        }

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_hud_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

