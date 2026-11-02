package com.jarvisai.assistant.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ParsedDocument(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val textContent: String,
    val isImage: Boolean = false,
    val imageBase64: String? = null
)

/**
 * Robust, memory-safe document & image parser for JARVIS AI Document & Vision Assistants.
 * Handles TXT, MD, CSV, JSON, LOG, XML, HTML, and raster images safely without OOM.
 */
class DocumentProcessor(private val context: Context) {

    companion object {
        private const val MAX_TEXT_CHARS = 100_000 // ~25,000 tokens safe limit
        private const val MAX_IMAGE_DIMEN = 1024
    }

    suspend fun parseUri(uri: Uri): Result<ParsedDocument> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val meta = queryMetadata(uri)
            val fileName = meta.first
            val fileSize = meta.second

            val isImg = mimeType.startsWith("image/") ||
                fileName.endsWith(".jpg", ignoreCase = true) ||
                fileName.endsWith(".jpeg", ignoreCase = true) ||
                fileName.endsWith(".png", ignoreCase = true) ||
                fileName.endsWith(".webp", ignoreCase = true)

            if (isImg) {
                val base64 = encodeImageToBase64(uri)
                if (base64 != null) {
                    return@withContext Result.success(
                        ParsedDocument(
                            fileName = fileName,
                            mimeType = if (mimeType.startsWith("image/")) mimeType else "image/jpeg",
                            sizeBytes = fileSize,
                            textContent = "Image file: $fileName (${fileSize / 1024} KB)",
                            isImage = true,
                            imageBase64 = base64
                        )
                    )
                }
            }

            // Text-based files
            val text = readTextSafely(uri)
            Result.success(
                ParsedDocument(
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = fileSize,
                    textContent = text,
                    isImage = false,
                    imageBase64 = null
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryMetadata(uri: Uri): Pair<String, Long> {
        var name = "document.txt"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}
        return Pair(name, size)
    }

    private fun readTextSafely(uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(8192)
            val output = StringBuilder()
            var bytesRead: Int
            var totalRead = 0

            while (stream.read(buffer).also { bytesRead = it } != -1) {
                output.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
                totalRead += bytesRead
                if (output.length > MAX_TEXT_CHARS) {
                    output.append("\n\n... [Content truncated at 100,000 characters for optimal processing] ...")
                    break
                }
            }
            output.toString()
        } ?: "Unable to read document contents."
    }

    fun encodeImageToBase64(uri: Uri): String? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            var scale = 1
            while (options.outWidth / scale > MAX_IMAGE_DIMEN || options.outHeight / scale > MAX_IMAGE_DIMEN) {
                scale *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
            }

            val bitmap: Bitmap? = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }

            if (bitmap == null) return null

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val byteArray = baos.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }
}
