package com.jarvisai.assistant.voice.agy

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Fetches and plays cached MP3 voice samples from `/voices/<name>_<1..3>.mp3` served by `agy --hub` (1.5+).
 */
object AgyVoicePreview {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _playing = MutableStateFlow<String?>(null)
    val playing: StateFlow<String?> = _playing.asStateFlow()

    private val _loading = MutableStateFlow<String?>(null)
    val loading: StateFlow<String?> = _loading.asStateFlow()

    private var player: MediaPlayer? = null
    private var job: Job? = null
    private val nextSample = mutableMapOf<String, Int>()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun play(context: Context, voice: String) {
        val app = context.applicationContext
        stop()
        val turn = nextSample.getOrPut(voice) { (0..2).random() }
        nextSample[voice] = turn + 1
        val n = turn % 3 + 1
        _loading.value = voice
        job = scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fetch(app, voice, n) } }
            if (_loading.value != voice) return@launch
            _loading.value = null
            val file = result.getOrElse {
                Toast.makeText(
                    app,
                    "Couldn't load $voice's sample: ${it.message ?: "unavailable"}",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val mp = MediaPlayer()
            runCatching {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mp.setDataSource(file.absolutePath)
                mp.setOnCompletionListener {
                    if (player === it) {
                        player = null
                        _playing.value = null
                    }
                    it.release()
                }
                mp.prepare()
                mp.start()
                player = mp
                _playing.value = voice
            }.onFailure {
                mp.release()
                file.delete()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _loading.value = null
        player?.let { p ->
            runCatching { p.stop() }
            p.release()
        }
        player = null
        _playing.value = null
    }

    private fun fetch(app: Context, voice: String, n: Int): File {
        val name = "${voice.lowercase()}_$n.mp3"
        val dir = File(app.cacheDir, "agy_voices").apply { mkdirs() }
        val file = File(dir, name)
        if (file.length() > 1024) return file
        val base = AgyVoiceHubResolver.currentHubBaseUrl(app)
        val url = "$base/voices/$name".toHttpUrlOrNull() ?: error("Invalid hub URL: $base")
        val req = AgyVoiceHubResolver.attachHubAuthHeaders(Request.Builder().url(url).get(), url, app).build()
        client.newCall(req).execute().use { r ->
            if (r.code == 404) error("this agy hub has no voice samples (agy 1.5+ required)")
            if (!r.isSuccessful) error("hub answered HTTP ${r.code}")
            val body = r.body ?: error("empty sample body")
            val tmp = File(dir, "$name.part")
            body.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(file)) error("could not save voice sample")
        }
        return file
    }
}
