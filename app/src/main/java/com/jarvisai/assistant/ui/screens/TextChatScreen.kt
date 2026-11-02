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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusSpeakingAzure
import com.example.ui.theme.StatusThinkingAmber
import com.jarvisai.assistant.data.local.ChatMessageEntity
import com.jarvisai.assistant.data.local.ChatSessionEntity
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.MemoryCategory
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.ui.components.HudCard
import com.jarvisai.assistant.ui.components.HudConfirmationDialog
import com.jarvisai.assistant.ui.components.HudTopBar
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
    isTtsSpeaking: Boolean = false,
    memorySavedNotification: String? = null,
    onDismissMemoryNotification: () -> Unit = {},
    onBack: () -> Unit,
    onSelectLanguage: (SupportedLanguage) -> Unit,
    onToggleAutoSpeak: (Boolean) -> Unit = {},
    onSendMessage: (String) -> Unit,
    onRetryFailed: () -> Unit,
    onNewChat: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onClearAllHistory: () -> Unit,
    onSaveApprovedMemory: (title: String, content: String, category: MemoryCategory) -> Unit,
    onSpeakMessage: (String) -> Unit,
    onStopSpeaking: () -> Unit = {},
    onNavigateToApiSettings: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var inputText by rememberSaveable { mutableStateOf("") }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    var showClearAllConfirm by rememberSaveable { mutableStateOf(false) }
    var memoryDraftContent by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, streamingPartialResponse) {
        val totalItems = messages.size + (if (streamingPartialResponse != null) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HudTopBar(
            title = "NEURAL TEXT CHAT",
            subtitle = "${settings.voiceGender.displayName} (${settings.voicePersona}) // ${settings.language.displayName}",
            status = status,
            onBackClick = onBack
        )

        // Top Action & Multilingual Selector Strip
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Language selector chips: Hinglish, Hindi, English + Auto-Speak toggle
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SupportedLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = settings.language == lang,
                                onClick = { onSelectLanguage(lang) },
                                label = {
                                    Text(
                                        text = lang.displayName,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("chat_lang_${lang.name.lowercase()}")
                            )
                        }

                        FilterChip(
                            selected = settings.autoSpeakChatReplies,
                            onClick = { onToggleAutoSpeak(!settings.autoSpeakChatReplies) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (settings.autoSpeakChatReplies) {
                                        Icons.AutoMirrored.Filled.VolumeUp
                                    } else {
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    },
                                    contentDescription = "Auto-Speak AI Replies",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = if (settings.autoSpeakChatReplies) "Voice ON" else "Voice Muted",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StatusSpeakingAzure.copy(alpha = 0.22f),
                                selectedLabelColor = StatusSpeakingAzure
                            ),
                            modifier = Modifier.testTag("chip_toggle_auto_speak")
                        )
                    }

                    // Session actions: New Chat, History Drawer, Clear All
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onNewChat,
                            modifier = Modifier.testTag("btn_new_chat")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Chat",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { showHistorySheet = !showHistorySheet },
                            modifier = Modifier.testTag("btn_toggle_sessions")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Chat Sessions (${sessions.size})",
                                tint = if (showHistorySheet) StatusListeningEmerald else MaterialTheme.colorScheme.primary
                            )
                        }
                        if (sessions.isNotEmpty()) {
                            IconButton(
                                onClick = { showClearAllConfirm = true },
                                modifier = Modifier.testTag("btn_clear_chat_history")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear Chat History",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Active TTS Speech Bar with Stop Speaking Control
                AnimatedVisibility(visible = isTtsSpeaking) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(StatusSpeakingAzure.copy(alpha = 0.16f))
                            .border(1.dp, StatusSpeakingAzure, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = StatusSpeakingAzure,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${settings.assistantName} bol raha hai (${settings.voiceGender.displayName})...",
                                style = MaterialTheme.typography.labelMedium,
                                color = StatusSpeakingAzure
                            )
                        }
                        OutlinedButton(
                            onClick = onStopSpeaking,
                            border = BorderStroke(1.dp, AlertCrimson),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_stop_speaking_chat")
                        ) {
                            Icon(
                                imageVector = Icons.Default.StopCircle,
                                contentDescription = "Stop Speaking",
                                tint = AlertCrimson,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "STOP SPEAKING",
                                style = MaterialTheme.typography.labelSmall,
                                color = AlertCrimson
                            )
                        }
                    }
                }

                // Memory Saved Confirmation Banner
                AnimatedVisibility(visible = !memorySavedNotification.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(StatusListeningEmerald.copy(alpha = 0.16f))
                            .border(1.dp, StatusListeningEmerald, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
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
                                tint = StatusListeningEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = memorySavedNotification.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusListeningEmerald,
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
                                tint = StatusListeningEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Expandable Session History Selector
                AnimatedVisibility(visible = showHistorySheet) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "STORED CONVERSATION SESSIONS (${sessions.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (sessions.isEmpty()) {
                            Text(
                                text = "Abhi koi session save nahi hai. Naya message bhejein.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            sessions.take(8).forEach { session ->
                                val isCurrent = session.id == currentSessionId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            onSelectSession(session.id)
                                            showHistorySheet = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatChatTimestamp(session.updatedAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            tint = MaterialTheme.colorScheme.error,
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
            HudCard(
                glowColor = StatusThinkingAmber,
                onClick = onNavigateToApiSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = StatusThinkingAmber
                    )
                    Text(
                        text = "Gemini API key set nahi hai. API Settings mein apni key enter karne ke liye yahan tap karein.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusThinkingAmber
                    )
                }
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty() && streamingPartialResponse == null) {
                item {
                    EmptyChatPlaceholder(
                        assistantName = settings.assistantName,
                        language = settings.language,
                        onQuickPrompt = { prompt ->
                            onSendMessage(prompt)
                        }
                    )
                }
            }

            items(messages, key = { it.id }) { msg ->
                ChatMessageBubble(
                    message = msg,
                    assistantName = settings.assistantName,
                    isTtsSpeaking = isTtsSpeaking,
                    onCopy = { text ->
                        copyTextToClipboard(context, text)
                    },
                    onSpeak = { text ->
                        onSpeakMessage(text)
                    },
                    onStopSpeak = onStopSpeaking,
                    onSaveToMemory = { text ->
                        memoryDraftContent = text
                    },
                    onRetry = if (msg.isError) onRetryFailed else null
                )
            }

            // Streaming Partial Response Bubble
            if (!streamingPartialResponse.isNullOrBlank()) {
                item {
                    ChatMessageBubble(
                        message = ChatMessageEntity(
                            id = -999L,
                            sessionId = currentSessionId ?: 0L,
                            role = "model",
                            content = "$streamingPartialResponse ▍",
                            modelUsed = settings.textModel
                        ),
                        assistantName = settings.assistantName,
                        isTtsSpeaking = false,
                        onCopy = {},
                        onSpeak = {},
                        onStopSpeak = {},
                        onSaveToMemory = {},
                        isStreaming = true
                    )
                }
            }

            // Error Retry Bar if failed
            if (chatError != null && lastFailedPrompt != null && !isLoading) {
                item {
                    HudCard(
                        glowColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TRANSMISSION ERROR",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = chatError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = onRetryFailed,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("btn_retry_chat")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("RETRY", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // Bottom Message Input Composer
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = when (settings.language) {
                                SupportedLanguage.HINDI -> "${settings.assistantName} से हिंदी या हिंग्लिश में पूछें..."
                                SupportedLanguage.HINGLISH -> "JARVIS se Hinglish mein baat karein..."
                                SupportedLanguage.ENGLISH -> "Transmit command to ${settings.assistantName}..."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                )

                Button(
                    onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            val toSend = inputText
                            inputText = ""
                            onSendMessage(toSend)
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(14.dp),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("btn_send_message")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message"
                        )
                    }
                }
            }
        }
    }

    // Clear All History Confirmation Dialog
    if (showClearAllConfirm) {
        HudConfirmationDialog(
            title = "PURGE ALL CHAT HISTORY?",
            message = "Kya aap saare ${sessions.size} chat sessions delete karna chahte hain? Aapki saved Memories safe rahengi.",
            confirmLabel = "PURGE HISTORY",
            isDestructive = true,
            onConfirm = onClearAllHistory,
            onDismiss = { showClearAllConfirm = false }
        )
    }

    // User-Approved Memory Save Dialog from Chat
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
}

@Composable
private fun EmptyChatPlaceholder(
    assistantName: String,
    language: SupportedLanguage,
    onQuickPrompt: (String) -> Unit
) {
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

    HudCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Namaste, main $assistantName hoon.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Hinglish, Hindi (हिंदी), aur English mein baat karein. Main har reply bol kar sunata hoon aur aapki zaroori memories automatically Room DB mein yaad rakhta hoon.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "QUICK TACTICAL PROMPTS:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        suggestions.forEach { prompt ->
            OutlinedButton(
                onClick = { onQuickPrompt(prompt) },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessageEntity,
    assistantName: String,
    isTtsSpeaking: Boolean,
    onCopy: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onStopSpeak: () -> Unit,
    onSaveToMemory: (String) -> Unit,
    onRetry: (() -> Unit)? = null,
    isStreaming: Boolean = false
) {
    val isUser = message.role == "user"
    val bubbleShape = if (isUser) {
        CutCornerShape(topStart = 12.dp, bottomStart = 12.dp, bottomEnd = 4.dp, topEnd = 4.dp)
    } else {
        CutCornerShape(topEnd = 12.dp, bottomEnd = 12.dp, topStart = 4.dp, bottomStart = 4.dp)
    }

    val borderColor = when {
        message.isError -> MaterialTheme.colorScheme.error
        isUser -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    val containerColor = when {
        message.isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        isUser -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .clip(bubbleShape)
                .background(containerColor)
                .border(1.dp, borderColor.copy(alpha = 0.6f), bubbleShape)
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "OPERATOR" else assistantName.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = borderColor,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (message.memoriesUsedCount > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(StatusSpeakingAzure.copy(alpha = 0.16f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = StatusSpeakingAzure,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${message.memoriesUsedCount} MEM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusSpeakingAzure
                                )
                            }
                        }

                        Text(
                            text = if (isStreaming) "STREAMING..." else formatChatTimestamp(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!isStreaming) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onCopy(message.content) },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("copy_msg_${message.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Response",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (!isUser && !message.isError) {
                            IconButton(
                                onClick = { onSpeak(message.content) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("speak_msg_${message.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Replay Voice Response",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            if (isTtsSpeaking) {
                                IconButton(
                                    onClick = onStopSpeak,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("stop_speak_msg_${message.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.StopCircle,
                                        contentDescription = "Stop Speaking",
                                        tint = AlertCrimson,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { onSaveToMemory(message.content) },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("save_memory_msg_${message.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = "Save to Memory Center",
                                tint = StatusSpeakingAzure,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        if (onRetry != null) {
                            IconButton(
                                onClick = onRetry,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("retry_msg_${message.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry Failed Request",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveChatToMemoryDialog(
    initialContent: String,
    onDismiss: () -> Unit,
    onConfirmSave: (title: String, content: String, category: MemoryCategory) -> Unit
) {
    var title by remember { mutableStateOf(initialContent.take(32).replace("\n", " ")) }
    var content by remember { mutableStateOf(initialContent) }
    var category by remember { mutableStateOf(MemoryCategory.PERSONAL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "SAVE TO MEMORY CENTER",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Yeh memory aapke phone ke local Room database mein permanently save ho jayegi:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Memory Key / Title (e.g. Name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Memory Fact / Detail") },
                    maxLines = 4,
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
                            label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) }
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
                enabled = title.isNotBlank() && content.isNotBlank()
            ) {
                Text("SAVE MEMORY")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("CANCEL")
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
