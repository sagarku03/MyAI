package com.jarvisai.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.data.local.ChatMessageEntity
import com.jarvisai.assistant.model.TokenUsage
import org.json.JSONObject
import java.util.Locale

fun formatCompactTokens(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fk", count / 1_000.0)
        else -> count.toString()
    }
}

fun parseTokenUsageFromMessage(message: ChatMessageEntity): TokenUsage {
    val raw = message.tokenUsageJson?.trim().orEmpty()
    if (raw.startsWith("{")) {
        val parsed = runCatching {
            val obj = JSONObject(raw)
            val prompt = obj.optInt("promptTokens", 0)
            val output = obj.optInt("outputTokens", 0)
            val cached = obj.optInt("cachedTokens", 0)
            val total = obj.optInt("totalTokens", prompt + output)
            val duration = obj.optLong("durationMs", message.thoughtDurationMs ?: 0L)
            val model = obj.optString("model", "").ifBlank { message.modelUsed }
            TokenUsage(
                promptTokens = prompt,
                outputTokens = output,
                cachedTokens = cached,
                totalTokens = total,
                durationMs = duration,
                model = model
            )
        }.getOrNull()
        if (parsed != null && (parsed.promptTokens > 0 || parsed.outputTokens > 0 || parsed.totalTokens > 0)) {
            return parsed
        }
    }
    // Legacy messages without stored telemetry: only estimate output length rather than fabricating identical prompt=output counts
    val estOut = (message.content.length / 4).coerceAtLeast(1)
    return TokenUsage(
        promptTokens = 0,
        outputTokens = estOut,
        totalTokens = estOut,
        durationMs = message.thoughtDurationMs ?: 0L,
        model = message.modelUsed
    )
}

@Composable
fun TokenUsageTelemetryPill(
    usage: TokenUsage,
    onViewPayload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(0.6.dp, Color(0xFF1E293B)),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = onViewPayload != null) { onViewPayload?.invoke() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (usage.promptTokens > 0) {
                Text(
                    text = "📥 ${formatCompactTokens(usage.promptTokens)}",
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )
            }
            Text(
                text = "📤 ${formatCompactTokens(usage.outputTokens)}",
                fontSize = 11.5.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF10B981)
            )
            if (usage.cachedTokens > 0) {
                Text(
                    text = "⚡ ${formatCompactTokens(usage.cachedTokens)}",
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFF59E0B)
                )
            }
            if (usage.durationMs > 0) {
                Text(
                    text = "⏱️ ${String.format(Locale.US, "%.1fs", usage.durationMs / 1000.0)}",
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCBD5E1)
                )
            }
            usage.model?.takeIf { it.isNotBlank() }?.let { model ->
                Text(
                    text = model.replace("MODEL_PLACEHOLDER_", ""),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00E5FF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}
