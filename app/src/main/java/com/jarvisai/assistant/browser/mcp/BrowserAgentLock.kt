package com.jarvisai.assistant.browser.mcp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import com.jarvisai.assistant.browser.BrowserAutomationController
import com.jarvisai.assistant.browser.BrowserJsDialogRequest
import com.jarvisai.assistant.browser.BrowserJsDialogType
import com.jarvisai.assistant.browser.BrowserTab
import kotlin.math.roundToInt

/**
 * Full-screen touch interceptor and draggable floating pill (`[Lock Icon] AI controls this tab [Unlock]`)
 * displayed when an AI agent holds an active `browser_lock` lease.
 */
@Composable
fun BrowserAgentLockOverlay(
    tab: BrowserTab?,
    isLocked: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    var pillW by remember { mutableIntStateOf(0) }
    var pillH by remember { mutableIntStateOf(0) }

    if (!isLocked) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(tab?.id) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .testTag("browser_agent_lock_overlay")
    ) {
        val areaW = constraints.maxWidth.toFloat()
        val areaH = constraints.maxHeight.toFloat()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                .onSizeChanged { pillW = it.width; pillH = it.height }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val maxX = ((areaW - pillW) / 2f).coerceAtLeast(0f)
                        dx = (dx + drag.x).coerceIn(-maxX, maxX)
                        dy = (dy + drag.y).coerceIn(0f, (areaH - pillH - 20.dp.toPx()).coerceAtLeast(0f))
                    }
                }
                .clip(RoundedCornerShape(50))
                .background(Color(0xF2202124), RoundedCornerShape(50))
                .padding(start = 6.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
                .testTag("browser_agent_lock_pill")
        ) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "AI Lock Active",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "AI controls this tab",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(2.dp))
            val unlockContext = androidx.compose.ui.platform.LocalContext.current
            TextButton(
                onClick = {
                    val unlockedId = tab?.id.orEmpty()
                    BrowserAutomationController.unlockAllTabs()
                    BrowserMcp.onUserUnlocked(unlockedId, unlockContext)
                    onUnlock()
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(30.dp)
                    .testTag("browser_agent_unlock_button")
            ) {
                Text(
                    text = "Unlock",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFB74D)
                )
            }
        }
    }
}

/**
 * Intercepts native WebView JavaScript `alert`, `confirm`, `prompt`, and `beforeunload` dialogs
 * and renders them as a non-blocking Material 3 overlay card so neither the Android UI thread
 * nor the autonomous MCP agent is deadlocked by modal system dialogs.
 */
@Composable
fun BrowserJsDialogCard(
    tab: BrowserTab?,
    modifier: Modifier = Modifier
) {
    val dialog: BrowserJsDialogRequest = tab?.activeJsDialog ?: return
    var promptInput by remember(dialog.id) { mutableStateOf(dialog.defaultPromptValue.orEmpty()) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFFFFB300), Color(0xFF00E5FF))
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("browser_js_dialog_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF511172E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "JavaScript Dialog",
                    tint = Color(0xFFFFCA28),
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (dialog.type) {
                            BrowserJsDialogType.ALERT -> "Page Alert"
                            BrowserJsDialogType.CONFIRM -> "Page Confirm"
                            BrowserJsDialogType.PROMPT -> "Page Prompt"
                            BrowserJsDialogType.BEFORE_UNLOAD -> "Leave Page Confirmation"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (dialog.url.isNotBlank()) {
                        Text(
                            text = dialog.url,
                            color = Color(0xFF80D8FF),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Text(
                text = dialog.message.ifBlank { "(Empty dialog message)" },
                color = Color(0xFFE8EAF6),
                style = MaterialTheme.typography.bodyMedium
            )

            if (dialog.type == BrowserJsDialogType.PROMPT) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    singleLine = true,
                    label = { Text("Prompt value") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF3949AB)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("browser_js_dialog_prompt_input")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dialog.type != BrowserJsDialogType.ALERT) {
                    OutlinedButton(
                        onClick = {
                            dialog.onCancel()
                            tab.activeJsDialog = null
                        },
                        modifier = Modifier.testTag("browser_js_dialog_cancel_button")
                    ) {
                        Text("Cancel", color = Color(0xFFB0BEC5))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Button(
                    onClick = {
                        dialog.onConfirm(if (dialog.type == BrowserJsDialogType.PROMPT) promptInput else null)
                        tab.activeJsDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00B8D4),
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("browser_js_dialog_ok_button")
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
