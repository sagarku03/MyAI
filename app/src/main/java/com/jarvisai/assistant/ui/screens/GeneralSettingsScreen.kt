package com.jarvisai.assistant.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentChampagneGold
import com.example.ui.theme.AccentMintEmerald
import com.example.ui.theme.AccentRosePink
import com.example.ui.theme.AccentSkyCyan
import com.example.ui.theme.DeepAmethystPressed
import com.example.ui.theme.ElectricOrchid
import com.example.ui.theme.LiquidGlassElevated
import com.example.ui.theme.LiquidGlassPill
import com.example.ui.theme.LiquidGlassSurface
import com.example.ui.theme.PaleLavenderIce
import com.example.ui.theme.RoyalIndigoAccent
import com.example.ui.theme.RoyalPurplePrimary
import com.example.ui.theme.SoftLavenderGlow
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusRoseRed
import com.example.ui.theme.TextLavenderMuted
import com.example.ui.theme.TextPureWhite
import com.example.ui.theme.TextVioletDim
import com.example.ui.theme.JarvisThemeMode
import com.example.ui.theme.ThemeDisplayMode
import com.example.ui.theme.ThemePalette
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.VelvetRoseAccent
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.CompanionEmotionMood
import com.jarvisai.assistant.model.CompanionModeState
import com.jarvisai.assistant.model.ConnectionTestResult
import com.jarvisai.assistant.model.GeminiModelOption
import com.jarvisai.assistant.model.PersonalityMode
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.model.VoiceGender
import com.jarvisai.assistant.ui.components.JarvisGlassPanel
import com.jarvisai.assistant.ui.components.JarvisModernDialog
import com.jarvisai.assistant.ui.components.JarvisPrimaryActionButton
import com.jarvisai.assistant.ui.components.JarvisSecondaryActionButton
import com.jarvisai.assistant.ui.components.LiquidGlassBottomSheet
import com.jarvisai.assistant.ui.components.LiquidGlassGroupCard
import com.jarvisai.assistant.ui.components.LiquidGlassSegmentedBar
import java.util.Locale

private enum class SettingsDetailSheet {
    IDENTITY_AND_LANGUAGE,
    API_KEYS_VAULT,
    AI_MODELS_PICKER,
    VOICE_STUDIO,
    WAKE_WORD_TUNING,
    PERSONA_AND_18_PLUS,
    PROACTIVE_AND_MOOD,
    AUTONOMOUS_AGENT_CONFIG,
    Appearance_THEME
}

/**
 * Apple iOS 26 "Liquid Glass" Unified Settings Hub.
 * Built strictly according to the approved Master Plan diagram (Part 6, Section 5):
 * - Top Header with "SETTINGS" ExtraBold + [Save Changes] Pill
 * - Hero Card: "JARVIS AI PRO CORE • All Systems Go" with Purple, Cyan & Rose status chips
 * - Frosted iOS Search Bar + Quick Category Filter Pills
 * - 6 Clean Apple iOS Grouped Cards (`LiquidGlassGroupCard`) with Multi-Color Squircle Icon Badges:
 *   1. AI ENGINE & API KEYS (Amber Gold, Sky Cyan, Royal Purple)
 *   2. VOICE, AUDIO & WAKE WORD (Sky Cyan, Mint Emerald, Electric Indigo)
 *   3. PERSONALITY & COMPANION MODES (Rose Pink, Velvet Coral, Royal Purple)
 *   4. AUTONOMOUS AGENT & SYSTEM PERMISSIONS (Royal Purple, Sky Cyan, Amber Gold)
 *   5. SECURITY, BIOMETRICS & PRIVACY VAULT (Mint Emerald, Royal Purple, Sky Cyan)
 *   6. MEMORY, BACKUP & DATA (Electric Indigo, Champagne Gold, Rose Red)
 * - Tapping any row (`›`) opens its dedicated Apple iOS Frosted Liquid Glass Bottom Sheet!
 */
@Composable
fun GeneralSettingsScreen(
    settings: UserSettings,
    status: AssistantStatus,
    sessionsCount: Int,
    memoriesCount: Int,
    isTtsSpeaking: Boolean = false,
    ttsErrorMessage: String? = null,
    onBack: () -> Unit,
    onUpdateAssistantName: (String) -> Unit,
    onUpdateLanguage: (SupportedLanguage) -> Unit,
    onUpdateVoiceGender: (VoiceGender) -> Unit,
    onUpdateVoicePersona: (String) -> Unit,
    onPreviewVoice: (VoiceGender, String) -> Unit = { _, _ -> },
    onStopVoicePreview: () -> Unit = {},
    onUpdateVoiceSpeed: (Float) -> Unit,
    onUpdateVoicePitch: (Float) -> Unit,
    onUpdateAutoSpeakChat: (Boolean) -> Unit,
    onUpdateFloatingOverlay: (Boolean) -> Unit,
    onUpdateThemeMode: (JarvisThemeMode) -> Unit,
    onUpdateThemePalette: (ThemePalette) -> Unit = {},
    onUpdateThemeDisplayMode: (ThemeDisplayMode) -> Unit = {},
    onUpdateMemoryEnabled: (Boolean) -> Unit,
    onUpdateAutoInjectMemories: (Boolean) -> Unit,
    onUpdateConfirmConsequential: (Boolean) -> Unit,
    onUpdatePrivacyRedact: (Boolean) -> Unit,
    onClearChatHistory: () -> Unit,
    onClearMemories: () -> Unit,
    onUpdatePersonalityMode: (PersonalityMode) -> Unit = {},
    onActivateFemaleVoiceCompanion: () -> Unit = {},
    onDeactivateFemaleVoiceCompanion: () -> Unit = {},
    companionModeState: CompanionModeState = CompanionModeState.INACTIVE,
    companionModeStatusMessage: String? = null,
    onUpdateWakeWordEnabled: (Boolean) -> Unit = {},
    wakeWordState: com.jarvisai.assistant.service.WakeWordState = if (settings.wakeWordEnabled) com.jarvisai.assistant.service.WakeWordState.LISTENING else com.jarvisai.assistant.service.WakeWordState.DISABLED,
    wakeWordErrorMessage: String? = null,
    onUpdateRequireBiometric: (Boolean) -> Unit = {},
    onUpdateMaxAutonomousSteps: (Int) -> Unit = {},
    companionEmotionMood: CompanionEmotionMood = CompanionEmotionMood.COMPOSED,
    onUpdateCustomPersonalityPrompt: (String) -> Unit = {},
    onUpdateProactiveCompanionEnabled: (Boolean) -> Unit = {},
    onUpdateAmbientRoomTalkEnabled: (Boolean) -> Unit = {},
    onTriggerProactiveCompanionNow: () -> Unit = {},
    // Unified API & Model Vault Parameters
    currentApiKey: String = "",
    maskedApiKey: String = "",
    isKeystoreBacked: Boolean = true,
    isTestingConnection: Boolean = false,
    connectionTestResult: ConnectionTestResult? = null,
    availableTextModels: List<GeminiModelOption> = emptyList(),
    availableVoiceModels: List<GeminiModelOption> = emptyList(),
    onSaveOrUpdateApiKey: (String) -> Unit = {},
    onDeleteApiKey: () -> Unit = {},
    onTestApiConnection: (String) -> Unit = {},
    onSyncModelsFromApi: (String) -> Unit = {},
    onSelectTextModel: (String) -> Unit = {},
    onSelectVoiceModel: (String) -> Unit = {},
    onOpenFullApiVault: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToMemoryReview: () -> Unit = {},
    onNavigateToControls: () -> Unit = {},
    onNavigateToWorkflow: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var activeSheet by rememberSaveable { mutableStateOf<SettingsDetailSheet?>(null) }

    var nameInput by remember(settings.assistantName) { mutableStateOf(settings.assistantName) }
    var customPromptInput by remember(settings.customPersonalityPrompt) {
        mutableStateOf(settings.customPersonalityPrompt)
    }
    var speedSlider by remember(settings.voiceSpeed) { mutableFloatStateOf(settings.voiceSpeed) }
    var pitchSlider by remember(settings.voicePitch) { mutableFloatStateOf(settings.voicePitch) }
    var autonomousStepsSlider by remember(settings.maxAutonomousSteps) {
        mutableFloatStateOf(settings.maxAutonomousSteps.toFloat())
    }

    var apiKeyInput by remember(currentApiKey) { mutableStateOf("") }
    var showRawApiKey by remember { mutableStateOf(false) }

    var showClearChatDialog by remember { mutableStateOf(false) }
    var showClearMemoriesDialog by remember { mutableStateOf(false) }
    var pendingAdultModeSelection by remember { mutableStateOf<PersonalityMode?>(null) }
    var savedBannerMessage by remember { mutableStateOf<String?>(null) }

    val hasConfiguredKey = currentApiKey.isNotBlank()
    val q = searchQuery.trim().lowercase(Locale.ROOT)
    fun matches(keywords: String): Boolean = q.isEmpty() || keywords.lowercase(Locale.ROOT).contains(q)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("general_settings_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ====================================================================
        // TOP HEADER: "⚙️ SETTINGS" + [Save Changes] Pill Button
        // ====================================================================
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xD811162B))
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                RoyalPurplePrimary.copy(alpha = 0.65f),
                                AccentSkyCyan.copy(alpha = 0.55f),
                                AccentRosePink.copy(alpha = 0.55f)
                            )
                        ),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(RoyalPurplePrimary, RoyalIndigoAccent.copy(alpha = 0.85f))
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(13.dp))
                            .clickable(onClick = onBack)
                            .testTag("top_bar_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Settings Hub",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPureWhite,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Apple iOS 26 Control Studio",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentSkyCyan,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // [Save Changes] Vibrant Multi-Color Pill Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(RoyalPurplePrimary, AccentRosePink, AccentChampagneGold)
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(999.dp))
                        .clickable {
                            if (nameInput.isNotBlank()) onUpdateAssistantName(nameInput.trim())
                            onUpdateCustomPersonalityPrompt(customPromptInput)
                            savedBannerMessage = "All settings saved & synced!"
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                        .testTag("btn_save_all_settings")
                ) {
                    Text(
                        text = "Save Changes",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Dynamic Island Saved Toast Banner
        if (savedBannerMessage != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(AccentMintEmerald.copy(alpha = 0.22f))
                        .border(1.dp, AccentMintEmerald.copy(alpha = 0.70f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AccentMintEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = savedBannerMessage ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPureWhite,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = "Dismiss",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentMintEmerald,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clickable { savedBannerMessage = null }
                    )
                }
            }
        }

        // ====================================================================
        // HERO STATUS CARD: Apple ID Style Multi-Color Profile & Core Card
        // ====================================================================
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                RoyalPurplePrimary.copy(alpha = 0.36f),
                                RoyalIndigoAccent.copy(alpha = 0.28f),
                                AccentRosePink.copy(alpha = 0.24f),
                                Color(0xEE11162B)
                            )
                        )
                    )
                    .border(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                SoftLavenderGlow.copy(alpha = 0.85f),
                                AccentSkyCyan.copy(alpha = 0.65f),
                                AccentRosePink.copy(alpha = 0.65f)
                            )
                        ),
                        RoundedCornerShape(26.dp)
                    )
                    .clickable { activeSheet = SettingsDetailSheet.IDENTITY_AND_LANGUAGE }
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(RoyalPurplePrimary, AccentSkyCyan, AccentRosePink)
                                        )
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "${settings.assistantName.uppercase()} AI PRO CORE",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPureWhite,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Identity (${settings.assistantName}) • ${settings.language.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // All Systems Go Solid Pill
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (hasConfiguredKey) AccentMintEmerald.copy(alpha = 0.24f)
                                    else StatusAmber.copy(alpha = 0.24f)
                                )
                                .border(
                                    1.dp,
                                    if (hasConfiguredKey) AccentMintEmerald else StatusAmber,
                                    RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (hasConfiguredKey) AccentMintEmerald else StatusAmber)
                            )
                            Text(
                                text = if (hasConfiguredKey) "All Systems Go" else "Key Needed",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPureWhite,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Multi-Color Status Chips Row (Sky Cyan Model + Rose Pink Voice + Royal Purple Mode + Gold Theme)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HeroSummaryChip(
                            label = "Model: ${settings.textModel.substringAfterLast("/")}",
                            accentColor = AccentSkyCyan,
                            onClick = { activeSheet = SettingsDetailSheet.AI_MODELS_PICKER }
                        )
                        HeroSummaryChip(
                            label = "Voice: ${settings.voicePersona}",
                            accentColor = AccentRosePink,
                            onClick = { activeSheet = SettingsDetailSheet.VOICE_STUDIO }
                        )
                        HeroSummaryChip(
                            label = "Mode: ${settings.personalityMode.title}",
                            accentColor = RoyalPurplePrimary,
                            onClick = { activeSheet = SettingsDetailSheet.PERSONA_AND_18_PLUS }
                        )
                        HeroSummaryChip(
                            label = "Theme: ${settings.themeMode.displayName}",
                            accentColor = AccentChampagneGold,
                            onClick = { activeSheet = SettingsDetailSheet.Appearance_THEME }
                        )
                    }
                }
            }
        }

        // ====================================================================
        // FROSTED iOS SEARCH BAR
        // ====================================================================
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search any setting: API Key, Voice, 18+, Wake Word...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextVioletDim,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(999.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = AccentSkyCyan
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = TextLavenderMuted
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPureWhite,
                    unfocusedTextColor = TextPureWhite,
                    focusedContainerColor = LiquidGlassElevated,
                    unfocusedContainerColor = LiquidGlassSurface,
                    focusedBorderColor = RoyalPurplePrimary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                    cursorColor = AccentSkyCyan
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_search_bar")
            )
        }

        // ====================================================================
        // 🎨 APPEARANCE & THEMES ENGINE (Ocean Blue, Royal Purple, Aqua Mint)
        // ====================================================================
        if (matches("theme appearance color dark light ocean purple mint display mode")) {
            item {
                LiquidGlassGroupCard(
                    sectionHeader = "🎨 APPEARANCE & THEMES ENGINE",
                    accentColor = JarvisTheme.colors.accent
                ) {
                    val activePalette = settings.themePalette
                    val activeDisplayMode = settings.themeDisplayMode
                    val currentColors = JarvisTheme.colors

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Live Interactive Theme Preview Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(currentColors.background)
                                .border(1.2.dp, currentColors.border, RoundedCornerShape(20.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(currentColors.accent)
                                        )
                                        Text(
                                            text = "LIVE PREVIEW: ${activePalette.displayName.uppercase()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = currentColors.textPrimary,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(currentColors.accent.copy(alpha = 0.20f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = activeDisplayMode.displayName.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = currentColors.accent,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.sp
                                        )
                                    }
                                }

                                // Mock User Bubble
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(currentColors.accent, currentColors.primary)
                                                )
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "Hey JARVIS, check system status",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Mock JARVIS Bubble
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                                            .background(currentColors.surface)
                                            .border(1.dp, currentColors.border, RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "All systems operational, sir. Theme synchronized.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = currentColors.textPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Curated Reference Palette Preset Pills
                        Text(
                            text = "COLOR PALETTE PRESET",
                            style = MaterialTheme.typography.labelSmall,
                            color = currentColors.textMuted,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemePalette.entries.forEach { palette ->
                                val selected = activePalette == palette
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (selected) palette.previewColor.copy(alpha = 0.22f)
                                            else currentColors.surface.copy(alpha = 0.60f)
                                        )
                                        .border(
                                            width = if (selected) 1.6.dp else 1.dp,
                                            color = if (selected) palette.previewColor else currentColors.border,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable {
                                            onUpdateThemePalette(palette)
                                            val resolved = when (activeDisplayMode) {
                                                ThemeDisplayMode.SYSTEM -> JarvisThemeMode.SYSTEM_DEFAULT
                                                ThemeDisplayMode.LIGHT -> when (palette) {
                                                    ThemePalette.OCEAN_BLUE -> JarvisThemeMode.OCEAN_BLUE_LIGHT
                                                    ThemePalette.ROYAL_PURPLE -> JarvisThemeMode.ROYAL_PURPLE_LIGHT
                                                    ThemePalette.AQUA_MINT -> JarvisThemeMode.AQUA_MINT_LIGHT
                                                }
                                                ThemeDisplayMode.DARK -> when (palette) {
                                                    ThemePalette.OCEAN_BLUE -> JarvisThemeMode.OCEAN_BLUE_DARK
                                                    ThemePalette.ROYAL_PURPLE -> JarvisThemeMode.ROYAL_PURPLE_DARK
                                                    ThemePalette.AQUA_MINT -> JarvisThemeMode.AQUA_MINT_DARK
                                                }
                                            }
                                            onUpdateThemeMode(resolved)
                                        }
                                        .padding(vertical = 10.dp, horizontal = 6.dp)
                                        .testTag("palette_preset_${palette.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(palette.previewColor)
                                        )
                                        Text(
                                            text = palette.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) currentColors.textPrimary else currentColors.textSecondary,
                                            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Display Mode Segmented Selector (Light, Dark, System Default)
                        Text(
                            text = "DISPLAY MODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = currentColors.textMuted,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(currentColors.surface.copy(alpha = 0.80f))
                                .border(1.dp, currentColors.border, RoundedCornerShape(16.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ThemeDisplayMode.entries.forEach { mode ->
                                val selected = activeDisplayMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) currentColors.accent
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            onUpdateThemeDisplayMode(mode)
                                            val resolved = when (mode) {
                                                ThemeDisplayMode.SYSTEM -> JarvisThemeMode.SYSTEM_DEFAULT
                                                ThemeDisplayMode.LIGHT -> when (activePalette) {
                                                    ThemePalette.OCEAN_BLUE -> JarvisThemeMode.OCEAN_BLUE_LIGHT
                                                    ThemePalette.ROYAL_PURPLE -> JarvisThemeMode.ROYAL_PURPLE_LIGHT
                                                    ThemePalette.AQUA_MINT -> JarvisThemeMode.AQUA_MINT_LIGHT
                                                }
                                                ThemeDisplayMode.DARK -> when (activePalette) {
                                                    ThemePalette.OCEAN_BLUE -> JarvisThemeMode.OCEAN_BLUE_DARK
                                                    ThemePalette.ROYAL_PURPLE -> JarvisThemeMode.ROYAL_PURPLE_DARK
                                                    ThemePalette.AQUA_MINT -> JarvisThemeMode.AQUA_MINT_DARK
                                                }
                                            }
                                            onUpdateThemeMode(resolved)
                                        }
                                        .padding(vertical = 8.dp)
                                        .testTag("display_mode_${mode.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) Color.White else currentColors.textSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ====================================================================
        // 🔑 SECTION 1: AI ENGINE & API KEYS (Report Section 1)
        // ====================================================================
        if (matches("api key gemini model flash pro cloud endpoint sync connection keystore")) {
            item {
                LiquidGlassGroupCard(
                    sectionHeader = "🔑 SECTION 1: AI ENGINE & API KEYS",
                    accentColor = AccentChampagneGold
                ) {
                    // Row 1: Gemini API Keys & Keystore Vault
                    IosGroupedSettingRow(
                        icon = Icons.Default.Key,
                        iconBadgeColor = AccentChampagneGold,
                        title = "Gemini API Keys & Keystore Vault",
                        subtitle = if (hasConfiguredKey) "AES-256 Encrypted: $maskedApiKey" else "No API key saved — Tap to add key",
                        valueBadge = if (hasConfiguredKey) "Active" else "Setup",
                        valueBadgeColor = if (hasConfiguredKey) AccentMintEmerald else StatusAmber,
                        onClick = { activeSheet = SettingsDetailSheet.API_KEYS_VAULT },
                        modifier = Modifier.testTag("row_api_keys_vault")
                    )

                    IosGroupDivider()

                    // Row 2: Default Chat, Live Voice & Vision Models
                    IosGroupedSettingRow(
                        icon = Icons.Default.Psychology,
                        iconBadgeColor = AccentSkyCyan,
                        title = "Default Chat, Live Voice & Vision Models",
                        subtitle = "Chat: ${settings.textModel} • Voice: ${settings.voiceModel.substringAfterLast("/")}",
                        valueBadge = settings.textModel.substringAfterLast("/"),
                        valueBadgeColor = AccentSkyCyan,
                        onClick = { activeSheet = SettingsDetailSheet.AI_MODELS_PICKER },
                        modifier = Modifier.testTag("row_ai_models_picker")
                    )

                    IosGroupDivider()

                    // Row 3: Full API Diagnostic Screen & Live Model Sync
                    IosGroupedSettingRow(
                        icon = Icons.Default.CloudSync,
                        iconBadgeColor = RoyalPurplePrimary,
                        title = "Cloud Connection Test & Full API Screen",
                        subtitle = connectionTestResult?.message ?: "Verify Gemini API latency & sync models",
                        valueBadge = if (isTestingConnection) "Testing..." else "Open",
                        valueBadgeColor = RoyalPurplePrimary,
                        onClick = onOpenFullApiVault,
                        modifier = Modifier.testTag("btn_open_full_api_vault")
                    )
                }
            }
        }

        // ====================================================================
        // 🗣️ SECTION 2: VOICE, AUDIO & WAKE WORD (Report Section 2)
        // ====================================================================
        if (matches("voice audio wake word hey jarvis speed pitch aoede charon female male duplex speak")) {
            item {
                LiquidGlassGroupCard(
                    sectionHeader = "🗣️ SECTION 2: VOICE, AUDIO & WAKE WORD",
                    accentColor = AccentSkyCyan
                ) {
                    // Row 1: AI Voice Studio (Voice Name, Speed, Pitch)
                    IosGroupedSettingRow(
                        icon = Icons.Default.RecordVoiceOver,
                        iconBadgeColor = AccentSkyCyan,
                        title = "AI Voice Studio (Voice Name, Speed, Pitch)",
                        subtitle = "${settings.voiceGender.displayName} • ${String.format(Locale.US, "%.2fx", settings.voiceSpeed)} Speed • ${String.format(Locale.US, "%.2fx", settings.voicePitch)} Pitch",
                        valueBadge = settings.voicePersona,
                        valueBadgeColor = AccentSkyCyan,
                        onClick = { activeSheet = SettingsDetailSheet.VOICE_STUDIO },
                        modifier = Modifier.testTag("row_voice_studio")
                    )

                    IosGroupDivider()

                    // Row 2: "Hey Jarvis" Wake Word & Sensitivity
                    IosGroupedToggleRow(
                        icon = Icons.Default.GraphicEq,
                        iconBadgeColor = AccentMintEmerald,
                        title = "\"Hey Jarvis\" Wake Word & Sensitivity",
                        subtitle = "State: ${wakeWordState.name} • Works hands-free offline",
                        checked = settings.wakeWordEnabled,
                        switchAccent = AccentMintEmerald,
                        onCheckedChange = onUpdateWakeWordEnabled,
                        onRowClick = { activeSheet = SettingsDetailSheet.WAKE_WORD_TUNING },
                        switchTestTag = "switch_wake_word"
                    )

                    IosGroupDivider()

                    // Row 3: Audio Duplex, Echo Cancellation & Auto-Speak Chat
                    IosGroupedToggleRow(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        iconBadgeColor = RoyalIndigoAccent,
                        title = "Audio Duplex, Barge-In & Auto-Speak Chat",
                        subtitle = "Automatically speak AI replies & allow instant voice interrupt",
                        checked = settings.autoSpeakChatReplies,
                        switchAccent = RoyalPurplePrimary,
                        onCheckedChange = onUpdateAutoSpeakChat,
                        switchTestTag = "switch_auto_speak"
                    )

                    IosGroupDivider()

                    // Row 4: Dedicated Assistant Voice Gender Selector (Google Gemini Human Voices: Puck / Aoede)
                    val isMaleVoiceSelected = settings.preferredTtsVoiceGender == VoiceGender.MALE
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("row_assistant_voice_gender_selector"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Assistant Voice Gender",
                                    color = TextPureWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Choose Male (Google Puck / Charon) or Female (Google Aoede / Kore)",
                                    color = TextLavenderMuted,
                                    fontSize = 12.sp
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = (if (isMaleVoiceSelected) AccentSkyCyan else AccentRosePink).copy(alpha = 0.16f),
                                border = BorderStroke(
                                    1.dp,
                                    (if (isMaleVoiceSelected) AccentSkyCyan else AccentRosePink).copy(alpha = 0.45f)
                                )
                            ) {
                                Text(
                                    text = if (isMaleVoiceSelected) "Google Puck (Male)" else "Google Aoede (Female)",
                                    color = if (isMaleVoiceSelected) AccentSkyCyan else AccentRosePink,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Option 1: 👨 Male (Google Puck)
                            Surface(
                                onClick = {
                                    onUpdateVoiceGender(VoiceGender.MALE)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("voice_gender_male_option"),
                                shape = RoundedCornerShape(14.dp),
                                color = if (isMaleVoiceSelected) {
                                    AccentSkyCyan.copy(alpha = 0.22f)
                                } else {
                                    Color.White.copy(alpha = 0.05f)
                                },
                                border = BorderStroke(
                                    width = if (isMaleVoiceSelected) 1.5.dp else 1.dp,
                                    color = if (isMaleVoiceSelected) AccentSkyCyan else Color.White.copy(alpha = 0.14f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    RadioButton(
                                        selected = isMaleVoiceSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = AccentSkyCyan,
                                            unselectedColor = TextLavenderMuted
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "👨 Male (Puck)",
                                        color = if (isMaleVoiceSelected) TextPureWhite else TextLavenderMuted,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isMaleVoiceSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Option 2: 👩 Female (Google Aoede)
                            Surface(
                                onClick = {
                                    onUpdateVoiceGender(VoiceGender.FEMALE)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("voice_gender_female_option"),
                                shape = RoundedCornerShape(14.dp),
                                color = if (!isMaleVoiceSelected) {
                                    AccentRosePink.copy(alpha = 0.22f)
                                } else {
                                    Color.White.copy(alpha = 0.05f)
                                },
                                border = BorderStroke(
                                    width = if (!isMaleVoiceSelected) 1.5.dp else 1.dp,
                                    color = if (!isMaleVoiceSelected) AccentRosePink else Color.White.copy(alpha = 0.14f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    RadioButton(
                                        selected = !isMaleVoiceSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = AccentRosePink,
                                            unselectedColor = TextLavenderMuted
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "👩 Female (Aoede)",
                                        color = if (!isMaleVoiceSelected) TextPureWhite else TextLavenderMuted,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (!isMaleVoiceSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ====================================================================
        // 💕 SECTION 3: PERSONALITY & COMPANION MODES (Report Section 3)
        // ====================================================================
        if (matches("personality companion romantic 18+ adult intimate girlfriend mood proactive emotion custom prompt")) {
            item {
                LiquidGlassGroupCard(
                    sectionHeader = "💕 SECTION 3: PERSONALITY & COMPANION MODES",
                    accentColor = AccentRosePink
                ) {
                    // Row 1: 1-Tap Female Voice Companion Mode
                    val isCompanionActive = companionModeState == CompanionModeState.ACTIVE
                    IosGroupedSettingRow(
                        icon = Icons.Default.Favorite,
                        iconBadgeColor = AccentRosePink,
                        title = "Female Voice Companion (One-Tap Setup)",
                        subtitle = if (isCompanionActive) "Active: Sweet Female Voice + Caring Companion"
                        else "Instant Female Voice (Aoede) + Caring Hinglish Persona",
                        valueBadge = if (isCompanionActive) "Active" else "Activate",
                        valueBadgeColor = AccentRosePink,
                        onClick = {
                            if (isCompanionActive) onDeactivateFemaleVoiceCompanion()
                            else onActivateFemaleVoiceCompanion()
                        },
                        modifier = Modifier.testTag("btn_activate_female_companion")
                    )

                    IosGroupDivider()

                    // Row 2: Active Persona Mode (Commander / Companion / Custom)
                    IosGroupedSettingRow(
                        icon = Icons.Default.Psychology,
                        iconBadgeColor = RoyalPurplePrimary,
                        title = "Active Persona Mode (Commander / Companion / Custom)",
                        subtitle = settings.personalityMode.subtitle,
                        valueBadge = settings.personalityMode.title,
                        valueBadgeColor = RoyalPurplePrimary,
                        onClick = { activeSheet = SettingsDetailSheet.PERSONA_AND_18_PLUS },
                        modifier = Modifier.testTag("row_persona_selector")
                    )

                    IosGroupDivider()

                    // Row 3: Romantic & 18+ Adult Intimate Mode Controls
                    val isAdultSelected = settings.personalityMode == PersonalityMode.ADULT_INTIMATE_18_PLUS ||
                        settings.personalityMode == PersonalityMode.ROMANTIC_GIRLFRIEND
                    IosGroupedSettingRow(
                        icon = Icons.Default.Whatshot,
                        iconBadgeColor = Color(0xFFF43F5E),
                        title = "Romantic & 18+ Adult Intimate Mode Controls",
                        subtitle = "Uncensored 18+ Girlfriend Mode, Bold Flirty Hinglish & Desires",
                        valueBadge = if (isAdultSelected) "18+ Active" else "Unlocked",
                        valueBadgeColor = AccentRosePink,
                        onClick = { activeSheet = SettingsDetailSheet.PERSONA_AND_18_PLUS },
                        modifier = Modifier.testTag("row_adult_intimate_controls")
                    )

                    IosGroupDivider()

                    // Row 4: Proactive Check-Ins & Emotional Tone Settings
                    IosGroupedSettingRow(
                        icon = Icons.Default.AutoAwesome,
                        iconBadgeColor = ElectricOrchid,
                        title = "Proactive Check-Ins & Emotional Tone Settings",
                        subtitle = "Mood: ${companionEmotionMood.displayTitle} • Self-Initiated Talk: ${if (settings.proactiveCompanionEnabled) "ON" else "OFF"}",
                        valueBadge = companionEmotionMood.displayTitle,
                        valueBadgeColor = ElectricOrchid,
                        onClick = { activeSheet = SettingsDetailSheet.PROACTIVE_AND_MOOD },
                        modifier = Modifier.testTag("row_proactive_and_mood")
                    )
                }
            }
        }

        // ====================================================================
        // 🤖 SECTION 4: AUTONOMOUS AGENT & SYSTEM PERMISSIONS (Report Section 4)
        // ====================================================================
        if (matches("autonomous agent accessibility overlay bubble react steps permissions workflow notifications controls")) {
            item {
                LiquidGlassGroupCard(
                    sectionHeader = "🤖 SECTION 4: AUTONOMOUS AGENT & SYSTEM PERMISSIONS",
                    accentColor = RoyalPurplePrimary
                ) {
                    // Row 1: Accessibility Service & Screen Control
                    IosGroupedSettingRow(
                        icon = Icons.Default.AccessibilityNew,
                        iconBadgeColor = AccentMintEmerald,
                        title = "Accessibility Service & Screen Control",
                        subtitle = "UI Tree Reader, Set-of-Mark Vision & Hardware Toggles",
                        valueBadge = "Controls",
                        valueBadgeColor = AccentMintEmerald,
                        onClick = onNavigateToControls
                    )

                    IosGroupDivider()

                    // Row 2: Floating Overlay Bubble & Background Service
                    IosGroupedToggleRow(
                        icon = Icons.Default.Tune,
                        iconBadgeColor = AccentSkyCyan,
                        title = "Floating Overlay Bubble & Cyber HUD",
                        subtitle = "Show live agent step progress pill over other apps",
                        checked = settings.floatingOverlayEnabled,
                        switchAccent = AccentSkyCyan,
                        onCheckedChange = onUpdateFloatingOverlay,
                        switchTestTag = "switch_floating_overlay"
                    )

                    IosGroupDivider()

                    // Row 3: Self-Healing Loop, ReAct Steps & Vision Speed
                    IosGroupedSettingRow(
                        icon = Icons.Default.AutoMode,
                        iconBadgeColor = AccentChampagneGold,
                        title = "Self-Healing Loop, ReAct Steps & Workflows",
                        subtitle = "Max Autonomous Steps: ${settings.maxAutonomousSteps} • Fast Vision Mode",
                        valueBadge = "${settings.maxAutonomousSteps} Steps",
                        valueBadgeColor = AccentChampagneGold,
                        onClick = { activeSheet = SettingsDetailSheet.AUTONOMOUS_AGENT_CONFIG },
                        modifier = Modifier.testTag("row_autonomous_agent_config")
                    )
                }
            }
        }

        // ====================================================================
        // 🛡️ SECTION 5: SECURITY, BIOMETRICS & PRIVACY VAULT (Report Section 5)
        // ====================================================================
        if (matches("security biometric fingerprint privacy redact vault payment confirm high risk")) {
            item {
                LiquidGlassGroupCard(
                    sectionHeader = "🛡️ SECTION 5: SECURITY, BIOMETRICS & PRIVACY VAULT",
                    accentColor = AccentMintEmerald
                ) {
                    // Row 1: Biometric Gate for Payments & High-Risk Actions
                    IosGroupedToggleRow(
                        icon = Icons.Default.Fingerprint,
                        iconBadgeColor = AccentMintEmerald,
                        title = "Biometric Gate for Payments & High-Risk Actions",
                        subtitle = "Require fingerprint/PIN before financial or delete steps",
                        checked = settings.requireBiometricForHighRisk,
                        switchAccent = AccentMintEmerald,
                        onCheckedChange = onUpdateRequireBiometric,
                        switchTestTag = "switch_require_biometric"
                    )

                    IosGroupDivider()

                    // Row 2: Confirm Consequential Actions
                    IosGroupedToggleRow(
                        icon = Icons.Default.Security,
                        iconBadgeColor = RoyalPurplePrimary,
                        title = "Confirm Consequential Phone Actions",
                        subtitle = "Ask approval before placing calls or sending messages",
                        checked = settings.confirmConsequentialActions,
                        switchAccent = RoyalPurplePrimary,
                        onCheckedChange = onUpdateConfirmConsequential,
                        switchTestTag = "switch_confirm_consequential"
                    )

                    IosGroupDivider()

                    // Row 3: Incognito Screen Filter (Hide Banking/OTP PII)
                    IosGroupedToggleRow(
                        icon = Icons.Default.Lock,
                        iconBadgeColor = AccentSkyCyan,
                        title = "Incognito Screen Filter (Redact OTP & PII)",
                        subtitle = "Mask sensitive passwords, tokens & banking numbers in logs",
                        checked = settings.privacyRedactTelemetry,
                        switchAccent = AccentSkyCyan,
                        onCheckedChange = onUpdatePrivacyRedact,
                        switchTestTag = "switch_privacy_redact"
                    )
                }
            }
        }

        // ====================================================================
        // 🧠 SECTION 6: MEMORY, BACKUP & APPEARANCE (Report Section 6)
        // ====================================================================
        if (matches("memory vault backup export clear chat reset history theme appearance")) {
            item {
                LiquidGlassGroupCard(
                    sectionHeader = "🧠 SECTION 6: MEMORY, BACKUP & APPEARANCE",
                    accentColor = RoyalIndigoAccent
                ) {
                    // Row 1: 5-Tier Memory Manager & Fact Review
                    IosGroupedSettingRow(
                        icon = Icons.Default.Memory,
                        iconBadgeColor = RoyalIndigoAccent,
                        title = "5-Tier Memory Manager & Fact Review",
                        subtitle = "$memoriesCount Saved Facts • $sessionsCount Chat Sessions",
                        valueBadge = "Manage",
                        valueBadgeColor = RoyalIndigoAccent,
                        onClick = onNavigateToMemory
                    )

                    IosGroupDivider()

                    // Row 2: Long-Term Memory & Auto-Inject Toggle
                    IosGroupedToggleRow(
                        icon = Icons.Default.History,
                        iconBadgeColor = AccentSkyCyan,
                        title = "Long-Term Memory & Auto-Context Injection",
                        subtitle = "Remember user facts & inject relevant memories into prompts",
                        checked = settings.memoryEnabled,
                        switchAccent = AccentSkyCyan,
                        onCheckedChange = {
                            onUpdateMemoryEnabled(it)
                            onUpdateAutoInjectMemories(it)
                        },
                        onRowClick = onNavigateToMemoryReview,
                        switchTestTag = "switch_memory_enabled"
                    )

                    IosGroupDivider()

                    // Row 3: Liquid Glass Theme Variant
                    IosGroupedSettingRow(
                        icon = Icons.Default.Palette,
                        iconBadgeColor = AccentChampagneGold,
                        title = "Apple Liquid Glass Theme Variant",
                        subtitle = "Active Theme: ${settings.themeMode.displayName}",
                        valueBadge = settings.themeMode.displayName,
                        valueBadgeColor = AccentChampagneGold,
                        onClick = { activeSheet = SettingsDetailSheet.Appearance_THEME }
                    )

                    IosGroupDivider()

                    // Row 4: Clear Chat History / Reset Memory Vault
                    IosGroupedSettingRow(
                        icon = Icons.Default.DeleteForever,
                        iconBadgeColor = StatusRoseRed,
                        title = "Clear Chat History / Reset Memory Vault",
                        subtitle = "Wipe local sessions ($sessionsCount) or memory vault ($memoriesCount)",
                        valueBadge = "Reset",
                        valueBadgeColor = StatusRoseRed,
                        onClick = { showClearChatDialog = true },
                        modifier = Modifier.testTag("btn_clear_chat_history")
                    )
                }
            }
        }
    }

    // ========================================================================
    // APPLE iOS 26 FROSTED LIQUID GLASS DETAIL BOTTOM SHEETS
    // Tapping any grouped setting row opens its clean, dedicated modal sheet!
    // ========================================================================
    when (activeSheet) {
        SettingsDetailSheet.IDENTITY_AND_LANGUAGE -> {
            LiquidGlassBottomSheet(
                title = "Assistant Identity & Language",
                subtitle = "Customize wake name, UI title & bilingual Hinglish mode",
                onDismissRequest = { activeSheet = null }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Assistant Name", fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPureWhite,
                            unfocusedTextColor = TextPureWhite,
                            focusedContainerColor = LiquidGlassPill,
                            unfocusedContainerColor = LiquidGlassSurface,
                            focusedBorderColor = RoyalPurplePrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.20f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_assistant_name")
                    )

                    Text(
                        text = "CONVERSATION LANGUAGE",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentSkyCyan,
                        fontWeight = FontWeight.ExtraBold
                    )

                    SupportedLanguage.entries.forEach { lang ->
                        val selected = settings.language == lang
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) RoyalPurplePrimary.copy(alpha = 0.22f)
                                    else LiquidGlassSurface
                                )
                                .border(
                                    1.dp,
                                    if (selected) RoyalPurplePrimary else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { onUpdateLanguage(lang) }
                                .padding(14.dp)
                                .testTag("lang_option_${lang.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = lang.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPureWhite,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Locale: ${lang.code}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLavenderMuted,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            RadioButton(
                                selected = selected,
                                onClick = { onUpdateLanguage(lang) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AccentSkyCyan,
                                    unselectedColor = TextLavenderMuted
                                )
                            )
                        }
                    }

                    JarvisPrimaryActionButton(
                        text = "Save Identity & Language",
                        icon = Icons.Default.CheckCircle,
                        onClick = {
                            if (nameInput.isNotBlank()) onUpdateAssistantName(nameInput.trim())
                            activeSheet = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_save_assistant_name")
                    )
                }
            }
        }

        SettingsDetailSheet.API_KEYS_VAULT -> {
            LiquidGlassBottomSheet(
                title = "Gemini API Key Vault",
                subtitle = if (isKeystoreBacked) "Protected by Android Hardware Keystore AES-256-GCM"
                else "Encrypted Local API Key Storage",
                onDismissRequest = { activeSheet = null }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(LiquidGlassSurface)
                            .border(
                                1.dp,
                                if (hasConfiguredKey) AccentMintEmerald.copy(alpha = 0.55f)
                                else StatusAmber.copy(alpha = 0.55f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasConfiguredKey) "Saved Key: $maskedApiKey" else "No API Key Configured",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPureWhite,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Get your key from Google AI Studio",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextLavenderMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (hasConfiguredKey) {
                            IconButton(
                                onClick = onDeleteApiKey,
                                modifier = Modifier.testTag("btn_delete_api_key")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Delete API Key",
                                    tint = StatusRoseRed
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = {
                            Text(
                                text = if (hasConfiguredKey) "Paste new Gemini API key to replace..."
                                else "Paste Gemini API Key (AIzaSy...)",
                                color = TextVioletDim,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (showRawApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showRawApiKey = !showRawApiKey }) {
                                Icon(
                                    imageVector = if (showRawApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Key Visibility",
                                    tint = AccentSkyCyan
                                )
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPureWhite,
                            unfocusedTextColor = TextPureWhite,
                            focusedContainerColor = LiquidGlassPill,
                            unfocusedContainerColor = LiquidGlassSurface,
                            focusedBorderColor = AccentChampagneGold,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.20f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_api_key")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        JarvisPrimaryActionButton(
                            text = "Save Key",
                            icon = Icons.Default.Key,
                            enabled = apiKeyInput.trim().length >= 10,
                            onClick = {
                                onSaveOrUpdateApiKey(apiKeyInput.trim())
                                apiKeyInput = ""
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_save_api_key")
                        )
                        JarvisSecondaryActionButton(
                            text = if (isTestingConnection) "Testing..." else "Test Key",
                            icon = Icons.Default.NetworkCheck,
                            accentColor = AccentSkyCyan,
                            enabled = !isTestingConnection && (apiKeyInput.isNotBlank() || hasConfiguredKey),
                            onClick = {
                                val candidate = apiKeyInput.trim().ifBlank { currentApiKey }
                                onTestApiConnection(candidate)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_test_connection")
                        )
                    }

                    if (connectionTestResult != null) {
                        val resColor = if (connectionTestResult.success) AccentMintEmerald else StatusRoseRed
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(resColor.copy(alpha = 0.15f))
                                .border(1.dp, resColor.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                                .testTag("connection_test_result_box")
                        ) {
                            Text(
                                text = connectionTestResult.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPureWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        SettingsDetailSheet.AI_MODELS_PICKER -> {
            LiquidGlassBottomSheet(
                title = "Cloud AI Models",
                subtitle = "Select default Gemini models for Chat, Reasoning & Live Voice",
                onDismissRequest = { activeSheet = null }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TEXT, VISION & REASONING MODELS",
                            style = MaterialTheme.typography.labelMedium,
                            color = AccentSkyCyan,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Sync Cloud ↻",
                            style = MaterialTheme.typography.labelSmall,
                            color = SoftLavenderGlow,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clickable {
                                    val candidate = apiKeyInput.trim().ifBlank { currentApiKey }
                                    onSyncModelsFromApi(candidate)
                                }
                                .testTag("btn_sync_models")
                        )
                    }

                    availableTextModels.forEach { model ->
                        val selected = settings.textModel == model.id
                        ModelPickerRow(
                            model = model,
                            selected = selected,
                            accentColor = AccentSkyCyan,
                            onClick = { onSelectTextModel(model.id) }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "LIVE BIDIRECTIONAL VOICE MODELS",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentRosePink,
                        fontWeight = FontWeight.ExtraBold
                    )

                    availableVoiceModels.forEach { model ->
                        val selected = settings.voiceModel == model.id
                        ModelPickerRow(
                            model = model,
                            selected = selected,
                            accentColor = AccentRosePink,
                            onClick = { onSelectVoiceModel(model.id) }
                        )
                    }
                }
            }
        }

        SettingsDetailSheet.VOICE_STUDIO -> {
            LiquidGlassBottomSheet(
                title = "AI Voice Studio",
                subtitle = "Configure Voice Gender, 8 Gemini Personas, Speed & Pitch",
                onDismissRequest = { activeSheet = null }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    LiquidGlassSegmentedBar(
                        items = VoiceGender.entries,
                        selectedItem = settings.voiceGender,
                        onItemSelected = { gender ->
                            onUpdateVoiceGender(gender)
                            val matching = SupportedGeminiModels.voiceOptions.filter { it.gender == gender }
                            if (matching.isNotEmpty() && matching.none { it.id.equals(settings.voicePersona, ignoreCase = true) }) {
                                onUpdateVoicePersona(matching.first().id)
                            }
                        },
                        itemLabel = { it.displayName }
                    )

                    val filteredPersonas = SupportedGeminiModels.voiceOptions.filter { it.gender == settings.voiceGender }
                    filteredPersonas.forEach { persona ->
                        val selected = settings.voicePersona.equals(persona.id, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) AccentSkyCyan.copy(alpha = 0.18f)
                                    else LiquidGlassSurface
                                )
                                .border(
                                    1.dp,
                                    if (selected) AccentSkyCyan else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { onUpdateVoicePersona(persona.id) }
                                .padding(12.dp)
                                .testTag("voice_persona_${persona.id.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = persona.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPureWhite,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = persona.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLavenderMuted,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(RoyalPurplePrimary.copy(alpha = 0.25f))
                                    .border(1.dp, SoftLavenderGlow.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                                    .clickable {
                                        if (isTtsSpeaking) onStopVoicePreview()
                                        else onPreviewVoice(settings.voiceGender, persona.id)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("btn_preview_voice_${persona.id.lowercase()}")
                            ) {
                                Text(
                                    text = if (isTtsSpeaking && selected) "Stop" else "Preview",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPureWhite,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    // Speed & Pitch Sliders
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Speech Speed", color = TextPureWhite, fontWeight = FontWeight.Bold)
                            Text(
                                String.format(Locale.US, "%.2fx", speedSlider),
                                color = AccentSkyCyan,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Slider(
                            value = speedSlider,
                            onValueChange = { speedSlider = it },
                            onValueChangeFinished = { onUpdateVoiceSpeed(speedSlider) },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = AccentSkyCyan
                            ),
                            modifier = Modifier.testTag("slider_voice_speed")
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Voice Pitch", color = TextPureWhite, fontWeight = FontWeight.Bold)
                            Text(
                                String.format(Locale.US, "%.2fx", pitchSlider),
                                color = AccentRosePink,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Slider(
                            value = pitchSlider,
                            onValueChange = { pitchSlider = it },
                            onValueChangeFinished = { onUpdateVoicePitch(pitchSlider) },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = AccentRosePink
                            ),
                            modifier = Modifier.testTag("slider_voice_pitch")
                        )
                    }
                }
            }
        }

        SettingsDetailSheet.WAKE_WORD_TUNING -> {
            LiquidGlassBottomSheet(
                title = "\"Hey Jarvis\" Offline Wake Word",
                subtitle = "Hands-free background phrase detection engine",
                onDismissRequest = { activeSheet = null }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    IosGroupedToggleRow(
                        icon = Icons.Default.GraphicEq,
                        iconBadgeColor = AccentMintEmerald,
                        title = "Enable Always-On \"Hey Jarvis\"",
                        subtitle = "Current Engine Status: ${wakeWordState.name}",
                        checked = settings.wakeWordEnabled,
                        switchAccent = AccentMintEmerald,
                        onCheckedChange = onUpdateWakeWordEnabled
                    )
                    if (!wakeWordErrorMessage.isNullOrBlank()) {
                        Text(
                            text = wakeWordErrorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        SettingsDetailSheet.PERSONA_AND_18_PLUS -> {
            LiquidGlassBottomSheet(
                title = "Persona & 18+ Adult Companion Modes",
                subtitle = "Choose from 8 Personality Modes or write a Custom Persona Prompt",
                onDismissRequest = { activeSheet = null }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PersonalityMode.entries.forEach { mode ->
                        val isSelected = settings.personalityMode == mode
                        val isAdultMode = mode == PersonalityMode.ADULT_INTIMATE_18_PLUS
                        val modeAccent = if (isAdultMode || mode == PersonalityMode.ROMANTIC_GIRLFRIEND || mode == PersonalityMode.GIRLFRIEND) {
                            AccentRosePink
                        } else {
                            RoyalPurplePrimary
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isSelected) modeAccent.copy(alpha = 0.20f)
                                    else LiquidGlassSurface
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) modeAccent else Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    if (isAdultMode && !isSelected) {
                                        pendingAdultModeSelection = mode
                                    } else {
                                        onUpdatePersonalityMode(mode)
                                    }
                                }
                                .padding(14.dp)
                                .testTag("persona_mode_${mode.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPureWhite,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    if (isAdultMode) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(AccentRosePink.copy(alpha = 0.24f))
                                                .border(1.dp, AccentRosePink, RoundedCornerShape(999.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "18+ ADULT",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFFBCFE8),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLavenderMuted,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    if (isAdultMode && !isSelected) {
                                        pendingAdultModeSelection = mode
                                    } else {
                                        onUpdatePersonalityMode(mode)
                                    }
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = modeAccent,
                                    unselectedColor = TextLavenderMuted
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "CUSTOM PERSONA PROMPT STUDIO",
                        style = MaterialTheme.typography.labelMedium,
                        color = SoftLavenderGlow,
                        fontWeight = FontWeight.ExtraBold
                    )

                    OutlinedTextField(
                        value = customPromptInput,
                        onValueChange = { customPromptInput = it },
                        placeholder = {
                            Text(
                                text = "Write custom behavior, backstory, nick-names or roleplay rules...",
                                color = TextVioletDim
                            )
                        },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPureWhite,
                            unfocusedTextColor = TextPureWhite,
                            focusedContainerColor = LiquidGlassPill,
                            unfocusedContainerColor = LiquidGlassSurface,
                            focusedBorderColor = AccentRosePink,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_custom_personality_prompt")
                    )

                    JarvisPrimaryActionButton(
                        text = "Save Custom Persona Prompt",
                        icon = Icons.Default.CheckCircle,
                        onClick = {
                            onUpdateCustomPersonalityPrompt(customPromptInput)
                            activeSheet = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_save_custom_personality_prompt")
                    )
                }
            }
        }

        SettingsDetailSheet.PROACTIVE_AND_MOOD -> {
            LiquidGlassBottomSheet(
                title = "Proactive Check-Ins & Emotional Mood",
                subtitle = "Configure self-initiated check-ins, ambient room talk & live emotion state",
                onDismissRequest = { activeSheet = null }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "LIVE EMOTIONAL MOOD STATES",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentRosePink,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompanionEmotionMood.entries.forEach { mood ->
                            val isCurrent = companionEmotionMood == mood
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (isCurrent) AccentRosePink.copy(alpha = 0.25f)
                                        else LiquidGlassSurface
                                    )
                                    .border(
                                        1.dp,
                                        if (isCurrent) AccentRosePink else Color.White.copy(alpha = 0.14f),
                                        RoundedCornerShape(999.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = mood.displayTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCurrent) TextPureWhite else TextLavenderMuted,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    IosGroupedToggleRow(
                        icon = Icons.Default.AutoAwesome,
                        iconBadgeColor = RoyalPurplePrimary,
                        title = "Proactive Self-Initiated Check-Ins",
                        subtitle = "AI automatically greets & checks on you throughout the day",
                        checked = settings.proactiveCompanionEnabled,
                        switchAccent = RoyalPurplePrimary,
                        onCheckedChange = onUpdateProactiveCompanionEnabled,
                        switchTestTag = "switch_proactive_companion"
                    )

                    IosGroupedToggleRow(
                        icon = Icons.Default.Mic,
                        iconBadgeColor = AccentRosePink,
                        title = "Ambient Room Presence Talk",
                        subtitle = "Natural spontaneous expressions during live voice sessions",
                        checked = settings.ambientRoomTalkEnabled,
                        switchAccent = AccentRosePink,
                        onCheckedChange = onUpdateAmbientRoomTalkEnabled,
                        switchTestTag = "switch_ambient_room_talk"
                    )

                    JarvisSecondaryActionButton(
                        text = "Trigger Proactive Greeting Now",
                        icon = Icons.Default.Favorite,
                        accentColor = AccentRosePink,
                        onClick = {
                            onTriggerProactiveCompanionNow()
                            activeSheet = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_trigger_proactive_now")
                    )
                }
            }
        }

        SettingsDetailSheet.AUTONOMOUS_AGENT_CONFIG -> {
            LiquidGlassBottomSheet(
                title = "Autonomous Agent & ReAct Engine",
                subtitle = "Configure maximum autonomous reasoning steps & OS integrations",
                onDismissRequest = { activeSheet = null }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Max Autonomous ReAct Steps",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPureWhite,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${autonomousStepsSlider.toInt()} Steps",
                            style = MaterialTheme.typography.labelLarge,
                            color = AccentChampagneGold,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Slider(
                        value = autonomousStepsSlider,
                        onValueChange = { autonomousStepsSlider = it },
                        onValueChangeFinished = {
                            onUpdateMaxAutonomousSteps(autonomousStepsSlider.toInt())
                        },
                        valueRange = 3f..15f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = AccentChampagneGold
                        ),
                        modifier = Modifier.testTag("slider_max_autonomous_steps")
                    )

                    JarvisSecondaryActionButton(
                        text = "Open Multi-App Workflows",
                        icon = Icons.Default.AutoMode,
                        accentColor = AccentChampagneGold,
                        onClick = {
                            activeSheet = null
                            onNavigateToWorkflow()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    JarvisSecondaryActionButton(
                        text = "Open Smart Notification Auto-Reply",
                        icon = Icons.Default.Notifications,
                        accentColor = AccentSkyCyan,
                        onClick = {
                            activeSheet = null
                            onNavigateToNotifications()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        SettingsDetailSheet.Appearance_THEME -> {
            LiquidGlassBottomSheet(
                title = "Appearance & Themes Engine",
                subtitle = "Choose from Ocean Blue, Royal Purple, or Aqua Mint",
                onDismissRequest = { activeSheet = null }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    JarvisThemeMode.entries.forEach { mode ->
                        val selected = settings.themeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) mode.palette.previewColor.copy(alpha = 0.22f)
                                    else JarvisTheme.colors.surface
                                )
                                .border(
                                    1.dp,
                                    if (selected) mode.palette.previewColor else JarvisTheme.colors.border,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    onUpdateThemePalette(mode.palette)
                                    onUpdateThemeDisplayMode(mode.displayMode)
                                    onUpdateThemeMode(mode)
                                }
                                .padding(14.dp)
                                .testTag("theme_mode_${mode.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(mode.palette.previewColor)
                                )
                                Column {
                                    Text(
                                        text = mode.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = JarvisTheme.colors.textPrimary,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    if (mode.subtitle.isNotBlank()) {
                                        Text(
                                            text = mode.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = JarvisTheme.colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    onUpdateThemePalette(mode.palette)
                                    onUpdateThemeDisplayMode(mode.displayMode)
                                    onUpdateThemeMode(mode)
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = mode.palette.previewColor,
                                    unselectedColor = JarvisTheme.colors.textMuted
                                )
                            )
                        }
                    }
                }
            }
        }

        null -> Unit
    }

    // ========================================================================
    // 18+ ADULT CONSENT DIALOG & DANGER RESET DIALOGS
    // ========================================================================
    pendingAdultModeSelection?.let { targetAdultMode ->
        JarvisModernDialog(
            title = "18+ Adult Intimate Mode",
            description = "This mode enables uncensored 18+ romantic & intimate adult conversation in Hinglish. Please confirm that you are 18 years of age or older.",
            confirmText = "I am 18+ (Activate)",
            dismissText = "Cancel",
            isDestructive = false,
            onConfirm = {
                onUpdatePersonalityMode(targetAdultMode)
                pendingAdultModeSelection = null
            },
            onDismiss = { pendingAdultModeSelection = null }
        )
    }

    if (showClearChatDialog) {
        JarvisModernDialog(
            title = "Reset Chat or Memory Vault?",
            description = "Choose 'Clear Chats' to delete all $sessionsCount chat sessions, or manage your $memoriesCount saved memories.",
            confirmText = "Clear All Chats",
            dismissText = "Wipe Memory Vault",
            isDestructive = true,
            onConfirm = {
                onClearChatHistory()
                showClearChatDialog = false
            },
            onDismiss = {
                showClearChatDialog = false
                showClearMemoriesDialog = true
            }
        )
    }

    if (showClearMemoriesDialog) {
        JarvisModernDialog(
            title = "Wipe 5-Tier Memory Vault?",
            description = "Are you sure you want to permanently erase all $memoriesCount saved memories?",
            confirmText = "Erase Memories",
            dismissText = "Keep Safe",
            isDestructive = true,
            onConfirm = {
                onClearMemories()
                showClearMemoriesDialog = false
            },
            onDismiss = { showClearMemoriesDialog = false }
        )
    }
}

@Composable
private fun HeroSummaryChip(
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(accentColor.copy(alpha = 0.30f), accentColor.copy(alpha = 0.16f))
                )
            )
            .border(1.dp, accentColor.copy(alpha = 0.70f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun IosGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 54.dp),
        thickness = 0.6.dp,
        color = Color.White.copy(alpha = 0.11f)
    )
}

@Composable
private fun IosGroupedSettingRow(
    icon: ImageVector,
    iconBadgeColor: Color,
    title: String,
    subtitle: String,
    valueBadge: String,
    valueBadgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            // 100% Solid Vibrant Gradient Apple iOS Squircle Icon Badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                iconBadgeColor,
                                iconBadgeColor.copy(alpha = 0.72f)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.36f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.padding(end = 6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPureWhite,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLavenderMuted,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(valueBadgeColor.copy(alpha = 0.22f))
                    .border(1.dp, valueBadgeColor.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = valueBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open",
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun IosGroupedToggleRow(
    icon: ImageVector,
    iconBadgeColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    switchAccent: Color,
    onCheckedChange: (Boolean) -> Unit,
    onRowClick: (() -> Unit)? = null,
    switchTestTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                if (onRowClick != null) onRowClick() else onCheckedChange(!checked)
            }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            // 100% Solid Vibrant Gradient Apple iOS Squircle Icon Badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                iconBadgeColor,
                                iconBadgeColor.copy(alpha = 0.72f)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.36f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPureWhite,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLavenderMuted,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = switchAccent,
                uncheckedThumbColor = TextLavenderMuted,
                uncheckedTrackColor = Color(0xFF1F253D)
            ),
            modifier = if (switchTestTag != null) Modifier.testTag(switchTestTag) else Modifier
        )
    }
}

@Composable
private fun ModelPickerRow(
    model: GeminiModelOption,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) accentColor.copy(alpha = 0.18f)
                else LiquidGlassSurface
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accentColor else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("model_option_${model.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPureWhite,
                    fontWeight = FontWeight.ExtraBold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentColor.copy(alpha = 0.20f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (model.isLiveVoiceModel) "LIVE AUDIO" else "MULTIMODAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp
                    )
                }
            }
            Text(
                text = model.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextLavenderMuted,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = accentColor,
                unselectedColor = TextLavenderMuted
            )
        )
    }
}
