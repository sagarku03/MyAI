package com.example

import com.jarvisai.assistant.data.local.ChatSessionEntity
import com.jarvisai.assistant.data.local.ChatMessageEntity
import com.jarvisai.assistant.data.local.JarvisDao
import com.jarvisai.assistant.data.local.MemoryEntity
import com.jarvisai.assistant.data.local.ReminderEntity
import com.jarvisai.assistant.data.local.WorkflowEntity
import com.jarvisai.assistant.data.local.NotificationLogEntity
import com.jarvisai.assistant.data.network.GeminiApiService
import com.jarvisai.assistant.data.repository.MemoryRepository
import com.jarvisai.assistant.model.MemoryCategory
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.VoiceGender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `system instruction includes assistant name language and approved memories`() {
        val service = GeminiApiService()
        val memories = listOf(
            MemoryEntity(
                id = 1L,
                title = "Name",
                content = "User's name is Sagar",
                category = MemoryCategory.PERSONAL.name
            )
        )
        val instruction = service.buildSystemInstruction(
            assistantName = "JARVIS",
            language = SupportedLanguage.HINGLISH,
            relevantMemories = memories
        )

        assertTrue(instruction.contains("JARVIS"))
        assertTrue(instruction.contains("Hinglish"))
        assertTrue(instruction.contains("Sagar"))
    }

    @Test
    fun `automatic memory repository saves Sagar and retrieves it for What is my name`() = runBlocking {
        val fakeDao = InMemoryTestJarvisDao()
        val memoryRepository = MemoryRepository(fakeDao)

        val saveName = memoryRepository.detectAndSaveAutomaticMemories("My name is Sagar. Remember this.")
        assertTrue(saveName.saved)
        assertTrue(saveName.confirmationMessage.contains("Memory saved successfully"))

        val saveLang = memoryRepository.detectAndSaveAutomaticMemories("My favorite language is Hindi. Remember it.")
        assertTrue(saveLang.saved)

        val memoriesForNameQuery = memoryRepository.retrieveRelevantMemories("What is my name?")
        assertTrue(memoriesForNameQuery.any { it.title == "Name" && it.content.contains("Sagar") })
        assertTrue(memoriesForNameQuery.any { it.title.contains("Favorite", ignoreCase = true) && it.content.contains("Hindi") })
    }

    @Test
    fun `voice options include both Male and Female voices and normal chat auto-speak defaults to false`() {
        val maleVoices = SupportedGeminiModels.voiceOptions.filter { it.gender == VoiceGender.MALE }
        val femaleVoices = SupportedGeminiModels.voiceOptions.filter { it.gender == VoiceGender.FEMALE }
        assertTrue(maleVoices.size >= 4)
        assertTrue(femaleVoices.size >= 4)
        assertEquals("Puck", SupportedGeminiModels.findVoiceOption("Puck", VoiceGender.MALE).id)
        assertEquals("Kore", SupportedGeminiModels.findVoiceOption("Kore", VoiceGender.FEMALE).id)
        assertEquals(false, com.jarvisai.assistant.model.UserSettings().autoSpeakChatReplies)
    }

    private class InMemoryTestJarvisDao : JarvisDao {
        private val memories = mutableListOf<MemoryEntity>()
        private val memoriesFlow = MutableStateFlow<List<MemoryEntity>>(emptyList())
        private var nextId = 1L

        override fun getAllChatSessions(): Flow<List<ChatSessionEntity>> = MutableStateFlow(emptyList())
        override suspend fun getChatSessionById(sessionId: Long): ChatSessionEntity? = null
        override suspend fun insertChatSession(session: ChatSessionEntity): Long = 1L
        override suspend fun updateChatSession(session: ChatSessionEntity) {}
        override suspend fun deleteChatSessionById(sessionId: Long) {}
        override suspend fun clearAllChatSessions() {}
        override fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>> = MutableStateFlow(emptyList())
        override suspend fun getMessagesForSessionOnce(sessionId: Long): List<ChatMessageEntity> = emptyList()
        override suspend fun insertChatMessage(message: ChatMessageEntity): Long = 1L
        override suspend fun updateChatMessage(message: ChatMessageEntity) {}
        override suspend fun deleteChatMessageById(messageId: Long) {}
        override suspend fun clearMessagesForSession(sessionId: Long) {}

        override fun getAllMemories(): Flow<List<MemoryEntity>> = memoriesFlow
        override suspend fun getAllMemoriesOnce(): List<MemoryEntity> = memories.toList()
        override suspend fun insertMemory(memory: MemoryEntity): Long {
            val id = if (memory.id == 0L) nextId++ else memory.id
            memories.removeAll { it.id == id }
            memories.add(memory.copy(id = id))
            memoriesFlow.value = memories.toList()
            return id
        }
        override suspend fun updateMemory(memory: MemoryEntity) {
            val idx = memories.indexOfFirst { it.id == memory.id }
            if (idx >= 0) {
                memories[idx] = memory
                memoriesFlow.value = memories.toList()
            }
        }
        override suspend fun deleteMemory(memory: MemoryEntity) {
            memories.removeAll { it.id == memory.id }
            memoriesFlow.value = memories.toList()
        }
        override suspend fun deleteMemoryById(memoryId: Long) {
            memories.removeAll { it.id == memoryId }
            memoriesFlow.value = memories.toList()
        }
        override suspend fun clearAllMemories() {
            memories.clear()
            memoriesFlow.value = emptyList()
        }

        override fun getAllReminders(): Flow<List<ReminderEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertReminder(reminder: ReminderEntity): Long = 1L
        override suspend fun updateReminder(reminder: ReminderEntity) {}
        override suspend fun deleteReminderById(reminderId: Long) {}

        override fun getActiveChatSessions(): Flow<List<ChatSessionEntity>> = MutableStateFlow(emptyList())
        override fun getArchivedChatSessions(): Flow<List<ChatSessionEntity>> = MutableStateFlow(emptyList())
        override fun getChatSessionsByFolder(folder: String): Flow<List<ChatSessionEntity>> = MutableStateFlow(emptyList())
        override suspend fun renameChatSession(sessionId: Long, newTitle: String, updatedAt: Long) {}
        override suspend fun setChatSessionPinned(sessionId: Long, isPinned: Boolean, updatedAt: Long) {}
        override suspend fun setChatSessionArchived(sessionId: Long, isArchived: Boolean, updatedAt: Long) {}
        override suspend fun setChatSessionFolder(sessionId: Long, folder: String, updatedAt: Long) {}
        override suspend fun searchMessages(query: String): List<ChatMessageEntity> = emptyList()

        override suspend fun findMemoryByTitle(title: String): MemoryEntity? = memories.find { it.title.equals(title, ignoreCase = true) }
        override suspend fun findMemoryByContentSnippet(snippet: String): MemoryEntity? = memories.find { it.content.contains(snippet, ignoreCase = true) }
        override suspend fun purgeExpiredMemories(now: Long): Int = 0

        override fun getAllWorkflows(): Flow<List<WorkflowEntity>> = MutableStateFlow(emptyList())
        override suspend fun getEnabledWorkflowsOnce(): List<WorkflowEntity> = emptyList()
        override suspend fun getWorkflowById(id: Long): WorkflowEntity? = null
        override suspend fun insertWorkflow(workflow: WorkflowEntity): Long = 1L
        override suspend fun updateWorkflow(workflow: WorkflowEntity) {}
        override suspend fun deleteWorkflowById(id: Long) {}

        override fun getAllNotificationLogs(): Flow<List<NotificationLogEntity>> = MutableStateFlow(emptyList())
        override suspend fun getRecentNotificationLogsOnce(): List<NotificationLogEntity> = emptyList()
        override suspend fun insertNotificationLog(log: NotificationLogEntity): Long = 1L
        override suspend fun clearAllNotificationLogs() {}

        override suspend fun incrementMemoryAccess(memoryId: Long, accessedAt: Long) {
            val idx = memories.indexOfFirst { it.id == memoryId }
            if (idx >= 0) {
                val cur = memories[idx]
                memories[idx] = cur.copy(accessCount = cur.accessCount + 1, lastAccessedAt = accessedAt)
                memoriesFlow.value = memories.toList()
            }
        }

        override fun getAllWorkingMemoryTasks(): Flow<List<com.jarvisai.assistant.data.local.WorkingMemoryTaskEntity>> = MutableStateFlow(emptyList())
        override suspend fun getActiveWorkingMemoryTaskOnce(): com.jarvisai.assistant.data.local.WorkingMemoryTaskEntity? = null
        override suspend fun upsertWorkingMemoryTask(task: com.jarvisai.assistant.data.local.WorkingMemoryTaskEntity) {}
        override suspend fun deleteWorkingMemoryTaskById(taskId: String) {}
        override suspend fun clearAllWorkingMemoryTasks() {}

        override fun getAllEpisodicEvents(): Flow<List<com.jarvisai.assistant.data.local.EpisodicEventEntity>> = MutableStateFlow(emptyList())
        override suspend fun getRecentEpisodicEventsOnce(limit: Int): List<com.jarvisai.assistant.data.local.EpisodicEventEntity> = emptyList()
        override suspend fun getEpisodicEventsInRange(startMs: Long, endMs: Long): List<com.jarvisai.assistant.data.local.EpisodicEventEntity> = emptyList()
        override suspend fun getEpisodicEventsInRangeAndBucket(startMs: Long, endMs: Long, timeBucket: String): List<com.jarvisai.assistant.data.local.EpisodicEventEntity> = emptyList()
        override suspend fun searchEpisodicEvents(query: String): List<com.jarvisai.assistant.data.local.EpisodicEventEntity> = emptyList()
        override suspend fun insertEpisodicEvent(event: com.jarvisai.assistant.data.local.EpisodicEventEntity): Long = 1L
        override suspend fun deleteEpisodicEventById(eventId: Long) {}
        override suspend fun clearAllEpisodicEvents() {}

        override fun getAllSemanticTriples(): Flow<List<com.jarvisai.assistant.data.local.SemanticTripleEntity>> = MutableStateFlow(emptyList())
        override suspend fun getAllSemanticTriplesOnce(): List<com.jarvisai.assistant.data.local.SemanticTripleEntity> = emptyList()
        override suspend fun findSemanticTripleBySubjectAndPredicate(subject: String, predicate: String): com.jarvisai.assistant.data.local.SemanticTripleEntity? = null
        override suspend fun searchSemanticTriples(query: String): List<com.jarvisai.assistant.data.local.SemanticTripleEntity> = emptyList()
        override suspend fun insertSemanticTriple(triple: com.jarvisai.assistant.data.local.SemanticTripleEntity): Long = 1L
        override suspend fun updateSemanticTriple(triple: com.jarvisai.assistant.data.local.SemanticTripleEntity) {}
        override suspend fun deleteSemanticTripleById(tripleId: Long) {}
        override suspend fun clearAllSemanticTriples() {}
    }
}

