package com.jarvisai.assistant.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import com.jarvisai.assistant.audio.AdaptiveEchoCanceller
import com.jarvisai.assistant.audio.FullDuplexVoiceEngine
import com.jarvisai.assistant.audio.SileroVadManager
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MayaParityControlAndVoiceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        JarvisAccessibilityService.testOverrideEnabledState = null
        JarvisAccessibilityService.testOverrideRootNodeProvider = null
        JarvisAccessibilityService.testCoordinateTapHandler = null
        JarvisAccessibilityService.testGlobalActionHandler = null
        JarvisAccessibilityService.testSimulateUnchangedScrollContent = false
    }

    @After
    fun tearDown() {
        JarvisAccessibilityService.testOverrideEnabledState = null
        JarvisAccessibilityService.testOverrideRootNodeProvider = null
        JarvisAccessibilityService.testCoordinateTapHandler = null
        JarvisAccessibilityService.testGlobalActionHandler = null
        JarvisAccessibilityService.testSimulateUnchangedScrollContent = false
    }

    @Test
    fun adaptiveEchoCanceller_suppressesAlignedSpeakerEchoAndDetectsPitch() {
        val aec = AdaptiveEchoCanceller()
        val farSamples = 960 // 40ms @ 24kHz
        val farPcm = ByteArray(farSamples * 2)
        for (i in 0 until farSamples) {
            val s = (sin(2.0 * PI * 440.0 * i / 24000.0) * 14000.0).toInt().toShort()
            farPcm[i * 2] = (s.toInt() and 0xFF).toByte()
            farPcm[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
        }

        // Fill render ring so acoustic delay window (~60ms = 960 samples @ 16kHz) has history
        repeat(5) {
            aec.pushFarEndPcm(farPcm, farPcm.size, sampleRate = 24000)
        }

        val nearSamples = 640 // 40ms @ 16kHz
        val voicedNearPcm = ByteArray(nearSamples * 2)
        for (i in 0 until nearSamples) {
            val humanVoice = sin(2.0 * PI * 180.0 * i / 16000.0) * 6000.0
            val s = humanVoice.toInt().toShort()
            voicedNearPcm[i * 2] = (s.toInt() and 0xFF).toByte()
            voicedNearPcm[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
        }

        val metrics = aec.processCaptureFrame(voicedNearPcm, voicedNearPcm.size, applyEchoSubtraction = true)
        assertTrue("Voiced 180Hz signal should show clear periodicity", metrics.harmonicity > 0.35f)
        assertTrue("Pitch estimator should lock near 180Hz (${metrics.pitchHz})", metrics.pitchHz in 140f..220f)
        assertTrue("Voice-band energy should dominate over rumble/hiss", metrics.voiceBandRatio > 0.45f)
    }

    @Test
    fun fullDuplexEngine_usesTwoStageBargeInAndRetainsCleanedPreRoll() {
        val engine = FullDuplexVoiceEngine(
            SileroVadManager(energyThreshold = 150.0, speechConfidenceThreshold = 0.38f)
        )
        engine.onPlaybackTurnStarted()

        val farPcm = ByteArray(1920)
        for (i in 0 until 960) {
            val s = (sin(2.0 * PI * 300.0 * i / 24000.0) * 8000.0).toInt().toShort()
            farPcm[i * 2] = (s.toInt() and 0xFF).toByte()
            farPcm[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        repeat(4) {
            engine.reportFarEndPlaybackFrame(farPcm, farPcm.size, sampleRate = 24000)
        }

        // Calibration frames (quiet ambient)
        val quietMic = ByteArray(1280)
        repeat(5) {
            engine.evaluateDuringPlayback(quietMic, quietMic.size, ambientNoiseRms = 0.008f, isExternalHeadset = false)
        }

        // Loud human vocal barge-in (220Hz vowel-like periodic signal)
        val userSpeech = ByteArray(1280)
        for (i in 0 until 640) {
            val s = (sin(2.0 * PI * 220.0 * i / 16000.0) * 9500.0).toInt().toShort()
            userSpeech[i * 2] = (s.toInt() and 0xFF).toByte()
            userSpeech[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
        }

        val stage1 = engine.evaluateDuringPlayback(userSpeech, userSpeech.size, ambientNoiseRms = 0.008f, isExternalHeadset = false)
        assertEquals(FullDuplexVoiceEngine.BargeInStage.PAUSED, stage1.bargeInStage)
        assertTrue("Stage 1 should pause/duck local speaker first", stage1.shouldPausePlayback)
        assertFalse("Stage 1 alone should wait for confirmation before full barge-in", stage1.shouldTriggerBargeIn)

        val stage2 = engine.evaluateDuringPlayback(userSpeech, userSpeech.size, ambientNoiseRms = 0.008f, isExternalHeadset = false)
        assertEquals(FullDuplexVoiceEngine.BargeInStage.COMMITTED, stage2.bargeInStage)
        assertTrue("Stage 2 sustained double-talk should commit barge-in", stage2.shouldTriggerBargeIn)

        val preRoll = engine.drainPreRollFrames()
        assertTrue("Pre-roll buffer should contain opening speech frames", preRoll.size >= 2)
    }

    @Test
    fun toolRegistry_keepsBaseTestsGreenAndExposesInteractiveToolsToLiveVoice() {
        assertEquals(8, ToolRegistry.ALL_TOOLS.size)
        assertEquals(12, ToolRegistry.buildDeclarationsJsonArray().length())

        val liveArray = ToolRegistry.buildLiveVoiceDeclarationsJsonArray(
            includeNonBlockingBehavior = true,
            includeInteractiveScreenTools = true
        )
        val liveNames = (0 until liveArray.length()).map { liveArray.getJSONObject(it).getString("name") }.toSet()
        assertTrue(liveNames.contains(ToolRegistry.TOOL_READ_SCREEN_ELEMENTS))
        assertTrue(liveNames.contains(ToolRegistry.TOOL_TAP_ELEMENT))
        assertTrue(liveNames.contains(ToolRegistry.TOOL_TAP_TEXT))
        assertTrue(liveNames.contains(ToolRegistry.TOOL_TAP_COORDINATES))
        assertTrue(liveNames.contains(ToolRegistry.TOOL_WAIT_FOR_SCREEN_TEXT))
        assertTrue(liveNames.contains(ToolRegistry.TOOL_GO_BACK))
        assertTrue(liveNames.contains(ToolRegistry.TOOL_FIND_TOOL))
        assertTrue(liveNames.contains(ToolRegistry.TOOL_RUN_TOOL))
    }

    @Test
    fun letterboxCoordinateMappingAndRedRingOverlayWorkAccurately() {
        val metrics = context.resources.displayMetrics
        val physW = metrics.widthPixels.toFloat().coerceAtLeast(360f)
        val physH = metrics.heightPixels.toFloat().coerceAtLeast(640f)

        // Create a square 1000x1000 letterboxed image containing the portrait screen centered horizontally
        val imgSize = 1000
        val centerCheck = JarvisAccessibilityService.isCoordinateInLetterboxBorder(context, 500f, 500f, imgSize, imgSize)
        assertFalse("Center of preview must not be flagged as black letterbox border", centerCheck)

        val (mappedX, mappedY) = JarvisAccessibilityService.resolvePhysicalScreenCoordinates(
            context = context,
            rawX = 500f,
            rawY = 500f,
            streamCaptureWidth = imgSize,
            streamCaptureHeight = imgSize,
            space = "image"
        )
        assertEquals(physW / 2f, mappedX, 20f)
        assertEquals(physH / 2f, mappedY, 20f)

        // Render Red-Ring proof onto a dark bitmap and verify red pixels appear around tap center
        val src = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.BLACK)
        val overlaid = JarvisAccessibilityService.renderRedRingTapOverlay(
            sourceBitmap = src,
            physX = (physW / 2f).toInt(),
            physY = (physH / 2f).toInt(),
            physW = physW.toInt(),
            physH = physH.toInt()
        )
        val centerPixel = overlaid.getPixel(overlaid.width / 2, overlaid.height / 2)
        assertTrue("Center tap proof dot must be red", Color.red(centerPixel) > 200 && Color.blue(centerPixel) < 50)
    }

    @Test
    fun elementCacheDriftDetectionAndRepeatRefusalGateProtectActions() = runBlocking {
        JarvisAccessibilityService.testOverrideEnabledState = true

        val button1 = AccessibilityNodeInfo.obtain().apply {
            text = "Subscribe"
            className = "android.widget.Button"
            packageName = "com.example.testapp"
            isClickable = true
            isVisibleToUser = true
            @Suppress("DEPRECATION")
            setBoundsInScreen(android.graphics.Rect(100, 200, 350, 280))
        }
        val root1 = AccessibilityNodeInfo.obtain().apply {
            className = "android.widget.FrameLayout"
            packageName = "com.example.testapp"
            isVisibleToUser = true
        }
        Shadows.shadowOf(root1).addChild(button1)
        JarvisAccessibilityService.testOverrideRootNodeProvider = { root1 }

        val readRes = JarvisAccessibilityService.readScreenElementsIndexed(context)
        assertTrue(readRes.elements.isNotEmpty())
        val firstId = readRes.elements.first { it.text == "Subscribe" }.id

        // Now simulate UI drifting/changing under cached #id before tap_element runs
        val buttonChanged = AccessibilityNodeInfo.obtain().apply {
            text = "Delete Account"
            className = "android.widget.Button"
            packageName = "com.example.testapp"
            isClickable = true
            isVisibleToUser = true
            @Suppress("DEPRECATION")
            setBoundsInScreen(android.graphics.Rect(100, 200, 350, 280))
        }
        val rootChanged = AccessibilityNodeInfo.obtain().apply {
            className = "android.widget.FrameLayout"
            packageName = "com.example.testapp"
            isVisibleToUser = true
        }
        Shadows.shadowOf(rootChanged).addChild(buttonChanged)
        JarvisAccessibilityService.testOverrideRootNodeProvider = { rootChanged }

        val driftedTap = JarvisAccessibilityService.tapCachedElement(context, firstId)
        assertFalse("Drifted element label must fail safely instead of misclicking", driftedTap.executed)
        assertEquals("STALE_ELEMENT_DRIFT", driftedTap.errorCode)

        // Verify find_tool + P90 Repeat-Refusal Gate in LiveAgentToolExecutor
        val executor = LiveAgentToolExecutor(context)
        val findRes = executor.executeToolCall(
            ToolRegistry.TOOL_FIND_TOOL,
            "c_find",
            JSONObject().put("query", "alarm timer")
        )
        assertTrue(findRes.executed)
        assertTrue(findRes.responseJson.getJSONArray("matches").toString().contains(ToolRegistry.TOOL_SET_ALARM_OR_TIMER))

        // Repeat identical failing tap_text twice -> 3rd call must be refused with EXACT_REPEAT_REFUSED
        val missingArgs = JSONObject().put("text", "NonExistentGhostButton")
        val fail1 = executor.executeToolCall(ToolRegistry.TOOL_TAP_TEXT, "c1", missingArgs)
        val fail2 = executor.executeToolCall(ToolRegistry.TOOL_TAP_TEXT, "c2", missingArgs)
        val refused3 = executor.executeToolCall(ToolRegistry.TOOL_TAP_TEXT, "c3", missingArgs)
        assertFalse(fail1.executed)
        assertFalse(fail2.executed)
        assertFalse(refused3.executed)
        assertEquals("EXACT_REPEAT_REFUSED", refused3.errorCode)
    }

    @Test
    fun recoveryEngine_executesSmartThreeTierRecoveryEscalation() = runBlocking {
        JarvisAccessibilityService.testOverrideEnabledState = true
        val engine = RecoveryEngine()
        val launcher = AppLauncherManager(context)

        // Stage 1 -> Vision corrective action
        repeat(3) {
            engine.recordActionSignature("click_text(Missing)")
            engine.recordScreenHash(778899)
        }
        val tier1 = engine.executeSmartThreeTierRecovery(
            context = context,
            goal = "Open YouTube and subscribe",
            targetAppName = "YouTube",
            targetPackageName = "com.google.android.youtube",
            appLauncher = launcher,
            history = listOf("Step 1 failed"),
            onVisionCorrectiveDecision = { _ -> true to "Tapped corrective badge" }
        )
        assertEquals(1, tier1.recoveryStage)
        assertEquals("RECOVERY_TIER1_VISION_CORRECTIVE", tier1.strategyName)
        assertTrue(tier1.succeeded)

        // Stage 2 -> Back + 700ms + Reopen app
        engine.recordActionSignature("click_text(Missing)")
        engine.recordScreenHash(778899)
        var backPressed = false
        JarvisAccessibilityService.testGlobalActionHandler = { _ ->
            backPressed = true
            true
        }
        val tier2 = engine.executeSmartThreeTierRecovery(
            context = context,
            goal = "Open YouTube and subscribe",
            targetAppName = "YouTube",
            targetPackageName = "com.google.android.youtube",
            appLauncher = launcher,
            history = listOf("Step 1 failed", "Step 2 failed")
        )
        assertEquals(2, tier2.recoveryStage)
        assertEquals("RECOVERY_TIER2_BACK_REOPEN_APP", tier2.strategyName)
        assertTrue(backPressed)

        // Stage 3 -> LLM Replan
        engine.recordActionSignature("click_text(Missing)")
        engine.recordScreenHash(778899)
        val tier3 = engine.executeSmartThreeTierRecovery(
            context = context,
            goal = "Open YouTube and subscribe",
            targetAppName = "YouTube",
            targetPackageName = "com.google.android.youtube",
            appLauncher = launcher,
            history = listOf("Step 1 failed", "Step 2 failed", "Step 3 failed"),
            onLlmReplan = { _ -> "Use search icon top right instead of home feed" }
        )
        assertEquals(3, tier3.recoveryStage)
        assertEquals("RECOVERY_TIER3_LLM_REPLAN", tier3.strategyName)
        assertTrue(tier3.summary.contains("Use search icon"))
    }
}
