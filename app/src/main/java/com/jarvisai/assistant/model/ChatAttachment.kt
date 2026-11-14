package com.jarvisai.assistant.model

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
    val extraFrameBase64List: List<String> = emptyList()
)
