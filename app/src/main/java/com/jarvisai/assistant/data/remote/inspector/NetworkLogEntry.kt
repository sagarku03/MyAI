package com.jarvisai.assistant.data.remote.inspector

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NetworkLogEntry(
    val id: String,
    val timestamp: Long,
    val url: String,
    val path: String,
    val method: String,
    val protocol: String = "HTTP/1.1",
    val serviceType: String = "Agentra API",
    val startTimeMs: Long,
    val durationMs: Long = -1L,
    val statusCode: Int = 0,
    val grpcStatus: String? = null,
    val isStreaming: Boolean = false,
    val requestHeaders: Map<String, String> = emptyMap(),
    val responseHeaders: Map<String, String> = emptyMap(),
    val error: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(startTimeMs))

    val isConnected: Boolean
        get() = durationMs < 0L && error == null

    val isSuccess: Boolean
        get() = error == null && (statusCode in 200..299 || (statusCode == 0 && isConnected)) &&
            (grpcStatus == null || grpcStatus == "0" || grpcStatus.startsWith("0 "))

    val displayName: String
        get() = path.trim('/').split('/').lastOrNull()?.ifBlank { path } ?: path
}
