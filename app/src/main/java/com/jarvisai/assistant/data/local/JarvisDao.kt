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
