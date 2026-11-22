package com.jarvisai.assistant.data.remote.inspector

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NetworkInspectorManager {
    private const val MAX_LOG_CAPACITY = 1000

    @Volatile
    var isEnabled: Boolean = false

    @Volatile
    var isFloatingBubbleEnabled: Boolean = false

    private val lock = Any()
    private val buffer = ArrayDeque<NetworkLogEntry>(MAX_LOG_CAPACITY)

    private val _logs = MutableStateFlow<List<NetworkLogEntry>>(emptyList())
    val logs: StateFlow<List<NetworkLogEntry>> = _logs.asStateFlow()

    private val _activeCallsCount = MutableStateFlow(0)
    val activeCallsCount: StateFlow<Int> = _activeCallsCount.asStateFlow()

    private val _enabledFlow = MutableStateFlow(false)
    val enabledFlow: StateFlow<Boolean> = _enabledFlow.asStateFlow()

    fun setCaptureEnabled(enabled: Boolean) {
        isEnabled = enabled
        _enabledFlow.value = enabled
    }

    fun recordStart(entry: NetworkLogEntry) {
        if (!isEnabled) return
        synchronized(lock) {
            if (buffer.size >= MAX_LOG_CAPACITY) {
                buffer.removeLast()
            }
            buffer.addFirst(entry)
            _logs.value = buffer.toList()
            recomputeCounts()
        }
    }

    fun recordComplete(
        id: String,
        statusCode: Int,
        grpcStatus: String? = null,
        responseHeaders: Map<String, String> = emptyMap(),
        durationMs: Long
    ) {
        if (!isEnabled) return
        synchronized(lock) {
            val idx = buffer.indexOfFirst { it.id == id }
            if (idx != -1) {
                val existing = buffer[idx]
                buffer[idx] = existing.copy(
                    statusCode = statusCode,
                    grpcStatus = grpcStatus,
                    responseHeaders = responseHeaders.ifEmpty { existing.responseHeaders },
                    durationMs = durationMs
                )
                _logs.value = buffer.toList()
                recomputeCounts()
            }
        }
    }

    fun recordError(id: String, errorMsg: String, durationMs: Long) {
        if (!isEnabled) return
        synchronized(lock) {
            val idx = buffer.indexOfFirst { it.id == id }
            if (idx != -1) {
                val existing = buffer[idx]
                buffer[idx] = existing.copy(
                    durationMs = durationMs,
                    error = errorMsg
                )
                _logs.value = buffer.toList()
                recomputeCounts()
            }
        }
    }

    fun clearLogs() {
        synchronized(lock) {
            val activeEntries = buffer.filter { it.durationMs < 0 && it.error == null }
            buffer.clear()
            for (entry in activeEntries.reversed()) {
                buffer.addFirst(entry)
            }
            _logs.value = buffer.toList()
            recomputeCounts()
        }
    }

    private fun recomputeCounts() {
        _activeCallsCount.value = buffer.count { it.durationMs < 0 && it.error == null }
    }
}
