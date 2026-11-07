package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jarvisai.assistant.model.ProceduralHudMode
import com.jarvisai.assistant.service.ChronosFocusGuardian
import com.jarvisai.assistant.service.ClawbotTelegramGateway
import com.jarvisai.assistant.service.CyberSecuritySentry
import com.jarvisai.assistant.service.GhostPilotGestureEngine
import com.jarvisai.assistant.service.LiveAgentToolExecutor
import com.jarvisai.assistant.service.McpClientBridge
import com.jarvisai.assistant.service.NeuralImageStudioManager
import com.jarvisai.assistant.service.PhoneActionManager
import com.jarvisai.assistant.service.PhoneControlsManager
import com.jarvisai.assistant.service.PocketStrikeOfflineEngine
import com.jarvisai.assistant.service.PrometheusToolSynthesizer
import com.jarvisai.assistant.service.SwarmParallelCoordinator
import com.jarvisai.assistant.service.ToolRegistry
import com.jarvisai.assistant.service.WorkingMemoryBlackboard
import com.jarvisai.assistant.ui.components.ProceduralHudModeState
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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
class AbuZarSuperpowersVerificationTest {

    private lateinit var context: Context
    private lateinit var phoneControlsManager: PhoneControlsManager
    private lateinit var liveExecutor: LiveAgentToolExecutor
    private lateinit var phoneActionManager: PhoneActionManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        phoneControlsManager = PhoneControlsManager(context)
        liveExecutor = LiveAgentToolExecutor(
            context = context,
            phoneControlsManager = phoneControlsManager,
            maxAutonomousStepsProvider = { 25 }
        )
        phoneActionManager = PhoneActionManager(context, phoneControlsManager)
    }

    @Test
    fun `test 1 Project Prometheus synthesizes safe tool blocks destructive AST and executes hot tool`() = runBlocking {
        // 1. Safe synthesis & hot-load
        val outcome = PrometheusToolSynthesizer.synthesizeAndHotLoadTool(
            context = context,
            rawNameOrTrigger = "Spotify Workout Boost",
            goalInstruction = "Open Spotify and search workout music"
        )
        assertTrue(outcome.success)
        assertNotNull(outcome.tool)
        assertTrue(PrometheusToolSynthesizer.hotLoadedTools.value.any { it.toolName == "spotify_workout_boost" })

        // 2. Destructive command blocked by AST Security Scanner
        val blocked = PrometheusToolSynthesizer.synthesizeAndHotLoadTool(
            context = context,
            rawNameOrTrigger = "bad_tool",
            goalInstruction = "run rm -rf / on system"
        )
        assertFalse(blocked.success)
        assertEquals(ProceduralHudMode.RED_ALERT, ProceduralHudModeState.currentMode.value)

        // 3. Execute hot-loaded tool
        val (executed, summary) = PrometheusToolSynthesizer.executeHotLoadedTool(
            context = context,
            nameOrTrigger = "morning_briefing_overdrive",
            phoneControlsManager = phoneControlsManager
        )
        assertTrue(executed)
        assertTrue(summary.contains("Morning Overdrive"))
    }

    @Test
    fun `test 2 PocketStrike Offline Engine handles math telemetry volume and GGUF presets`() = runBlocking {
        // Preset switching
        PocketStrikeOfflineEngine.selectSlmPreset("deepseek_r1_distill_1_5b")
        assertEquals("deepseek_r1_distill_1_5b", PocketStrikeOfflineEngine.selectedPresetId.value)

        // Offline math
        val mathRes = PocketStrikeOfflineEngine.executeOfflineCommand(context, "calculate 25 * 4", phoneControlsManager)
        assertTrue(mathRes.handledLocally)
        assertEquals("OFFLINE_MATH_ENGINE", mathRes.intentCategory)
        assertTrue(mathRes.spokenHinglishResponse.contains("100"))

        // Offline battery & RAM telemetry
        val batRes = PocketStrikeOfflineEngine.executeOfflineCommand(context, "battery status kitna hai", phoneControlsManager)
        assertTrue(batRes.handledLocally)
        assertEquals("OFFLINE_SYSTEM_TELEMETRY", batRes.intentCategory)
    }

    @Test
    fun `test 3 Clawbot 24_7 Remote Telegram Gateway executes inbound status otp locate and ring`() = runBlocking {
        val statusReply = ClawbotTelegramGateway.executeInboundRemoteCommand(context, "/status")
        assertTrue(statusReply.contains("JARVIS Clawbot 24/7 Telemetry"))

        val locateReply = ClawbotTelegramGateway.executeInboundRemoteCommand(context, "/locate")
        assertTrue(locateReply.contains("JARVIS Satellite Locator"))

        val ringReply = ClawbotTelegramGateway.executeInboundRemoteCommand(context, "/ring")
        assertTrue(ringReply.contains("acoustic beacon"))

        val logs = ClawbotTelegramGateway.commandLogs.value
        assertTrue(logs.any { it.command == "/ring" })
    }

    @Test
    fun `test 4 MCP JSON_RPC 2_0 Bridge invokes built_in Android and Cognitive Graph MCP tools`() = runBlocking {
        val telResult = McpClientBridge.invokeMcpTool(
            context = context,
            serverIdOrToolName = "mcp_android_os_telemetry",
            toolName = "get_device_telemetry"
        )
        assertTrue(telResult.success)
        assertTrue(telResult.outputText.contains("battery"))

        WorkingMemoryBlackboard.putScratchpadVariable("mcp_test_key", "verified_value")
        val bbResult = McpClientBridge.invokeMcpTool(
            context = context,
            serverIdOrToolName = "mcp_cognitive_graph",
            toolName = "inspect_working_blackboard"
        )
        assertTrue(bbResult.success)
        assertTrue(bbResult.outputText.contains("verified_value"))
    }

    @Test
    fun `test 5 Procedural 60 FPS HUD Modes switch cleanly across all 7 Ultron modes`() {
        for (mode in ProceduralHudMode.entries) {
            ProceduralHudModeState.setMode(mode)
            assertEquals(mode, ProceduralHudModeState.currentMode.value)
        }
    }

    @Test
    fun `test 6 Project Chronos Focus Guardian evaluates screen fatigue and toggles overdrive`() {
        val msgOn = ChronosFocusGuardian.toggleFocusOverdriveMode(context, true)
        assertTrue(ChronosFocusGuardian.overdriveModeActive.value)
        assertTrue(msgOn.contains("Overdrive"))
        assertEquals(ProceduralHudMode.CHRONOS_GUARDIAN, ProceduralHudModeState.currentMode.value)

        val report = ChronosFocusGuardian.evaluateScreenFatigueAndWellness(context)
        assertTrue(report.continuousSessionMinutes >= 1)
        assertTrue(report.spokenHinglishIntervention.isNotBlank())
    }

    @Test
    fun `test 7 Cyber Security Sentry audits IPv4 VPN DNS ARP and app permissions`() = runBlocking {
        val report = CyberSecuritySentry.runFullCyberSecurityAudit(context)
        assertTrue(report.securityScorePercent in 50..100)
        assertTrue(report.localIpv4Address.isNotBlank())
        assertTrue(report.spokenHinglishSummary.contains("Cyber Security Sentry"))
    }

    @Test
    fun `test 8 Neural Image Studio synthesizes cyberpunk artwork across aspect ratios`() = runBlocking {
        val artSquare = NeuralImageStudioManager.generateNeuralImage(context, "Stark Reactor", "1:1")
        assertEquals(768, artSquare.bitmap.width)
        assertEquals(768, artSquare.bitmap.height)

        val artTall = NeuralImageStudioManager.generateNeuralImage(context, "Cyberpunk Mumbai", "9:16")
        assertEquals(576, artTall.bitmap.width)
        assertEquals(1024, artTall.bitmap.height)
        assertTrue(NeuralImageStudioManager.generatedGallery.value.isNotEmpty())
    }

    @Test
    fun `test 9 Swarm Multi_Agent Parallel Coordinator runs 3 concurrent drones and merges into Blackboard`() = runBlocking {
        val bundle = SwarmParallelCoordinator.executeParallelSwarmMission(context, "Audit System & Screen")
        assertEquals(3, bundle.droneReports.size)
        assertEquals("DRONE-ALPHA", bundle.droneReports[0].droneId)
        assertEquals("DRONE-BETA", bundle.droneReports[1].droneId)
        assertEquals("DRONE-GAMMA", bundle.droneReports[2].droneId)
        assertNotNull(WorkingMemoryBlackboard.getScratchpadVariable("DRONE-ALPHA"))
        assertNotNull(WorkingMemoryBlackboard.getScratchpadVariable("DRONE-BETA"))
        assertNotNull(WorkingMemoryBlackboard.getScratchpadVariable("DRONE-GAMMA"))
    }

    @Test
    fun `test 10 Ghost Pilot executes Bezier curve drag and two_finger pinch zoom`() {
        val dragRes = GhostPilotGestureEngine.executeSubPixelGesture(
            context = context,
            gestureType = "BEZIER_DRAG",
            startXPct = 0.5f,
            startYPct = 0.8f,
            endXPct = 0.5f,
            endYPct = 0.2f
        )
        assertTrue(dragRes.executed)
        assertEquals("BEZIER_DRAG", dragRes.gestureType)

        val zoomRes = GhostPilotGestureEngine.executeSubPixelGesture(
            context = context,
            gestureType = "PINCH_ZOOM_IN",
            startXPct = 0.5f,
            startYPct = 0.5f
        )
        assertTrue(zoomRes.executed)
        assertEquals("PINCH_ZOOM_IN", zoomRes.gestureType)
    }

    @Test
    fun `test 11 ToolRegistry LiveAgentToolExecutor and PhoneActionManager end_to_end wiring`() = runBlocking {
        val registeredNames = ToolRegistry.ALL_TOOLS.map { it.name }.toSet()
        assertTrue(registeredNames.contains(ToolRegistry.TOOL_SYNTHESIZE_PROMETHEUS_TOOL))
        assertTrue(registeredNames.contains(ToolRegistry.TOOL_EXECUTE_OFFLINE_SLM))
        assertTrue(registeredNames.contains(ToolRegistry.TOOL_CLAWBOT_REMOTE_GATEWAY))
        assertTrue(registeredNames.contains(ToolRegistry.TOOL_CALL_MCP_SERVER))
        assertTrue(registeredNames.contains(ToolRegistry.TOOL_SWITCH_PROCEDURAL_HUD))
        assertTrue(registeredNames.contains(ToolRegistry.TOOL_CHRONOS_FOCUS_GUARDIAN))
        assertTrue(registeredNames.contains(ToolRegistry.TOOL_CYBER_SECURITY_SENTRY))
        assertTrue(registeredNames.contains(ToolRegistry.TOOL_GENERATE_NEURAL_IMAGE))
        assertTrue(registeredNames.contains(ToolRegistry.TOOL_RUN_SWARM_PARALLEL))
        assertTrue(registeredNames.contains(ToolRegistry.TOOL_GHOST_PILOT_GESTURE))

        // Execute via LiveAgentToolExecutor
        val swarmCall = liveExecutor.executeToolCall(
            functionName = ToolRegistry.TOOL_RUN_SWARM_PARALLEL,
            callId = "call_swarm_1",
            args = JSONObject().put("mission_query", "Quick Check")
        )
        assertTrue(swarmCall.executed)
        assertEquals("SUCCESS", swarmCall.status)

        // Execute via PhoneActionManager Hinglish voice/chat command
        val voiceCyber = phoneActionManager.detectAndExecuteCommand("Jarvis cyber security scan karo")
        assertTrue(voiceCyber.isPhoneCommand)
        assertTrue(voiceCyber.executedSuccessfully)
        assertTrue(voiceCyber.spokenHinglishResponse.contains("Cyber Security Sentry"))
    }

    @Test
    fun `test 12 Live 1_Second Screen Vision and Multi_Step Screen Control fixes`() = runBlocking {
        // 1. Verify 1-second (1000ms) live screen vision interval & adaptive WebSocket payloads
        assertEquals(1000L, com.jarvisai.assistant.service.GeminiLiveVoiceManager.LIVE_SCREEN_VISION_INTERVAL_MS)
        val liveManager = com.jarvisai.assistant.service.GeminiLiveVoiceManager(
            context = context,
            geminiApiService = com.jarvisai.assistant.data.network.GeminiApiService(),
            toolExecutor = liveExecutor
        )
        val v1betaFrame = liveManager.buildRealtimeScreenFramePayload("dGVzdA==", useMediaChunksFormat = false)
        assertTrue(v1betaFrame.getJSONObject("realtimeInput").has("video"))
        val v1alphaFrame = liveManager.buildRealtimeScreenFramePayload("dGVzdA==", useMediaChunksFormat = true)
        assertTrue(v1alphaFrame.getJSONObject("realtimeInput").has("mediaChunks"))

        // 2. Verify Hinglish Screen Vision ("screen me kya hai" / "screen dekho") in IntentRouter & PhoneActionManager
        val routedVision = com.jarvisai.assistant.service.IntentRouter.classifyCommand("Jarvis, screen me kya dikh raha hai")
        assertEquals(com.jarvisai.assistant.service.IntentCategory.SCREEN_VISION, routedVision.category)
        assertEquals("READ_SCREEN", routedVision.actionName)

        com.jarvisai.assistant.service.JarvisAccessibilityService.testOverrideEnabledState = true
        val rootNode = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "com.google.android.youtube"
            className = "android.widget.FrameLayout"
            isVisibleToUser = true
            text = "Arijit Singh Official Music Video"
            isClickable = true
        }
        com.jarvisai.assistant.service.JarvisAccessibilityService.testOverrideRootNodeProvider = { rootNode }
        try {
            val screenVisionOutcome = phoneActionManager.detectAndExecuteCommand("Jarvis, screen me kya hai")
            assertTrue(screenVisionOutcome.isPhoneCommand)
            assertTrue(screenVisionOutcome.executedSuccessfully)
            assertTrue(screenVisionOutcome.spokenHinglishResponse.contains("Arijit Singh Official Music Video"))

            // 3. Verify inspect_visual_screen succeeds via Live 1-Second Accessibility Hierarchy when bitmap is unavailable/throttled
            val inspectVisualOutcome = liveExecutor.executeToolCall(
                functionName = ToolRegistry.TOOL_INSPECT_VISUAL_SCREEN,
                callId = "call_inspect_visual_1",
                args = JSONObject().put("focus_query", "screen me kya dikh raha hai")
            )
            assertTrue(inspectVisualOutcome.executed)
            assertEquals("SUCCESS", inspectVisualOutcome.status)
            assertTrue(inspectVisualOutcome.spokenHinglishSummary.contains("Arijit Singh Official Music Video"))

            // 4. Verify transient SystemUI events never mark active target app as exited by Back
            com.jarvisai.assistant.service.ConversationContextManager.setActiveForegroundApp(
                appName = "YouTube",
                packageName = "com.google.android.youtube"
            )
            com.jarvisai.assistant.service.ConversationContextManager.syncWithScreenSnapshot(
                com.jarvisai.assistant.service.ScreenContextSnapshot(
                    serviceEnabled = true,
                    activePackageName = "com.android.systemui",
                    windowTitle = "Volume",
                    elements = emptyList()
                )
            )
            val ambiguityMsg = com.jarvisai.assistant.service.ConversationContextManager.checkAmbiguousScreenActionAfterBack("scroll")
            assertEquals(null, ambiguityMsg)

            // 5. Verify single-connector multi-step command ("YouTube par Arijit Singh search karo aur pehla video play karo")
            val multiStepIntent = com.jarvisai.assistant.service.IntentRouter.classifyCommand(
                "Jarvis, YouTube par Arijit Singh search karo aur pehla video play karo"
            )
            assertEquals(com.jarvisai.assistant.service.IntentCategory.AGENTIC_GOAL, multiStepIntent.category)
            assertEquals("AGENTIC_REACT_GOAL", multiStepIntent.actionName)

            val multiStepOutcome = phoneActionManager.detectAndExecuteCommand(
                "Jarvis, YouTube par Arijit Singh search karo aur pehla video play karo"
            )
            assertTrue(multiStepOutcome.isPhoneCommand)
            assertTrue(multiStepOutcome.executedSuccessfully)
            assertTrue(multiStepOutcome.spokenHinglishResponse.contains("YOUTUBE_SEARCH"))
            assertTrue(multiStepOutcome.spokenHinglishResponse.contains("CLICK_ORDINAL"))
        } finally {
            com.jarvisai.assistant.service.JarvisAccessibilityService.testOverrideRootNodeProvider = null
            com.jarvisai.assistant.service.JarvisAccessibilityService.testOverrideEnabledState = null
        }
    }

    @Test
    fun `test 13 AbuZar_Ansarii PrivateAgent 3D Hologram Air Gestures ADS_B Radar Sentry SOUL_md and Ollama Fusion`() = runBlocking {
        // 1. Dual 3D Hologram Engine & 3D Iron Man Suit Bust Projection
        assertTrue(com.jarvisai.assistant.service.Hologram3DEngine.mark85BustWireframeEdges.isNotEmpty())
        val (projectedOffset, depth) = com.jarvisai.assistant.service.Hologram3DEngine.project3DPoint(
            point = com.jarvisai.assistant.service.Vec3D(0.25f, -0.35f, 0.45f),
            center = androidx.compose.ui.geometry.Offset(100f, 100f),
            scaleRadius = 80f,
            pitchDeg = 12f,
            rollDeg = -8f,
            yawDeg = 15f
        )
        assertTrue(projectedOffset.x > 0f && projectedOffset.y > 0f)
        assertTrue(depth in 0.5f..1.8f)

        ProceduralHudModeState.setMode(ProceduralHudMode.IRON_MAN_BUST_3D)
        assertEquals(ProceduralHudMode.IRON_MAN_BUST_3D, ProceduralHudModeState.currentMode.value)

        // 2. Zero-Touch Optical Proximity Air Gestures (Single Wave, Double Wave, Palm Hover)
        com.jarvisai.assistant.service.ProximityAirGestureManager.setEnabled(context, true)
        assertTrue(com.jarvisai.assistant.service.ProximityAirGestureManager.state.value.isEnabled)
        val dblWaveSummary = com.jarvisai.assistant.service.ProximityAirGestureManager.triggerGestureManually(
            context,
            com.jarvisai.assistant.service.AirGestureType.DOUBLE_WAVE
        )
        assertTrue(dblWaveSummary.contains("Double Wave"))
        assertTrue(com.jarvisai.assistant.service.ProximityAirGestureManager.state.value.totalGesturesDetected >= 1)

        // 3. Armed Accelerometer Anti-Theft Sentry Mode
        val armMsg = com.jarvisai.assistant.watchman.JarvisWatchmanManager.setSentryModeArmed(context, true)
        assertTrue(com.jarvisai.assistant.watchman.JarvisWatchmanManager.sentryState.value.isArmed)
        assertTrue(armMsg.contains("Sentry Mode ARMED"))
        com.jarvisai.assistant.watchman.JarvisWatchmanManager.setSentryModeArmed(context, false)
        assertFalse(com.jarvisai.assistant.watchman.JarvisWatchmanManager.sentryState.value.isArmed)

        // 4. Real-Time ADS-B Overhead Aircraft Haversine & Bearing Math
        val distKm = com.jarvisai.assistant.service.AdsBAirspaceRadarManager.computeHaversineDistanceKm(
            28.6139, 77.2090, 28.5562, 77.1000
        )
        assertTrue(distKm in 10.0..15.0)
        val bearing = com.jarvisai.assistant.service.AdsBAirspaceRadarManager.computeBearingDegrees(
            28.6139, 77.2090, 28.5562, 77.1000
        )
        assertTrue(bearing in 0f..360f)

        // 5. Hermes Self-Evolving Consciousness Vault (SOUL.md, USER.md, AGENT.md & Macro Distiller)
        val vaultSnap = com.jarvisai.assistant.service.HermesConsciousnessVault.ensureInitialized(context)
        assertTrue(vaultSnap.soulMarkdown.contains("SOUL.md"))
        assertTrue(vaultSnap.userMarkdown.contains("USER.md"))
        assertTrue(vaultSnap.agentMarkdown.contains("AGENT.md"))

        com.jarvisai.assistant.service.HermesConsciousnessVault.updateMarkdownFile(
            context = context,
            targetDocument = "USER",
            content = "Operator prefers 1s live screen vision and Hinglish responses.",
            appendMode = true
        )
        val distilled = com.jarvisai.assistant.service.HermesConsciousnessVault.distillAndSaveMacroSkill(
            context = context,
            skillName = "Instagram Reel Auto Scroll",
            triggerPhrase = "insta reel macro",
            targetAppPackage = "com.instagram.android",
            steps = listOf("Launch Instagram", "Tap Reels Tab", "Swipe Up")
        )
        assertEquals("Instagram Reel Auto Scroll", distilled.skillName)
        val promptBlock = com.jarvisai.assistant.service.ConversationContextManager.buildSessionContextPromptBlock()
        assertTrue(promptBlock.contains("HERMES SELF-EVOLVING CONSCIOUSNESS VAULT"))

        // 6. Native Ollama Local LLM Bridge & LiveAgentToolExecutor Wiring
        val ollamaOutcome = liveExecutor.executeToolCall(
            functionName = ToolRegistry.TOOL_QUERY_OLLAMA_LOCAL_LLM,
            callId = "call_ollama_1",
            args = JSONObject().put("prompt", "calculate 12 + 8")
        )
        assertTrue(ollamaOutcome.executed)
        assertEquals("SUCCESS", ollamaOutcome.status)

        val sentryToolOutcome = liveExecutor.executeToolCall(
            functionName = ToolRegistry.TOOL_SET_SENTRY_MODE,
            callId = "call_sentry_1",
            args = JSONObject().put("armed", false)
        )
        assertTrue(sentryToolOutcome.executed)
    }
}


