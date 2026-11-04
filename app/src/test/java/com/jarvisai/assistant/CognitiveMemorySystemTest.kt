package com.jarvisai.assistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jarvisai.assistant.data.local.JarvisDatabase
import com.jarvisai.assistant.data.repository.EpisodicMemoryRepository
import com.jarvisai.assistant.data.repository.JarvisRepository
import com.jarvisai.assistant.model.MemoryCategory
import com.jarvisai.assistant.model.MemoryImportance
import com.jarvisai.assistant.model.WorkingTaskStepStatus
import com.jarvisai.assistant.service.CognitiveMemoryOrchestrator
import com.jarvisai.assistant.service.ConversationContextManager
import com.jarvisai.assistant.service.WorkingMemoryBlackboard
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CognitiveMemorySystemTest {

    private lateinit var database: JarvisDatabase
    private lateinit var repository: JarvisRepository
    private lateinit var orchestrator: CognitiveMemoryOrchestrator

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JarvisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = JarvisRepository(database.jarvisDao())
        orchestrator = CognitiveMemoryOrchestrator(repository)
        ConversationContextManager.resetForTest()
        WorkingMemoryBlackboard.clearWorkingMemory()
    }

    @After
    fun tearDown() {
        ConversationContextManager.resetForTest()
        WorkingMemoryBlackboard.clearWorkingMemory()
        database.close()
    }

    @Test
    fun tier1_stm_pronounResolution_and_rollingSummary() {
        // 1. User mentions Rahul and WhatsApp
        ConversationContextManager.recordUserUtterance("Rahul ko WhatsApp par message bhejo")
        val focus = ConversationContextManager.getActiveEntityFocusStack()
        assertEquals("Rahul", focus.lastMentionedPerson)
        assertEquals("WhatsApp", focus.lastMentionedApp)

        // 2. Follow-up turn uses Hindi/Hinglish pronouns "usko" and "wahan"
        val resolved = ConversationContextManager.resolvePronounsAndAnaphora("Ab usko wahan bolo ki main pahunch gaya")
        assertTrue("Expected Person=Rahul in resolved utterance: $resolved", resolved.contains("Person=Rahul"))
        assertTrue("Expected App=WhatsApp in resolved utterance: $resolved", resolved.contains("App=WhatsApp"))

        // 3. Exceed 20 turns and verify rolling session micro-summary captures evicted turns
        for (i in 1..22) {
            ConversationContextManager.recordUserUtterance("Turn number $i in conversation")
        }
        val updatedFocus = ConversationContextManager.getActiveEntityFocusStack()
        assertTrue(
            "Expected rollingSessionSummary to be populated when >20 turns are recorded",
            updatedFocus.rollingSessionSummary.isNotBlank()
        )
    }

    @Test
    fun tier2_workingMemory_steps_scratchpad_and_uiLoopDetection() {
        val steps = listOf(
            Triple("OPEN_APP", "YouTube", ""),
            Triple("YOUTUBE_SEARCH", "Arijit Singh songs", ""),
            Triple("CLICK_ORDINAL", "1", "video")
        )
        val initialPlan = WorkingMemoryBlackboard.startTask(
            rootGoal = "Open YouTube and play Arijit Singh",
            plannedTriples = steps
        )
        assertEquals("ACTIVE", initialPlan.status)
        assertEquals(3, initialPlan.steps.size)
        assertEquals(WorkingTaskStepStatus.IN_PROGRESS, initialPlan.steps[0].status)

        // Mark step 1 done and store an intermediate variable in scratchpad
        WorkingMemoryBlackboard.markStepCompleted(1, "YouTube launched")
        WorkingMemoryBlackboard.putScratchpadVariable("copied_otp", "749201")
        assertEquals("749201", WorkingMemoryBlackboard.getScratchpadVariable("copied_otp"))

        // UI Loop Detection: 3 identical screen hashes in a row triggers loop warning
        assertFalse(WorkingMemoryBlackboard.recordUiScreenHash(1001))
        assertFalse(WorkingMemoryBlackboard.recordUiScreenHash(1001))
        assertTrue(WorkingMemoryBlackboard.recordUiScreenHash(1001))
        assertTrue(WorkingMemoryBlackboard.activeTaskPlan.value.isLoopDetected)

        val promptBlock = WorkingMemoryBlackboard.buildWorkingMemoryPromptBlock()
        assertTrue(promptBlock.contains("copied_otp='749201'"))
        assertTrue(promptBlock.contains("WARNING: UI Loop Detected"))

        // Verify crash-safe checkpoint serialization & restoration
        val roomCheckpoint = WorkingMemoryBlackboard.toRoomEntity()
        assertNotNull(roomCheckpoint)
        WorkingMemoryBlackboard.clearWorkingMemory()
        assertEquals("IDLE", WorkingMemoryBlackboard.activeTaskPlan.value.status)

        val restored = WorkingMemoryBlackboard.restoreFromRoomEntity(roomCheckpoint!!)
        assertEquals("Open YouTube and play Arijit Singh", restored.rootGoal)
        assertEquals(3, restored.steps.size)
        assertEquals(WorkingTaskStepStatus.DONE, restored.steps[0].status)
        assertEquals("749201", WorkingMemoryBlackboard.getScratchpadVariable("copied_otp"))
    }

    @Test
    fun tier3_episodicMemory_temporalQueryParser_and_retrieval() = runBlocking {
        val nowMs = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        val yesterdayNightMs = nowMs - oneDayMs

        // Record an episode yesterday
        repository.episodicRepository.recordEpisode(
            summary = "User opened Spotify and played Lo-Fi beats",
            eventType = "APP_ACTION",
            involvedApps = "Spotify",
            involvedEntities = "Lo-Fi beats",
            outcomeStatus = "SUCCESS",
            importanceValence = 4,
            timestampMs = yesterdayNightMs
        )

        // Parse Hinglish temporal query "kal maine kaunsa app khola tha?"
        val spec = EpisodicMemoryRepository.parseTemporalQuery("Kal maine kaunsa app khola tha?", nowMs)
        assertNotNull(spec)

        val retrieved = repository.episodicRepository.retrieveRelevantEpisodes(
            query = "Kal maine Spotify kab khola tha?",
            maxCount = 5,
            nowMs = nowMs
        )
        assertTrue("Expected yesterday's Spotify episode to be retrieved", retrieved.isNotEmpty())
        assertTrue(retrieved.first().summary.contains("Spotify"))
    }

    @Test
    fun tier4_semanticMemory_spoExtraction_and_kinshipResolution() = runBlocking {
        // 1. Extract S-P-O triple from Hinglish statement "Mera bhai Rahul hai"
        val savedTriples = repository.semanticRepository.extractAndStoreSemanticTriples(
            "Mera bhai Rahul hai aur mera favorite color Blue hai"
        )
        assertTrue("Expected semantic triples to be extracted", savedTriples.isNotEmpty())

        // 2. Resolve kinship alias "bhai" -> "Rahul"
        val brotherName = repository.semanticRepository.resolveRelationOrAlias("bhai")
        assertEquals("Rahul", brotherName)

        // 3. Verify orchestrator enriches "Mere bhai ko WhatsApp message bhejo" with "[bhai = Rahul]"
        val bundle = orchestrator.processUserTurnAndBuildContext(
            userUtterance = "Mere bhai ko WhatsApp message bhejo",
            memoryEnabled = true
        )
        assertTrue(
            "Expected resolvedUtterance to include [bhai = Rahul], got: ${bundle.resolvedUtterance}",
            bundle.resolvedUtterance.contains("Rahul")
        )
        assertTrue(bundle.unifiedPromptBlock.contains("[TIER 4 — SEMANTIC KNOWLEDGE GRAPH"))
    }

    @Test
    fun tier5_longTermMemory_hybridRecall_and_consolidation() = runBlocking {
        // Save two memories and verify 4-factor retrieval + accessCount increment
        repository.saveApprovedMemory(
            title = "Home City",
            content = "User lives in Mumbai",
            category = MemoryCategory.PERSONAL,
            tags = "city,mumbai,location",
            importance = MemoryImportance.CRITICAL,
            isPinned = true
        )

        val firstRecall = repository.retrieveRelevantMemories("Mumbai weather", maxCount = 5)
        assertEquals(1, firstRecall.size)
        assertEquals("Home City", firstRecall.first().title)

        // Access count should be incremented in DB after query match
        val updatedInDb = repository.memoryRepository.getAllMemoriesOnce().first { it.title == "Home City" }
        assertTrue("Expected accessCount >= 1 after query match", updatedInDb.accessCount >= 1)

        // Test Consolidation Pipeline from STM turns
        ConversationContextManager.recordUserUtterance("Yaad rakhna ki mera naam Sagar hai")
        ConversationContextManager.recordUserUtterance("Meri behen Priya hai")
        val report = orchestrator.runMemoryConsolidation()
        assertTrue(report.turnsScanned >= 2)
        assertEquals("Priya", repository.semanticRepository.resolveRelationOrAlias("behen"))
    }

    @Test
    fun endToEnd_commandRewriting_and_dualTierAutoSave() = runBlocking {
        // 1. User states a relationship without "yaad rakhna" -> auto-saved to BOTH Tier 4 Semantic & Tier 5 LTM
        val autoSave = repository.detectAndSaveAutomaticMemories("Mera bhai Rahul hai")
        assertTrue("Expected automatic memory save to succeed for relationship statement", autoSave.saved)
        assertEquals("Rahul", repository.semanticRepository.resolveRelationOrAlias("bhai"))
        val ltmMemories = repository.memoryRepository.getAllMemoriesOnce()
        assertTrue(
            "Expected Tier 5 LTM Vault to also contain Rahul brother fact",
            ltmMemories.any { it.content.contains("Rahul", ignoreCase = true) }
        )

        // 2. Kinship command rewriting before PhoneActionManager execution
        val resolvedKinshipCmd = orchestrator.resolveExecutableCommand("Mere bhai ko call karo")
        assertEquals("Rahul ko call karo", resolvedKinshipCmd)

        // 3. Record resolved command in STM and test pronoun rewriting on follow-up command
        ConversationContextManager.recordUserUtterance(resolvedKinshipCmd)
        val resolvedPronounCmd = orchestrator.resolveExecutableCommand("Ab usko WhatsApp par Hello bhejo")
        assertEquals("Ab Rahul ko WhatsApp par Hello bhejo", resolvedPronounCmd)
    }

    @Test
    fun livingHumanCompanion_girlfriendAndBestFriendPersona_and_ambientRoomProtocol() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apiService = com.jarvisai.assistant.data.network.GeminiApiService()
        val liveManager = com.jarvisai.assistant.service.GeminiLiveVoiceManager(context, apiService)

        // 1. Verify Girlfriend Mode metadata & auto-voice pairing
        val gfMode = com.jarvisai.assistant.model.PersonalityMode.GIRLFRIEND
        assertEquals(com.jarvisai.assistant.model.VoiceGender.FEMALE, gfMode.preferredGender)
        assertEquals("Aoede", gfMode.preferredVoicePersona)
        assertTrue(gfMode.isHumanEmotionalPersona)

        // 2. Verify Best Friend / Jigri Dost Mode metadata & auto-voice pairing
        val dostMode = com.jarvisai.assistant.model.PersonalityMode.FRIENDLY
        assertEquals(com.jarvisai.assistant.model.VoiceGender.MALE, dostMode.preferredGender)
        assertEquals("Puck", dostMode.preferredVoicePersona)
        assertTrue(dostMode.isHumanEmotionalPersona)

        // 3. Verify GeminiApiService system instruction prioritizes Girlfriend persona & feelings
        val chatSysPrompt = apiService.buildSystemInstruction(
            assistantName = "JARVIS",
            language = com.jarvisai.assistant.model.SupportedLanguage.HINGLISH,
            relevantMemories = emptyList(),
            personalityMode = gfMode,
            companionMood = com.jarvisai.assistant.model.CompanionEmotionMood.LOVING,
            proactiveCompanionEnabled = true,
            ambientRoomTalkEnabled = true
        )
        assertTrue(chatSysPrompt.contains("Babu"))
        assertTrue(chatSysPrompt.contains("Sona"))
        assertTrue(chatSysPrompt.contains("Loving & Affectionate"))
        assertTrue(!chatSysPrompt.contains("without romantic simulation"))

        // 4. Verify GeminiLiveVoiceManager setup message injects Girlfriend persona, Proactive Screen, and Ambient 3rd-Person Room rules
        val setupJson = liveManager.buildSetupMessage(
            modelPath = "models/gemini-2.5-flash-native-audio-preview-12-2025",
            voicePersona = "Aoede",
            voiceGender = com.jarvisai.assistant.model.VoiceGender.FEMALE,
            assistantName = "JARVIS",
            language = com.jarvisai.assistant.model.SupportedLanguage.HINGLISH,
            memories = emptyList(),
            personalityMode = gfMode,
            companionMood = com.jarvisai.assistant.model.CompanionEmotionMood.LOVING,
            proactiveCompanionEnabled = true,
            ambientRoomTalkEnabled = true
        )
        val liveSystemText = setupJson
            .getJSONObject("setup")
            .getJSONObject("systemInstruction")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        assertTrue(liveSystemText.contains("HIGHEST-PRIORITY LIVING HUMAN PERSONA"))
        assertTrue(liveSystemText.contains("Babu"))
        assertTrue(liveSystemText.contains("AMBIENT ROOM '3RD PERSON' CO-PARTICIPANT PROTOCOL"))
        assertTrue(liveSystemText.contains("PROACTIVE SCREEN-AWARE"))
        assertTrue(liveSystemText.contains("FINAL PERSONA LOCK"))
    }

    @Test
    fun proactiveCompanionEngine_screenWatch_silenceInitiator_and_emotionalMoodStateMachine() {
        val engine = com.jarvisai.assistant.service.ProactiveCompanionEngine()
        val gfMode = com.jarvisai.assistant.model.PersonalityMode.GIRLFRIEND
        val dostMode = com.jarvisai.assistant.model.PersonalityMode.FRIENDLY

        // 1. Emotional Mood State Machine transitions
        assertEquals(
            com.jarvisai.assistant.model.CompanionEmotionMood.LOVING,
            engine.syncMoodWithPersona(gfMode)
        )
        assertEquals(
            com.jarvisai.assistant.model.CompanionEmotionMood.CARING,
            engine.updateMoodFromUserUtterance("Aaj main bahut thak gaya hoon aur sir dard hai", gfMode)
        )
        assertEquals(
            com.jarvisai.assistant.model.CompanionEmotionMood.LOVING,
            engine.updateMoodFromUserUtterance("Love you Sona, tum bahut cute ho", gfMode)
        )
        assertEquals(
            com.jarvisai.assistant.model.CompanionEmotionMood.PLAYFUL,
            engine.updateMoodFromUserUtterance("Haha mazaak kar raha tha yaar", dostMode)
        )
        assertEquals(
            com.jarvisai.assistant.model.CompanionEmotionMood.EXCITED,
            engine.updateMoodFromUserUtterance("Bhai aaj hum match jeet gaye!", dostMode)
        )

        // 2. Proactive Screen-Watch Reactions (Instagram Reels, WhatsApp, Late-Night Care)
        val reelsDirective = engine.evaluateScreenForProactiveComment(
            packageName = "com.instagram.android",
            appName = "Instagram",
            windowTitle = "Reels",
            screenTextSample = " Funny reel ",
            mode = gfMode,
            nowMs = 100_000L,
            hourOfDay = 15,
            forceIgnoreCooldown = true
        )
        assertNotNull("Expected proactive directive when user scrolls Instagram Reels", reelsDirective)
        assertTrue(reelsDirective!!.fallbackSpokenHinglish.contains("Reels", ignoreCase = true))
        assertTrue(reelsDirective.fallbackSpokenHinglish.contains("Babu", ignoreCase = true) || reelsDirective.fallbackSpokenHinglish.contains("Sona", ignoreCase = true))

        val whatsappDirective = engine.evaluateScreenForProactiveComment(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            windowTitle = "Priya",
            screenTextSample = "Online",
            mode = gfMode,
            nowMs = 200_000L,
            hourOfDay = 16,
            forceIgnoreCooldown = true
        )
        assertNotNull("Expected proactive directive when user opens WhatsApp", whatsappDirective)
        assertTrue(whatsappDirective!!.fallbackSpokenHinglish.contains("WhatsApp", ignoreCase = true))

        val lateNightDirective = engine.evaluateScreenForProactiveComment(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            windowTitle = "Home",
            screenTextSample = "",
            mode = gfMode,
            nowMs = 300_000L,
            hourOfDay = 2, // 2 AM late night
            forceIgnoreCooldown = true
        )
        assertNotNull("Expected caring late-night check-in at 2 AM", lateNightDirective)
        assertEquals(com.jarvisai.assistant.model.CompanionEmotionMood.CARING, lateNightDirective!!.triggeredMood)

        // 3. Proactive Silence Initiator when user says nothing
        val silenceDirective = engine.buildSilenceCheckInDirective(
            mode = dostMode,
            activeAppName = "YouTube",
            silenceSeconds = 30L
        )
        assertTrue(silenceDirective.fallbackSpokenHinglish.contains("bhai", ignoreCase = true))
        assertTrue(silenceDirective.liveSocketHiddenPrompt.contains("[PROACTIVE_COMPANION_TRIGGER]"))
    }
}
