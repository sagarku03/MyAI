package com.jarvisai.assistant.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JarvisDao {

    // --- CHAT SESSIONS ---
    @Query("SELECT * FROM chat_sessions ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllChatSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getActiveChatSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedChatSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE folder = :folder AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getChatSessionsByFolder(folder: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getChatSessionById(sessionId: Long): ChatSessionEntity?

    @Query("UPDATE chat_sessions SET title = :newTitle, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun renameChatSession(sessionId: Long, newTitle: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun setChatSessionPinned(sessionId: Long, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET isArchived = :isArchived, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun setChatSessionArchived(sessionId: Long, isArchived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET folder = :folder, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun setChatSessionFolder(sessionId: Long, folder: String, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatSession(session: ChatSessionEntity): Long

    @Update
    suspend fun updateChatSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteChatSessionById(sessionId: Long)

    @Query("DELETE FROM chat_sessions")
    suspend fun clearAllChatSessions()

    // --- CHAT MESSAGES ---
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionOnce(sessionId: Long): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    suspend fun searchMessages(query: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    @Update
    suspend fun updateChatMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteChatMessageById(messageId: Long)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearMessagesForSession(sessionId: Long)

    // --- MEMORIES ---
    @Query("SELECT * FROM memories ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getAllMemoriesOnce(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE title LIKE :title LIMIT 1")
    suspend fun findMemoryByTitle(title: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :snippet || '%' LIMIT 1")
    suspend fun findMemoryByContentSnippet(snippet: String): MemoryEntity?

    @Query("DELETE FROM memories WHERE expiresAtMillis IS NOT NULL AND expiresAtMillis < :now")
    suspend fun purgeExpiredMemories(now: Long = System.currentTimeMillis()): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :memoryId")
    suspend fun deleteMemoryById(memoryId: Long)

    @Query("DELETE FROM memories")
    suspend fun clearAllMemories()

    @Query("UPDATE memories SET accessCount = accessCount + 1, lastAccessedAt = :accessedAt WHERE id = :memoryId")
    suspend fun incrementMemoryAccess(memoryId: Long, accessedAt: Long = System.currentTimeMillis())

    // --- WORKING MEMORY TASKS (TIER 2 CHECKPOINTS) ---
    @Query("SELECT * FROM working_memory_tasks ORDER BY updatedAt DESC")
    fun getAllWorkingMemoryTasks(): Flow<List<WorkingMemoryTaskEntity>>

    @Query("SELECT * FROM working_memory_tasks WHERE status = 'ACTIVE' OR status = 'PAUSED' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getActiveWorkingMemoryTaskOnce(): WorkingMemoryTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkingMemoryTask(task: WorkingMemoryTaskEntity)

    @Query("DELETE FROM working_memory_tasks WHERE taskId = :taskId")
    suspend fun deleteWorkingMemoryTaskById(taskId: String)

    @Query("DELETE FROM working_memory_tasks")
    suspend fun clearAllWorkingMemoryTasks()

    // --- EPISODIC MEMORIES (TIER 3 TIMELINE) ---
    @Query("SELECT * FROM episodic_memories ORDER BY timestampMs DESC LIMIT 250")
    fun getAllEpisodicEvents(): Flow<List<EpisodicEventEntity>>

    @Query("SELECT * FROM episodic_memories ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecentEpisodicEventsOnce(limit: Int = 50): List<EpisodicEventEntity>

    @Query("SELECT * FROM episodic_memories WHERE timestampMs BETWEEN :startMs AND :endMs ORDER BY timestampMs DESC")
    suspend fun getEpisodicEventsInRange(startMs: Long, endMs: Long): List<EpisodicEventEntity>

    @Query(
        "SELECT * FROM episodic_memories WHERE " +
            "(timestampMs BETWEEN :startMs AND :endMs) AND " +
            "(:timeBucket = '' OR timeBucket = :timeBucket) " +
            "ORDER BY importanceValence DESC, timestampMs DESC"
    )
    suspend fun getEpisodicEventsInRangeAndBucket(
        startMs: Long,
        endMs: Long,
        timeBucket: String = ""
    ): List<EpisodicEventEntity>

    @Query(
        "SELECT * FROM episodic_memories WHERE " +
            "summary LIKE '%' || :query || '%' OR " +
            "involvedApps LIKE '%' || :query || '%' OR " +
            "involvedEntities LIKE '%' || :query || '%' " +
            "ORDER BY timestampMs DESC LIMIT 30"
    )
    suspend fun searchEpisodicEvents(query: String): List<EpisodicEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodicEvent(event: EpisodicEventEntity): Long

    @Query("DELETE FROM episodic_memories WHERE id = :eventId")
    suspend fun deleteEpisodicEventById(eventId: Long)

    @Query("DELETE FROM episodic_memories")
    suspend fun clearAllEpisodicEvents()

    // --- SEMANTIC KNOWLEDGE GRAPH TRIPLES (TIER 4) ---
    @Query("SELECT * FROM semantic_triples ORDER BY confidence DESC, updatedAt DESC")
    fun getAllSemanticTriples(): Flow<List<SemanticTripleEntity>>

    @Query("SELECT * FROM semantic_triples ORDER BY confidence DESC, updatedAt DESC")
    suspend fun getAllSemanticTriplesOnce(): List<SemanticTripleEntity>

    @Query(
        "SELECT * FROM semantic_triples WHERE " +
            "LOWER(subject) = LOWER(:subject) AND LOWER(predicate) = LOWER(:predicate) LIMIT 1"
    )
    suspend fun findSemanticTripleBySubjectAndPredicate(
        subject: String,
        predicate: String
    ): SemanticTripleEntity?

    @Query(
        "SELECT * FROM semantic_triples WHERE " +
            "subject LIKE '%' || :query || '%' OR " +
            "predicate LIKE '%' || :query || '%' OR " +
            "objectValue LIKE '%' || :query || '%' " +
            "ORDER BY confidence DESC, updatedAt DESC LIMIT 30"
    )
    suspend fun searchSemanticTriples(query: String): List<SemanticTripleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSemanticTriple(triple: SemanticTripleEntity): Long

    @Update
    suspend fun updateSemanticTriple(triple: SemanticTripleEntity)

    @Query("DELETE FROM semantic_triples WHERE id = :tripleId")
    suspend fun deleteSemanticTripleById(tripleId: Long)

    @Query("DELETE FROM semantic_triples")
    suspend fun clearAllSemanticTriples()

    // --- REMINDERS ---
    @Query("SELECT * FROM reminders ORDER BY isCompleted ASC, scheduledTimeMillis ASC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :reminderId")
    suspend fun deleteReminderById(reminderId: Long)

    // --- WORKFLOWS ---
    @Query("SELECT * FROM workflows ORDER BY createdAt ASC")
    fun getAllWorkflows(): Flow<List<WorkflowEntity>>

    @Query("SELECT * FROM workflows WHERE isEnabled = 1")
    suspend fun getEnabledWorkflowsOnce(): List<WorkflowEntity>

    @Query("SELECT * FROM workflows WHERE id = :id LIMIT 1")
    suspend fun getWorkflowById(id: Long): WorkflowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkflow(workflow: WorkflowEntity): Long

    @Update
    suspend fun updateWorkflow(workflow: WorkflowEntity)

    @Query("DELETE FROM workflows WHERE id = :id")
    suspend fun deleteWorkflowById(id: Long)

    // --- NOTIFICATION LOGS ---
    @Query("SELECT * FROM notification_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllNotificationLogs(): Flow<List<NotificationLogEntity>>

    @Query("SELECT * FROM notification_logs ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecentNotificationLogsOnce(): List<NotificationLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationLog(log: NotificationLogEntity): Long

    @Query("DELETE FROM notification_logs")
    suspend fun clearAllNotificationLogs()
}

