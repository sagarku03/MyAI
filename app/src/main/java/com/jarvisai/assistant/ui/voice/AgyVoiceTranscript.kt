package com.jarvisai.assistant.ui.voice

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.HeadsetMic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.voice.agy.VoiceChatRecord
import java.util.Date

private val CallAccentBlue = Color(0xFF4285F4)

fun voiceCallLength(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val m = s / 60
    return if (m > 0) "${m}m ${(s % 60).toString().padStart(2, '0')}s" else "${s}s"
}

private fun clockTime(context: Context, ms: Long): String =
    android.text.format.DateFormat.getTimeFormat(context).format(Date(ms))

/**
 * In-chat wavy divider for a voice call: "Voice call · 2:11 PM · 3m 01s" (opens transcript sheet)
 * or "Voice call started · 2:11 PM" while active (opens live call screen).
 */
@Composable
fun VoiceCallMarker(
    startedAt: Long,
    durationMs: Long?,
    live: Boolean,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val time = clockTime(context, startedAt)
    val label = when {
        live -> "Voice call started · $time"
        durationMs != null -> "Voice call · $time · ${voiceCallLength(durationMs)}"
        else -> "Voice call · $time"
    }
    WaveDivider(
        label = label,
        icon = Icons.Rounded.HeadsetMic,
        tint = if (live) CallAccentBlue else null,
        onClick = onOpen
    )
}

/**
 * Bottom sheet displaying a saved voice call's full spoken transcript and agent handoff markers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgyVoiceTranscriptSheet(record: VoiceChatRecord, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val subtle = Color.White.copy(alpha = 0.58f)
    val lines = remember(record) { record.lines.filter { it.isHandoff || it.text.isNotBlank() } }
    val subtitle = listOfNotNull(
        record.startedAtMs?.let { clockTime(context, it) },
        record.durationMs?.let { voiceCallLength(it) }
    ).joinToString(" · ")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Color(0xFF14161C),
        contentColor = Color.White
    ) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.HeadsetMic, null, tint = CallAccentBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Voice transcript", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = subtle)
                }
                TextButton(onClick = {
                    val text = lines.joinToString("\n\n") { l ->
                        val t = l.timeMs?.let { "[${clockTime(context, it)}] " } ?: ""
                        when {
                            l.isHandoff -> "${t}── Sent to agent ──"
                            l.isUser -> "${t}You: ${l.text}"
                            else -> "${t}Voice: ${l.text}"
                        }
                    }
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Voice transcript", text))
                    Toast.makeText(context, "Transcript copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy", color = CallAccentBlue) }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(lines) { _, l ->
                    val time = l.timeMs?.let { clockTime(context, it) }
                    when {
                        l.isHandoff -> Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = subtle, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "Sent to agent${time?.let { " · $it" } ?: ""}",
                                fontSize = 11.5.sp,
                                color = subtle
                            )
                        }
                        l.isUser -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                            Text("You${time?.let { " · $it" } ?: ""}", fontSize = 10.5.sp, color = subtle)
                            Text(
                                l.text,
                                fontSize = 14.5.sp,
                                lineHeight = 20.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .widthIn(max = 300.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF1F2430))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        else -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                            Text(
                                "Voice${time?.let { " · $it" } ?: ""}",
                                fontSize = 10.5.sp,
                                color = CallAccentBlue.copy(alpha = 0.9f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                l.text,
                                fontSize = 14.5.sp,
                                lineHeight = 20.sp,
                                color = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.widthIn(max = 320.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
