package com.jarvisai.assistant.service

import com.jarvisai.assistant.data.local.EpisodicEventEntity
import com.jarvisai.assistant.data.local.MemoryEntity
import com.jarvisai.assistant.data.local.SemanticTripleEntity
import com.jarvisai.assistant.data.repository.JarvisRepository
import com.jarvisai.assistant.model.ActiveEntityFocusStack
import com.jarvisai.assistant.model.ShortTermTurn
import com.jarvisai.assistant.model.WorkingTaskPlanSnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class CognitiveContextBundle(
    val originalUtterance: String,
    val resolvedUtterance: String,
    val stmTurns: List<ShortTermTurn>,
    val entityFocusStack: ActiveEntityFocusStack,
    val workingMemorySnapshot: WorkingTaskPlanSnapshot,
    val episodicMemories: List<EpisodicEventEntity>,
    val semanticTriples: List<SemanticTripleEntity>,
    val longTermMemories: List<MemoryEntity>,
    val unifiedPromptBlock: String,
    val retrievalLatencyMs: Long
)

data class ConsolidationReport(
    val turnsScanned: Int,
    val newLtmMemoriesSaved: Int,
    val newSemanticTriplesSaved: Int,
    val summaryMessage: String
)

/**
 * Central 5-Tier Cognitive Memory Orchestrator for JARVIS AI.
 *
 * Coordinates all 5 human-like memory tiers in parallel (< 35ms):
 * 1. Short-Term Memory (STM) — Current conversation turns, active entity focus stack & pronoun resolution.
 * 2. Working Memory (WM) — Current task steps, intermediate scratchpad variables & UI loop detector.
 * 3. Episodic Memory (EM) — Time-stamped past interactions & temporal query resolution ("kal raat", "aaj subah").
 * 4. Semantic Memory (SM) — Subject-Predicate-Object knowledge graph, kinship/alias resolution & UI concepts.
 * 5. Long-Term Memory (LTM) — Permanent memory vault with 4-factor hybrid retrieval & consolidation.
 */
class CognitiveMemoryOrchestrator(
    private val repository: JarvisRepository
) {

    /**
     * Processes a user utterance across all 5 memory tiers in parallel:
     * - Resolves Hinglish/English pronouns ("usko", "woh", "wahan") and kinship references ("mere bhai ko")
     * - Queries STM, WM, Episodic, Semantic, and LTM simultaneously
     * - Builds the unified 5-Tier Cognitive Context block for Gemini prompt injection.
     */
    suspend fun processUserTurnAndBuildContext(
        userUtterance: String,
        memoryEnabled: Boolean = true,
        maxLtmCount: Int = 16,
        maxEpisodicCount: Int = 5,
        maxSemanticCount: Int = 8
    ): CognitiveContextBundle = coroutineScope {
        val startMs = System.currentTimeMillis()
        val cleanUtterance = userUtterance.trim()

        // 1. Resolve pronouns via Tier 1 STM Focus Stack + Tier 4 Semantic Graph kinship lookup
        val stmResolved = ConversationContextManager.resolvePronounsAndAnaphora(cleanUtterance)
        val kinshipResolved = resolveKinshipReferencesInText(stmResolved)

        val stmTurns = ConversationContextManager.getRecentTurns(limit = 15)
        val focusStack = ConversationContextManager.getActiveEntityFocusStack()
        val wmSnapshot = WorkingMemoryBlackboard.activeTaskPlan.value
        val activeAppName = ConversationContextManager.activeAppContext.value.activeAppName

        if (!memoryEnabled) {
            val block = buildUnifiedPromptBlockInternal(
                resolvedUtterance = kinshipResolved,
                focusStack = focusStack,
                wmSnapshot = wmSnapshot,
                episodes = emptyList(),
                triples = emptyList(),
                ltmMemories = emptyList()
            )
            return@coroutineScope CognitiveContextBundle(
                originalUtterance = cleanUtterance,
                resolvedUtterance = kinshipResolved,
                stmTurns = stmTurns,
                entityFocusStack = focusStack,
                workingMemorySnapshot = wmSnapshot,
                episodicMemories = emptyList(),
                semanticTriples = emptyList(),
                longTermMemories = emptyList(),
                unifiedPromptBlock = block,
                retrievalLatencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            )
        }

        // On session setup (empty utterance) or when user asks memory inquiry questions, expand recall budget
        val lowerUtterance = cleanUtterance.lowercase()
        val isMemoryInquiry = cleanUtterance.isEmpty() ||
            listOf("yaad", "remember", "pata", "jante", "jaante", "kya", "mera naam", "meri", "who am i", "about me", "kuch").any { lowerUtterance.contains(it) }

        val effectiveLtmCount = if (isMemoryInquiry) maxOf(maxLtmCount, 25) else maxLtmCount
        val effectiveSemanticCount = if (isMemoryInquiry) maxOf(maxSemanticCount, 12) else maxSemanticCount

        // Parallel retrieval from Tier 3 (Episodic), Tier 4 (Semantic), and Tier 5 (LTM)
        val episodicDeferred = async {
            repository.episodicRepository.retrieveRelevantEpisodes(
                query = kinshipResolved,
                maxCount = maxEpisodicCount
            )
        }
        val semanticDeferred = async {
            repository.semanticRepository.retrieveRelevantTriples(
                query = kinshipResolved,
                activeAppName = activeAppName,
                maxCount = effectiveSemanticCount
            )
        }
        val ltmDeferred = async {
            repository.retrieveRelevantMemories(
                query = kinshipResolved,
                maxCount = effectiveLtmCount
            )
        }

        val episodes = episodicDeferred.await()
        val triples = semanticDeferred.await()
        val ltmMemories = ltmDeferred.await()

        val unifiedBlock = buildUnifiedPromptBlockInternal(
            resolvedUtterance = kinshipResolved,
            focusStack = focusStack,
            wmSnapshot = wmSnapshot,
            episodes = episodes,
            triples = triples,
            ltmMemories = ltmMemories
        )

        val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        CognitiveContextBundle(
            originalUtterance = cleanUtterance,
            resolvedUtterance = kinshipResolved,
            stmTurns = stmTurns,
            entityFocusStack = focusStack,
            workingMemorySnapshot = wmSnapshot,
            episodicMemories = episodes,
            semanticTriples = triples,
            longTermMemories = ltmMemories,
            unifiedPromptBlock = unifiedBlock,
            retrievalLatencyMs = latency
        )
    }

    /**
     * Resolves kinship terms like "mere bhai", "papa", "mummy" using Tier 4 Semantic Graph
     * so commands like "mere bhai ko WhatsApp message bhejo" automatically include the brother's name.
     */
    private suspend fun resolveKinshipReferencesInText(utterance: String): String {
        val lower = utterance.lowercase()
        val kinshipKeywords = listOf("bhai", "brother", "behen", "didi", "sister", "papa", "father", "dad", "mummy", "mother", "mom", "best friend", "boss")
        val matchedKeyword = kinshipKeywords.firstOrNull { Regex("\\b$it\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) }
            ?: return utterance

        // Do not rewrite if the user is declaring the relationship ("mera bhai Rahul hai")
        if (lower.contains("naam") || lower.contains("name is") || Regex("(?i)\\b(?:mera|meri|my)\\s+$matchedKeyword\\s+[A-Za-z]+\\s+hai\\b").containsMatchIn(utterance)) {
            return utterance
        }

        val resolvedName = repository.semanticRepository.resolveRelationOrAlias(matchedKeyword)
        return if (!resolvedName.isNullOrBlank() && !utterance.contains(resolvedName, ignoreCase = true)) {
            ConversationContextManager.updateFocusedPerson(resolvedName)
            "$utterance [$matchedKeyword = $resolvedName]"
        } else {
            utterance
        }
    }

    /**
     * Rewrites a spoken or typed command with resolved kinship names (Tier 4 Semantic Graph)
     * and resolved pronouns (Tier 1 STM Focus Stack) so PhoneActionManager can directly execute
     * commands like "Mere bhai ko call karo" -> "Rahul ko call karo" and
     * "Usko WhatsApp par Hello bhejo" -> "Rahul ko WhatsApp par Hello bhejo".
     */
    suspend fun resolveExecutableCommand(utterance: String): String {
        var current = utterance.trim()
        if (current.isEmpty()) return current

        val lower = current.lowercase()
        val isDeclaration = lower.contains("naam") ||
            lower.contains("name is") ||
            Regex("(?i)\\b(?:mera|meri|my)\\s+(?:bhai|brother|behen|didi|sister|papa|father|mummy|mother|dost|friend)\\s+[A-Za-z]+\\s+hai\\b").containsMatchIn(current)

        if (!isDeclaration) {
            val kinshipKeywords = listOf(
                "mere bhai" to "bhai",
                "mera bhai" to "bhai",
                "my brother" to "brother",
                "bhai" to "bhai",
                "meri behen" to "behen",
                "my sister" to "sister",
                "behen" to "behen",
                "didi" to "didi",
                "mere papa" to "papa",
                "papa" to "papa",
                "my father" to "father",
                "meri mummy" to "mummy",
                "mummy" to "mummy",
                "my mother" to "mother",
                "mere dost" to "dost",
                "my best friend" to "best friend",
                "my friend" to "friend"
            )
            for ((phrase, key) in kinshipKeywords) {
                val regex = Regex("(?i)\\b$phrase\\b")
                if (regex.containsMatchIn(current)) {
                    val resolvedName = repository.semanticRepository.resolveRelationOrAlias(key)
                    if (!resolvedName.isNullOrBlank()) {
                        ConversationContextManager.updateFocusedPerson(resolvedName)
                        current = current.replace(regex, resolvedName)
                        break
                    }
                }
            }
        }

        return ConversationContextManager.rewritePronounsInCommand(current)
    }

    private val turnsSinceLastConsolidation = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * Records an interaction or action into Tier 3 Episodic Memory, syncs any active
     * Tier 2 Working Memory checkpoint to Room, and triggers automatic Tier 5 consolidation
     * every 10 turns.
     */
    suspend fun recordInteractionEpisode(
        userUtterance: String,
        assistantReply: String,
        involvedApp: String = "",
        eventType: String = "CONVERSATION",
        succeeded: Boolean = true,
        importanceValence: Int = 2
    ) {
        val cleanUser = userUtterance.trim()
        if (cleanUser.isEmpty()) return

        val focus = ConversationContextManager.getActiveEntityFocusStack()
        val appToRecord = involvedApp.ifBlank {
            ConversationContextManager.activeAppContext.value.activeAppName.ifBlank { focus.lastMentionedApp }
        }
        val entities = listOf(focus.lastMentionedPerson, focus.lastMentionedQueryOrItem)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

        val conciseSummary = buildString {
            append("User: \"${cleanUser.take(80)}\"")
            if (assistantReply.isNotBlank()) {
                append(" -> JARVIS: \"${assistantReply.replace("\n", " ").take(90)}\"")
            }
        }

        repository.recordEpisodicEvent(
            summary = conciseSummary,
            eventType = eventType,
            involvedApps = appToRecord,
            involvedEntities = entities,
            outcomeStatus = if (succeeded) "SUCCESS" else "FAILED",
            importanceValence = importanceValence
        )

        // Sync Working Memory checkpoint if active
        WorkingMemoryBlackboard.toRoomEntity()?.let { wmEntity ->
            repository.upsertWorkingMemoryTask(wmEntity)
        }

        // Automatic background consolidation every 10 interactions (Layer 5.1)
        if (turnsSinceLastConsolidation.incrementAndGet() >= 10) {
            turnsSinceLastConsolidation.set(0)
            runCatching { runMemoryConsolidation() }
        }
    }

    /**
     * Runs the Memory Consolidation Pipeline:
     * Scans recent Short-Term Memory turns, extracts Semantic Graph S-P-O triples and Long-Term Memories,
     * and logs a consolidation episode.
     */
    suspend fun runMemoryConsolidation(): ConsolidationReport {
        val recentTurns = ConversationContextManager.getRecentTurns(limit = 20)
        val userTexts = recentTurns.filter { it.role.equals("user", ignoreCase = true) }.map { it.text }

        var newTriplesCount = 0
        for (text in userTexts) {
            val savedTriples = repository.semanticRepository.extractAndStoreSemanticTriples(text)
            newTriplesCount += savedTriples.size
        }

        val consolidatedLtm = repository.memoryRepository.consolidateTurnsToLongTermMemory(userTexts)

        // Also mirror high-confidence IDENTITY/PREFERENCE/RELATIONSHIP semantic triples into LTM if not already present
        val allTriples = repository.semanticRepository.getAllTriplesOnce()
        var mirroredCount = 0
        for (triple in allTriples.take(15)) {
            if (triple.category in setOf("IDENTITY", "RELATIONSHIP", "PREFERENCE")) {
                val title = "${triple.subject} ${triple.predicate.replace("_", " ")}"
                val content = "${triple.subject}'s ${triple.predicate.replace("_", " ")} is ${triple.objectValue}"
                val dup = repository.findDuplicateMemory(title, content)
                if (dup == null) {
                    repository.saveApprovedMemory(
                        title = title,
                        content = content,
                        category = when (triple.category) {
                            "PREFERENCE" -> com.jarvisai.assistant.model.MemoryCategory.PREFERENCES
                            else -> com.jarvisai.assistant.model.MemoryCategory.PERSONAL
                        },
                        tags = "consolidated,semantic,${triple.predicate}",
                        isPinned = triple.confidence >= 0.95f
                    )
                    mirroredCount++
                }
            }
        }

        val totalLtmSaved = consolidatedLtm.size + mirroredCount
        val summary = "Consolidated ${userTexts.size} STM turns -> $newTriplesCount Semantic triples & $totalLtmSaved LTM records."
        return ConsolidationReport(
            turnsScanned = userTexts.size,
            newLtmMemoriesSaved = totalLtmSaved,
            newSemanticTriplesSaved = newTriplesCount,
            summaryMessage = summary
        )
    }

    private fun buildUnifiedPromptBlockInternal(
        resolvedUtterance: String,
        focusStack: ActiveEntityFocusStack,
        wmSnapshot: WorkingTaskPlanSnapshot,
        episodes: List<EpisodicEventEntity>,
        triples: List<SemanticTripleEntity>,
        ltmMemories: List<MemoryEntity>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("=== [JARVIS 5-TIER COGNITIVE MEMORY ARCHITECTURE] ===")

        // Tier 1: STM & Entity Focus
        sb.appendLine(ConversationContextManager.buildSessionContextPromptBlock())
        if (focusStack.lastMentionedPerson.isNotBlank() ||
            focusStack.lastMentionedApp.isNotBlank() ||
            focusStack.lastMentionedQueryOrItem.isNotBlank()
        ) {
            sb.appendLine(
                "[TIER 1 — ACTIVE ENTITY FOCUS STACK] " +
                    "Person='${focusStack.lastMentionedPerson.ifBlank { "none" }}', " +
                    "App='${focusStack.lastMentionedApp.ifBlank { "none" }}', " +
                    "Topic/Query='${focusStack.lastMentionedQueryOrItem.ifBlank { "none" }}'"
            )
        }
        if (focusStack.rollingSessionSummary.isNotBlank()) {
            sb.appendLine("[TIER 1 — ROLLING SESSION MICRO-SUMMARY] ${focusStack.rollingSessionSummary}")
        }
        if (resolvedUtterance.isNotBlank()) {
            sb.appendLine("[PRONOUN-RESOLVED USER INTENT] $resolvedUtterance")
        }

        // Tier 2: Working Memory Blackboard
        val wmBlock = WorkingMemoryBlackboard.buildWorkingMemoryPromptBlock()
        if (wmBlock.isNotBlank()) {
            sb.appendLine()
            sb.appendLine(wmBlock)
        } else if (wmSnapshot.rootGoal.isNotBlank()) {
            sb.appendLine()
            sb.appendLine("[TIER 2 — WORKING MEMORY] Active Goal: ${wmSnapshot.rootGoal}")
        }

        // Tier 3: Episodic Memory
        if (episodes.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("[TIER 3 — EPISODIC MEMORY (PAST INTERACTIONS & TIMELINE)]")
            episodes.forEachIndexed { idx, ep ->
                sb.appendLine("${idx + 1}. [${ep.timeBucket} | ${ep.eventType} | ${ep.outcomeStatus}] ${ep.summary}")
            }
        }

        // Tier 4: Semantic Memory Graph
        if (triples.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("[TIER 4 — SEMANTIC KNOWLEDGE GRAPH (S-P-O FACTS & CONCEPTS)]")
            triples.forEachIndexed { idx, tr ->
                sb.appendLine("${idx + 1}. (${tr.subject}) --[${tr.predicate}]--> (${tr.objectValue}) [${tr.category}]")
            }
        }

        // Tier 5: Long-Term Memory
        if (ltmMemories.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("[TIER 5 — LONG-TERM MEMORY VAULT (HYBRID RECALL)]")
            sb.appendLine("CRITICAL DIRECTIVE: You KNOW and REMEMBER all verified facts listed below. When the user asks what you remember or inquires about their identity/preferences, cite these facts. NEVER say 'mujhe kuch yaad nahi hai'.")
            ltmMemories.forEachIndexed { idx, mem ->
                sb.appendLine("${idx + 1}. [${mem.category}] ${mem.title}: ${mem.content} (importance=${mem.importance}, recallCount=${mem.accessCount})")
            }
        }

        sb.appendLine("=====================================================")
        return sb.toString().trim()
    }
}
