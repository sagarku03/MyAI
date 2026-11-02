package com.jarvisai.assistant.data.repository

import com.jarvisai.assistant.data.local.ChatMessageEntity
import com.jarvisai.assistant.data.local.ChatSessionEntity
import com.jarvisai.assistant.data.local.JarvisDao
import com.jarvisai.assistant.data.local.MemoryEntity
import com.jarvisai.assistant.data.local.ReminderEntity
import com.jarvisai.assistant.model.MemoryCategory
import kotlinx.coroutines.flow.Flow

class JarvisRepository(private val dao: JarvisDao) {

    // --- CHAT SESSIONS & MESSAGES ---
    val allChatSessions: Flow<List<ChatSessionEntity>> = dao.getAllChatSessions()

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>> =
        dao.getMessagesForSession(sessionId)

    suspend fun getMessagesForSessionOnce(sessionId: Long): List<ChatMessageEntity> =
        dao.getMessagesForSessionOnce(sessionId)

    suspend fun createNewChatSession(title: String = "New Tactical Session", languageCode: String = "ENGLISH"): Long {
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

    // --- MEMORY CENTER ---
    val allMemories: Flow<List<MemoryEntity>> = dao.getAllMemories()

    suspend fun saveApprovedMemory(
        title: String,
        content: String,
        category: MemoryCategory,
        tags: String = "",
        isPinned: Boolean = false
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insertMemory(
            MemoryEntity(
                title = title.trim(),
                content = content.trim(),
                category = category.name,
                tags = tags.trim(),
                isPinned = isPinned,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateMemory(memory: MemoryEntity) {
        dao.updateMemory(memory.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteMemoryById(memoryId: Long) {
        dao.deleteMemoryById(memoryId)
    }

    suspend fun clearAllMemories() {
        dao.clearAllMemories()
    }

    /**
     * Retrieves relevant user-approved memories based on query keyword overlap + pinned status.
     * Never automatically saves conversations—only reads memories explicitly approved by the user.
     */
    suspend fun retrieveRelevantMemories(query: String, maxCount: Int = 8): List<MemoryEntity> {
        val all = dao.getAllMemoriesOnce()
        if (all.isEmpty()) return emptyList()

        val tokens = query
            .lowercase()
            .split(Regex("[^a-zA-Z0-9\\u0900-\\u097F]+"))
            .filter { it.length >= 2 }
            .toSet()

        if (tokens.isEmpty()) {
            return all.sortedWith(compareByDescending<MemoryEntity> { it.isPinned }.thenByDescending { it.updatedAt })
                .take(maxCount)
        }

        val scored = all.map { memory ->
            val searchable = "${memory.title} ${memory.content} ${memory.category} ${memory.tags}".lowercase()
            val matchScore = tokens.count { token -> searchable.contains(token) } * 10
            val pinBonus = if (memory.isPinned) 15 else 0
            memory to (matchScore + pinBonus)
        }

        val matching = scored.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<MemoryEntity, Int>> { it.second }.thenByDescending { it.first.updatedAt })
            .map { it.first }

        return if (matching.isNotEmpty()) {
            matching.take(maxCount)
        } else {
            // Include pinned memories and top recent core memories for persistent personal context
            all.sortedWith(compareByDescending<MemoryEntity> { it.isPinned }.thenByDescending { it.updatedAt })
                .take(4)
        }
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
