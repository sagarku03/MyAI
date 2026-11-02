package com.jarvisai.assistant.data.repository

import com.jarvisai.assistant.data.local.JarvisDao
import com.jarvisai.assistant.data.local.MemoryEntity
import com.jarvisai.assistant.model.MemoryCategory
import kotlinx.coroutines.flow.Flow

data class AutomaticMemorySaveResult(
    val saved: Boolean,
    val savedMemories: List<MemoryEntity>,
    val confirmationMessage: String
)

/**
 * Dedicated Room-backed repository for JARVIS Persistent Memory.
 *
 * Implements:
 * - Automatic memory detection from English, Hindi, and Hinglish user statements
 *   (e.g., "My name is Sagar. Remember this.", "Remember that my name is Sagar.",
 *   "My favorite language is Hindi. Remember it.", "Mera naam Sagar hai, yaad rakhna").
 * - Upsert deduplication by normalized title/key so updating "Name" or "Favorite language"
 *   updates the existing record cleanly.
 * - Verified Room write confirmation before reporting "Memory saved successfully."
 * - Always-on retrieval of PERSONAL, PREFERENCES, and Pinned memories alongside
 *   query-relevant memories so facts like the user's name are never missed across app restarts.
 */
class MemoryRepository(private val dao: JarvisDao) {

    val allMemories: Flow<List<MemoryEntity>> = dao.getAllMemories()

    suspend fun getAllMemoriesOnce(): List<MemoryEntity> = dao.getAllMemoriesOnce()

    suspend fun saveOrUpdateMemory(
        title: String,
        content: String,
        category: MemoryCategory,
        tags: String = "",
        isPinned: Boolean = false
    ): MemoryEntity? {
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        if (cleanTitle.isEmpty() || cleanContent.isEmpty()) return null

        val now = System.currentTimeMillis()
        val existingList = dao.getAllMemoriesOnce()
        val existing = existingList.firstOrNull {
            it.title.equals(cleanTitle, ignoreCase = true)
        }

        return if (existing != null) {
            val updated = existing.copy(
                title = cleanTitle,
                content = cleanContent,
                category = category.name,
                tags = if (tags.isNotBlank()) tags.trim() else existing.tags,
                isPinned = isPinned || existing.isPinned,
                updatedAt = now
            )
            dao.updateMemory(updated)
            updated
        } else {
            val entity = MemoryEntity(
                title = cleanTitle,
                content = cleanContent,
                category = category.name,
                tags = tags.trim(),
                isPinned = isPinned,
                createdAt = now,
                updatedAt = now
            )
            val rowId = dao.insertMemory(entity)
            if (rowId > 0L) {
                entity.copy(id = rowId)
            } else {
                null
            }
        }
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
     * Inspects a user message (from Text Chat or Live Voice transcript) for explicit
     * memory-saving instructions or personal identity declarations and persists them to Room.
     */
    suspend fun detectAndSaveAutomaticMemories(userMessage: String): AutomaticMemorySaveResult {
        val trimmed = userMessage.trim()
        if (trimmed.isEmpty()) {
            return AutomaticMemorySaveResult(false, emptyList(), "")
        }

        val extractedFacts = extractFactsFromMessage(trimmed)
        if (extractedFacts.isEmpty()) {
            return AutomaticMemorySaveResult(false, emptyList(), "")
        }

        val savedEntities = mutableListOf<MemoryEntity>()
        for (fact in extractedFacts) {
            val saved = saveOrUpdateMemory(
                title = fact.title,
                content = fact.content,
                category = fact.category,
                tags = fact.tags,
                isPinned = fact.isPinned
            )
            if (saved != null) {
                savedEntities.add(saved)
            }
        }

        if (savedEntities.isEmpty()) {
            return AutomaticMemorySaveResult(false, emptyList(), "")
        }

        // Verify persistence in Room database
        val allInDb = dao.getAllMemoriesOnce()
        val verified = savedEntities.all { saved ->
            allInDb.any { it.id == saved.id || it.title.equals(saved.title, ignoreCase = true) }
        }

        return if (verified) {
            AutomaticMemorySaveResult(
                saved = true,
                savedMemories = savedEntities,
                confirmationMessage = "Memory saved successfully. (Memory successfully save ho gayi.)"
            )
        } else {
            AutomaticMemorySaveResult(false, emptyList(), "")
        }
    }

    /**
     * Retrieves all core identity/preference/pinned memories PLUS query-matched memories
     * so Gemini always knows the user's name, preferences, and saved facts in every session.
     */
    suspend fun retrieveRelevantMemories(query: String, maxCount: Int = 10): List<MemoryEntity> {
        val all = dao.getAllMemoriesOnce()
        if (all.isEmpty()) return emptyList()

        // 1. Always include PERSONAL, PREFERENCES, and Pinned memories first so questions like
        //    "What is my name?" or "Mera naam kya hai?" always have the saved fact in context.
        val coreMemories = all.filter {
            it.isPinned ||
                it.category == MemoryCategory.PERSONAL.name ||
                it.category == MemoryCategory.PREFERENCES.name ||
                it.category == MemoryCategory.IMPORTANT.name
        }

        val tokens = query
            .lowercase()
            .split(Regex("[^a-zA-Z0-9\\u0900-\\u097F]+"))
            .filter { it.length >= 2 }
            .toSet()

        val scored = all.map { memory ->
            val searchable = "${memory.title} ${memory.content} ${memory.category} ${memory.tags}".lowercase()
            val matchScore = tokens.count { token -> searchable.contains(token) } * 12
            val coreBonus = if (coreMemories.any { it.id == memory.id }) 20 else 0
            val pinBonus = if (memory.isPinned) 15 else 0
            memory to (matchScore + coreBonus + pinBonus)
        }

        return scored
            .sortedWith(
                compareByDescending<Pair<MemoryEntity, Int>> { it.second }
                    .thenByDescending { it.first.updatedAt }
            )
            .map { it.first }
            .distinctBy { it.id }
            .take(maxCount)
    }

    internal data class ExtractedFact(
        val title: String,
        val content: String,
        val category: MemoryCategory,
        val tags: String,
        val isPinned: Boolean
    )

    internal fun extractFactsFromMessage(message: String): List<ExtractedFact> {
        val results = mutableListOf<ExtractedFact>()
        val normalized = message.replace(Regex("\\s+"), " ").trim()
        val lower = normalized.lowercase()

        val hasRememberTrigger = lower.contains("remember") ||
            lower.contains("yaad rakh") ||
            lower.contains("yad rakh") ||
            lower.contains("save this") ||
            lower.contains("note that") ||
            lower.contains("yaad kar") ||
            lower.contains("याद रख")

        // 1. Detect User's Name in English, Hinglish, or Hindi
        // Examples:
        // "My name is Sagar. Remember this."
        // "Remember that my name is Sagar."
        // "Mera naam Sagar hai"
        // "मेरा नाम सागर है"
        val nameRegexes = listOf(
            Regex("(?:remember\\s+(?:that\\s+)?)?my\\s+name\\s+is\\s+([A-Za-z\\u0900-\\u097F]+(?:\\s+[A-Za-z\\u0900-\\u097F]+)?)", RegexOption.IGNORE_CASE),
            Regex("(?:yaad\\s+rakhna\\s+(?:ki\\s+)?)?mera\\s+naam\\s+([A-Za-z\\u0900-\\u097F]+(?:\\s+[A-Za-z\\u0900-\\u097F]+?)?)\\s+hai", RegexOption.IGNORE_CASE),
            Regex("मेरा\\s+नाम\\s+([^।.!?,]+?)\\s+है")
        )

        for (regex in nameRegexes) {
            val match = regex.find(normalized)
            if (match != null) {
                val rawName = match.groupValues[1]
                    .replace(Regex("(?i)\\b(and|aur|remember|this|it|please|plz|hai)\\b.*"), "")
                    .trim(' ', '.', ',', '!', '?', '"', '\'')
                if (rawName.isNotBlank() && rawName.length <= 40) {
                    results.add(
                        ExtractedFact(
                            title = "Name",
                            content = "User's name is $rawName (Mera naam $rawName hai)",
                            category = MemoryCategory.PERSONAL,
                            tags = "name,naam,identity,personal,$rawName",
                            isPinned = true
                        )
                    )
                    break
                }
            }
        }

        // 2. Detect "My favorite <topic> is <value>" / "Mera favorite <topic> <value> hai"
        // Example: "My favorite language is Hindi. Remember it."
        val favoriteEnRegex = Regex(
            "(?:remember\\s+(?:that\\s+)?)?my\\s+fav(?:ou?rite)\\s+([a-zA-Z0-9_ ]{2,25}?)\\s+is\\s+([^.!?\\n]+)",
            RegexOption.IGNORE_CASE
        )
        val favEnMatch = favoriteEnRegex.find(normalized)
        if (favEnMatch != null) {
            val topic = favEnMatch.groupValues[1].trim()
            val value = favEnMatch.groupValues[2]
                .replace(Regex("(?i)\\b(remember\\s+this|remember\\s+it|please\\s+remember|yaad\\s+rakhna)\\b.*"), "")
                .trim(' ', '.', ',', '!', '?')
            if (topic.isNotBlank() && value.isNotBlank()) {
                val formattedTopic = topic.replaceFirstChar { it.uppercase() }
                results.add(
                    ExtractedFact(
                        title = "Favorite $formattedTopic",
                        content = "User's favorite ${topic.lowercase()} is $value",
                        category = MemoryCategory.PREFERENCES,
                        tags = "favorite,preference,${topic.lowercase()},${value.lowercase()}",
                        isPinned = true
                    )
                )
            }
        }

        val favoriteHinglishRegex = Regex(
            "(?:yaad\\s+rakhna\\s+(?:ki\\s+)?)?mera\\s+fav(?:ou?rite)\\s+([a-zA-Z0-9_ ]{2,25}?)\\s+([a-zA-Z0-9_ ]+?)\\s+hai",
            RegexOption.IGNORE_CASE
        )
        val favHiMatch = favoriteHinglishRegex.find(normalized)
        if (favHiMatch != null && results.none { it.title.startsWith("Favorite", ignoreCase = true) }) {
            val topic = favHiMatch.groupValues[1].trim()
            val value = favHiMatch.groupValues[2].trim(' ', '.', ',', '!', '?')
            if (topic.isNotBlank() && value.isNotBlank()) {
                val formattedTopic = topic.replaceFirstChar { it.uppercase() }
                results.add(
                    ExtractedFact(
                        title = "Favorite $formattedTopic",
                        content = "User's favorite ${topic.lowercase()} is $value",
                        category = MemoryCategory.PREFERENCES,
                        tags = "favorite,preference,${topic.lowercase()},${value.lowercase()}",
                        isPinned = true
                    )
                )
            }
        }

        // 3. If user explicitly asked to remember something and it wasn't already captured above
        if (hasRememberTrigger && results.isEmpty()) {
            val cleanedFact = normalized
                .replace(Regex("(?i)^(?:jarvis[,\\s]+)?(?:please\\s+)?remember\\s+(?:that\\s+)?"), "")
                .replace(Regex("(?i)^(?:jarvis[,\\s]+)?(?:kripya\\s+)?yaad\\s+rakh(?:na|o)\\s+(?:ki\\s+)?"), "")
                .replace(Regex("(?i)[.!,\\s]*(?:please\\s+)?remember\\s+(?:this|it|that)[.!,\\s]*$"), "")
                .replace(Regex("(?i)[.!,\\s]*(?:isko\\s+|ise\\s+)?yaad\\s+rakh(?:na|o)[.!,\\s]*$"), "")
                .trim(' ', '.', ',', '!', '?')

            if (cleanedFact.length >= 3) {
                // Check if it looks like "my <key> is <value>"
                val myKeyVal = Regex("^my\\s+([a-zA-Z0-9_ ]{2,25}?)\\s+is\\s+(.+)$", RegexOption.IGNORE_CASE)
                    .find(cleanedFact)
                if (myKeyVal != null) {
                    val keyTitle = myKeyVal.groupValues[1].trim().replaceFirstChar { it.uppercase() }
                    val valPart = myKeyVal.groupValues[2].trim()
                    results.add(
                        ExtractedFact(
                            title = keyTitle,
                            content = "User's ${keyTitle.lowercase()} is $valPart",
                            category = MemoryCategory.PERSONAL,
                            tags = "${keyTitle.lowercase()},personal,remembered",
                            isPinned = true
                        )
                    )
                } else {
                    val shortTitle = cleanedFact.split(" ").take(4).joinToString(" ")
                        .replaceFirstChar { it.uppercase() }
                        .take(32)
                    results.add(
                        ExtractedFact(
                            title = shortTitle.ifBlank { "Saved Memory" },
                            content = cleanedFact,
                            category = MemoryCategory.IMPORTANT,
                            tags = "remembered,user-fact",
                            isPinned = true
                        )
                    )
                }
            }
        }

        return results
    }
}
