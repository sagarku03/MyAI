package com.jarvisai.assistant.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvisTheme
import com.jarvisai.assistant.data.local.ChatMessageEntity
import com.jarvisai.assistant.data.local.ChatSessionEntity
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.ChoiceQuestionnaire
import com.jarvisai.assistant.model.ChoiceResponseItem
import com.jarvisai.assistant.model.MemoryCategory
import com.jarvisai.assistant.model.QuestionnaireOption
import com.jarvisai.assistant.model.QuestionnaireQuestion
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.TokenUsage
import com.jarvisai.assistant.model.ToolCall
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.ui.components.AgentToolCallCard
import com.jarvisai.assistant.ui.components.ChoiceQuestionnaireCard
import com.jarvisai.assistant.ui.components.CodeBlock
import com.jarvisai.assistant.ui.components.ContextSummaryCheckpointBanner
import com.jarvisai.assistant.ui.components.FileLinkRow
import com.jarvisai.assistant.ui.components.JarvisAnimatedIndicator
import com.jarvisai.assistant.ui.components.JarvisModernDialog
import com.jarvisai.assistant.ui.components.JarvisPremiumTopBar
import com.jarvisai.assistant.ui.components.MermaidDiagramView
import com.jarvisai.assistant.ui.components.RawPayloadDialog
import com.jarvisai.assistant.ui.components.ThinkingAccordion
import com.jarvisai.assistant.ui.components.TokenUsageDetailsSheet
import com.jarvisai.assistant.ui.components.TokenUsageTelemetryPill
import com.jarvisai.assistant.ui.components.UnifiedDiffViewer
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TextChatScreen(
    settings: UserSettings,
    status: AssistantStatus,
    hasApiKey: Boolean,
    sessions: List<ChatSessionEntity>,
    currentSessionId: Long?,
    messages: List<ChatMessageEntity>,
    streamingPartialResponse: String?,
    isLoading: Boolean,
    chatError: String?,
    lastFailedPrompt: String?,
    currentTurnThought: String? = null,
    currentTurnToolCalls: List<ToolCall> = emptyList(),
    memorySavedNotification: String? = null,
    onDismissMemoryNotification: () -> Unit = {},
    onBack: () -> Unit,
    onSelectLanguage: (SupportedLanguage) -> Unit,
    onSendMessage: (String) -> Unit,
    onRetryFailed: () -> Unit,
    onNewChat: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onClearAllHistory: () -> Unit,
    onSaveApprovedMemory: (title: String, content: String, category: MemoryCategory) -> Unit,
    onNavigateToApiSettings: () -> Unit,
    onSubmitQuestionnaire: ((ToolCall, List<ChoiceResponseItem>) -> Unit)? = null,
    onSkipQuestionnaire: ((ToolCall) -> Unit)? = null
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeColors = JarvisTheme.colors
    var inputText by rememberSaveable { mutableStateOf("") }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    var showClearAllConfirm by rememberSaveable { mutableStateOf(false) }
    var memoryDraftContent by remember { mutableStateOf<String?>(null) }
    var inspectingRawPayload by remember { mutableStateOf<String?>(null) }
    var inspectingTokenUsage by remember { mutableStateOf<Pair<TokenUsage, String>?>(null) }

    val handleQuestionnaireSubmit: (ToolCall, List<ChoiceResponseItem>) -> Unit = { toolCall, responses ->
        if (onSubmitQuestionnaire != null) {
            onSubmitQuestionnaire(toolCall, responses)
        } else {
            scope.launch(Dispatchers.IO) {
                com.jarvisai.assistant.data.remote.AntigravityApiService().submitAskQuestionAnswers(
                    stepIndex = toolCall.stepIndex,
                    responses = responses
                )
            }
        }
    }

    val handleQuestionnaireSkip: (ToolCall) -> Unit = { toolCall ->
        if (onSkipQuestionnaire != null) {
            onSkipQuestionnaire(toolCall)
        } else {
            scope.launch(Dispatchers.IO) {
                com.jarvisai.assistant.data.remote.AntigravityApiService().submitAskQuestionAnswers(
                    stepIndex = toolCall.stepIndex,
                    responses = emptyList(),
                    cancelled = true
                )
            }
        }
    }

    val listState = rememberLazyListState()

    val hasActiveStreamingTurn = !streamingPartialResponse.isNullOrBlank() || !currentTurnThought.isNullOrBlank() || currentTurnToolCalls.isNotEmpty()

    LaunchedEffect(messages.size, streamingPartialResponse, currentTurnThought, currentTurnToolCalls.size) {
        val totalItems = messages.size + (if (hasActiveStreamingTurn) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. APPLE iOS 26 FLOATING CAPSULE HEADER
        JarvisPremiumTopBar(
            title = "${settings.assistantName} AI Studio",
            subtitle = "${settings.textModel.substringAfterLast("/")} • ${settings.language.displayName}",
            status = status,
            onBackClick = onBack
        )

        // 2. APPLE iOS 26 MULTI-PALETTE CONTROL DOCK (New Chat | Sessions | Clear + Language Pills)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        themeColors.surface.copy(alpha = if (themeColors.isDark) 0.88f else 0.94f)
                    )
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                themeColors.glassRimTop,
                                themeColors.borderHighlight,
                                themeColors.glassRimBottom
                            )
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Action Squircle Pills
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Theme Primary & Accent New Chat Squircle
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(themeColors.accent, themeColors.primary)
                                )
                            )
                            .border(
                                1.dp,
                                themeColors.onPrimary.copy(alpha = 0.45f),
                                RoundedCornerShape(13.dp)
                            )
                            .clickable(onClick = onNewChat)
                            .testTag("btn_new_chat"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Chat",
                            tint = themeColors.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Theme Accent Sessions Drawer Squircle
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                if (showHistorySheet) {
                                    Brush.linearGradient(listOf(themeColors.accent, themeColors.primary))
                                } else {
                                    Brush.linearGradient(
                                        listOf(
                                            themeColors.surfacePill,
                                            themeColors.accent.copy(alpha = 0.18f)
                                        )
                                    )
                                }
                            )
                            .border(
                                1.dp,
                                themeColors.accent.copy(alpha = 0.65f),
                                RoundedCornerShape(13.dp)
                            )
                            .clickable { showHistorySheet = !showHistorySheet }
                            .testTag("btn_toggle_sessions"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Chat Sessions (${sessions.size})",
                            tint = if (showHistorySheet) themeColors.onPrimary else themeColors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (sessions.isNotEmpty()) {
                        // Rose Red Clear History Squircle
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(themeColors.statusRoseRed.copy(alpha = 0.18f))
                                .border(
                                    1.dp,
                                    themeColors.statusRoseRed.copy(alpha = 0.65f),
                                    RoundedCornerShape(13.dp)
                                )
                                .clickable { showClearAllConfirm = true }
                                .testTag("btn_clear_chat_history"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Chat History",
                                tint = themeColors.statusRoseRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: iOS Segmented Language Pills
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(themeColors.surfacePill)
                        .border(1.dp, themeColors.border.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
                        .padding(3.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SupportedLanguage.entries.forEach { lang ->
                        val selected = settings.language == lang
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (selected) {
                                        Brush.horizontalGradient(
                                            listOf(themeColors.accent, themeColors.primary)
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            listOf(Color.Transparent, Color.Transparent)
                                        )
                                    }
                                )
                                .clickable { onSelectLanguage(lang) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("chat_lang_${lang.name.lowercase()}")
                        ) {
                            Text(
                                text = lang.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) themeColors.onPrimary else themeColors.textSecondary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            // Memory Saved Notification Pill Banner
            AnimatedVisibility(visible = !memorySavedNotification.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(themeColors.statusEmerald.copy(alpha = 0.16f))
                        .border(1.dp, themeColors.statusEmerald.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = themeColors.statusEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = memorySavedNotification.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = themeColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismissMemoryNotification,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = themeColors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Expandable Conversation Archive Drawer
            AnimatedVisibility(visible = showHistorySheet) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(themeColors.surfaceElevated.copy(alpha = 0.96f))
                        .border(1.2.dp, themeColors.borderHighlight, RoundedCornerShape(24.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Conversation Archive (${sessions.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = themeColors.textPrimary,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(themeColors.accent.copy(alpha = 0.18f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "VAULT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = themeColors.accent,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (sessions.isEmpty()) {
                            Text(
                                text = "Abhi koi session save nahi hai. Naya message bhejein.",
                                style = MaterialTheme.typography.bodySmall,
                                color = themeColors.textSecondary
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                sessions.take(8).forEach { session ->
                                    val isCurrent = session.id == currentSessionId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                if (isCurrent) {
                                                    themeColors.accent.copy(alpha = 0.20f)
                                                } else {
                                                    themeColors.cardSurface
                                                }
                                            )
                                            .border(
                                                1.dp,
                                                if (isCurrent) themeColors.accent else themeColors.border,
                                                RoundedCornerShape(16.dp)
                                            )
                                            .clickable {
                                                onSelectSession(session.id)
                                                showHistorySheet = false
                                            }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = session.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = themeColors.textPrimary,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = formatChatTimestamp(session.updatedAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = themeColors.textSecondary
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteSession(session.id) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("delete_session_${session.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Chat Session",
                                                tint = themeColors.statusRoseRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!hasApiKey) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(themeColors.statusAmber.copy(alpha = 0.16f))
                        .border(1.dp, themeColors.statusAmber, RoundedCornerShape(18.dp))
                        .clickable(onClick = onNavigateToApiSettings)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = themeColors.statusAmber
                        )
                        Text(
                            text = "Gemini API key set nahi hai. API Settings mein apni key enter karne ke liye yahan tap karein.",
                            style = MaterialTheme.typography.bodySmall,
                            color = themeColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 3. CHAT MESSAGE STREAM
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (messages.isEmpty() && !hasActiveStreamingTurn) {
                item {
                    EmptyChatExperience(
                        assistantName = settings.assistantName,
                        language = settings.language,
                        onQuickPrompt = { prompt ->
                            onSendMessage(prompt)
                        }
                    )
                }
            }

            items(messages, key = { it.id }) { msg ->
                JarvisMessageBubble(
                    message = msg,
                    assistantName = settings.assistantName,
                    onCopy = { text ->
                        copyTextToClipboard(context, text)
                    },
                    onSaveToMemory = { text ->
                        memoryDraftContent = text
                    },
                    onRetry = if (msg.isError) onRetryFailed else null,
                    onSubmitQuestionnaire = handleQuestionnaireSubmit,
                    onSkipQuestionnaire = handleQuestionnaireSkip,
                    onInspectRawPayload = { payload ->
                        inspectingRawPayload = payload
                    },
                    onInspectTokenDetails = { usage, payload ->
                        inspectingTokenUsage = usage to payload
                    }
                )
            }

            if (!streamingPartialResponse.isNullOrBlank() || !currentTurnThought.isNullOrBlank() || currentTurnToolCalls.isNotEmpty()) {
                item {
                    JarvisMessageBubble(
                        message = ChatMessageEntity(
                            id = -999L,
                            sessionId = currentSessionId ?: 0L,
                            role = "model",
                            content = if (!streamingPartialResponse.isNullOrBlank()) "$streamingPartialResponse ▍" else "Thinking...",
                            modelUsed = settings.textModel,
                            thoughtText = currentTurnThought,
                            toolCallsJson = if (currentTurnToolCalls.isNotEmpty()) serializeToolCallsForBubble(currentTurnToolCalls) else null
                        ),
                        assistantName = settings.assistantName,
                        onCopy = {},
                        onSaveToMemory = {},
                        isStreaming = true,
                        onSubmitQuestionnaire = handleQuestionnaireSubmit,
                        onSkipQuestionnaire = handleQuestionnaireSkip,
                        onInspectRawPayload = { payload ->
                            inspectingRawPayload = payload
                        }
                    )
                }
            }

            if (chatError != null && lastFailedPrompt != null && !isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(themeColors.statusRoseRed.copy(alpha = 0.16f))
                            .border(1.dp, themeColors.statusRoseRed, RoundedCornerShape(22.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Request Failed",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = themeColors.statusRoseRed,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = chatError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = themeColors.textPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = onRetryFailed,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, themeColors.statusRoseRed),
                                modifier = Modifier.testTag("btn_retry_chat")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = themeColors.statusRoseRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Retry",
                                    color = themeColors.statusRoseRed,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. APPLE iOS 26 ADAPTIVE FLOATING COMPOSER CAPSULE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                themeColors.inputBackground,
                                themeColors.surfaceElevated
                            )
                        )
                    )
                    .border(
                        width = 1.4.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                themeColors.accent,
                                themeColors.primary,
                                themeColors.secondary
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = when (settings.language) {
                                SupportedLanguage.HINDI -> "${settings.assistantName} से हिंदी या हिंग्लिश में पूछें..."
                                SupportedLanguage.HINGLISH -> "Ask ${settings.assistantName} in Hinglish..."
                                SupportedLanguage.ENGLISH -> "Message ${settings.assistantName}..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = themeColors.textMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = themeColors.textPrimary,
                        unfocusedTextColor = themeColors.textPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                )

                val canSend = inputText.isNotBlank() && !isLoading
                Button(
                    onClick = {
                        if (canSend) {
                            val toSend = inputText
                            inputText = ""
                            onSendMessage(toSend)
                        }
                    },
                    enabled = canSend,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canSend) themeColors.accent else themeColors.surfacePill,
                        contentColor = themeColors.onPrimary,
                        disabledContainerColor = themeColors.surfacePill,
                        disabledContentColor = themeColors.textMuted
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_send_message")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = themeColors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showClearAllConfirm) {
        JarvisModernDialog(
            title = "Clear All Chat History?",
            message = "Kya aap saare ${sessions.size} chat sessions delete karna chahte hain? Aapki saved Memories safe rahengi.",
            confirmLabel = "Clear History",
            isDestructive = true,
            onConfirm = onClearAllHistory,
            onDismiss = { showClearAllConfirm = false }
        )
    }

    memoryDraftContent?.let { contentToSave ->
        SaveChatToMemoryDialog(
            initialContent = contentToSave,
            onDismiss = { memoryDraftContent = null },
            onConfirmSave = { title, content, category ->
                onSaveApprovedMemory(title, content, category)
                Toast.makeText(context, "Memory saved successfully.", Toast.LENGTH_SHORT).show()
                memoryDraftContent = null
            }
        )
    }

    if (inspectingRawPayload != null) {
        RawPayloadDialog(
            payloadJson = inspectingRawPayload!!,
            onDismiss = { inspectingRawPayload = null }
        )
    }

    inspectingTokenUsage?.let { (usage, payload) ->
        TokenUsageDetailsSheet(
            usage = usage,
            onInspectRawPayload = { inspectingRawPayload = payload },
            onDismiss = { inspectingTokenUsage = null }
        )
    }
}

@Composable
private fun EmptyChatExperience(
    assistantName: String,
    language: SupportedLanguage,
    onQuickPrompt: (String) -> Unit
) {
    val themeColors = JarvisTheme.colors

    val suggestions = when (language) {
        SupportedLanguage.HINDI -> listOf(
            "My name is Sagar. Remember this.",
            "JARVIS, YouTube par search karo latest technology videos",
            "आज के लिए एक प्रोडक्टिव रूटीन बनाओ"
        )
        SupportedLanguage.HINGLISH -> listOf(
            "My name is Sagar. Remember this.",
            "JARVIS, YouTube par search karo latest technology videos",
            "Mera daily productivity schedule plan karne mein help karo"
        )
        SupportedLanguage.ENGLISH -> listOf(
            "My name is Sagar. Remember this.",
            "What is my name?",
            "JARVIS, YouTube par search karo latest technology videos"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Adaptive Theme Hero Banner Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            themeColors.primary,
                            themeColors.accent
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(800f, 600f)
                    )
                )
                .border(
                    width = 1.4.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            themeColors.onPrimary.copy(alpha = 0.55f),
                            themeColors.secondary,
                            themeColors.accent
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(themeColors.onPrimary.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = themeColors.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "NEURAL STUDIO 26",
                            style = MaterialTheme.typography.labelSmall,
                            color = themeColors.onPrimary.copy(alpha = 0.88f),
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Ask $assistantName Anything",
                            style = MaterialTheme.typography.headlineSmall,
                            color = themeColors.onPrimary,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Text(
                    text = "Chat in Hinglish, Hindi, or English. Automatically saves memories and executes phone commands.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = themeColors.onPrimary.copy(alpha = 0.90f),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Theme-Adaptive Prompt Bento Cards
        val cardAccents = listOf(
            Pair(themeColors.accent, "MEMORY & FACT"),
            Pair(themeColors.primary, "PHONE AUTOMATION"),
            Pair(themeColors.statusEmerald, "PRODUCTIVITY")
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            suggestions.forEachIndexed { idx, prompt ->
                val (accent, tagLabel) = cardAccents[idx % cardAccents.size]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    themeColors.cardSurface,
                                    themeColors.surfaceElevated.copy(alpha = 0.88f)
                                )
                            )
                        )
                        .border(1.2.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                        .clickable { onQuickPrompt(prompt) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = themeColors.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tagLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.bodyMedium,
                                color = themeColors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Run",
                                color = themeColors.onPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = themeColors.onPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun parseToolCallsFromJson(jsonStr: String?): List<com.jarvisai.assistant.model.ToolCall> {
    if (jsonStr.isNullOrBlank()) return emptyList()
    return try {
        val arr = org.json.JSONArray(jsonStr)
        val list = mutableListOf<com.jarvisai.assistant.model.ToolCall>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val qObj = obj.optJSONObject("questionnaire")
            val questionnaire = if (qObj != null) {
                val qArr = qObj.optJSONArray("questions")
                val qList = mutableListOf<QuestionnaireQuestion>()
                if (qArr != null) {
                    for (qi in 0 until qArr.length()) {
                        val itemObj = qArr.getJSONObject(qi)
                        val optsArr = itemObj.optJSONArray("options")
                        val optsList = mutableListOf<QuestionnaireOption>()
                        if (optsArr != null) {
                            for (oi in 0 until optsArr.length()) {
                                val optText = optsArr.optString(oi, "")
                                if (optText.isNotBlank()) {
                                    optsList.add(QuestionnaireOption(id = "opt_$oi", text = optText))
                                }
                            }
                        }
                        qList.add(
                            QuestionnaireQuestion(
                                question = itemObj.optString("question", ""),
                                options = optsList,
                                isMultiSelect = itemObj.optBoolean("is_multi_select", false)
                            )
                        )
                    }
                }
                ChoiceQuestionnaire(title = qObj.optString("title", "Questions for you"), questions = qList)
            } else null

            list.add(
                com.jarvisai.assistant.model.ToolCall(
                    id = obj.optString("id", "tool_$i"),
                    stepIndex = obj.optInt("stepIndex", i),
                    name = obj.optString("name", "bash"),
                    command = obj.optString("command", ""),
                    args = obj.optString("args", ""),
                    output = obj.optString("output", ""),
                    status = runCatching {
                        com.jarvisai.assistant.model.ToolCallStatus.valueOf(obj.optString("status", "SUCCESS"))
                    }.getOrDefault(com.jarvisai.assistant.model.ToolCallStatus.SUCCESS),
                    durationMs = obj.optLong("durationMs", 0L),
                    exitCode = if (obj.has("exitCode")) obj.optInt("exitCode") else null,
                    questionnaire = questionnaire
                )
            )
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}

private fun serializeToolCallsForBubble(tools: List<com.jarvisai.assistant.model.ToolCall>): String {
    val arr = org.json.JSONArray()
    tools.forEach { t ->
        val obj = org.json.JSONObject()
            .put("id", t.id)
            .put("stepIndex", t.stepIndex)
            .put("name", t.name)
            .put("command", t.command)
            .put("args", t.args)
            .put("output", t.output)
            .put("status", t.status.name)
            .put("durationMs", t.durationMs)
        if (t.exitCode != null) obj.put("exitCode", t.exitCode)
        t.questionnaire?.let { q ->
            val qArr = org.json.JSONArray()
            q.questions.forEach { qItem ->
                val optsArr = org.json.JSONArray()
                qItem.options.forEach { opt -> optsArr.put(opt.text) }
                qArr.put(
                    org.json.JSONObject()
                        .put("question", qItem.question)
                        .put("is_multi_select", qItem.isMultiSelect)
                        .put("options", optsArr)
                )
            }
            obj.put("questionnaire", org.json.JSONObject().put("title", q.title).put("questions", qArr))
        }
        arr.put(obj)
    }
    return arr.toString()
}

fun buildMessageRawPayloadJson(message: ChatMessageEntity): String {
    return try {
        JSONObject().apply {
            put("id", message.id)
            put("sessionId", message.sessionId)
            put("role", message.role)
            put("modelUsed", message.modelUsed)
            put("timestamp", message.timestamp)
            put("content", message.content)
            if (!message.thoughtText.isNullOrBlank()) {
                put("thoughtText", message.thoughtText)
                put("thoughtDurationMs", message.thoughtDurationMs ?: 0L)
            }
            if (!message.toolCallsJson.isNullOrBlank()) {
                put("toolCalls", runCatching { org.json.JSONArray(message.toolCallsJson) }.getOrDefault(message.toolCallsJson))
            }
            if (!message.tokenUsageJson.isNullOrBlank()) {
                put("tokenUsage", runCatching { JSONObject(message.tokenUsageJson) }.getOrDefault(message.tokenUsageJson))
            }
        }.toString(2)
    } catch (_: Exception) {
        message.toolCallsJson ?: message.thoughtText ?: message.content
    }
}

@Composable
fun JarvisMessageBubble(
    message: ChatMessageEntity,
    assistantName: String,
    onCopy: (String) -> Unit,
    onSaveToMemory: (String) -> Unit,
    onRetry: (() -> Unit)? = null,
    isStreaming: Boolean = false,
    onSubmitQuestionnaire: (ToolCall, List<ChoiceResponseItem>) -> Unit = { _, _ -> },
    onSkipQuestionnaire: (ToolCall) -> Unit = {},
    onInspectRawPayload: ((String) -> Unit)? = null,
    onInspectTokenDetails: ((TokenUsage, String) -> Unit)? = null
) {
    val isUser = message.role == "user"
    var fallbackPayloadModal by remember { mutableStateOf<String?>(null) }
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    } else {
        RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    }

    val themeColors = JarvisTheme.colors

    val backgroundBrush = when {
        message.isError -> Brush.linearGradient(
            listOf(
                themeColors.statusRoseRed.copy(alpha = 0.22f),
                themeColors.surfaceElevated
            )
        )
        isUser -> Brush.linearGradient(
            listOf(
                themeColors.accent,
                themeColors.primary
            )
        )
        else -> Brush.linearGradient(
            listOf(
                themeColors.chatBubbleAssistant,
                themeColors.surfaceElevated.copy(alpha = 0.96f)
            )
        )
    }

    val borderBrush = when {
        message.isError -> Brush.horizontalGradient(
            listOf(themeColors.statusRoseRed, themeColors.statusRoseRed.copy(alpha = 0.45f))
        )
        isUser -> Brush.horizontalGradient(
            listOf(
                themeColors.onPrimary.copy(alpha = 0.50f),
                themeColors.glassRimTop
            )
        )
        else -> Brush.horizontalGradient(
            listOf(
                themeColors.glassRimTop,
                themeColors.borderHighlight,
                themeColors.glassRimBottom
            )
        )
    }

    val contentTextColor = if (isUser) themeColors.onPrimary else themeColors.textPrimary
    val timestampColor = if (isUser) themeColors.onPrimary.copy(alpha = 0.80f) else themeColors.textSecondary

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // 0. Context Compaction Summary Checkpoint Banner
        if (!isUser && message.content.startsWith("[CONTEXT_SUMMARY]")) {
            ContextSummaryCheckpointBanner(
                summaryText = message.content.removePrefix("[CONTEXT_SUMMARY]").trim(),
                modifier = Modifier.widthIn(max = 640.dp)
            )
        }

        // 1. Permanent Thinking Accordion (Rendered for Assistant messages)
        if (!isUser && !message.thoughtText.isNullOrBlank()) {
            ThinkingAccordion(
                thoughtText = message.thoughtText,
                durationMs = message.thoughtDurationMs,
                isStreaming = isStreaming,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .padding(bottom = 4.dp)
            )
        }

        // 2. Permanent Tool Execution Cards & Interactive Questionnaires
        if (!isUser && !message.toolCallsJson.isNullOrBlank()) {
            val tools = remember(message.toolCallsJson) { parseToolCallsFromJson(message.toolCallsJson) }
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .padding(bottom = 6.dp)
            ) {
                tools.forEach { tool ->
                    if (tool.questionnaire != null) {
                        ChoiceQuestionnaireCard(
                            toolCall = tool,
                            onSubmit = { toolCall, responses ->
                                onSubmitQuestionnaire(toolCall, responses)
                            },
                            onSkip = { toolCall ->
                                onSkipQuestionnaire(toolCall)
                            }
                        )
                    } else {
                        AgentToolCallCard(toolCall = tool)
                    }
                }
            }
        }

        // 3. Message Content (User = Right Box Bubble; AI = Flat Left-Aligned Borderless)
        val containerModifier = if (isUser) {
            Modifier
                .padding(start = 52.dp)
                .widthIn(max = 320.dp)
                .clip(bubbleShape)
                .background(backgroundBrush)
                .border(1.dp, borderBrush, bubbleShape)
                .padding(14.dp)
        } else if (message.isError) {
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        }

        Box(modifier = containerModifier) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isUser) themeColors.onPrimary.copy(alpha = 0.22f)
                                    else themeColors.accent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isUser) Icons.Default.Person else Icons.Default.Psychology,
                                contentDescription = null,
                                tint = themeColors.onPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = if (isUser) "You" else assistantName,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isUser) themeColors.onPrimary else themeColors.accent,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (message.memoriesUsedCount > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(themeColors.statusEmerald.copy(alpha = 0.18f))
                                    .border(1.dp, themeColors.statusEmerald, RoundedCornerShape(50))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = themeColors.statusEmerald,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${message.memoriesUsedCount} MEM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = themeColors.statusEmerald,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        if (isStreaming) {
                            JarvisAnimatedIndicator(
                                label = "STREAMING",
                                accentColor = themeColors.accent
                            )
                        } else {
                            Text(
                                text = formatChatTimestamp(message.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = timestampColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val decodedUserContent = remember(message.content, isUser) {
                    if (isUser) com.jarvisai.assistant.model.ChatAttachmentCodec.decodeMessage(message.content)
                    else com.jarvisai.assistant.model.DecodedChatMessage(emptyList(), message.content)
                }
                var previewAttachment by remember { mutableStateOf<com.jarvisai.assistant.model.PersistedChatAttachment?>(null) }
                val localCtx = androidx.compose.ui.platform.LocalContext.current

                if (isUser && decodedUserContent.attachments.isNotEmpty()) {
                    com.jarvisai.assistant.ui.components.ChatBubbleAttachmentsSection(
                        attachments = decodedUserContent.attachments,
                        onAttachmentClick = { att ->
                            if (att.kind == com.jarvisai.assistant.model.AttachmentKind.IMAGE) {
                                previewAttachment = att
                            } else {
                                com.jarvisai.assistant.ui.components.openAttachmentExternally(localCtx, att)
                            }
                        }
                    )
                    if (decodedUserContent.bodyText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if (decodedUserContent.bodyText.isNotBlank() || !isUser) {
                    SelectionContainer {
                        RenderMessageContentWithCodeBlocks(
                            content = decodedUserContent.bodyText,
                            textColor = if (message.isError && !isUser) themeColors.statusRoseRed else contentTextColor,
                            fillMaxWidth = !isUser,
                            onCopy = onCopy
                        )
                    }
                }

                previewAttachment?.let { att ->
                    com.jarvisai.assistant.ui.components.FullscreenImagePreviewDialog(
                        attachment = att,
                        onDismiss = { previewAttachment = null }
                    )
                }

                if (!isStreaming) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isUser) {
                                        Color.Black.copy(alpha = 0.22f)
                                    } else {
                                        themeColors.surfacePill.copy(alpha = 0.85f)
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (isUser) themeColors.onPrimary.copy(alpha = 0.24f) else themeColors.border,
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    onCopy(com.jarvisai.assistant.model.ChatAttachmentCodec.stripForCopy(message.content))
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("copy_msg_${message.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Response",
                                    tint = if (isUser) themeColors.onPrimary else themeColors.textPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            IconButton(
                                onClick = { onSaveToMemory(message.content) },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("save_memory_msg_${message.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkAdd,
                                    contentDescription = "Save to Memory Center",
                                    tint = if (isUser) themeColors.onPrimary else themeColors.statusAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Raw RPC / Payload Inspector Button
                            IconButton(
                                onClick = {
                                    val payload = buildMessageRawPayloadJson(message)
                                    if (onInspectRawPayload != null) {
                                        onInspectRawPayload(payload)
                                    } else {
                                        fallbackPayloadModal = payload
                                    }
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("raw_payload_msg_${message.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DataObject,
                                    contentDescription = "View Raw Request Payload",
                                    tint = if (isUser) themeColors.onPrimary.copy(alpha = 0.8f) else themeColors.textSecondary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            if (onRetry != null) {
                                IconButton(
                                    onClick = onRetry,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .testTag("retry_msg_${message.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry Failed Request",
                                        tint = themeColors.statusRoseRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Token Telemetry Pill (Under Assistant Message)
        if (!isUser && !isStreaming && message.content.isNotBlank()) {
            Row(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val usage = remember(message) {
                    val promptApprox = (message.content.length / 4).coerceAtLeast(1)
                    TokenUsage(
                        promptTokens = promptApprox,
                        outputTokens = (message.content.length / 4).coerceAtLeast(1),
                        durationMs = message.thoughtDurationMs ?: 0L,
                        model = message.modelUsed
                    )
                }
                TokenUsageTelemetryPill(
                    usage = usage,
                    onViewPayload = {
                        val payload = buildMessageRawPayloadJson(message)
                        if (onInspectTokenDetails != null) {
                            onInspectTokenDetails(usage, payload)
                        } else if (onInspectRawPayload != null) {
                            onInspectRawPayload(payload)
                        } else {
                            fallbackPayloadModal = payload
                        }
                    }
                )
            }
        }

        if (fallbackPayloadModal != null) {
            RawPayloadDialog(
                payloadJson = fallbackPayloadModal!!,
                onDismiss = { fallbackPayloadModal = null }
            )
        }
    }
}

@Composable
private fun SaveChatToMemoryDialog(
    initialContent: String,
    onDismiss: () -> Unit,
    onConfirmSave: (title: String, content: String, category: MemoryCategory) -> Unit
) {
    val themeColors = JarvisTheme.colors
    var title by remember { mutableStateOf(initialContent.take(32).replace("\n", " ")) }
    var content by remember { mutableStateOf(initialContent) }
    var category by remember { mutableStateOf(MemoryCategory.PERSONAL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = if (themeColors.isDark) themeColors.surfaceElevated else themeColors.cardSurface,
        title = {
            Text(
                text = "Save to Memory Center",
                style = MaterialTheme.typography.titleLarge,
                color = themeColors.textPrimary,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Save this fact permanently to your local Room memory database:",
                    style = MaterialTheme.typography.bodySmall,
                    color = themeColors.textSecondary
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Memory Key / Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Memory Fact / Detail") },
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MemoryCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = themeColors.accent.copy(alpha = 0.22f),
                                selectedLabelColor = themeColors.accent
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onConfirmSave(title, content, category)
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeColors.accent,
                    contentColor = themeColors.onPrimary
                )
            ) {
                Text("Save Memory", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, themeColors.border)
            ) {
                Text("Cancel", color = themeColors.textSecondary)
            }
        }
    )
}

private fun copyTextToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("JARVIS Response", text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun formatChatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

@Composable
fun RenderMessageContentWithCodeBlocks(
    content: String,
    textColor: Color = JarvisTheme.colors.textPrimary,
    fillMaxWidth: Boolean = true,
    onCopy: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.jarvisai.assistant.ui.components.MarkdownContent(
            content = content,
            textColor = textColor,
            fillMaxWidth = fillMaxWidth,
            onCopy = onCopy
        )
        FileLinkRow(text = content)
    }
}
