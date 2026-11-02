package com.example

import com.jarvisai.assistant.data.local.MemoryEntity
import com.jarvisai.assistant.data.network.GeminiApiService
import com.jarvisai.assistant.model.MemoryCategory
import com.jarvisai.assistant.model.SupportedLanguage
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `system instruction includes assistant name language and approved memories`() {
        val service = GeminiApiService()
        val memories = listOf(
            MemoryEntity(
                id = 1L,
                title = "Home City",
                content = "User lives in Bengaluru",
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
        assertTrue(instruction.contains("Bengaluru"))
    }
}
