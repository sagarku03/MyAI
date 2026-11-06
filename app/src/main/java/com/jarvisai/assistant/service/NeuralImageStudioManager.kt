package com.jarvisai.assistant.service

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin

data class GeneratedNeuralArtwork(
    val id: String,
    val prompt: String,
    val aspectRatio: String,
    val bitmap: Bitmap,
    val localFilePath: String,
    val usedCloudImagen: Boolean,
    val createdAtMs: Long = System.currentTimeMillis()
)

/**
 * Feature 8: In-App Unlimited AI Neural Image Generator Studio.
 * Inspired by AbuZar-Ansarii/PocketStrike `/image <prompt>` Engine:
 * - Supports custom aspect ratios (1:1, 16:9, 9:16, 4:3)
 * - Calls Gemini/Imagen image generation API when API key is configured, and includes a
 *   high-resolution Procedural Cyberpunk Neural Art synthesizer fallback so image generation
 *   always succeeds 100% of the time (even offline!).
 * - Supports 1-tap "Apply as Phone Wallpaper" and "Save to Gallery".
 */
object NeuralImageStudioManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    private val _generatedGallery = MutableStateFlow<List<GeneratedNeuralArtwork>>(emptyList())
    val generatedGallery: StateFlow<List<GeneratedNeuralArtwork>> = _generatedGallery.asStateFlow()

    suspend fun generateNeuralImage(
        context: Context,
        prompt: String,
        aspectRatio: String = "9:16",
        apiKey: String = ""
    ): GeneratedNeuralArtwork = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim().ifBlank { "Futuristic JARVIS Cyberpunk Holographic Citadel" }
        val (targetW, targetH) = when (aspectRatio.trim()) {
            "16:9" -> 1024 to 576
            "9:16" -> 576 to 1024
            "4:3" -> 800 to 600
            else -> 768 to 768
        }

        var cloudBitmap: Bitmap? = null
        if (apiKey.isNotBlank()) {
            cloudBitmap = tryCloudGeminiImageGeneration(cleanPrompt, apiKey)
        }

        val finalBitmap = cloudBitmap ?: synthesizeProceduralCyberpunkArt(cleanPrompt, targetW, targetH)
        val file = File(context.filesDir, "jarvis_neural_art_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }

        val art = GeneratedNeuralArtwork(
            id = "art_${System.currentTimeMillis()}",
            prompt = cleanPrompt,
            aspectRatio = aspectRatio,
            bitmap = finalBitmap,
            localFilePath = file.absolutePath,
            usedCloudImagen = cloudBitmap != null
        )
        _generatedGallery.value = (listOf(art) + _generatedGallery.value).take(12)
        art
    }

    private fun tryCloudGeminiImageGeneration(prompt: String, apiKey: String): Bitmap? {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp-image-generation:generateContent?key=$apiKey"
            val payload = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", "Generate a high-resolution artwork: $prompt"))
                        )
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
                )
            }
            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val candidates = json.optJSONArray("candidates") ?: return null
                for (c in 0 until candidates.length()) {
                    val parts = candidates.getJSONObject(c)
                        .optJSONObject("content")
                        ?.optJSONArray("parts") ?: continue
                    for (p in 0 until parts.length()) {
                        val inlineData = parts.getJSONObject(p).optJSONObject("inlineData") ?: continue
                        val b64 = inlineData.optString("data", "")
                        if (b64.isNotBlank()) {
                            val bytes = Base64.decode(b64, Base64.DEFAULT)
                            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Synthesizes a vibrant, multi-layered sci-fi cyberpunk/synthwave artwork directly on Android Canvas
     * seeded by the prompt string so it works 100% offline or when cloud image quota is unavailable.
     */
    private fun synthesizeProceduralCyberpunkArt(prompt: String, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val hash = prompt.hashCode()

        val bgColors = intArrayOf(
            Color.rgb(10, 12, 28),
            Color.rgb(36, 16, 68),
            Color.rgb(14, 34, 64),
            Color.rgb(8, 10, 22)
        )
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                bgColors, null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Glowing Nebula Sun / Core
        val sunCx = width * 0.5f
        val sunCy = height * 0.42f
        val sunRadius = minOf(width, height) * 0.28f
        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                sunCx, sunCy, sunRadius * 1.4f,
                intArrayOf(
                    Color.argb(240, 236, 72, 153),
                    Color.argb(180, 139, 92, 246),
                    Color.argb(90, 56, 189, 248),
                    Color.TRANSPARENT
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(sunCx, sunCy, sunRadius * 1.4f, sunPaint)

        // Concentric Cyber Rings
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.argb(190, 56, 189, 248)
        }
        for (r in 1..4) {
            canvas.drawCircle(sunCx, sunCy, sunRadius * (0.35f + r * 0.22f), ringPaint)
        }

        // Perspective Synthwave Cyber Grid Horizon
        val horizonY = height * 0.65f
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(150, 139, 92, 246)
        }
        for (i in -10..10) {
            val startX = sunCx + i * (width * 0.04f)
            val endX = sunCx + i * (width * 0.22f)
            canvas.drawLine(startX, horizonY, endX, height.toFloat(), gridPaint)
        }
        for (row in 0..8) {
            val y = horizonY + (height - horizonY) * (row / 8f) * (row / 8f)
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }

        // Starfield & Neural Nodes
        val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        for (n in 0 until 36) {
            val angle = (n * 0.45f) + (hash % 100) * 0.05f
            val px = ((sin(angle.toDouble()) * 0.5 + 0.5) * width).toFloat()
            val py = ((cos(angle.toDouble() * 1.3) * 0.5 + 0.5) * horizonY).toFloat()
            canvas.drawCircle(px, py, (n % 3 + 1.5f), nodePaint)
        }

        // Caption Watermark
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 220, 245, 255)
            textSize = (width * 0.028f).coerceIn(18f, 32f)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "JARVIS NEURAL STUDIO // ${prompt.take(36).uppercase()}",
            width * 0.5f,
            height - 28f,
            textPaint
        )
        return bmp
    }

    suspend fun setArtworkAsDeviceWallpaper(context: Context, artwork: GeneratedNeuralArtwork): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val wm = WallpaperManager.getInstance(context)
                wm.setBitmap(artwork.bitmap)
                true
            } catch (_: Exception) {
                false
            }
        }

    suspend fun saveArtworkToGallery(context: Context, artwork: GeneratedNeuralArtwork): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val filename = "JARVIS_AI_${System.currentTimeMillis()}.png"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/JARVIS")
                    }
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
                resolver.openOutputStream(uri)?.use { out ->
                    artwork.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                true
            } catch (_: Exception) {
                false
            }
        }
}
