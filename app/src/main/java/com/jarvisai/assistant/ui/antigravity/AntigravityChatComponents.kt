package com.jarvisai.assistant.ui.antigravity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.model.PersistedChatAttachment
import com.jarvisai.assistant.model.TokenUsage
import com.jarvisai.assistant.ui.screens.RenderMessageContentWithCodeBlocks

private val MENTION_TOKEN_REGEX = Regex("""@(file|folder|conversation|browser)(?::([^\s,;]+))?""", RegexOption.IGNORE_CASE)

/**
 * Authentic Original Google Antigravity User Prompt Card:
 * Full-width neutral charcoal card (#252525, 10dp radius, 1dp #333333 hairline)
 * rather than a right-aligned messaging bubble, with inline @mention IDE chips.
 */
@Composable
fun AntigravityUserPromptCard(
    bodyText: String,
    attachments: List<PersistedChatAttachment>,
    onCopyCode: (String) -> Unit,
    attachmentsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val mentionTokens = remember(bodyText) {
        MENTION_TOKEN_REGEX.findAll(bodyText)
            .map { match ->
                val kind = match.groupValues[1].lowercase()
                val value = match.groupValues[2].ifBlank { kind }
                kind to value
            }
            .distinct()
            .toList()
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AntigravityPalette.UserPromptBg,
        border = BorderStroke(1.dp, AntigravityPalette.BorderHairline),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp)
            .testTag("antigravity_user_prompt_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 10.dp)
        ) {
            if (mentionTokens.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    mentionTokens.forEach { (kind, value) ->
                        val icon = when (kind) {
                            "file" -> Icons.Default.Description
                            "folder" -> Icons.Default.Folder
                            "browser" -> Icons.Default.Public
                            else -> Icons.Default.AlternateEmail
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AntigravityPalette.SurfaceElevated,
                            border = BorderStroke(0.75.dp, AntigravityPalette.BorderAccent)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = AntigravityPalette.ElectricBlue,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = value.substringAfterLast('/'),
                                    color = AntigravityPalette.TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (attachments.isNotEmpty()) {
                attachmentsContent()
                if (bodyText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (bodyText.isNotBlank()) {
                SelectionContainer {
                    RenderMessageContentWithCodeBlocks(
                        content = bodyText,
                        textColor = AntigravityPalette.TextPrimary,
                        fillMaxWidth = true,
                        onCopy = onCopyCode
                    )
                }
            }
        }
    }
}

/**
 * Authentic Original Google Antigravity "Thought for Xs ⌄" Minimal Disclosure.
 * Calm muted gray text + clean 2dp vertical left rule when expanded (no neon brain box).
 */
@Composable
fun AntigravityThoughtDisclosure(
    thoughtText: String,
    durationMs: Long? = null,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "ag_thought_chevron"
    )

    val pulseAlpha = if (isStreaming) {
        val transition = rememberInfiniteTransition(label = "ag_thought_pulse")
        val alpha by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
        alpha
    } else {
        1f
    }

    val headerLabel = when {
        isStreaming -> "Thinking..."
        durationMs != null && durationMs >= 1000L -> "Thought for ${durationMs / 1000}s"
        durationMs != null && durationMs > 0L -> "Thought for <1s"
        else -> "Thought process"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag("antigravity_thought_disclosure")
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 2.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headerLabel,
                color = AntigravityPalette.TextSecondary.copy(alpha = pulseAlpha),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse thought" else "Expand thought",
                tint = AntigravityPalette.TextMuted,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(rotation)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 2.dp, bottom = 6.dp)
                    .drawBehind {
                        drawLine(
                            color = AntigravityPalette.BorderAccent,
                            start = Offset(0f, 4f),
                            end = Offset(0f, size.height - 4f),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    .padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
            ) {
                Text(
                    text = thoughtText.trim(),
                    color = AntigravityPalette.TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * Calm Authentic Original Google Antigravity Live Activity Status Row.
 */
@Composable
fun AntigravityLiveActivityRow(
    headline: String,
    isLoading: Boolean,
    stepCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    color = AntigravityPalette.TextSecondary,
                    strokeWidth = 1.6.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = AntigravityPalette.VerifiedGreen,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = headline,
                color = AntigravityPalette.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (stepCount > 0) {
            Text(
                text = "$stepCount step${if (stepCount == 1) "" else "s"}",
                color = AntigravityPalette.TextMuted,
                fontSize = 11.5.sp
            )
        }
    }
}

/**
 * Authentic Original Google Antigravity Assistant Turn Action Footer:
 * Subtle muted icons [Copy] [Good] [Bad] [Rewind] on left + clean muted model/latency metadata on right.
 */
@Composable
fun AntigravityAssistantTurnFooter(
    usage: TokenUsage,
    copiedFeedback: Boolean,
    showRewind: Boolean,
    onCopy: () -> Unit,
    onRewind: () -> Unit,
    onInspectUsage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var feedbackVote by remember { mutableStateOf<Int?>(null) } // 1 = up, -1 = down

    val metaText = remember(usage) {
        buildList {
            val modelShort = usage.model
                ?.removePrefix("agy:")
                ?.removePrefix("models/")
                ?.takeIf { it.isNotBlank() }
            if (modelShort != null) add(modelShort)
            if (usage.totalTokens > 0) {
                val tok = if (usage.totalTokens >= 1000) {
                    String.format(java.util.Locale.US, "%.1fk tok", usage.totalTokens / 1000f)
                } else {
                    "${usage.totalTokens} tok"
                }
                add(tok)
            }
            if (usage.durationMs > 0L) {
                add(String.format(java.util.Locale.US, "%.1fs", usage.durationMs / 1000f))
            }
        }.joinToString(" · ")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onCopy)
                    .testTag("ag_turn_copy_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (copiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy response",
                    tint = if (copiedFeedback) AntigravityPalette.VerifiedGreen else AntigravityPalette.TextMuted,
                    modifier = Modifier.size(15.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { feedbackVote = if (feedbackVote == 1) null else 1 }
                    .testTag("ag_turn_thumb_up_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ThumbUp,
                    contentDescription = "Good response",
                    tint = if (feedbackVote == 1) AntigravityPalette.ElectricBlue else AntigravityPalette.TextMuted,
                    modifier = Modifier.size(15.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { feedbackVote = if (feedbackVote == -1) null else -1 }
                    .testTag("ag_turn_thumb_down_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ThumbDown,
                    contentDescription = "Bad response",
                    tint = if (feedbackVote == -1) AntigravityPalette.DangerRed else AntigravityPalette.TextMuted,
                    modifier = Modifier.size(15.dp)
                )
            }

            if (showRewind) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onRewind)
                        .testTag("ag_turn_rewind_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "Rewind this turn",
                        tint = AntigravityPalette.TextMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        if (metaText.isNotBlank()) {
            Text(
                text = metaText,
                color = AntigravityPalette.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onInspectUsage)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("ag_turn_usage_meta")
            )
        }
    }
}
