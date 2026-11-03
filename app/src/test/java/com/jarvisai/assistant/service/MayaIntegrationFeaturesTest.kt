package com.jarvisai.assistant.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jarvisai.assistant.audio.SileroVadManager
import com.jarvisai.assistant.telephony.JarvisTelephonyManager
import com.jarvisai.assistant.termux.JarvisTermuxBridge
import com.jarvisai.assistant.voicenote.JarvisVoiceNoteManager
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
class MayaIntegrationFeaturesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testToolRegistry_includesAllMayaSuperchargedTools() {
        val toolNames = ToolRegistry.ALL_TOOLS.map { it.name }

        assertTrue(toolNames.contains(ToolRegistry.TOOL_EXECUTE_TERMUX_COMMAND))
        assertTrue(toolNames.contains(ToolRegistry.TOOL_ANSWER_OR_END_CALL))
        assertTrue(toolNames.contains(ToolRegistry.TOOL_TRIGGER_WATCHMAN_ALERT))
        assertTrue(toolNames.contains(ToolRegistry.TOOL_REPLY_TO_VOICE_NOTE))
        assertTrue(toolNames.contains(ToolRegistry.TOOL_EXECUTE_BUILTIN_MACRO))
        assertTrue(toolNames.contains(ToolRegistry.TOOL_OPEN_IN_APP_BROWSER))

        val declarations = ToolRegistry.buildDeclarationsJsonArray()
        assertTrue(declarations.length() >= ToolRegistry.ALL_TOOLS.size)
    }

    @Test
    fun testSileroVadManager_classifiesSilenceVsAcousticEnergy() {
        val vad = SileroVadManager(energyThreshold = 200.0, speechConfidenceThreshold = 0.4f)

        // Flat silence buffer (all zeros)
        val silenceBuffer = ByteArray(1024)
        val silenceResult = vad.processFrame(silenceBuffer)
        assertFalse("Pure silence should not be classified as speech", silenceResult.isSpeech)
        assertEquals(0.0, silenceResult.energyRms, 0.001)

        // Simulated alternating speech waveform (500Hz sine approximation with energy)
        val speechBuffer = ByteArray(1024)
        for (i in 0 until 512) {
            val sample = (kotlin.math.sin(i.toDouble() * 0.2) * 1500).toInt().toShort()
            speechBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
            speechBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        val speechResult1 = vad.processFrame(speechBuffer)
        val speechResult2 = vad.processFrame(speechBuffer)
        assertTrue("Speech frame with sustained energy should be classified as active speech", speechResult2.isSpeech)
        assertTrue("Speech frame should have RMS above energyThreshold", speechResult2.energyRms > 200.0)
    }

    @Test
    fun testTermuxBridge_handlesUninstalledAppGracefully() {
        val bridge = JarvisTermuxBridge(context)
        val isInstalled = bridge.isTermuxInstalled()
        assertNotNull(isInstalled)

        // Calling hasRunCommandPermission should not throw an exception
        val hasPermission = bridge.hasRunCommandPermission()
        assertNotNull(hasPermission)
    }

    @Test
    fun testTelephonyManager_resolvesCallerSafely() {
        val telephonyManager = JarvisTelephonyManager(context)
        val resolvedUnknown = telephonyManager.resolveCallerName(null)
        assertEquals("Unknown Caller", resolvedUnknown)

        val resolvedBlank = telephonyManager.resolveCallerName("")
        assertEquals("Unknown Caller", resolvedBlank)

        val resolvedNumber = telephonyManager.resolveCallerName("+919876543210")
        assertNotNull(resolvedNumber)
    }

    @Test
    fun testVoiceNoteManager_recordsAndTracksContext() {
        JarvisVoiceNoteManager.recordVoiceNoteDetected(
            senderName = "Alex",
            chatName = "Project Alpha",
            detectedLanguage = "English",
            meaning = "Meeting scheduled at 4 PM"
        )

        val current = JarvisVoiceNoteManager.lastVoiceNote
        assertNotNull(current)
        assertEquals("Alex", current?.senderName)
        assertEquals("Project Alpha", current?.chatName)
        assertEquals("English", current?.detectedLanguage)
        assertEquals("Meeting scheduled at 4 PM", current?.transcribedMeaning)
    }

    @Test
    fun testJarvisDestinations_includesBrowserAndAgentHub() {
        val destRoutes = com.jarvisai.assistant.ui.JarvisDestination.entries.map { it.route }
        assertTrue("JarvisDestination should contain browser", destRoutes.contains("browser"))
        assertTrue("JarvisDestination should contain agent_hub", destRoutes.contains("agent_hub"))
    }
}
