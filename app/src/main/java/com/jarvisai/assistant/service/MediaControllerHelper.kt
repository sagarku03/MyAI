package com.jarvisai.assistant.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

data class MediaControlResult(
    val executed: Boolean,
    val action: String,
    val message: String,
    val spokenHinglishSummary: String
)

/**
 * Handles media playback commands (Play, Pause, Next, Previous, Volume)
 * using legitimate Android KeyEvent media actions and music intent integrations.
 */
class MediaControllerHelper(private val context: Context) {

    companion object {
        private const val TAG = "MediaControllerHelper"
        const val SPOTIFY_PKG = "com.spotify.music"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun executeMediaAction(action: String): MediaControlResult {
        val norm = action.trim().lowercase()
        val keyEventCode = when (norm) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause", "stop", "rok", "roko" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "play_pause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next", "skip", "aage" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev", "peeche" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }

        return try {
            val am = audioManager
            if (am != null) {
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyEventCode)
                am.dispatchMediaKeyEvent(downEvent)
                am.dispatchMediaKeyEvent(upEvent)
                val spoken = when (norm) {
                    "play" -> "Media playback start kar diya hai."
                    "pause", "stop", "rok", "roko" -> "Media pause kar diya hai."
                    "next", "skip" -> "Next track play kiya ja raha hai."
                    "previous", "prev" -> "Previous track play kiya ja raha hai."
                    else -> "Media control execute kar diya hai."
                }
                MediaControlResult(true, norm, "Dispatched media key $keyEventCode.", spoken)
            } else {
                MediaControlResult(false, norm, "AudioManager unavailable.", "Media service available nahi hai.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Media key dispatch failed: ${e.message}")
            MediaControlResult(false, norm, "Failed to dispatch media action: ${e.localizedMessage}", "Media action execute nahi ho paya.")
        }
    }

    fun openSpotifySearch(query: String): MediaControlResult {
        val clean = query.trim()
        return try {
            val spotifyUri = if (clean.isNotBlank()) {
                Uri.parse("spotify:search:${Uri.encode(clean)}")
            } else {
                Uri.parse("spotify:")
            }
            val intent = Intent(Intent.ACTION_VIEW, spotifyUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                MediaControlResult(true, "spotify_search", "Opened Spotify for '$clean'.", "Maine Spotify open kar ke '$clean' search kar diya hai.")
            } else {
                // Fallback to web player
                val webUrl = if (clean.isNotBlank()) "https://open.spotify.com/search/${Uri.encode(clean)}" else "https://open.spotify.com"
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                MediaControlResult(true, "spotify_search", "Opened Spotify web player for '$clean'.", "Spotify web player open kar diya hai.")
            }
        } catch (e: Exception) {
            MediaControlResult(false, "spotify_search", "Could not open Spotify: ${e.localizedMessage}", "Spotify open karne mein problem aayi.")
        }
    }
}
