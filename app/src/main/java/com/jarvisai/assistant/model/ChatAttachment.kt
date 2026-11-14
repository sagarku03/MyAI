package com.jarvisai.assistant.model

import org.json.JSONArray
import org.json.JSONObject

enum class AttachmentKind {
    IMAGE,
    VIDEO,
    DOCUMENT,
    FILE
}

data class ChatAttachment(
    val id: String,
    val uriString: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val kind: AttachmentKind
)

data class ResolvedAttachmentPart(
    val displayName: String,
    val mimeType: String,
    val kind: AttachmentKind,
    val sizeBytes: Long = 0L,
    val base64Data: String? = null,
    val extractedText: String? = null,
    val savedWorkspacePath: String? = null,
    val thumbnailPath: String? = null,
    val sourceUriString: String? = null,
    val extraFrameBase64List: List<String> = emptyList()
)

data class PersistedChatAttachment(
    val kind: AttachmentKind,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String?,
    val thumbnailPath: String?,
    val fallbackUri: String?
)

data class DecodedChatMessage(
    val attachments: List<PersistedChatAttachment>,
    val bodyText: String
)

object ChatAttachmentCodec {
    private const val START_TAG = "[[JARVIS_ATTACHMENTS]]"
    private const val END_TAG = "[[/JARVIS_ATTACHMENTS]]"

    fun encodeMessage(
        attachments: List<PersistedChatAttachment>,
        userPrompt: String
    ): String {
        val cleanPrompt = userPrompt.trim()
        if (attachments.isEmpty()) return cleanPrompt

        val arr = JSONArray()
        attachments.forEach { att ->
            arr.put(
                JSONObject().apply {
                    put("kind", att.kind.name)
                    put("name", att.name)
                    put("mime", att.mimeType)
                    put("size", att.sizeBytes)
                    if (!att.localPath.isNullOrBlank()) put("path", att.localPath)
                    if (!att.thumbnailPath.isNullOrBlank()) put("thumb", att.thumbnailPath)
                    if (!att.fallbackUri.isNullOrBlank()) put("uri", att.fallbackUri)
                }
            )
        }
        return buildString {
            append(START_TAG)
            append('\n')
            append(arr.toString())
            append('\n')
            append(END_TAG)
            if (cleanPrompt.isNotBlank()) {
                append("\n\n")
                append(cleanPrompt)
            }
        }
    }

    fun decodeMessage(rawContent: String): DecodedChatMessage {
        val trimmed = rawContent.trimStart()
        if (!trimmed.startsWith(START_TAG)) {
            return DecodedChatMessage(emptyList(), rawContent)
        }
        val endIdx = trimmed.indexOf(END_TAG)
        if (endIdx < 0) {
            return DecodedChatMessage(emptyList(), rawContent)
        }
        val jsonBlock = trimmed.substring(START_TAG.length, endIdx).trim()
        val body = trimmed.substring(endIdx + END_TAG.length).trimStart('\n', '\r', ' ')
        val list = mutableListOf<PersistedChatAttachment>()
        try {
            val arr = JSONArray(jsonBlock)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val kindName = obj.optString("kind", AttachmentKind.FILE.name)
                val kind = runCatching { AttachmentKind.valueOf(kindName) }.getOrDefault(AttachmentKind.FILE)
                list.add(
                    PersistedChatAttachment(
                        kind = kind,
                        name = obj.optString("name", "attachment"),
                        mimeType = obj.optString("mime", "application/octet-stream"),
                        sizeBytes = obj.optLong("size", 0L),
                        localPath = obj.optString("path").takeIf { it.isNotBlank() },
                        thumbnailPath = obj.optString("thumb").takeIf { it.isNotBlank() },
                        fallbackUri = obj.optString("uri").takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (_: Exception) {
            return DecodedChatMessage(emptyList(), rawContent)
        }
        return DecodedChatMessage(list, body)
    }

    fun stripForCopy(rawContent: String): String {
        val decoded = decodeMessage(rawContent)
        if (decoded.attachments.isEmpty()) return rawContent
        if (decoded.bodyText.isNotBlank()) return decoded.bodyText
        return decoded.attachments.joinToString(", ") { it.name }
    }

    fun toPromptReadableText(rawContent: String): String {
        val decoded = decodeMessage(rawContent)
        if (decoded.attachments.isEmpty()) return rawContent
        val header = decoded.attachments.joinToString(" | ") { att ->
            "[Attached ${att.kind.name}: ${att.name}]"
        }
        return if (decoded.bodyText.isNotBlank()) {
            "$header\n${decoded.bodyText}"
        } else {
            header
        }
    }
}
