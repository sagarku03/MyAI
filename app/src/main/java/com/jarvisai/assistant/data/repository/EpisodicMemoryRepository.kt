package com.jarvisai.assistant.data.repository

import com.jarvisai.assistant.data.local.EpisodicEventEntity
import com.jarvisai.assistant.data.local.JarvisDao
import java.util.Calendar
import kotlinx.coroutines.flow.Flow

data class TemporalQuerySpec(
    val startMs: Long,
    val endMs: Long,
    val timeBucket: String = "", // "", MORNING, AFTERNOON, EVENING, NIGHT
    val label: String = ""
)

/**
 * Tier 3 — Episodic Memory Repository (Autobiographical Event & Time-Stamped Interaction Log).
 *
 * Stores and retrieves time-anchored user interactions, app launches, autonomous goals,
 * and workflow executions with Hinglish + English temporal query parsing
 * ("kal raat", "aaj subah", "parso", "last week", "yesterday evening").
 */
class EpisodicMemoryRepository(private val dao: JarvisDao) {

    val allEpisodicEvents: Flow<List<EpisodicEventEntity>> = dao.getAllEpisodicEvents()

    suspend fun getRecentEpisodesOnce(limit: Int = 50): List<EpisodicEventEntity> {
        return dao.getRecentEpisodicEventsOnce(limit)
    }

    suspend fun recordEpisode(
        summary: String,
        eventType: String = "CONVERSATION",
        involvedApps: String = "",
        involvedEntities: String = "",
        outcomeStatus: String = "SUCCESS",
        importanceValence: Int = 2,
        timestampMs: Long = System.currentTimeMillis()
    ): EpisodicEventEntity? {
        val cleanSummary = summary.trim()
        if (cleanSummary.isEmpty()) return null

        // Avoid duplicate rapid-fire identical episode logs within 8 seconds
        val recent = dao.getRecentEpisodicEventsOnce(limit = 5)
        val duplicate = recent.firstOrNull {
            it.summary.equals(cleanSummary, ignoreCase = true) &&
                kotlin.math.abs(timestampMs - it.timestampMs) < 8_000L
        }
        if (duplicate != null) return duplicate

        val bucket = computeTimeBucket(timestampMs)
        val entity = EpisodicEventEntity(
            timestampMs = timestampMs,
            timeBucket = bucket,
            eventType = eventType.uppercase(),
            summary = cleanSummary,
            involvedApps = involvedApps.trim(),
            involvedEntities = involvedEntities.trim(),
            outcomeStatus = outcomeStatus.uppercase(),
            importanceValence = importanceValence.coerceIn(1, 5)
        )
        val rowId = dao.insertEpisodicEvent(entity)
        return if (rowId > 0L) entity.copy(id = rowId) else null
    }

    suspend fun deleteEpisodeById(eventId: Long) {
        dao.deleteEpisodicEventById(eventId)
    }

    suspend fun clearAllEpisodes() {
        dao.clearAllEpisodicEvents()
    }

    /**
     * Retrieves relevant past episodes for a user prompt using both Temporal Window parsing
     * ("kal raat", "aaj subah", "parso") and keyword/entity matching.
     */
    suspend fun retrieveRelevantEpisodes(
        query: String,
        maxCount: Int = 5,
        nowMs: Long = System.currentTimeMillis()
    ): List<EpisodicEventEntity> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return dao.getRecentEpisodicEventsOnce(maxCount)
        }

        val temporalSpec = parseTemporalQuery(cleanQuery, nowMs)
        val tokens = extractSearchTokens(cleanQuery)

        if (temporalSpec != null) {
            val inWindow = dao.getEpisodicEventsInRangeAndBucket(
                startMs = temporalSpec.startMs,
                endMs = temporalSpec.endMs,
                timeBucket = temporalSpec.timeBucket
            )
            if (inWindow.isNotEmpty()) {
                if (tokens.isEmpty()) return inWindow.take(maxCount)
                val scored = inWindow.map { ev ->
                    val haystack = "${ev.summary} ${ev.involvedApps} ${ev.involvedEntities} ${ev.eventType}".lowercase()
                    val hits = tokens.count { haystack.contains(it) }
                    ev to (hits * 15 + ev.importanceValence * 3)
                }
                return scored.sortedByDescending { it.second }.map { it.first }.take(maxCount)
            }
            // Fallback to full day range if specific timeBucket had no exact match
            if (temporalSpec.timeBucket.isNotEmpty()) {
                val fullDay = dao.getEpisodicEventsInRange(temporalSpec.startMs, temporalSpec.endMs)
                if (fullDay.isNotEmpty()) return fullDay.take(maxCount)
            }
        }

        // Check if user is asking an episodic/past question ("maine kya kiya", "last time", "pehle", "kab")
        val lower = cleanQuery.lowercase()
        val hasEpisodicCue = temporalSpec != null ||
            lower.contains("kab") ||
            lower.contains("kya kiya") ||
            lower.contains("khola tha") ||
            lower.contains("bheja tha") ||
            lower.contains("baat hui") ||
            lower.contains("last time") ||
            lower.contains("pichli baar") ||
            lower.contains("pehle") ||
            lower.contains("history") ||
            lower.contains("timeline") ||
            lower.contains("when did") ||
            lower.contains("what did i")

        val allRecent = dao.getRecentEpisodicEventsOnce(limit = 80)
        if (allRecent.isEmpty()) return emptyList()

        if (tokens.isEmpty()) {
            return if (hasEpisodicCue) allRecent.take(maxCount) else emptyList()
        }

        val scored = allRecent.mapNotNull { ev ->
            val haystack = "${ev.summary} ${ev.involvedApps} ${ev.involvedEntities} ${ev.timeBucket}".lowercase()
            val matchCount = tokens.count { token -> haystack.contains(token) }
            if (matchCount == 0 && !hasEpisodicCue) {
                null
            } else {
                val score = matchCount * 14 + ev.importanceValence * 3 + (if (hasEpisodicCue) 5 else 0)
                ev to score
            }
        }

        return scored
            .sortedWith(compareByDescending<Pair<EpisodicEventEntity, Int>> { it.second }.thenByDescending { it.first.timestampMs })
            .map { it.first }
            .take(maxCount)
    }

    companion object {
        private val TEMPORAL_STOP_WORDS = setOf(
            "aaj", "kal", "parso", "subah", "dopahar", "shaam", "raat", "abhi",
            "today", "yesterday", "morning", "afternoon", "evening", "night",
            "last", "week", "pichle", "hafte", "maine", "humne", "kya", "kiya",
            "tha", "thi", "the", "kaunsa", "konsa", "app", "khola", "open", "jarvis",
            "what", "did", "when", "where", "hai", "mein", "par", "pe", "ko"
        )

        fun computeTimeBucket(timestampMs: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 5..11 -> "MORNING"
                in 12..16 -> "AFTERNOON"
                in 17..20 -> "EVENING"
                else -> "NIGHT"
            }
        }

        fun parseTemporalQuery(query: String, nowMs: Long = System.currentTimeMillis()): TemporalQuerySpec? {
            val lower = query.lowercase()

            val timeBucket = when {
                lower.contains("subah") || lower.contains("morning") -> "MORNING"
                lower.contains("dopahar") || lower.contains("afternoon") -> "AFTERNOON"
                lower.contains("shaam") || lower.contains("evening") -> "EVENING"
                lower.contains("raat") || lower.contains("night") -> "NIGHT"
                else -> ""
            }

            val calStart = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfTodayMs = calStart.timeInMillis
            val oneDayMs = 24 * 60 * 60 * 1000L

            return when {
                lower.contains("parso") || lower.contains("day before yesterday") -> {
                    val start = startOfTodayMs - (2 * oneDayMs)
                    val end = start + oneDayMs - 1L
                    TemporalQuerySpec(start, end, timeBucket, "Parso (2 Days Ago)")
                }
                lower.contains("kal") || lower.contains("yesterday") || lower.contains("last night") -> {
                    val start = startOfTodayMs - oneDayMs
                    val end = startOfTodayMs - 1L
                    val resolvedBucket = if (lower.contains("last night") && timeBucket.isEmpty()) "NIGHT" else timeBucket
                    TemporalQuerySpec(start, end, resolvedBucket, "Kal (Yesterday)")
                }
                lower.contains("pichle hafte") || lower.contains("last week") || lower.contains("this week") -> {
                    val start = startOfTodayMs - (7 * oneDayMs)
                    TemporalQuerySpec(start, nowMs, timeBucket, "Last 7 Days")
                }
                lower.contains("aaj") || lower.contains("today") || timeBucket.isNotEmpty() -> {
                    TemporalQuerySpec(startOfTodayMs, nowMs, timeBucket, "Aaj (Today)")
                }
                else -> null
            }
        }

        private fun extractSearchTokens(query: String): Set<String> {
            return query.lowercase()
                .split(Regex("[^a-zA-Z0-9\\u0900-\\u097F]+"))
                .filter { it.length >= 3 && it !in TEMPORAL_STOP_WORDS }
                .toSet()
        }
    }
}
