package exa.remote_agent_proxy_pb

import java.io.Serializable

/**
 * Kotlin representation of `app/src/main/proto/Voice.proto` (`exa.remote_agent_proxy_pb`),
 * providing zero-overhead binary/JSON serialization and full type safety for
 * bidirectional voice agent communication.
 */
data class VoiceClientMessage(
    val mic_audio: ByteArray? = null,
    val settings_update: SettingsUpdate? = null,
    val user_transcript: UserTranscript? = null
) : Serializable {

    data class SettingsUpdate(
        val plan_model: String = "",
        val playback_speed: Double = 1.0
    ) : Serializable

    data class UserTranscript(
        val text: String = "",
        val is_final: Boolean = false
    ) : Serializable
}

data class VoiceServerMessage(
    val voice_audio: ByteArray? = null,
    val status: SessionStatus? = null,
    val event: VoiceEvent? = null,
    val voice_transcript: VoiceTranscript? = null,
    val user_speech: UserSpeech? = null,
    val clear_audio: ClearAudio? = null,
    val audio_timing: AudioTiming? = null
) : Serializable {

    data class SessionStatus(
        val state: State = State.STATE_UNSPECIFIED,
        val failure_reason: FailureReason = FailureReason.FAILURE_REASON_UNSPECIFIED,
        val message: String = "",
        val attempt: Int = 0,
        val max_attempts: Int = 0,
        val cascade_id: String = ""
    ) : Serializable {

        enum class State(val value: Int) {
            STATE_UNSPECIFIED(0),
            STARTING(1),
            LISTENING(2),
            RETRYING(3),
            STOPPING(4),
            FAILED(5);

            companion object {
                fun fromValue(value: Int): State = entries.firstOrNull { it.value == value } ?: STATE_UNSPECIFIED
            }
        }

        enum class FailureReason(val value: Int) {
            FAILURE_REASON_UNSPECIFIED(0),
            SERVER_UNAVAILABLE(1),
            THROTTLED(2),
            SETUP_STALLED(3),
            CONNECTION_FAILED(4),
            ACCESS_DENIED(5),
            TIMEOUT(6),
            OTHER(7),
            VOICE_STALLED(8),
            CONVERSATION_GONE(9);

            companion object {
                fun fromValue(value: Int): FailureReason = entries.firstOrNull { it.value == value } ?: FAILURE_REASON_UNSPECIFIED
            }
        }
    }

    data class VoiceTranscript(
        val text: String = "",
        val seq: Int = 0,
        val audio_ms: Int = 0
    ) : Serializable

    data class AudioTiming(
        val seq: Int = 0,
        val audio_ms: Int = 0,
        val playback_speed: Double = 1.0
    ) : Serializable

    data class UserSpeech(
        val text: String = "",
        val seq: Int = 0,
        val interim: Boolean = false
    ) : Serializable

    class ClearAudio : Serializable
}

data class VoiceEvent(
    val event_name: String = "",
    val attributes: Map<String, String> = emptyMap()
) : Serializable
