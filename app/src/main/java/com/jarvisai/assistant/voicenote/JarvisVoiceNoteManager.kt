package com.jarvisai.assistant.voicenote

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.service.JarvisNotificationListenerService
import com.jarvisai.assistant.service.JarvisTtsManager

data class VoiceNoteContext(
    val senderName: String,
    val chatName: String,
    val detectedLanguage: String,
    val transcribedMeaning: String,
    val timestamp: Long = System.currentTimeMillis()
)

object JarvisVoiceNoteManager {

    private const val TAG = "JarvisVoiceNote"

    @Volatile
    var lastVoiceNote: VoiceNoteContext? = null

    fun recordVoiceNoteDetected(
        senderName: String,
        chatName: String,
        detectedLanguage: String,
        meaning: String
    ) {
        lastVoiceNote = VoiceNoteContext(
            senderName = senderName,
            chatName = chatName,
            detectedLanguage = detectedLanguage,
            transcribedMeaning = meaning
        )
        Log.d(TAG, "Voice note recorded for chat '$chatName' ($detectedLanguage): $meaning")
    }

    suspend fun replyToVoiceNote(
        context: Context,
        chatName: String?,
        replyText: String
    ): Boolean {
        val targetChat = chatName ?: lastVoiceNote?.chatName ?: "WhatsApp"
        Log.d(TAG, "Replying to voice note in chat '$targetChat': $replyText")

        val result = JarvisNotificationListenerService.sendBackgroundInlineReply(
            context = context,
            senderOrAppQuery = targetChat,
            replyText = replyText
        )

        val success = result.executed

        if (success) {
            JarvisTtsManager.speak(context, "$targetChat ko reply bhej diya: $replyText")
        } else {
            JarvisTtsManager.speak(context, "$targetChat ka notification reply action nahi mila.")
        }

        return success
    }
}
