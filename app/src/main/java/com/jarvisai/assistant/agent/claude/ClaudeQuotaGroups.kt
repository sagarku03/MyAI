package com.jarvisai.assistant.agent.claude

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Telemetry engine tracking Claude Code token usage (input, output, cache creation, cache read),
 * model-grouped quota buckets, and cumulative session USD costs.
 */
object ClaudeQuotaGroups {

    data class ModelQuotaBucket(
        val modelId: String,
        val displayName: String,
        val inputTokens: Long = 0L,
        val outputTokens: Long = 0L,
        val cacheCreationTokens: Long = 0L,
        val cacheReadTokens: Long = 0L,
        val cumulativeCostUsd: Double = 0.0,
        val turnCount: Int = 0
    ) {
        val totalTokens: Long
            get() = inputTokens + outputTokens + cacheCreationTokens + cacheReadTokens
    }

    private val _buckets = MutableStateFlow<Map<String, ModelQuotaBucket>>(emptyMap())
    val buckets: StateFlow<Map<String, ModelQuotaBucket>> = _buckets.asStateFlow()

    private val _rateLimits = MutableStateFlow(ClaudeRateLimits())
    val rateLimits: StateFlow<ClaudeRateLimits> = _rateLimits.asStateFlow()

    fun recordTurnUsage(modelId: String, usage: ClaudeUsageInfo) {
        val cleanModel = modelId.ifBlank { "claude-sonnet-4-5" }
        val estimatedCost = if (usage.totalCostUsd > 0.0) {
            usage.totalCostUsd
        } else {
            estimateCostUsd(cleanModel, usage)
        }

        _buckets.update { current ->
            val existing = current[cleanModel] ?: ModelQuotaBucket(
                modelId = cleanModel,
                displayName = formatModelDisplayName(cleanModel)
            )
            val updated = existing.copy(
                inputTokens = existing.inputTokens + usage.inputTokens,
                outputTokens = existing.outputTokens + usage.outputTokens,
                cacheCreationTokens = existing.cacheCreationTokens + usage.cacheCreationInputTokens,
                cacheReadTokens = existing.cacheReadTokens + usage.cacheReadInputTokens,
                cumulativeCostUsd = existing.cumulativeCostUsd + estimatedCost,
                turnCount = existing.turnCount + 1
            )
            current + (cleanModel to updated)
        }
    }

    fun updateRateLimits(limits: ClaudeRateLimits) {
        _rateLimits.value = limits
    }

    fun resetSessionTelemetry() {
        _buckets.value = emptyMap()
    }

    private fun estimateCostUsd(modelId: String, usage: ClaudeUsageInfo): Double {
        val isOpus = modelId.contains("opus", ignoreCase = true)
        val isHaiku = modelId.contains("haiku", ignoreCase = true)

        val inputPerMillion = when {
            isOpus -> 15.0
            isHaiku -> 0.80
            else -> 3.0 // Sonnet
        }
        val outputPerMillion = when {
            isOpus -> 75.0
            isHaiku -> 4.0
            else -> 15.0 // Sonnet
        }

        val inputCost = (usage.inputTokens / 1_000_000.0) * inputPerMillion
        val outputCost = (usage.outputTokens / 1_000_000.0) * outputPerMillion
        val cacheWriteCost = (usage.cacheCreationInputTokens / 1_000_000.0) * (inputPerMillion * 1.25)
        val cacheReadCost = (usage.cacheReadInputTokens / 1_000_000.0) * (inputPerMillion * 0.10)
        return inputCost + outputCost + cacheWriteCost + cacheReadCost
    }

    private fun formatModelDisplayName(modelId: String): String {
        return when {
            modelId.contains("opus", ignoreCase = true) -> "Claude Opus"
            modelId.contains("haiku", ignoreCase = true) -> "Claude Haiku"
            else -> "Claude Sonnet"
        }
    }
}
