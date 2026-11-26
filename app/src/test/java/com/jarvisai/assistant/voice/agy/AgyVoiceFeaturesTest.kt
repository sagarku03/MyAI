package com.jarvisai.assistant.voice.agy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import exa.language_server_pb.Model
import exa.remote_agent_proxy_pb.VoiceClientMessage
import exa.remote_agent_proxy_pb.VoiceServerMessage
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AgyVoiceFeaturesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AgyVoicePreferences.init(context)
    }

    @Test
    fun voiceTurn_parsesXmlHandoffAndStripsTranscriptProperly() {
        val raw = """
            <voice_instructions>Respond briefly and hand off file creation.</voice_instructions>
            <talker_agent_context>
            [12:01:00] talker: "Sure, what would you like to build?"
            [12:01:05] user: "Create a python script"
            [12:01:07] user: "for log parsing"
            </talker_agent_context>
        """.trimIndent()

        assertTrue(VoiceTurn.isVoiceTurn(raw))
        val parsed = VoiceTurn.parse(raw)
        assertNotNull(parsed)
        assertEquals("Create a python script for log parsing", parsed?.spoken)
        assertEquals("Create a python script for log parsing", VoiceTurn.displayText(raw))

        val plain = "Regular text prompt"
        assertFalse(VoiceTurn.isVoiceTurn(plain))
        assertNull(VoiceTurn.parse(plain))
        assertEquals(plain, VoiceTurn.displayText(plain))
    }

    @Test
    fun preferences_persistVoicesLanguagesSpeedsAndCallChats() {
        AgyVoicePreferences.setVoice("Arlo")
        assertEquals("Arlo", AgyVoicePreferences.voice.value)
        assertEquals("Fola", AgyVoicePreferences.voiceOf("UnknownFallback").name)

        AgyVoicePreferences.setLanguage("hi-IN")
        assertEquals("hi-IN", AgyVoicePreferences.language.value)
        assertEquals("हिन्दी", AgyVoicePreferences.languageLabel("hi-IN"))
        assertEquals("hi-IN,en;q=0.5", AgyVoicePreferences.acceptLanguage("hi-IN"))

        AgyVoicePreferences.setLanguage(AgyVoicePreferences.LANGUAGE_AUTO)
        assertEquals("Phone language", AgyVoicePreferences.languageLabel(AgyVoicePreferences.LANGUAGE_AUTO))
        assertTrue(AgyVoicePreferences.acceptLanguage(AgyVoicePreferences.LANGUAGE_AUTO).isNotBlank())

        AgyVoicePreferences.setSpeed(1.5)
        assertEquals(1.5, AgyVoicePreferences.speed.value, 0.001)

        AgyVoicePreferences.setPicked()
        assertTrue(AgyVoicePreferences.picked.value)

        AgyVoicePreferences.rememberCallChat("http://127.0.0.1:2235/", "cid-alpha")
        AgyVoicePreferences.rememberCallChat("http://127.0.0.1:2235", "cid-beta")
        assertEquals(listOf("cid-beta", "cid-alpha"), AgyVoicePreferences.callChats("http://127.0.0.1:2235"))
    }

    @Test
    fun agyVoiceChats_parsesJsonHistoryAndDuration() {
        val json = """
            {
              "chats": [
                {
                  "startedAt": "2026-11-26T10:00:00Z",
                  "stoppedAt": "2026-11-26T10:01:30Z",
                  "lines": [
                    { "role": "ROLE_USER", "text": "Open settings", "time": "2026-11-26T10:00:10Z" },
                    { "role": "ROLE_VOICE", "text": "Opening settings now.", "time": "2026-11-26T10:00:12Z" }
                  ]
                },
                {
                  "startedAt": "2026-11-26T09:00:00Z",
                  "stoppedAt": "2026-11-26T09:00:05Z",
                  "lines": []
                }
              ]
            }
        """.trimIndent()

        val parsed = AgyVoiceChats.parseChatsResponse(json)
        assertEquals(2, parsed.size)
        val first = parsed.first()
        assertTrue(first.hasTalk)
        assertEquals(90_000L, first.durationMs)
        assertTrue(first.lines[0].isUser)
        assertTrue(first.lines[1].isVoice)
        assertFalse(parsed[1].hasTalk)
    }

    @Test
    fun wireProto_serializesAndDeserializesVoiceAndModelEnums() {
        val pcmBytes = byteArrayOf(1, 2, 3, 4)

        val audioMsg = VoiceClientMessage(mic_audio = pcmBytes.toByteString())
        val audioDecoded = VoiceClientMessage.ADAPTER.decode(VoiceClientMessage.ADAPTER.encode(audioMsg))
        assertEquals(pcmBytes.toByteString(), audioDecoded.mic_audio)

        val settingsMsg = VoiceClientMessage(
            settings_update = VoiceClientMessage.SettingsUpdate(
                playback_speed = 1.25,
                plan_model = Model.MODEL_PLACEHOLDER_M319
            )
        )
        val settingsDecoded = VoiceClientMessage.ADAPTER.decode(VoiceClientMessage.ADAPTER.encode(settingsMsg))
        assertEquals(1.25, settingsDecoded.settings_update?.playback_speed ?: 0.0, 0.001)
        assertEquals(Model.MODEL_PLACEHOLDER_M319, settingsDecoded.settings_update?.plan_model)
        assertEquals(1319, Model.MODEL_PLACEHOLDER_M319.value)

        val statusMsg = VoiceServerMessage(
            status = VoiceServerMessage.SessionStatus(
                state = VoiceServerMessage.SessionStatus.State.LISTENING
            )
        )
        val statusDecoded = VoiceServerMessage.ADAPTER.decode(VoiceServerMessage.ADAPTER.encode(statusMsg))
        assertEquals(VoiceServerMessage.SessionStatus.State.LISTENING, statusDecoded.status?.state)

        val srvAudioMsg = VoiceServerMessage(voice_audio = pcmBytes.toByteString())
        val srvAudioDecoded = VoiceServerMessage.ADAPTER.decode(VoiceServerMessage.ADAPTER.encode(srvAudioMsg))
        assertEquals(pcmBytes.toByteString(), srvAudioDecoded.voice_audio)
    }
}
