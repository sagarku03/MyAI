package com.jarvisai.assistant.data.repository

import com.jarvisai.assistant.data.local.JarvisDao
import com.jarvisai.assistant.data.local.MemoryEntity
import com.jarvisai.assistant.model.MemoryCategory
import com.jarvisai.assistant.model.MemoryImportance
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

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
 * - Upsert deduplication by normalized title/key so updating "Name" or "Favorite language" updates the existing record cleanly.
 * - Importance ratings (LOW, NORMAL, IMPORTANT, CRITICAL).
 * - Temporary memory expiration.
 * - Exporting memories as Text or JSON.
 * - Always-on retrieval of core identity facts.
 */
class MemoryRepository(private val dao: JarvisDao) {

    val allMemories: Flow<List<MemoryEntity>> = dao.getAllMemories()

    suspend fun getAllMemoriesOnce(): List<MemoryEntity> = dao.getAllMemoriesOnce()

    suspend fun findDuplicateMemory(title: String, content: String): MemoryEntity? {
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        val all = dao.getAllMemoriesOnce()
        return all.firstOrNull {
            it.title.equals(cleanTitle, ignoreCase = true) ||
                it.content.equals(cleanContent, ignoreCase = true) ||
                (cleanTitle.length >= 5 && it.title.contains(cleanTitle, ignoreCase = true))
        }
    }

    suspend fun purgeExpiredMemories(): Int {
        return dao.purgeExpiredMemories(System.currentTimeMillis())
    }

    suspend fun saveOrUpdateMemory(
        title: String,
        content: String,
        category: MemoryCategory,
        tags: String = "",
        importance: MemoryImportance = MemoryImportance.NORMAL,
        expiresAtMillis: Long? = null,
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
                importance = importance.name,
                expiresAtMillis = expiresAtMillis ?: existing.expiresAtMillis,
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
                importance = importance.name,
                expiresAtMillis = expiresAtMillis,
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

    suspend fun updateMemoryImportance(id: Long, importance: MemoryImportance) {
        val list = dao.getAllMemoriesOnce()
        val mem = list.firstOrNull { it.id == id } ?: return
        dao.updateMemory(mem.copy(importance = importance.name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteMemoryById(memoryId: Long) {
        dao.deleteMemoryById(memoryId)
    }

    suspend fun clearAllMemories() {
        dao.clearAllMemories()
    }

    suspend fun exportMemoriesAsText(): String {
        val list = dao.getAllMemoriesOnce()
        if (list.isEmpty()) return "No memories stored in JARVIS."
        val sb = StringBuilder("=== JARVIS AI NEURAL MEMORY ARCHIVE ===\n")
        sb.append("Total Records: ${list.size}\n\n")
        list.forEachIndexed { idx, mem ->
            sb.append("${idx + 1}. [${mem.category}] ${mem.title} (${mem.importance})\n")
            sb.append("   ${mem.content}\n")
            if (mem.tags.isNotBlank()) sb.append("   Tags: ${mem.tags}\n")
            sb.append("\n")
        }
        return sb.toString()
    }

    suspend fun exportMemoriesAsJson(): String {
        val list = dao.getAllMemoriesOnce()
        val root = JSONObject()
        root.put("app", "JARVIS AI")
        root.put("version", "1.0")
        root.put("exportedAt", System.currentTimeMillis())
        val arr = JSONArray()
        list.forEach { m ->
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("title", m.title)
            obj.put("content", m.content)
            obj.put("category", m.category)
            obj.put("importance", m.importance)
            obj.put("isPinned", m.isPinned)
            obj.put("tags", m.tags)
            obj.put("createdAt", m.createdAt)
            arr.put(obj)
        }
        root.put("memories", arr)
        return root.toString(2)
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
                importance = fact.importance,
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
     * Detects potential memory suggestions from text without auto-saving immediately.
     */
    fun detectMemorySuggestions(message: String): List<ExtractedFact> {
        return extractFactsFromMessage(message)
    }

    /**
     * Retrieves all core identity/preference/pinned memories PLUS query-matched memories
     * using the 4-Factor Hybrid LTM Scoring Formula:
     * Score(m, q) = Relevance(m, q) + Importance(m) + EbbinghausRecency(m) + AccessFrequency(m)
     */
    suspend fun retrieveRelevantMemories(query: String, maxCount: Int = 16): List<MemoryEntity> {
        val now = System.currentTimeMillis()
        val all = dao.getAllMemoriesOnce()
        if (all.isEmpty()) return emptyList()

        val coreMemories = all.filter {
            it.isPinned ||
                it.category == MemoryCategory.PERSONAL.name ||
                it.category == MemoryCategory.PREFERENCES.name ||
                it.category == MemoryCategory.IMPORTANT.name
        }

        val rawTokens = query
            .lowercase()
            .split(Regex("[^a-zA-Z0-9\\u0900-\\u097F]+"))
            .filter { it.length >= 2 }
            .toSet()

        // Semantic synonym expansion for Hindi/Hinglish inquiry keywords -> English memory tags
        val expandedTokens = rawTokens.toMutableSet()
        if (rawTokens.any { it in setOf("naam", "name", "call me") }) expandedTokens.addAll(listOf("name", "naam", "identity"))
        if (rawTokens.any { it in setOf("bhai", "brother", "bhaiya", "bro") }) expandedTokens.addAll(listOf("brother", "bhai", "family", "relationship"))
        if (rawTokens.any { it in setOf("behen", "sister", "didi", "sis") }) expandedTokens.addAll(listOf("sister", "behen", "didi", "family"))
        if (rawTokens.any { it in setOf("papa", "father", "dad", "pitaji") }) expandedTokens.addAll(listOf("father", "papa", "family"))
        if (rawTokens.any { it in setOf("mummy", "mother", "mom", "maa") }) expandedTokens.addAll(listOf("mother", "mummy", "mom", "family"))
        if (rawTokens.any { it in setOf("dost", "friend", "yaar", "bestfriend") }) expandedTokens.addAll(listOf("friend", "best friend", "dost"))
        if (rawTokens.any { it in setOf("fav", "favorite", "favourite", "pasand", "choice") }) expandedTokens.addAll(listOf("favorite", "favourite", "preference", "likes"))
        if (rawTokens.any { it in setOf("rehta", "rahta", "ghar", "city", "kahan", "lives", "home", "address") }) expandedTokens.addAll(listOf("city", "lives", "location", "home", "address"))
        if (rawTokens.any { it in setOf("kaam", "naukri", "job", "work", "office", "company", "profession") }) expandedTokens.addAll(listOf("job", "work", "role", "profession", "company"))
        if (rawTokens.any { it in setOf("umar", "age", "saal", "birthday", "janamdin") }) expandedTokens.addAll(listOf("age", "birthday", "birth"))

        val isGeneralRecallInquiry = rawTokens.isEmpty() || rawTokens.any {
            it in setOf("yaad", "remember", "memory", "memories", "kuch", "baare", "pata", "jante", "jaante", "kya", "sab", "all", "who", "tell")
        }

        val scored = all.map { memory ->
            val searchable = "${memory.title} ${memory.content} ${memory.category} ${memory.tags}".lowercase()
            // 1. Relevance score
            val tokenHits = expandedTokens.count { searchable.contains(it) }
            val matchScore = tokenHits * 18.0
            val coreBonus = if (coreMemories.any { it.id == memory.id }) 25.0 else 0.0
            val pinBonus = if (memory.isPinned) 20.0 else 0.0
            val recallBonus = if (isGeneralRecallInquiry) 15.0 else 0.0

            // 2. Importance weight
            val importanceBonus = when (memory.importance) {
                "CRITICAL" -> 30.0
                "IMPORTANT" -> 20.0
                "NORMAL" -> 10.0
                else -> 0.0
            }

            // 3. Ebbinghaus Recency Decay: exp(-lambda * daysSinceLastTouch)
            val lastTouch = maxOf(memory.updatedAt, memory.lastAccessedAt)
            val daysElapsed = ((now - lastTouch).coerceAtLeast(0L)).toDouble() / (24.0 * 3600.0 * 1000.0)
            val recencyBonus = 10.0 * kotlin.math.exp(-0.08 * daysElapsed)

            // 4. Access Frequency Reinforcement: 6 * ln(1 + accessCount)
            val frequencyBonus = 6.0 * kotlin.math.ln(1.0 + memory.accessCount.coerceAtLeast(0).toDouble())

            val totalScore = (matchScore + coreBonus + pinBonus + recallBonus + importanceBonus + recencyBonus + frequencyBonus).toInt()
            memory to totalScore
        }

        val selected = scored
            .sortedWith(
                compareByDescending<Pair<MemoryEntity, Int>> { it.second }
                    .thenByDescending { it.first.updatedAt }
            )
            .map { it.first }
            .distinctBy { it.id }
            .take(maxCount)

        // Reinforce accessed memories when retrieved for an active user query
        if (expandedTokens.isNotEmpty()) {
            for (mem in selected) {
                val searchable = "${mem.title} ${mem.content} ${mem.tags}".lowercase()
                if (expandedTokens.any { searchable.contains(it) }) {
                    dao.incrementMemoryAccess(mem.id, now)
                }
            }
        }

        return selected
    }

    /**
     * Background Memory Consolidation Pipeline (Tier 1/3 -> Tier 5 LTM):
     * Scans a batch of recent conversation turns and distills any high-value facts,
     * preferences, or relationships into permanent Long-Term Memory.
     */
    suspend fun consolidateTurnsToLongTermMemory(userTurns: List<String>): List<MemoryEntity> {
        val consolidated = mutableListOf<MemoryEntity>()
        for (turn in userTurns) {
            val res = detectAndSaveAutomaticMemories(turn)
            if (res.saved) {
                consolidated.addAll(res.savedMemories)
            }
        }
        return consolidated.distinctBy { it.id }
    }


    data class ExtractedFact(
        val title: String,
        val content: String,
        val category: MemoryCategory,
        val tags: String,
        val importance: MemoryImportance = MemoryImportance.NORMAL,
        val isPinned: Boolean = false
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
                            importance = MemoryImportance.CRITICAL,
                            isPinned = true
                        )
                    )
                    break
                }
            }
        }

        // 2. Detect "My favorite <topic> is <value>" / "Mera favorite <topic> <value> hai"
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
                        importance = MemoryImportance.IMPORTANT,
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
                        importance = MemoryImportance.IMPORTANT,
                        isPinned = true
                    )
                )
            }
        }

        // 3. Explicit remember trigger
        if (hasRememberTrigger && results.isEmpty()) {
            val cleanedFact = normalized
                .replace(Regex("(?i)^(?:jarvis[,\\s]+)?(?:please\\s+)?remember\\s+(?:that\\s+)?"), "")
                .replace(Regex("(?i)^(?:jarvis[,\\s]+)?(?:kripya\\s+)?yaad\\s+rakh(?:na|o)\\s+(?:ki\\s+)?"), "")
                .replace(Regex("(?i)[.!,\\s]*(?:please\\s+)?remember\\s+(?:this|it|that)[.!,\\s]*$"), "")
                .replace(Regex("(?i)[.!,\\s]*(?:isko\\s+|ise\\s+)?yaad\\s+rakh(?:na|o)[.!,\\s]*$"), "")
                .trim(' ', '.', ',', '!', '?')

            if (cleanedFact.length >= 3) {
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
                            importance = MemoryImportance.IMPORTANT,
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
                            importance = MemoryImportance.IMPORTANT,
                            isPinned = true
                        )
                    )
                }
            }
        }

        return results
    }
}
