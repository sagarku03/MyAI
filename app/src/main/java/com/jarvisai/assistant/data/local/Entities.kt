package com.jarvisai.assistant.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val languageCode: String = "ENGLISH",
    val folder: String = "General",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val modelUsed: String = "",
    val memoriesUsedCount: Int = 0
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String, // MemoryCategory name
    val tags: String = "",
    val importance: String = "NORMAL", // LOW, NORMAL, IMPORTANT, CRITICAL
    val isPinned: Boolean = false,
    val expiresAtMillis: Long? = null,
    val accessCount: Int = 0,
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "working_memory_tasks")
data class WorkingMemoryTaskEntity(
    @PrimaryKey val taskId: String,
    val rootGoal: String,
    val status: String = "ACTIVE", // ACTIVE, PAUSED, COMPLETED, FAILED
    val stepsJson: String = "[]",
    val scratchpadJson: String = "{}",
    val currentStepIndex: Int = 0,
    val obstacleRetryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "episodic_memories",
    indices = [Index(value = ["timestampMs"]), Index(value = ["timeBucket"])]
)
data class EpisodicEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long = System.currentTimeMillis(),
    val timeBucket: String = "MORNING", // MORNING, AFTERNOON, EVENING, NIGHT
    val eventType: String = "CONVERSATION", // CONVERSATION, APP_ACTION, WORKFLOW, SYSTEM_EVENT
    val summary: String,
    val involvedApps: String = "",
    val involvedEntities: String = "",
    val outcomeStatus: String = "SUCCESS", // SUCCESS, FAILED, CANCELLED
    val importanceValence: Int = 2 // 1..5
)

@Entity(
    tableName = "semantic_triples",
    indices = [Index(value = ["subject", "predicate"]), Index(value = ["category"])]
)
data class SemanticTripleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val predicate: String,
    val objectValue: String,
    val category: String = "KNOWLEDGE", // IDENTITY, PREFERENCE, RELATIONSHIP, APP_UI_CONCEPT, KNOWLEDGE
    val confidence: Float = 1.0f,
    val source: String = "EXPLICIT", // EXPLICIT, INFERRED, LEARNED_UI, SEEDED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val scheduledTimeMillis: Long,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val trigger: String = "MANUAL",
    val isEnabled: Boolean = true,
    val actionsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long? = null
)

@Entity(tableName = "notification_logs")
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSensitive: Boolean = false
)

