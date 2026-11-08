package com.jarvisai.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.JarvisElectricBlue
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
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.ConnectionTestResult
import com.jarvisai.assistant.model.GeminiModelOption
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.ui.components.JarvisGlassPanel
import com.jarvisai.assistant.ui.components.JarvisPremiumTopBar
import com.jarvisai.assistant.ui.components.JarvisSecondaryActionButton

@Composable
fun ApiSettingsScreen(
    settings: UserSettings,
    status: AssistantStatus,
    currentApiKey: String,
    maskedApiKey: String,
    isKeystoreBacked: Boolean,
    isTestingConnection: Boolean,
    connectionTestResult: ConnectionTestResult?,
    availableTextModels: List<GeminiModelOption>,
    availableVoiceModels: List<GeminiModelOption>,
    onBack: () -> Unit,
    onSaveOrUpdateKey: (String) -> Unit,
    onDeleteKey: () -> Unit,
    onTestConnection: (String) -> Unit,
    onSyncModelsFromApi: (String) -> Unit,
    onSelectTextModel: (String) -> Unit,
    onSelectVoiceModel: (String) -> Unit,
    onSelectScreenOperatorModel: (String) -> Unit = onSelectTextModel,
    onSelectRouterModel: (String) -> Unit = {}
) {
    var apiKeyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var customScreenModelInput by remember(settings.screenOperatorModel) { mutableStateOf(settings.screenOperatorModel) }
    var customVoiceModelInput by remember(settings.voiceModel) { mutableStateOf(settings.voiceModel) }
    var customRouterModelInput by remember(settings.routerModel) { mutableStateOf(settings.routerModel) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ollamaConfig by com.jarvisai.assistant.service.OllamaLocalLlmBridge.config.collectAsState()
    var ollamaUrlInput by remember(ollamaConfig.endpointUrl) { mutableStateOf(ollamaConfig.endpointUrl) }
    var ollamaModelInput by remember(ollamaConfig.modelName) { mutableStateOf(ollamaConfig.modelName) }
    var isTestingOllama by remember { mutableStateOf(false) }
    var ollamaFeedbackMessage by remember { mutableStateOf<String?>(null) }

    val hasStoredKey = currentApiKey.isNotBlank()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            JarvisPremiumTopBar(
                title = "API & Model Vault",
                subtitle = if (hasStoredKey) "KEYSTORE VAULT ARMED • $maskedApiKey" else "API CREDENTIAL REQUIRED",
                status = status,
                onBackClick = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. IMAGE 9 ("SHRUHH WELCOME BACK") FROSTED VIOLET RIBBED GLASS CREDENTIAL CARD
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // Flowing 3D violet ribbon aura behind card (Image 9 reference!)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF9333EA).copy(alpha = 0.36f),
                                        Color(0xFF4F46E5).copy(alpha = 0.14f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.8f, size.height * 0.25f),
                                    radius = size.width * 0.75f
                                ),
                                radius = size.width * 0.75f
                            )
                        }
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF281646).copy(alpha = 0.92f),
                                    Color(0xFF17102B).copy(alpha = 0.95f),
                                    Color(0xFF23133E).copy(alpha = 0.92f)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(800f, 800f)
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.65f),
                                    Color(0xFFC084FC).copy(alpha = 0.55f),
                                    Color.White.copy(alpha = 0.45f)
                                )
                            ),
                            shape = RoundedCornerShape(30.dp)
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Centered 3D Dual-Pill Emblem + Brand Label (Image 9 "SHRUHH" logo header)
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFFA855F7), Color(0xFF6366F1))
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Text(
                            text = "KEYSTORE VAULT",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFE9D5FF),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = if (hasStoredKey) "Credential Verified, Commander" else "Welcome Back, Commander",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        // Minimalist Dark Glass Input with White-Violet Specular Rim (Image 9 input style!)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Gemini API Key (${if (isKeystoreBacked) "AES-256 Hardware Encrypted" else "Local Vault"})",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFE9D5FF)
                            )

                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                placeholder = {
                                    Text(
                                        text = "AIzaSy...",
                                        color = JarvisTextMuted
                                    )
                                },
                                singleLine = true,
                                visualTransformation = if (isKeyVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { isKeyVisible = !isKeyVisible },
                                        modifier = Modifier.testTag("btn_toggle_key_visibility")
                                    ) {
                                        Icon(
                                            imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isKeyVisible) "Hide API Key" else "Show API Key",
                                            tint = Color(0xFFD8B4FE)
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF0E081C).copy(alpha = 0.85f),
                                    unfocusedContainerColor = Color(0xFF0B0617).copy(alpha = 0.85f),
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.55f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_key_input_field")
                            )
                        }

                        // Image 9 Pastel-Violet Luminous Gradient Button with Crisp White Border ("Login" button style!)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = if (apiKeyInput.trim().isNotEmpty()) {
                                            listOf(
                                                Color(0xFFC084FC),
                                                Color(0xFF9333EA),
                                                Color(0xFFA855F7)
                                            )
                                        } else {
                                            listOf(JarvisGlassSurface, JarvisGlassSurface)
                                        }
                                    )
                                )
                                .border(
                                    width = 1.2.dp,
                                    color = Color.White.copy(alpha = if (apiKeyInput.trim().isNotEmpty()) 0.85f else 0.2f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable(enabled = apiKeyInput.trim().isNotEmpty()) {
                                    onSaveOrUpdateKey(apiKeyInput)
                                }
                                .testTag("btn_save_api_key"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (hasStoredKey) "Update API Key" else "Save API Key",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        // Secondary Actions Row: Test Connection | Sync Models | Delete
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            JarvisSecondaryActionButton(
                                text = if (isTestingConnection) "Testing..." else "Test API",
                                icon = Icons.Default.NetworkCheck,
                                enabled = !isTestingConnection && (apiKeyInput.isNotBlank() || hasStoredKey),
                                accentColor = JarvisNeonCyan,
                                onClick = { onTestConnection(apiKeyInput) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_test_connection")
                            )

                            JarvisSecondaryActionButton(
                                text = "Sync Models",
                                icon = Icons.Default.CloudSync,
                                enabled = !isTestingConnection && (apiKeyInput.isNotBlank() || hasStoredKey),
                                accentColor = Color(0xFFD8B4FE),
                                onClick = { onSyncModelsFromApi(apiKeyInput) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_sync_list_models")
                            )

                            if (hasStoredKey) {
                                JarvisSecondaryActionButton(
                                    text = "Delete",
                                    icon = Icons.Default.Delete,
                                    accentColor = AlertCrimson,
                                    onClick = {
                                        apiKeyInput = ""
                                        onDeleteKey()
                                    },
                                    modifier = Modifier.testTag("btn_delete_api_key")
                                )
                            }
                        }

                        if (isTestingConnection) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = JarvisNeonCyan
                                )
                                Text(
                                    text = "Verifying Gemini handshake...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisNeonCyan
                                )
                            }
                        }
                    }
                }
            }

            // 2. IMAGE 6 ("SYSTEM UPDATE") ROYAL SAPPHIRE DIAGNOSTIC RESULT CARD
            if (connectionTestResult != null) {
                item {
                    val isOk = connectionTestResult.success
                    val accent = if (isOk) JarvisNeonCyan else AlertCrimson
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF0A193B),
                                        Color(0xFF050E24)
                                    )
                                )
                            )
                            .border(1.5.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(28.dp))
                            .padding(20.dp)
                            .testTag("connection_test_result_box")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Model: ${connectionTestResult.modelTested}",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (isOk) "Handshake Verified" else "Connection Error",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = connectionTestResult.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextSecondary,
                                textAlign = TextAlign.Center
                            )

                            // Image 6 Thick Pill Gradient Progress Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xFF040917))
                                    .border(1.dp, Color(0xFF253B6E), RoundedCornerShape(999.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(if (isOk) 1f else 0.25f)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                if (isOk) listOf(JarvisNeonCyan, Color(0xFF3B82F6), Color(0xFF6366F1))
                                                else listOf(AlertCrimson, StatusThinkingAmber)
                                            )
                                        )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Latency",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                                Text(
                                    text = "${connectionTestResult.latencyMs} ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 3. DECK 1: SCREEN OPERATOR & AUTONOMOUS AUTOMATION MODEL SLOT
            item {
                JarvisGlassPanel(
                    accentColor = JarvisViolet,
                    cornerRadius = 26.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = JarvisVioletBright,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Screen Operator & Automation Slot",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Powers Accessibility CUA Agent & Multi-step Screen Operator",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextSecondary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customScreenModelInput,
                                onValueChange = { customScreenModelInput = it },
                                label = { Text("Active / Custom Screen Operator Model ID") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("custom_screen_model_input")
                            )
                            Button(
                                onClick = { onSelectScreenOperatorModel(customScreenModelInput.trim()) },
                                enabled = customScreenModelInput.isNotBlank(),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF060912)
                                )
                            ) {
                                Text("Apply", fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        com.jarvisai.assistant.model.SupportedGeminiModels.screenOperatorModels.forEach { model ->
                            ModelSelectionCard(
                                model = model,
                                isSelected = settings.screenOperatorModel == model.id,
                                accentColor = JarvisVioletBright,
                                onSelect = {
                                    customScreenModelInput = model.id
                                    onSelectScreenOperatorModel(model.id)
                                },
                                testTag = "screen_model_${model.id}"
                            )
                        }
                    }
                }
            }

            // 4. DECK 2: GEMINI LIVE VOICE ASSISTANT WEBSOCKET SLOT
            item {
                JarvisGlassPanel(
                    accentColor = JarvisNeonCyan,
                    cornerRadius = 26.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = JarvisNeonCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Gemini Live Voice WebSocket Slot",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Powers Bidirectional Audio Streaming & Native Voice",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextSecondary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customVoiceModelInput,
                                onValueChange = { customVoiceModelInput = it },
                                label = { Text("Active / Custom Live Voice Model ID") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("custom_voice_model_input")
                            )
                            Button(
                                onClick = { onSelectVoiceModel(customVoiceModelInput.trim()) },
                                enabled = customVoiceModelInput.isNotBlank(),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = JarvisNeonGreen,
                                    contentColor = Color(0xFF060912)
                                )
                            ) {
                                Text("Apply", fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        com.jarvisai.assistant.model.SupportedGeminiModels.liveVoiceModels.forEach { model ->
                            ModelSelectionCard(
                                model = model,
                                isSelected = settings.voiceModel == model.id,
                                accentColor = JarvisNeonCyan,
                                onSelect = {
                                    customVoiceModelInput = model.id
                                    onSelectVoiceModel(model.id)
                                },
                                testTag = "voice_model_${model.id}"
                            )
                        }
                    }
                }
            }

            // 5. DECK 3: INSTANT INTENT ROUTER & FAST MEMORY SLOT
            item {
                JarvisGlassPanel(
                    accentColor = Color(0xFFF59E0B),
                    cornerRadius = 26.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Instant Intent Router & Fast Memory Slot",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Powers Sub-100ms Utterance Classification & Working Memory",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextSecondary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customRouterModelInput,
                                onValueChange = { customRouterModelInput = it },
                                label = { Text("Active / Custom Intent Router Model ID") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("custom_router_model_input")
                            )
                            Button(
                                onClick = { onSelectRouterModel(customRouterModelInput.trim()) },
                                enabled = customRouterModelInput.isNotBlank(),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color(0xFF060912)
                                )
                            ) {
                                Text("Apply", fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        com.jarvisai.assistant.model.SupportedGeminiModels.intentRouterModels.forEach { model ->
                            ModelSelectionCard(
                                model = model,
                                isSelected = settings.routerModel == model.id,
                                accentColor = Color(0xFFF59E0B),
                                onSelect = {
                                    customRouterModelInput = model.id
                                    onSelectRouterModel(model.id)
                                },
                                testTag = "router_model_${model.id}"
                            )
                        }
                    }
                }
            }

            // 5. NATIVE LOCAL OLLAMA & MULTI-PROVIDER BRIDGE (AbuZar PrivateAgent Architecture)
            item {
                JarvisGlassPanel(
                    accentColor = Color(0xFF10B981),
                    cornerRadius = 26.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Native Local Ollama & Multi-Provider Bridge",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (ollamaConfig.isOnline) "🟢 Ollama Daemon Online (${ollamaConfig.endpointUrl})" else "⚪ Local LLM Standby / Offline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (ollamaConfig.isOnline) Color(0xFF10B981) else JarvisTextSecondary
                                )
                            }
                        }

                        Text(
                            text = "Connect JARVIS to an on-device Termux Ollama server, local Wi-Fi PC, or custom OpenAI-compatible endpoint without cloud dependency.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextSecondary
                        )

                        OutlinedTextField(
                            value = ollamaUrlInput,
                            onValueChange = { ollamaUrlInput = it },
                            label = { Text("Ollama Daemon URL") },
                            placeholder = { Text("http://10.0.2.2:11434 or http://127.0.0.1:11434") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_ollama_endpoint")
                        )

                        OutlinedTextField(
                            value = ollamaModelInput,
                            onValueChange = { ollamaModelInput = it },
                            label = { Text("Ollama Model Tag") },
                            placeholder = { Text("llama3.2, qwen2.5, deepseek-r1") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_ollama_model")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    isTestingOllama = true
                                    scope.launch {
                                        com.jarvisai.assistant.service.OllamaLocalLlmBridge.saveConfig(
                                            context = context,
                                            endpointUrl = ollamaUrlInput.trim(),
                                            modelName = ollamaModelInput.trim()
                                        )
                                        val testRes = com.jarvisai.assistant.service.OllamaLocalLlmBridge.probeOllamaServer(context)
                                        isTestingOllama = false
                                        ollamaFeedbackMessage = if (testRes.isOnline) {
                                            "Handshake Successful! Daemon Online • Discovered Models: ${testRes.discoveredModels.joinToString(", ")}"
                                        } else {
                                            "Connection Failed: Could not reach Ollama at ${testRes.endpointUrl}. Ensure Termux or Ollama daemon is running."
                                        }
                                    }
                                },
                                enabled = !isTestingOllama,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E293B),
                                    contentColor = Color(0xFF10B981)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_test_ollama_handshake")
                            ) {
                                Text(
                                    text = if (isTestingOllama) "Connecting..." else "Test Handshake",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    com.jarvisai.assistant.service.OllamaLocalLlmBridge.saveConfig(
                                        context = context,
                                        endpointUrl = ollamaUrlInput.trim(),
                                        modelName = ollamaModelInput.trim()
                                    )
                                    ollamaFeedbackMessage = "Config saved! Local Ollama bridge armed for ${ollamaModelInput.trim()}."
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color(0xFF04101E)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_save_ollama_config")
                            ) {
                                Text(
                                    text = "Save Config",
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        if (!ollamaFeedbackMessage.isNullOrBlank()) {
                            Text(
                                text = ollamaFeedbackMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ollamaFeedbackMessage?.startsWith("Handshake") == true) Color(0xFF10B981) else Color(0xFFF59E0B),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSelectionCard(
    model: GeminiModelOption,
    isSelected: Boolean,
    accentColor: Color,
    onSelect: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isSelected) accentColor.copy(alpha = 0.16f)
                else Color(0xFF141827)
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onSelect)
            .padding(14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = accentColor,
                unselectedColor = JarvisTextMuted
            )
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                val badgeColor = if (model.pricing.isFreeTier) Color(0xFF10B981) else Color(0xFFF59E0B)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.18f))
                        .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = model.pricing.tierBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                if (model.supportsBidiWebSocket) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(JarvisNeonGreen.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "BIDI LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisNeonGreen,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
            Text(
                text = model.id,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Text(
                text = model.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = JarvisTextSecondary,
                fontSize = 11.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "• ${model.pricing.rateLimitSummary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (model.pricing.isFreeTier) Color(0xFF34D399) else Color(0xFFFBBF24),
                    fontSize = 10.sp
                )
            }
        }
    }
}
