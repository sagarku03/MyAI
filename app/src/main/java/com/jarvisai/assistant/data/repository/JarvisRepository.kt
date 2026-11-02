package com.jarvisai.assistant.data.repository

import com.jarvisai.assistant.data.local.ChatMessageEntity
import com.jarvisai.assistant.data.local.ChatSessionEntity
import com.jarvisai.assistant.data.local.JarvisDao
import com.jarvisai.assistant.data.local.MemoryEntity
import com.jarvisai.assistant.data.local.ReminderEntity
import com.jarvisai.assistant.model.MemoryCategory
import kotlinx.coroutines.flow.Flow

class JarvisRepository(
    private val dao: JarvisDao,
    val memoryRepository: MemoryRepository = MemoryRepository(dao)
) {

    // --- CHAT SESSIONS & MESSAGES ---
    val allChatSessions: Flow<List<ChatSessionEntity>> = dao.getAllChatSessions()

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>> =
        dao.getMessagesForSession(sessionId)

    suspend fun getMessagesForSessionOnce(sessionId: Long): List<ChatMessageEntity> =
        dao.getMessagesForSessionOnce(sessionId)

    suspend fun createNewChatSession(title: String = "New Tactical Session", languageCode: String = "HINGLISH"): Long {
        val now = System.currentTimeMillis()
        return dao.insertChatSession(
            ChatSessionEntity(
                title = title,
                languageCode = languageCode,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateSessionTitleAndTimestamp(sessionId: Long, newTitle: String? = null) {
        val existing = dao.getChatSessionById(sessionId) ?: return
        dao.updateChatSession(
            existing.copy(
                title = newTitle ?: existing.title,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun insertMessage(message: ChatMessageEntity): Long {
        val id = dao.insertChatMessage(message)
        updateSessionTitleAndTimestamp(message.sessionId)
        return id
    }

    suspend fun updateMessage(message: ChatMessageEntity) {
        dao.updateChatMessage(message)
        updateSessionTitleAndTimestamp(message.sessionId)
    }

    suspend fun deleteMessageById(messageId: Long) {
        dao.deleteChatMessageById(messageId)
    }

    suspend fun deleteChatSession(sessionId: Long) {
        dao.deleteChatSessionById(sessionId)
    }

    suspend fun clearAllChatHistory() {
        dao.clearAllChatSessions()
    }

    // --- MEMORY CENTER (Delegated to MemoryRepository) ---
    val allMemories: Flow<List<MemoryEntity>> = memoryRepository.allMemories

    suspend fun saveApprovedMemory(
        title: String,
        content: String,
        category: MemoryCategory,
        tags: String = "",
        isPinned: Boolean = false
    ): Long {
        val saved = memoryRepository.saveOrUpdateMemory(
            title = title,
            content = content,
            category = category,
            tags = tags,
            isPinned = isPinned
        )
        return saved?.id ?: 0L
    }

    suspend fun detectAndSaveAutomaticMemories(userMessage: String): AutomaticMemorySaveResult {
        return memoryRepository.detectAndSaveAutomaticMemories(userMessage)
    }

    suspend fun updateMemory(memory: MemoryEntity) {
        memoryRepository.updateMemory(memory)
    }

    suspend fun deleteMemoryById(memoryId: Long) {
        memoryRepository.deleteMemoryById(memoryId)
    }

    suspend fun clearAllMemories() {
        memoryRepository.clearAllMemories()
    }

    suspend fun retrieveRelevantMemories(query: String, maxCount: Int = 10): List<MemoryEntity> {
        return memoryRepository.retrieveRelevantMemories(query, maxCount)
    }

    // --- REMINDERS ---
    val allReminders: Flow<List<ReminderEntity>> = dao.getAllReminders()

    suspend fun insertReminder(title: String, notes: String, scheduledTimeMillis: Long): Long {
        return dao.insertReminder(
            ReminderEntity(
                title = title.trim(),
                notes = notes.trim(),
                scheduledTimeMillis = scheduledTimeMillis
            )
        )
    }

    suspend fun toggleReminderCompleted(reminder: ReminderEntity) {
        dao.updateReminder(reminder.copy(isCompleted = !reminder.isCompleted))
    }

    suspend fun deleteReminder(reminderId: Long) {
        dao.deleteReminderById(reminderId)
    }
}
