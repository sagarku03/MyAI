package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jarvisai.assistant.data.local.ChatMessageEntity
import com.jarvisai.assistant.data.local.JarvisDatabase
import com.jarvisai.assistant.data.repository.JarvisRepository
import com.jarvisai.assistant.model.MemoryCategory
import com.jarvisai.assistant.security.SecureApiKeyManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: JarvisDatabase
    private lateinit var repository: JarvisRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, JarvisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = JarvisRepository(db.jarvisDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read app_name from context matches JARVIS AI`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("JARVIS AI", appName)
    }

    @Test
    fun `chat session and message persistence works in Room`() = runBlocking {
        val sessionId = repository.createNewChatSession("Propulsion Diagnostics", "HINDI")
        repository.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = "user",
                content = "Check thruster calibration"
            )
        )
        repository.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = "model",
                content = "All thrusters operating at 99.4% efficiency."
            )
        )

        val messages = repository.getMessagesForSessionOnce(sessionId)
        assertEquals(2, messages.size)
        assertEquals("Check thruster calibration", messages[0].content)
        assertEquals("All thrusters operating at 99.4% efficiency.", messages[1].content)

        repository.deleteChatSession(sessionId)
        val afterDelete = repository.getMessagesForSessionOnce(sessionId)
        assertTrue(afterDelete.isEmpty())
    }

    @Test
    fun `memory center saves and retrieves relevant user approved memories`() = runBlocking {
        repository.saveApprovedMemory(
            title = "Preferred Stack",
            content = "Uses Kotlin, Jetpack Compose, and MVVM for Android development",
            category = MemoryCategory.PROJECTS,
            tags = "android,kotlin",
            isPinned = true
        )
        repository.saveApprovedMemory(
            title = "Coffee Order",
            content = "Likes iced americano without sugar",
            category = MemoryCategory.PREFERENCES,
            tags = "food"
        )

        val relevant = repository.retrieveRelevantMemories("Help me write a Kotlin Compose screen")
        assertTrue(relevant.isNotEmpty())
        assertEquals("Preferred Stack", relevant.first().title)

        repository.clearAllMemories()
        val afterClear = repository.retrieveRelevantMemories("Kotlin")
        assertTrue(afterClear.isEmpty())
    }

    @Test
    fun `secure api key manager saves masks and deletes key`() {
        val keyManager = SecureApiKeyManager(context)
        keyManager.deleteApiKey()
        assertFalse(keyManager.hasValidApiKey())

        val saved = keyManager.saveApiKey("AIzaSyTestKey1234567890")
        assertTrue(saved)
        assertTrue(keyManager.hasValidApiKey())
        assertEquals("AIzaSyTestKey1234567890", keyManager.getApiKey())
        assertTrue(keyManager.getMaskedApiKey().startsWith("AIza"))
        assertTrue(keyManager.getMaskedApiKey().endsWith("7890"))

        keyManager.deleteApiKey()
        assertFalse(keyManager.hasValidApiKey())
    }
}
