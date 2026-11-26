package com.jarvisai.assistant.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvisai.assistant.data.remote.AntigravityModelInfo
import com.jarvisai.assistant.model.ThinkingLevel
import kotlinx.coroutines.launch

@Composable
fun AntigravityAuthDialogHost(
    visible: Boolean,
    viewModel: com.jarvisai.assistant.viewmodel.JarvisViewModel,
    selectedModelId: String,
    currentThinkingLevel: ThinkingLevel,
    onSelectModel: (AntigravityModelInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pendingLocalHubLoginUrl by viewModel.pendingLocalHubLoginUrl.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(pendingLocalHubLoginUrl) {
        val url = pendingLocalHubLoginUrl?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        viewModel.clearPendingLocalHubLoginUrl()
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    }

    if (!visible) return

    val isLoggedIn by viewModel.isAntigravityLoggedIn.collectAsStateWithLifecycle()
    val userEmail by viewModel.antigravityUserEmail.collectAsStateWithLifecycle()
    val tier by viewModel.antigravityTier.collectAsStateWithLifecycle()
    val isServerListening by viewModel.isOAuthServerActive.collectAsStateWithLifecycle()
    val authUrl by viewModel.antigravityAuthUrl.collectAsStateWithLifecycle()
    val antigravityModels by viewModel.antigravityModels.collectAsStateWithLifecycle()
    val isRefreshingModels by viewModel.isRefreshingAntigravityModels.collectAsStateWithLifecycle()
    val hubAuthStatus by viewModel.hubAuthStatus.collectAsStateWithLifecycle()
    val isLocalHubSigningIn by viewModel.isLocalHubSigningIn.collectAsStateWithLifecycle()
    val localHubAuthMessage by viewModel.localHubAuthMessage.collectAsStateWithLifecycle()

    AntigravityLoginDialog(
        isLoggedIn = isLoggedIn,
        userEmail = userEmail,
        tier = tier,
        isServerListening = isServerListening,
        authUrl = authUrl,
        antigravityModels = antigravityModels,
        isRefreshingModels = isRefreshingModels,
        selectedModelId = selectedModelId,
        currentThinkingLevel = currentThinkingLevel,
        hubAuthStatus = hubAuthStatus,
        isLocalHubSigningIn = isLocalHubSigningIn,
        localHubAuthMessage = localHubAuthMessage,
        onRefreshModels = { viewModel.refreshAntigravityModels() },
        onSelectModel = onSelectModel,
        onSelectThinkingLevel = { level -> viewModel.setThinkingLevel(level) },
        onStartLogin = { viewModel.startAntigravitySignIn() },
        onStartLocalHubLogin = { viewModel.startLocalAgyHubSignIn() },
        onSyncTokenToLocalHub = { viewModel.syncAccountTokenToLocalAgyHub() },
        onCancelLocalHubLogin = { viewModel.cancelLocalAgyHubSignIn() },
        onRefreshLocalHubAuth = { viewModel.refreshLocalHubAuthStatus() },
        onSubmitManualCode = { code -> viewModel.handleAntigravityCode(code) },
        onLogout = { viewModel.logoutAntigravity() },
        onDismiss = onDismiss
    )
}

@Composable
fun AntigravityLoginDialog(
    isLoggedIn: Boolean,
    userEmail: String,
    tier: String,
    isServerListening: Boolean,
    authUrl: String,
    projectId: String = "",
    antigravityModels: List<AntigravityModelInfo> = emptyList(),
    isRefreshingModels: Boolean = false,
    selectedModelId: String = "",
    currentThinkingLevel: ThinkingLevel = ThinkingLevel.MEDIUM,
    hubAuthStatus: com.jarvisai.assistant.terminal.HubAuthSnapshot = com.jarvisai.assistant.terminal.HubAuthSnapshot(),
    isLocalHubSigningIn: Boolean = false,
    localHubAuthMessage: String? = null,
    onRefreshModels: () -> Unit = {},
    onSelectModel: (AntigravityModelInfo) -> Unit = {},
    onSelectThinkingLevel: (ThinkingLevel) -> Unit = {},
    onStartLogin: () -> String,
    onStartLocalHubLogin: () -> Unit = {},
    onSyncTokenToLocalHub: () -> Unit = {},
    onCancelLocalHubLogin: () -> Unit = {},
    onRefreshLocalHubAuth: () -> Unit = {},
    onSubmitManualCode: (String) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        onRefreshLocalHubAuth()
    }
    val resolvedProjectId = remember(projectId) {
        projectId.ifBlank { com.jarvisai.assistant.data.preferences.AuthPreferences.getInstance(context).getProjectId() }
    }
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var manualCodeInput by remember { mutableStateOf("") }
    val tierDisplay = remember(tier) { formatAntigravityTierBadge(tier) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .testTag("antigravity_login_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF111827),
            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Antigravity Cloud",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isLoggedIn) "Google Antigravity Account" else "Google Antigravity Auth",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = if (isLoggedIn) "Plan: $tierDisplay • Synced to your Google account entitlements"
                    else "Sign in with Google to load models & quota supported by your account",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Text(
                    text = "Isolated Hub: 127.0.0.1:${com.jarvisai.assistant.data.remote.JarvisHubConfig.getHubPort(context)} (${com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_APP_DATA_DIR})",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00E5FF).copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (isLoggedIn) {
                    // Profile Summary Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1F2937),
                        border = BorderStroke(1.dp, Color(0xFF374151))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Signed in account",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = userEmail,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Project / Quota:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Text(
                                    text = if (resolvedProjectId.isBlank()) "Personal Antigravity Quota" else resolvedProjectId,
                                    fontSize = 11.sp,
                                    color = Color(0xFF00E5FF),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Subscription Tier:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Text(
                                    text = tierDisplay,
                                    fontSize = 11.sp,
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Local AGY CLI Hub Auth Status & 1-Tap Native Sign-In Card (AntiGem Parity)
                    val hubSignedIn = hubAuthStatus.isSignedIn
                    val prefsScopeKnown = com.jarvisai.assistant.data.preferences.AuthPreferences
                        .getInstance(context)
                        .hasVoiceAiCodeScope()
                    val missingVoiceScope = hubAuthStatus.hasVoiceScope == false ||
                        (hubAuthStatus.hasVoiceScope == null && prefsScopeKnown == false)
                    val hubBorderColor = when {
                        missingVoiceScope -> Color(0xFFF59E0B).copy(alpha = 0.75f)
                        hubSignedIn == true -> Color(0xFF10B981).copy(alpha = 0.55f)
                        hubSignedIn == false -> Color(0xFFF59E0B).copy(alpha = 0.65f)
                        else -> Color(0xFF334155)
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("local_agy_hub_auth_card"),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, hubBorderColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Local AGY CLI Daemon",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        hubSignedIn == true && missingVoiceScope -> "Missing Voice Scope"
                                        hubSignedIn == true -> "Signed In ✓"
                                        hubSignedIn == false -> "Not Signed In"
                                        else -> "Checking..."
                                    },
                                    color = when {
                                        missingVoiceScope -> Color(0xFFF59E0B)
                                        hubSignedIn == true -> Color(0xFF10B981)
                                        hubSignedIn == false -> Color(0xFFF59E0B)
                                        else -> Color(0xFF94A3B8)
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (hubSignedIn == true && hubAuthStatus.email.isNotBlank()) {
                                Text(
                                    text = "Active CLI Account: ${hubAuthStatus.email}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (missingVoiceScope) {
                                Text(
                                    text = "Aapke current login mein Google 'aicode' Voice scope missing hai. Voice Calls ke liye neeche 'Update Sign-In (Voice)' ya 'Sign Out' karke dubara Sign-In karein.",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 11.sp
                                )
                            } else if (hubSignedIn == false) {
                                Text(
                                    text = "CLI Only mode ke liye Local AGY CLI sign-in zaroori hai.",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 11.sp
                                )
                            }
                            if (!localHubAuthMessage.isNullOrBlank()) {
                                Text(
                                    text = localHubAuthMessage,
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isLocalHubSigningIn) {
                                    OutlinedButton(
                                        onClick = onCancelLocalHubLogin,
                                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.7f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 44.dp)
                                            .testTag("local_agy_hub_cancel_signin_button")
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = Color(0xFFF59E0B)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Cancel Sign-In", color = Color(0xFFF59E0B), fontSize = 11.5.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = onStartLocalHubLogin,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (missingVoiceScope) Color(0xFFF59E0B) else if (hubSignedIn != true) Color(0xFF10B981) else Color(0xFF1E293B)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 44.dp)
                                            .testTag("local_agy_hub_native_signin_button")
                                    ) {
                                        Text(
                                            text = when {
                                                missingVoiceScope -> "Update Sign-In (Voice)"
                                                hubSignedIn != true -> "Sign in Local AGY CLI"
                                                else -> "Re-sign in Local CLI"
                                            },
                                            color = if (missingVoiceScope || hubSignedIn != true) Color(0xFF052E16) else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    if (hubSignedIn != true) {
                                        OutlinedButton(
                                            onClick = onSyncTokenToLocalHub,
                                            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.55f)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 44.dp)
                                                .testTag("local_agy_hub_sync_token_button")
                                        ) {
                                            Text(
                                                text = "Sync",
                                                color = Color(0xFF00E5FF),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Thinking Mode Status Banner (Controlled solely via the dedicated Top Bar Thinking button)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = "Thinking Depth",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Thinking Mode: ${currentThinkingLevel.label}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Use Top Bar 🧠",
                                color = Color(0xFF00E5FF),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Account-Entitled Supported Models List
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Supported Models (${antigravityModels.size})",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = onRefreshModels,
                            enabled = !isRefreshingModels,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag("antigravity_refresh_models_button")
                        ) {
                            if (isRefreshingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF00E5FF)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Models",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRefreshingModels) "Syncing..." else "Refresh",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    if (antigravityModels.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0xFF1E293B))
                        ) {
                            Text(
                                text = if (isRefreshingModels) {
                                    "Fetching models entitled for $userEmail..."
                                } else {
                                    "Tap Refresh above to load supported models for your Antigravity account."
                                },
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            antigravityModels.forEach { model ->
                                val bareSelected = selectedModelId.removePrefix("agy:").trim()
                                val isCurrent = bareSelected.equals(model.id, ignoreCase = true) ||
                                    model.thinkingVariantIds.values.any { it.equals(bareSelected, ignoreCase = true) }
                                val quotaPct = model.quotaPercent
                                val quotaColor = when {
                                    quotaPct <= 15 -> Color(0xFFEF4444)
                                    quotaPct <= 40 -> Color(0xFFF59E0B)
                                    else -> Color(0xFF10B981)
                                }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            onSelectModel(model)
                                            Toast.makeText(
                                                context,
                                                "Selected ${model.displayName}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        .testTag("agy_supported_model_${model.id}"),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isCurrent) Color(0xFF00E5FF).copy(alpha = 0.14f) else Color(0xFF0F172A),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isCurrent) Color(0xFF00E5FF) else Color(0xFF1E293B)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (isCurrent) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Active",
                                                        tint = Color(0xFF00E5FF),
                                                        modifier = Modifier
                                                            .padding(end = 6.dp)
                                                            .size(14.dp)
                                                    )
                                                }
                                                Text(
                                                    text = model.displayName,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = "$quotaPct%",
                                                color = quotaColor,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { model.remainingFraction.toFloat().coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = quotaColor,
                                            trackColor = Color(0xFF1E293B)
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = buildString {
                                                append(model.id)
                                                if (model.supportsThinking) append(" • Thinking")
                                                if (model.isRecommended) append(" • Recommended")
                                            },
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            onLogout()
                            Toast.makeText(context, "Logged out of Antigravity", Toast.LENGTH_SHORT).show()
                        },
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("antigravity_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign Out",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Sign Out", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                } else {
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    val agyAuthService = remember { com.jarvisai.assistant.data.remote.AgyAuthService(context) }
                    Button(
                        onClick = {
                            scope.launch {
                                val pkceUrl = onStartLogin().ifBlank { authUrl }
                                val generatedUrl = pkceUrl.ifBlank {
                                    agyAuthService.startLogin().getOrNull().orEmpty()
                                }
                                if (generatedUrl.isNotBlank()) {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(generatedUrl))
                                        context.startActivity(intent)
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("antigravity_sign_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Login,
                            contentDescription = "Sign in with Google",
                            tint = Color(0xFF0A0E17),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sign in with Google",
                            color = Color(0xFF0A0E17),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            if (isLocalHubSigningIn) onCancelLocalHubLogin() else onStartLocalHubLogin()
                        },
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.65f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("antigravity_native_hub_sign_in_button")
                    ) {
                        if (isLocalHubSigningIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color(0xFF10B981),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Signing in Local AGY CLI... (Tap to Cancel)",
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        } else {
                            Text(
                                text = "Sign in Local AGY CLI (Direct Hub)",
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                    }

                    if (!localHubAuthMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = localHubAuthMessage,
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }

                    if (isServerListening) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color(0xFF00E5FF),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Listening on port 51121 for callback...",
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = manualCodeInput,
                        onValueChange = { manualCodeInput = it },
                        label = { Text("Paste Code or Callback URL (Fallback)", fontSize = 11.sp) },
                        placeholder = { Text("4/0A...", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        trailingIcon = {
                            IconButton(onClick = {
                                val clip = clipboardManager.getText()?.text.orEmpty()
                                if (clip.isNotBlank()) manualCodeInput = clip
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = Color(0xFF00E5FF)
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (manualCodeInput.isNotBlank()) {
                                val cleanCode = Regex("[?&]code=([^&\\s]+)").find(manualCodeInput)?.groupValues?.get(1)
                                    ?: manualCodeInput.trim()
                                onSubmitManualCode(cleanCode)
                            }
                        },
                        enabled = manualCodeInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2937)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("antigravity_verify_code_button")
                    ) {
                        Text(text = "Verify & Complete Login", color = Color.White, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("antigravity_login_close_button")
                ) {
                    Text(text = "Close", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }
    }
}
