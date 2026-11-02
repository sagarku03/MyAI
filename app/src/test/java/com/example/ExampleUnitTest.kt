package com.example

import com.jarvisai.assistant.data.local.ChatSessionEntity
import com.jarvisai.assistant.data.local.ChatMessageEntity
import com.jarvisai.assistant.data.local.JarvisDao
import com.jarvisai.assistant.data.local.MemoryEntity
import com.jarvisai.assistant.data.local.ReminderEntity
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
    }
}
