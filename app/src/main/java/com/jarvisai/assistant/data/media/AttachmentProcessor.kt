package com.jarvisai.assistant.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.jarvisai.assistant.model.AttachmentKind
import com.jarvisai.assistant.model.ChatAttachment
import com.jarvisai.assistant.model.ResolvedAttachmentPart
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object AttachmentProcessor {

    private const val MAX_INLINE_BINARY_BYTES = 8L * 1024L * 1024L // 8 MB safe inline threshold
    private const val MAX_TEXT_CHARS = 48_000
    private const val MAX_IMAGE_DIMENSION = 1280

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "json", "xml", "yaml", "yml", "toml", "ini", "conf",
        "cfg", "properties", "csv", "tsv", "log", "sh", "bash", "zsh", "py", "js",
        "ts", "tsx", "jsx", "kt", "kts", "java", "c", "cpp", "h", "hpp", "rs", "go",
        "rb", "php", "html", "htm", "css", "scss", "sql", "gradle", "env", "gitignore"
    )

    fun inspectUri(context: Context, uri: Uri): ChatAttachment? {
        return try {
            val cr = context.contentResolver
            var name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "attachment"
            var size = -1L

            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }

            val detectedMime = cr.getType(uri)?.lowercase()?.trim()
                ?: guessMimeFromName(name)
            val kind = when {
                detectedMime.startsWith("image/") -> AttachmentKind.IMAGE
                detectedMime.startsWith("video/") -> AttachmentKind.VIDEO
                detectedMime == "application/pdf" || detectedMime.startsWith("text/") || isTextFileName(name) -> AttachmentKind.DOCUMENT
                else -> AttachmentKind.FILE
            }

            ChatAttachment(
                id = "att_${UUID.randomUUID()}",
                uriString = uri.toString(),
                displayName = name,
                mimeType = detectedMime,
                sizeBytes = size.coerceAtLeast(0L),
                kind = kind
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun prepareForPrompt(
        context: Context,
        attachments: List<ChatAttachment>
    ): List<ResolvedAttachmentPart> = withContext(Dispatchers.IO) {
        if (attachments.isEmpty()) return@withContext emptyList()

        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val attachDir = File(homeDir, "attachments").apply { if (!exists()) mkdirs() }

        attachments.map { att ->
            val uri = Uri.parse(att.uriString)
            val safeName = att.displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "file" }
            val destFile = File(attachDir, "${System.currentTimeMillis()}_$safeName")

            // Copy into Jarvis workspace so CLI tools (`run_command`, `read_file`) can access it too
            val copiedBytesSize = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (destFile.exists()) destFile.length() else att.sizeBytes
            }.getOrDefault(att.sizeBytes)

            val savedPath = if (destFile.exists()) destFile.absolutePath else null

            when (att.kind) {
                AttachmentKind.IMAGE -> {
                    val jpegBase64 = encodeScaledImageBase64(context, uri, destFile)
                    ResolvedAttachmentPart(
                        displayName = att.displayName,
                        mimeType = if (jpegBase64 != null) "image/jpeg" else att.mimeType,
                        kind = att.kind,
                        sizeBytes = copiedBytesSize,
                        base64Data = jpegBase64,
                        savedWorkspacePath = savedPath
                    )
                }

                AttachmentKind.VIDEO -> {
                    if (copiedBytesSize in 1..MAX_INLINE_BINARY_BYTES && destFile.exists()) {
                        val b64 = runCatching {
                            Base64.encodeToString(destFile.readBytes(), Base64.NO_WRAP)
                        }.getOrNull()
                        ResolvedAttachmentPart(
                            displayName = att.displayName,
                            mimeType = att.mimeType.ifBlank { "video/mp4" },
                            kind = att.kind,
                            sizeBytes = copiedBytesSize,
                            base64Data = b64,
                            savedWorkspacePath = savedPath
                        )
                    } else {
                        // Extract representative keyframes + metadata for large videos so API payload stays healthy
                        val (frames, summary) = extractVideoFramesAndSummary(context, uri, destFile, att.displayName, copiedBytesSize)
                        ResolvedAttachmentPart(
                            displayName = att.displayName,
                            mimeType = "image/jpeg",
                            kind = att.kind,
                            sizeBytes = copiedBytesSize,
                            extractedText = summary,
                            savedWorkspacePath = savedPath,
                            extraFrameBase64List = frames
                        )
                    }
                }

                AttachmentKind.DOCUMENT, AttachmentKind.FILE -> {
                    val isPlainText = att.mimeType.startsWith("text/") ||
                        att.mimeType in setOf("application/json", "application/xml", "application/javascript") ||
                        isTextFileName(att.displayName)

                    if (isPlainText && destFile.exists()) {
                        val content = runCatching {
                            destFile.readText(Charsets.UTF_8).take(MAX_TEXT_CHARS)
                        }.getOrNull()
                        ResolvedAttachmentPart(
                            displayName = att.displayName,
                            mimeType = att.mimeType,
                            kind = att.kind,
                            sizeBytes = copiedBytesSize,
                            extractedText = content,
                            savedWorkspacePath = savedPath
                        )
                    } else if ((att.mimeType == "application/pdf" || att.mimeType.startsWith("audio/")) &&
                        copiedBytesSize in 1..MAX_INLINE_BINARY_BYTES && destFile.exists()
                    ) {
                        val b64 = runCatching {
                            Base64.encodeToString(destFile.readBytes(), Base64.NO_WRAP)
                        }.getOrNull()
                        ResolvedAttachmentPart(
                            displayName = att.displayName,
                            mimeType = att.mimeType,
                            kind = att.kind,
                            sizeBytes = copiedBytesSize,
                            base64Data = b64,
                            savedWorkspacePath = savedPath
                        )
                    } else {
                        ResolvedAttachmentPart(
                            displayName = att.displayName,
                            mimeType = att.mimeType,
                            kind = att.kind,
                            sizeBytes = copiedBytesSize,
                            extractedText = "Binary/Document file '${att.displayName}' (${att.mimeType}, ${formatBytes(copiedBytesSize)}) saved on device at: $savedPath",
                            savedWorkspacePath = savedPath
                        )
                    }
                }
            }
        }
    }

    private fun encodeScaledImageBase64(context: Context, uri: Uri, localFile: File): String? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (localFile.exists()) {
                BitmapFactory.decodeFile(localFile.absolutePath, bounds)
            } else {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            }

            var sampleSize = 1
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            if (maxDim > MAX_IMAGE_DIMENSION) {
                while (maxDim / sampleSize > MAX_IMAGE_DIMENSION) {
                    sampleSize *= 2
                }
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = if (localFile.exists()) {
                BitmapFactory.decodeFile(localFile.absolutePath, decodeOpts)
            } else {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            } ?: return null

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 84, out)
            bitmap.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun extractVideoFramesAndSummary(
        context: Context,
        uri: Uri,
        localFile: File,
        displayName: String,
        sizeBytes: Long
    ): Pair<List<String>, String> {
        val frames = mutableListOf<String>()
        val retriever = MediaMetadataRetriever()
        var durationSec = 0L
        var width = ""
        var height = ""
        try {
            if (localFile.exists()) {
                retriever.setDataSource(localFile.absolutePath)
            } else {
                retriever.setDataSource(context, uri)
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            durationSec = durationMs / 1000L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).orEmpty()
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).orEmpty()

            val sampleTimestampsUs = if (durationMs > 1000L) {
                listOf(durationMs * 100L, durationMs * 500L, durationMs * 850L)
            } else {
                listOf(0L)
            }
            for (tsUs in sampleTimestampsUs) {
                val bmp = retriever.getFrameAtTime(tsUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                val scaled = scaleBitmapIfNeeded(bmp, 960)
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 78, baos)
                if (scaled !== bmp) scaled.recycle()
                bmp.recycle()
                frames.add(Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP))
            }
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }
        val resInfo = if (width.isNotBlank() && height.isNotBlank()) "${width}x${height}" else "unknown resolution"
        val summary = "Video attachment '$displayName' (${formatBytes(sizeBytes)}, ${durationSec}s, $resInfo). Extracted ${frames.size} representative keyframes for visual inspection; full video saved at ${localFile.absolutePath}."
        return frames to summary
    }

    private fun scaleBitmapIfNeeded(bmp: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bmp.width, bmp.height)
        if (longest <= maxEdge || longest <= 0) return bmp
        val ratio = maxEdge.toFloat() / longest.toFloat()
        val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, w, h, true)
    }

    private fun isTextFileName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in TEXT_EXTENSIONS
    }

    private fun guessMimeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "pdf" -> "application/pdf"
            "json" -> "application/json"
            "txt", "md", "log", "kt", "java", "py", "js", "ts", "sh", "xml", "html", "css" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes <= 0L -> "0 B"
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
