package com.jarvisai.assistant.data.repository

import com.jarvisai.assistant.data.local.JarvisDao
import com.jarvisai.assistant.data.local.SemanticTripleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Tier 4 — Semantic Memory Repository (Structured Knowledge Graph & Concept Triples).
 *
 * Stores Subject-Predicate-Object (S-P-O) facts, personal relationships
 * (e.g., "User --[brother_name]--> Rahul"), preferences, and procedural App UI concepts
 * (e.g., "WhatsApp --[send_action]--> Tap right-side paper-plane icon").
 */
class SemanticGraphRepository(private val dao: JarvisDao) {

    val allSemanticTriples: Flow<List<SemanticTripleEntity>> = dao.getAllSemanticTriples()

    suspend fun getAllTriplesOnce(): List<SemanticTripleEntity> {
        return dao.getAllSemanticTriplesOnce()
    }

    /**
     * Upserts a Subject-Predicate-Object triple.
     * If (subject, predicate) already exists, updates its objectValue, confidence, and updatedAt timestamp.
     */
    suspend fun upsertTriple(
        subject: String,
        predicate: String,
        objectValue: String,
        category: String = "KNOWLEDGE",
        confidence: Float = 1.0f,
        source: String = "EXPLICIT"
    ): SemanticTripleEntity? {
        val cleanSub = subject.trim()
        val cleanPred = predicate.trim().lowercase().replace(Regex("\\s+"), "_")
        val cleanObj = objectValue.trim()
        if (cleanSub.isEmpty() || cleanPred.isEmpty() || cleanObj.isEmpty()) return null

        val now = System.currentTimeMillis()
        val existing = dao.findSemanticTripleBySubjectAndPredicate(cleanSub, cleanPred)
        return if (existing != null) {
            val updated = existing.copy(
                subject = cleanSub,
                predicate = cleanPred,
                objectValue = cleanObj,
                category = category.uppercase(),
                confidence = maxOf(existing.confidence, confidence).coerceIn(0.1f, 1.0f),
                source = source.uppercase(),
                updatedAt = now
            )
            dao.updateSemanticTriple(updated)
            updated
        } else {
            val entity = SemanticTripleEntity(
                subject = cleanSub,
                predicate = cleanPred,
                objectValue = cleanObj,
                category = category.uppercase(),
                confidence = confidence.coerceIn(0.1f, 1.0f),
                source = source.uppercase(),
                createdAt = now,
                updatedAt = now
            )
            val id = dao.insertSemanticTriple(entity)
            if (id > 0L) entity.copy(id = id) else null
        }
    }

    suspend fun deleteTripleById(tripleId: Long) {
        dao.deleteSemanticTripleById(tripleId)
    }

    suspend fun clearAllTriples() {
        dao.clearAllSemanticTriples()
    }

    /**
     * Seeds essential procedural App UI concepts into Semantic Memory if the graph is empty.
     */
    suspend fun ensureDefaultAppConceptsSeeded() {
        val existing = dao.getAllSemanticTriplesOnce()
        if (existing.isNotEmpty()) return

        val defaultConcepts = listOf(
            SemanticTripleEntity(
                subject = "WhatsApp",
                predicate = "send_message_ui",
                objectValue = "Type message in bottom input field and tap Send button on right",
                category = "APP_UI_CONCEPT",
                confidence = 0.95f,
                source = "SEEDED"
            ),
            SemanticTripleEntity(
                subject = "YouTube",
                predicate = "play_first_video_ui",
                objectValue = "Search query via search intent and tap first video result card",
                category = "APP_UI_CONCEPT",
                confidence = 0.95f,
                source = "SEEDED"
            ),
            SemanticTripleEntity(
                subject = "Android System",
                predicate = "dismiss_popup_ui",
                objectValue = "Tap Allow, While using the app, or Dismiss button on dialog",
                category = "APP_UI_CONCEPT",
                confidence = 0.90f,
                source = "SEEDED"
            )
        )
        for (concept in defaultConcepts) {
            dao.insertSemanticTriple(concept)
        }
    }

    /**
     * Automatically extracts Subject-Predicate-Object semantic facts from Hinglish/English user statements
     * and persists them in the Semantic Knowledge Graph.
     */
    suspend fun extractAndStoreSemanticTriples(utterance: String): List<SemanticTripleEntity> {
        val extracted = parseSemanticTriplesFromText(utterance)
        if (extracted.isEmpty()) return emptyList()

        val saved = mutableListOf<SemanticTripleEntity>()
        for (candidate in extracted) {
            val entity = upsertTriple(
                subject = candidate.subject,
                predicate = candidate.predicate,
                objectValue = candidate.objectValue,
                category = candidate.category,
                confidence = candidate.confidence,
                source = "EXPLICIT"
            )
            if (entity != null) saved.add(entity)
        }
        return saved
    }

    /**
     * Resolves relationship or alias references like "bhai", "brother", "mummy", "papa", "boss", "best friend"
     * using the Semantic Knowledge Graph.
     * Example: if User --[brother]--> Rahul exists, resolveRelationOrAlias("bhai") returns "Rahul".
     */
    suspend fun resolveRelationOrAlias(relationOrKeyword: String): String? {
        val clean = relationOrKeyword.trim().lowercase()
        if (clean.isEmpty()) return null

        val canonicalPredicates = when (clean) {
            "bhai", "brother", "bro", "bhaiya" -> listOf("brother", "bhai", "bhaiya")
            "behen", "sister", "did", "didi", "sis" -> listOf("sister", "behen", "didi")
            "papa", "father", "dad", "pitaji", "abba" -> listOf("father", "papa", "dad")
            "mummy", "mother", "mom", "maa", "ammi" -> listOf("mother", "mummy", "mom", "maa")
            "dost", "best friend", "friend", "yaar" -> listOf("best_friend", "friend", "dost")
            "boss", "manager", "sir" -> listOf("boss", "manager")
            "wife", "patni", "biwi", "partner" -> listOf("wife", "partner")
            "husband", "pati" -> listOf("husband", "partner")
            else -> listOf(clean.replace(Regex("\\s+"), "_"))
        }

        val all = dao.getAllSemanticTriplesOnce()
        for (pred in canonicalPredicates) {
            val match = all.firstOrNull {
                it.predicate.equals(pred, ignoreCase = true) ||
                    it.predicate.contains(pred, ignoreCase = true) ||
                    (it.category == "RELATIONSHIP" && it.subject.equals(clean, ignoreCase = true))
            }
            if (match != null) return match.objectValue
        }
        return null
    }

    /**
     * Retrieves relevant Semantic Knowledge Graph triples for a user prompt or active app.
     */
    suspend fun retrieveRelevantTriples(
        query: String,
        activeAppName: String = "",
        maxCount: Int = 8
    ): List<SemanticTripleEntity> {
        val all = dao.getAllSemanticTriplesOnce()
        if (all.isEmpty()) return emptyList()

        val tokens = "$query $activeAppName"
            .lowercase()
            .split(Regex("[^a-zA-Z0-9\\u0900-\\u097F]+"))
            .filter { it.length >= 2 }
            .toSet()

        // Map Hinglish kinship terms to canonical English predicates so "mere bhai ko call karo" matches "brother"
        val expandedTokens = tokens.toMutableSet()
        if (tokens.any { it in setOf("bhai", "bhaiya", "brother", "bro") }) {
            expandedTokens.addAll(listOf("brother", "bhai", "bhaiya"))
        }
        if (tokens.any { it in setOf("mummy", "mom", "maa", "mother") }) {
            expandedTokens.addAll(listOf("mother", "mummy", "mom", "maa"))
        }
        if (tokens.any { it in setOf("papa", "dad", "father", "pitaji") }) {
            expandedTokens.addAll(listOf("father", "papa", "dad"))
        }
        if (tokens.any { it in setOf("behen", "didi", "sister", "sis") }) {
            expandedTokens.addAll(listOf("sister", "behen", "didi"))
        }
        if (tokens.any { it in setOf("dost", "friend", "yaar") }) {
            expandedTokens.addAll(listOf("friend", "best_friend", "dost"))
        }

        val scored = all.map { triple ->
            val searchable = "${triple.subject} ${triple.predicate} ${triple.objectValue} ${triple.category}".lowercase()
            val tokenMatches = expandedTokens.count { searchable.contains(it) }
            val identityBonus = if (triple.category == "IDENTITY" || triple.category == "RELATIONSHIP") 10 else 0
            val appBonus = if (activeAppName.isNotBlank() && triple.subject.equals(activeAppName, ignoreCase = true)) 20 else 0
            val totalScore = (tokenMatches * 15) + identityBonus + appBonus + (triple.confidence * 5).toInt()
            triple to totalScore
        }

        return scored
            .sortedWith(
                compareByDescending<Pair<SemanticTripleEntity, Int>> { it.second }
                    .thenByDescending { it.first.updatedAt }
            )
            .map { it.first }
            .take(maxCount)
    }

    data class CandidateTriple(
        val subject: String,
        val predicate: String,
        val objectValue: String,
        val category: String,
        val confidence: Float = 0.95f
    )

    companion object {
        fun parseSemanticTriplesFromText(text: String): List<CandidateTriple> {
            val normalized = text.replace(Regex("\\s+"), " ").trim()
            if (normalized.length < 4) return emptyList()
            val results = mutableListOf<CandidateTriple>()

            // 1. Kinship / Relationship patterns in Hinglish & English:
            // "mera bhai Rahul hai", "mere bhai ka naam Rahul hai", "my brother is Rahul", "my brother's name is Rahul"
            val relationMap = mapOf(
                "bhai" to "brother",
                "bhaiya" to "brother",
                "brother" to "brother",
                "behen" to "sister",
                "didi" to "sister",
                "sister" to "sister",
                "papa" to "father",
                "father" to "father",
                "dad" to "father",
                "mummy" to "mother",
                "maa" to "mother",
                "mom" to "mother",
                "mother" to "mother",
                "best friend" to "best_friend",
                "dost" to "friend",
                "friend" to "friend",
                "boss" to "boss",
                "manager" to "manager",
                "wife" to "wife",
                "patni" to "wife",
                "husband" to "husband",
                "pati" to "husband"
            )

            val relKeysPattern = relationMap.keys.sortedByDescending { it.length }.joinToString("|")
            val hinglishRelRegex1 = Regex(
                "(?i)\\b(?:mera|meri|mere)\\s+($relKeysPattern)(?:\\s+ka\\s+naam)?\\s+([A-Za-z\\u0900-\\u097F]+(?:\\s+[A-Za-z\\u0900-\\u097F]+)?)\\s+hai\\b"
            )
            val englishRelRegex = Regex(
                "(?i)\\bmy\\s+($relKeysPattern)(?:'s\\s+name|\\s+name)?\\s+is\\s+([A-Za-z\\u0900-\\u097F]+(?:\\s+[A-Za-z\\u0900-\\u097F]+)?)\\b"
            )
            val reverseHinglishRelRegex = Regex(
                "(?i)\\b([A-Za-z\\u0900-\\u097F]+)\\s+(?:mera|meri|mere)\\s+($relKeysPattern)\\s+hai\\b"
            )

            for (regex in listOf(hinglishRelRegex1, englishRelRegex)) {
                val match = regex.find(normalized)
                if (match != null) {
                    val rawRel = match.groupValues[1].lowercase().trim()
                    val personName = cleanExtractedEntityValue(match.groupValues[2])
                    val predicate = relationMap[rawRel] ?: rawRel
                    if (personName.isNotBlank()) {
                        results.add(
                            CandidateTriple(
                                subject = "User",
                                predicate = predicate,
                                objectValue = personName,
                                category = "RELATIONSHIP",
                                confidence = 0.98f
                            )
                        )
                    }
                }
            }

            val revMatch = reverseHinglishRelRegex.find(normalized)
            if (revMatch != null && results.isEmpty()) {
                val personName = cleanExtractedEntityValue(revMatch.groupValues[1])
                val rawRel = revMatch.groupValues[2].lowercase().trim()
                val predicate = relationMap[rawRel] ?: rawRel
                if (personName.isNotBlank() && !personName.equals("woh", ignoreCase = true) && !personName.equals("ye", ignoreCase = true)) {
                    results.add(
                        CandidateTriple(
                            subject = "User",
                            predicate = predicate,
                            objectValue = personName,
                            category = "RELATIONSHIP",
                            confidence = 0.96f
                        )
                    )
                }
            }

            // 2. Identity facts: "mera naam X hai" / "my name is X" / "main X mein rehta hoon" / "I live in X"
            val nameRegex = Regex("(?i)(?:my\\s+name\\s+is|mera\\s+naam)\\s+([A-Za-z\\u0900-\\u097F]+(?:\\s+[A-Za-z\\u0900-\\u097F]+)?)(?:\\s+hai)?")
            nameRegex.find(normalized)?.let { m ->
                val name = cleanExtractedEntityValue(m.groupValues[1])
                if (name.isNotBlank()) {
                    results.add(
                        CandidateTriple(
                            subject = "User",
                            predicate = "name",
                            objectValue = name,
                            category = "IDENTITY",
                            confidence = 1.0f
                        )
                    )
                }
            }

            val cityEnRegex = Regex("(?i)\\bi\\s+live\\s+in\\s+([A-Za-z ]{2,30})")
            val cityHiRegex = Regex("(?i)\\bmain\\s+([A-Za-z ]{2,25}?)\\s+(?:mein|me)\\s+reh(?:ta|ti)\\s+(?:hoon|hu|hun)")
            val cityMatch = cityEnRegex.find(normalized) ?: cityHiRegex.find(normalized)
            if (cityMatch != null) {
                val city = cleanExtractedEntityValue(cityMatch.groupValues[1])
                if (city.isNotBlank()) {
                    results.add(
                        CandidateTriple(
                            subject = "User",
                            predicate = "lives_in",
                            objectValue = city,
                            category = "IDENTITY",
                            confidence = 0.95f
                        )
                    )
                }
            }

            // 3. Preferences: "mera favorite <topic> <value> hai" / "my favorite <topic> is <value>" / "mujhe <value> pasand hai"
            val favEn = Regex("(?i)my\\s+fav(?:ou?rite)\\s+([a-zA-Z0-9_ ]{2,20}?)\\s+is\\s+([^.!?\\n]+)")
                .find(normalized)
            if (favEn != null) {
                val topic = favEn.groupValues[1].trim().lowercase().replace(Regex("\\s+"), "_")
                val value = cleanExtractedEntityValue(favEn.groupValues[2])
                if (topic.isNotBlank() && value.isNotBlank()) {
                    results.add(
                        CandidateTriple(
                            subject = "User",
                            predicate = "favorite_$topic",
                            objectValue = value,
                            category = "PREFERENCE",
                            confidence = 0.95f
                        )
                    )
                }
            }

            val favHi = Regex("(?i)mera\\s+fav(?:ou?rite)\\s+([a-zA-Z0-9_ ]{2,20}?)\\s+([a-zA-Z0-9_ ]+?)\\s+hai")
                .find(normalized)
            if (favHi != null && results.none { it.predicate.startsWith("favorite_") }) {
                val topic = favHi.groupValues[1].trim().lowercase().replace(Regex("\\s+"), "_")
                val value = cleanExtractedEntityValue(favHi.groupValues[2])
                if (topic.isNotBlank() && value.isNotBlank()) {
                    results.add(
                        CandidateTriple(
                            subject = "User",
                            predicate = "favorite_$topic",
                            objectValue = value,
                            category = "PREFERENCE",
                            confidence = 0.95f
                        )
                    )
                }
            }

            return results
        }

        private fun cleanExtractedEntityValue(raw: String): String {
            return raw
                .replace(Regex("(?i)\\b(and|aur|remember|this|it|please|plz|hai|hain|yaad|rakhna)\\b.*"), "")
                .trim(' ', '.', ',', '!', '?', '"', '\'')
        }
    }
}
