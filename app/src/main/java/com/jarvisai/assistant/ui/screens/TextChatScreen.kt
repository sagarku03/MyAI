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
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.JarvisElevatedSurface
import com.example.ui.theme.JarvisGlassBorder
import com.example.ui.theme.JarvisGlassSurface
import com.example.ui.theme.JarvisNeonCyan
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet
import com.example.ui.theme.JarvisVioletBright
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusThinkingAmber
import com.jarvisai.assistant.data.local.ChatMessageEntity
import com.jarvisai.assistant.data.local.ChatSessionEntity
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.MemoryCategory
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.ui.components.JarvisAIOrb
import com.jarvisai.assistant.ui.components.JarvisAnimatedIndicator
import com.jarvisai.assistant.ui.components.JarvisGlassPanel
import com.jarvisai.assistant.ui.components.JarvisGradientTitleWithLaserFlare
import com.jarvisai.assistant.ui.components.JarvisIconControl
import com.jarvisai.assistant.ui.components.JarvisModernDialog
import com.jarvisai.assistant.ui.components.JarvisPremiumTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MonexLimeGreen = Color(0xFFA3FF12)
private val ShruhhPurpleGlassTop = Color(0xFF4C1D95)
private val ShruhhPurpleGlassBottom = Color(0xFF1E1035)
private val SapphireCardTop = Color(0xFF0C214E)
private val SapphireCardBottom = Color(0xFF07122B)
private val SapphireBorderCyan = Color(0xFF38BDF8)

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
        // 1. TOP BAR
        JarvisPremiumTopBar(
            title = "${settings.assistantName} Neural Chat",
            subtitle = "${settings.textModel} • ${settings.language.displayName}",
            status = status,
            onBackClick = onBack
        )

        // 2. MONEX / FULL CONTROL RECESSED SEGMENTED RIBBON
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF141822))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: New Chat, History Drawer Toggle, Clear All
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    JarvisIconControl(
                        icon = Icons.Default.Add,
                        contentDescription = "New Chat",
                        onClick = onNewChat,
                        tint = MonexLimeGreen,
                        activeColor = MonexLimeGreen,
                        isActive = true,
                        size = 36.dp,
                        modifier = Modifier.testTag("btn_new_chat")
                    )

                    JarvisIconControl(
                        icon = Icons.Default.History,
                        contentDescription = "Chat Sessions (${sessions.size})",
                        onClick = { showHistorySheet = !showHistorySheet },
                        tint = if (showHistorySheet) JarvisNeonCyan else JarvisTextPrimary,
                        activeColor = JarvisNeonCyan,
                        isActive = showHistorySheet,
                        size = 36.dp,
                        modifier = Modifier.testTag("btn_toggle_sessions")
                    )

                    if (sessions.isNotEmpty()) {
                        JarvisIconControl(
                            icon = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Chat History",
                            onClick = { showClearAllConfirm = true },
                            tint = AlertCrimson,
                            activeColor = AlertCrimson,
                            size = 36.dp,
                            modifier = Modifier.testTag("btn_clear_chat_history")
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: Monex / Image 5 Recessed Segmented Language Pills
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF0B0E16))
                        .padding(3.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SupportedLanguage.entries.forEach { lang ->
                        val selected = settings.language == lang
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) Color(0xFF23293A) else Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (selected) JarvisNeonCyan.copy(alpha = 0.60f) else Color.Transparent,
                                    RoundedCornerShape(50)
                                )
                                .clickable { onSelectLanguage(lang) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("chat_lang_${lang.name.lowercase()}")
                        ) {
                            Text(
                                text = lang.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else JarvisTextSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(StatusListeningEmerald.copy(alpha = 0.14f))
                        .border(1.dp, StatusListeningEmerald.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
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
                            tint = JarvisNeonGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = memorySavedNotification.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(
                        onClick = onDismissMemoryNotification,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = JarvisTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Expandable Apple Arcade-style Conversation History Sheet
            AnimatedVisibility(visible = showHistorySheet) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF141822))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
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
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ROOM VAULT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MonexLimeGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (sessions.isEmpty()) {
                            Text(
                                text = "Abhi koi session save nahi hai. Naya message bhejein.",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextSecondary
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
                                                if (isCurrent) JarvisViolet.copy(alpha = 0.22f)
                                                else Color(0xFF0D1017)
                                            )
                                            .border(
                                                1.dp,
                                                if (isCurrent) JarvisVioletBright.copy(alpha = 0.65f)
                                                else Color.White.copy(alpha = 0.06f),
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
                                                color = if (isCurrent) JarvisNeonCyan else Color.White,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = formatChatTimestamp(session.updatedAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = JarvisTextMuted
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
                                                tint = AlertCrimson,
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
                JarvisGlassPanel(
                    accentColor = StatusThinkingAmber,
                    cornerRadius = 18.dp,
                    contentPadding = PaddingValues(12.dp),
                    onClick = onNavigateToApiSettings,
                    modifier = Modifier.fillMaxWidth()
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
                            color = StatusThinkingAmber,
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
            if (messages.isEmpty() && streamingPartialResponse == null) {
                item {
                    EmptyChatExperience(
                        assistantName = settings.assistantName,
                        language = settings.language,
                        status = status,
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
                    onRetry = if (msg.isError) onRetryFailed else null
                )
            }

            // Streaming Partial Response Bubble
            if (!streamingPartialResponse.isNullOrBlank()) {
                item {
                    JarvisMessageBubble(
                        message = ChatMessageEntity(
                            id = -999L,
                            sessionId = currentSessionId ?: 0L,
                            role = "model",
                            content = "$streamingPartialResponse ▍",
                            modelUsed = settings.textModel
                        ),
                        assistantName = settings.assistantName,
                        onCopy = {},
                        onSaveToMemory = {},
                        isStreaming = true
                    )
                }
            }

            // Error Retry Card
            if (chatError != null && lastFailedPrompt != null && !isLoading) {
                item {
                    JarvisGlassPanel(
                        accentColor = AlertCrimson,
                        cornerRadius = 20.dp,
                        contentPadding = PaddingValues(14.dp),
                        modifier = Modifier.fillMaxWidth()
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
                                    color = AlertCrimson,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = chatError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = onRetryFailed,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, AlertCrimson),
                                modifier = Modifier.testTag("btn_retry_chat")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = AlertCrimson,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", color = AlertCrimson)
                            }
                        }
                    }
                }
            }
        }

        // 4. IMAGE 3 (GOOGLE WIDGY 4-COLOR NEON BAR) + IMAGE 9 (FROSTED RIM) COMPOSER DOCK
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF141824),
                                Color(0xFF1A1F30)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                JarvisVioletBright.copy(alpha = 0.45f),
                                JarvisNeonCyan.copy(alpha = 0.55f)
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
                            color = JarvisTextMuted
                        )
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
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
                        containerColor = if (canSend) MonexLimeGreen else Color(0xFF23293A),
                        contentColor = Color(0xFF071204),
                        disabledContainerColor = Color(0xFF23293A),
                        disabledContentColor = JarvisTextMuted
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_send_message")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = JarvisNeonCyan,
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

            Spacer(modifier = Modifier.height(6.dp))

            // Image 3 signature 4-color glowing bar strip under input capsule
            Row(
                modifier = Modifier.fillMaxWidth(0.55f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Color(0xFF4285F4),
                    Color(0xFFEA4335),
                    Color(0xFFFBBC05),
                    Color(0xFF34A853)
                ).forEach { barColor ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(barColor.copy(alpha = 0.85f))
                    )
                }
            }
        }
    }

    // Clear All History Confirmation Dialog
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
private fun EmptyChatExperience(
    assistantName: String,
    language: SupportedLanguage,
    status: AssistantStatus,
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        JarvisAIOrb(
            status = status,
            audioLevel = 0.08f,
            modifier = Modifier.size(104.dp)
        )

        // Image 8: Gradient Title + Horizontal Laser Flare
        JarvisGradientTitleWithLaserFlare(
            leadingWord = "Ask",
            gradientWord = assistantName,
            subtitle = "Chat in Hinglish, Hindi (हिंदी), or English. Memories & device actions execute automatically."
        )

        // Image 7: Apple Arcade Library style prompt rows with squircle icon + dark "Run" pill button
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            suggestions.forEachIndexed { idx, prompt ->
                val badgeColors = listOf(
                    Color(0xFF7C3AED),
                    Color(0xFF0284C7),
                    Color(0xFF10B981)
                )
                val badgeColor = badgeColors[idx % badgeColors.size]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF141822))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                        .clickable { onQuickPrompt(prompt) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
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
                                .background(badgeColor.copy(alpha = 0.22f))
                                .border(1.dp, badgeColor.copy(alpha = 0.55f), RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF232836))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Run",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JarvisMessageBubble(
    message: ChatMessageEntity,
    assistantName: String,
    onCopy: (String) -> Unit,
    onSaveToMemory: (String) -> Unit,
    onRetry: (() -> Unit)? = null,
    isStreaming: Boolean = false
) {
    val isUser = message.role == "user"
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 24.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    } else {
        RoundedCornerShape(topStart = 6.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    }

    // Image 9 Frosted Violet Glass for User; Image 6 Royal Sapphire Glass for AI Assistant
    val backgroundBrush = when {
        message.isError -> Brush.linearGradient(
            listOf(
                AlertCrimson.copy(alpha = 0.25f),
                Color(0xFF1A0D18)
            )
        )
        isUser -> Brush.linearGradient(
            listOf(
                ShruhhPurpleGlassTop.copy(alpha = 0.88f),
                ShruhhPurpleGlassBottom.copy(alpha = 0.94f)
            )
        )
        else -> Brush.linearGradient(
            listOf(
                SapphireCardTop.copy(alpha = 0.92f),
                SapphireCardBottom.copy(alpha = 0.96f)
            )
        )
    }

    val borderBrush = when {
        message.isError -> Brush.horizontalGradient(listOf(AlertCrimson, AlertCrimson.copy(alpha = 0.4f)))
        isUser -> Brush.horizontalGradient(
            listOf(
                Color.White.copy(alpha = 0.55f),
                Color(0xFFC084FC).copy(alpha = 0.65f)
            )
        )
        else -> Brush.horizontalGradient(
            listOf(
                SapphireBorderCyan.copy(alpha = 0.65f),
                Color(0xFF3B82F6).copy(alpha = 0.35f)
            )
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .clip(bubbleShape)
                .background(backgroundBrush)
                .border(1.2.dp, borderBrush, bubbleShape)
                .padding(15.dp)
        ) {
            Column {
                // Header Row: Avatar + Role Name + Memory Badge + Time/Streaming
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
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isUser) Color.White.copy(alpha = 0.16f)
                                    else SapphireBorderCyan.copy(alpha = 0.20f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isUser) Icons.Default.Person else Icons.Default.Psychology,
                                contentDescription = null,
                                tint = if (isUser) Color(0xFFE9D5FF) else SapphireBorderCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = if (isUser) "You" else assistantName,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isUser) Color(0xFFE9D5FF) else SapphireBorderCyan,
                            fontWeight = FontWeight.Bold
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
                                    .background(JarvisViolet.copy(alpha = 0.25f))
                                    .border(1.dp, JarvisVioletBright.copy(alpha = 0.55f), RoundedCornerShape(50))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = JarvisVioletBright,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${message.memoriesUsedCount} MEM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisVioletBright
                                )
                            }
                        }

                        if (isStreaming) {
                            JarvisAnimatedIndicator(
                                label = "STREAMING",
                                accentColor = SapphireBorderCyan
                            )
                        } else {
                            Text(
                                text = formatChatTimestamp(message.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                RenderMessageContentWithCodeBlocks(
                    content = message.content,
                    onCopy = onCopy
                )

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
                                .background(Color.Black.copy(alpha = 0.28f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onCopy(message.content) },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("copy_msg_${message.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Response",
                                    tint = Color.White.copy(alpha = 0.75f),
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
                                    tint = MonexLimeGreen,
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
                                        tint = AlertCrimson,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
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
        shape = RoundedCornerShape(28.dp),
        containerColor = JarvisElevatedSurface,
        title = {
            Text(
                text = "Save to Memory Center",
                style = MaterialTheme.typography.titleLarge,
                color = JarvisTextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Save this fact permanently to your local Room memory database:",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisTextSecondary
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
                                selectedContainerColor = JarvisViolet.copy(alpha = 0.25f),
                                selectedLabelColor = JarvisNeonCyan
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
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MonexLimeGreen,
                    contentColor = Color(0xFF071204)
                )
            ) {
                Text("Save Memory", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, JarvisGlassBorder)
            ) {
                Text("Cancel", color = JarvisTextSecondary)
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
private fun RenderMessageContentWithCodeBlocks(
    content: String,
    onCopy: (String) -> Unit
) {
    if (!content.contains("```")) {
        Text(
            text = content,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
        return
    }

    val parts = content.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 0) {
                if (part.isNotBlank()) {
                    Text(
                        text = part.trim(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            } else {
                val lines = part.trim().lines()
                val langTag = if (lines.isNotEmpty() && lines.first().matches(Regex("^[a-zA-Z0-9_+#.-]+$"))) {
                    lines.first()
                } else {
                    "code"
                }
                val codeBody = if (langTag != "code") lines.drop(1).joinToString("\n") else part.trim()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF090D16))
                        .border(1.dp, SapphireBorderCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF131A29))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = langTag.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = SapphireBorderCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E283D))
                                    .clickable { onCopy(codeBody) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Code",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Copy Code",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.LightGray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = codeBody,
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
