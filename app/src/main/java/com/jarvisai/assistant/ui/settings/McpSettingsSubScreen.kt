package com.jarvisai.assistant.ui.settings

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvisai.assistant.data.remote.JarvisHubConfig
import com.jarvisai.assistant.extension.BrowserExtensionManager
import com.jarvisai.assistant.mcp.McpServerSpec
import com.jarvisai.assistant.mcp.McpServerState
import com.jarvisai.assistant.mcp.McpServersManager
import com.jarvisai.assistant.mcp.McpToolInfo
import com.jarvisai.assistant.ui.components.bouncyOverscroll
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class McpPresetTemplate(
    val name: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val spec: McpServerSpec?,
    val onSpecialClick: (() -> Unit)? = null
)

@Composable
fun McpSettingsSubScreen(
    onOpenRea: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers by McpServersManager.servers.collectAsState()
    val isLoading by McpServersManager.isLoading.collectAsState()
    val statusMessage by McpServersManager.statusMessage.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingSpec by remember { mutableStateOf<McpServerSpec?>(null) }
    var editingOriginalName by remember { mutableStateOf<String?>(null) }
    var showCatalogDialog by remember { mutableStateOf(false) }
    var serverToDelete by remember { mutableStateOf<String?>(null) }
    var chooseWebViewExtension by remember { mutableStateOf(false) }
    var toolProbeOutput by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        McpServersManager.refreshAll(context)
    }

    val deviceId = remember(context) {
        runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "" }.getOrDefault("")
    }
    val localBase = "http://127.0.0.1:${JarvisHubConfig.ISOLATED_MCP_PORT}"
    val browserBase = "http://127.0.0.1:${JarvisHubConfig.ISOLATED_BROWSER_MCP_PORT}"
    val deviceHeaders = remember(deviceId) {
        if (deviceId.isNotBlank()) mapOf("X-Device-Id" to deviceId) else emptyMap()
    }

    fun localSpec(name: String, base: String, path: String) =
        McpServerSpec(serverName = name, serverUrl = "$base$path", headers = deviceHeaders)

    val presets = remember(localBase, browserBase, deviceHeaders) {
        listOf(
            McpPresetTemplate("Custom Plugin MCP", "Expose tools from an extension", Icons.Outlined.Extension, AgySettingsColors.GeminiPurple, null),
            McpPresetTemplate("Plugin Developer", "Build & inspect extensions", Icons.Outlined.DeveloperMode, AgySettingsColors.GeminiPurple, localSpec("extension_developer", localBase, "/extension_developer")),
            McpPresetTemplate("Mini App Developer", "Manage local workspaces", Icons.Outlined.Apps, AgySettingsColors.GeminiBlue, localSpec("mini_app_developer", localBase, "/mini_app_developer")),
            McpPresetTemplate("Browser", "Control in-app Chrome tabs", Icons.Outlined.Language, AgySettingsColors.GeminiBlue, localSpec("browser_mcp", browserBase, "/browser")),
            McpPresetTemplate("Terminal", "Run commands in PTY shell", Icons.Outlined.Terminal, AgySettingsColors.QuotaGreen, localSpec("terminal_mcp", localBase, "/terminal")),
            McpPresetTemplate("Android", "Tap, type & read phone UI", Icons.Outlined.PhoneAndroid, AgySettingsColors.AccentTeal, localSpec("android_mcp", localBase, "/android")),
            McpPresetTemplate("Notes & To-dos", "Read & edit local notes", Icons.Outlined.EditNote, AgySettingsColors.Warning, localSpec("notes_mcp", localBase, "/notes")),
            McpPresetTemplate("Jarvis", "5-Tier memory & controls", Icons.Outlined.RecordVoiceOver, AgySettingsColors.AccentTeal, localSpec("jarvis_mcp", localBase, "/jarvis")),
            McpPresetTemplate("Theme", "List & switch color palettes", Icons.Outlined.Palette, AgySettingsColors.GeminiBlue, localSpec("theme_mcp", localBase, "/theme")),
            McpPresetTemplate(
                "Stitch",
                "Google Stitch UI · API key",
                Icons.Outlined.Palette,
                AgySettingsColors.AccentPink,
                McpServerSpec(serverName = "stitch", serverUrl = "https://stitch.googleapis.com/mcp", headers = mapOf("X-Goog-Api-Key" to ""))
            ),
            McpPresetTemplate("REA", "Reverse engineering APK", Icons.Outlined.BugReport, AgySettingsColors.AccentPink, null, onSpecialClick = onOpenRea)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .bouncyOverscroll()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Management Card (matches AntiGem's ~/.gemini/.../mcp_config.json card)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
                border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text(
                            text = "MCP Server Configuration",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgySettingsColors.TextPrimary
                        )
                        Text(
                            text = "~/.gemini/${JarvisHubConfig.ISOLATED_APP_DATA_DIR}/mcp_config.json",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AgySettingsColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { scope.launch { McpServersManager.refreshAll(context) } },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AgySettingsColors.CardBorder),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("mcp_test_refresh_btn")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AgySettingsColors.Terracotta)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = AgySettingsColors.TextPrimary)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("Test & Refresh", fontSize = 12.5.sp, color = AgySettingsColors.TextPrimary)
                        }

                        Button(
                            onClick = {
                                editingSpec = null
                                editingOriginalName = null
                                showAddEditDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.Terracotta),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("mcp_add_server_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Add Server", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    OutlinedButton(
                        onClick = { showCatalogDialog = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).testTag("browse_mcp_catalog_btn"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AgySettingsColors.Terracotta.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Outlined.Storefront, contentDescription = null, tint = AgySettingsColors.Terracotta, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Browse MCP Catalog / Marketplace (1-Click)", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AgySettingsColors.Terracotta)
                    }
                }
            }

            // Quick Presets & Templates 2-Column Grid
            Text(
                text = "Quick Presets & Templates",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = AgySettingsColors.TextSecondary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.chunked(2).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPresets.forEach { preset ->
                            val existing = preset.spec?.let { ps ->
                                servers.firstOrNull { it.serverName == ps.serverName || (ps.isRemote && it.spec.serverUrl == ps.serverUrl) }
                            }
                            McpPresetTile(
                                name = preset.name,
                                subtitle = preset.subtitle,
                                icon = preset.icon,
                                accent = preset.accent,
                                added = existing != null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = {
                                    when {
                                        preset.onSpecialClick != null -> preset.onSpecialClick.invoke()
                                        preset.spec == null -> chooseWebViewExtension = true
                                        else -> {
                                            editingSpec = existing?.spec ?: preset.spec
                                            editingOriginalName = existing?.serverName
                                            showAddEditDialog = true
                                        }
                                    }
                                }
                            )
                        }
                        if (rowPresets.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // Configured Servers Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Configured Servers (${servers.size})",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgySettingsColors.TextPrimary
                )
                val totalTools = servers.sumOf { it.tools.size }
                Text(
                    text = "$totalTools tools available",
                    fontSize = 11.5.sp,
                    color = AgySettingsColors.TextSecondary
                )
            }

            if (servers.isEmpty() && !isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
                    border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("No MCP Servers Configured", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                        Text("Tap any Quick Preset above, Add Server, or Browse Marketplace.", fontSize = 12.sp, color = AgySettingsColors.TextSecondary)
                    }
                }
            } else {
                servers.forEach { state ->
                    McpConfiguredServerCard(
                        state = state,
                        onToggle = { enabled ->
                            scope.launch { McpServersManager.toggleServer(context, state.serverName, enabled) }
                        },
                        onRefresh = {
                            scope.launch { McpServersManager.refreshSingleServer(context, state.serverName) }
                        },
                        onEdit = {
                            editingSpec = state.spec
                            editingOriginalName = state.serverName
                            showAddEditDialog = true
                        },
                        onRemove = { serverToDelete = state.serverName },
                        onTestTool = { tool ->
                            scope.launch {
                                val res = McpServersManager.callMcpTool(context, state.serverName, tool.name, JSONObject())
                                val text = res.fold(
                                    onSuccess = { it.toString(2) },
                                    onFailure = { "Error: ${it.message}" }
                                )
                                toolProbeOutput = "${state.serverName} / ${tool.name}" to text
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(36.dp))
        }

        // Floating toast status banner
        if (!statusMessage.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = AgySettingsColors.CardBgElevated,
                border = BorderStroke(1.dp, AgySettingsColors.QuotaGreen.copy(alpha = 0.5f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = AgySettingsColors.QuotaGreen, modifier = Modifier.size(16.dp))
                    Text(statusMessage.orEmpty(), fontSize = 12.5.sp, color = AgySettingsColors.TextPrimary, modifier = Modifier.weight(1f, fill = false))
                    IconButton(onClick = { McpServersManager.clearStatusMessage() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = AgySettingsColors.TextSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditMcpServerDialog(
            initialSpec = editingSpec,
            onDismiss = { showAddEditDialog = false },
            onSaveSpec = { spec ->
                scope.launch {
                    McpServersManager.upsertServer(context, spec, editingOriginalName)
                    showAddEditDialog = false
                }
            },
            onSaveRawJson = { name, rawJson ->
                scope.launch {
                    McpServersManager.upsertFromRawJson(context, name, rawJson, editingOriginalName)
                    showAddEditDialog = false
                }
            }
        )
    }

    if (showCatalogDialog) {
        CascadeMcpCatalogDialog(onDismiss = { showCatalogDialog = false })
    }

    if (chooseWebViewExtension) {
        BrowserExtensionManager.init(context)
        val extensions by BrowserExtensionManager.extensions.collectAsState()
        AlertDialog(
            onDismissRequest = { chooseWebViewExtension = false },
            containerColor = AgySettingsColors.CardBg,
            title = { Text("Expose Extension MCP", color = AgySettingsColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                if (extensions.isEmpty()) {
                    Text("No browser extensions installed yet.", color = AgySettingsColors.TextSecondary, fontSize = 13.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        extensions.forEach { ext ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                    editingSpec = localSpec("ext_${ext.id}", localBase, "/extension/${ext.id}")
                                    editingOriginalName = null
                                    chooseWebViewExtension = false
                                    showAddEditDialog = true
                                },
                                color = AgySettingsColors.CardBgElevated,
                                border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(ext.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                                    Text("${ext.tools.size} MCP tool(s) · /extension/${ext.id}", fontSize = 11.sp, color = AgySettingsColors.TextSecondary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { chooseWebViewExtension = false }) { Text("Close", color = AgySettingsColors.TextSecondary) }
            }
        )
    }

    serverToDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            containerColor = AgySettingsColors.CardBg,
            title = { Text("Remove '$name'?", color = AgySettingsColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This removes '$name' from mcp_config.json immediately.", color = AgySettingsColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { McpServersManager.deleteServer(context, name) }
                    serverToDelete = null
                }) { Text("Remove", color = AgySettingsColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) { Text("Cancel", color = AgySettingsColors.TextSecondary) }
            }
        )
    }

    toolProbeOutput?.let { (title, output) ->
        AlertDialog(
            onDismissRequest = { toolProbeOutput = null },
            containerColor = AgySettingsColors.CardBg,
            title = { Text(title, color = AgySettingsColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = {
                Text(
                    text = output,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AgySettingsColors.QuotaGreen,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { toolProbeOutput = null }) { Text("Close", color = AgySettingsColors.Terracotta) }
            }
        )
    }
}

@Composable
private fun McpPresetTile(
    name: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    added: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("mcp_preset_${name.lowercase().replace(Regex("[^a-z0-9]+"), "_")}"),
        shape = RoundedCornerShape(14.dp),
        color = AgySettingsColors.CardBg,
        border = BorderStroke(1.dp, if (added) accent.copy(alpha = 0.45f) else AgySettingsColors.CardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
                }
                if (added) {
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape).background(AgySettingsColors.QuotaGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Configured", tint = AgySettingsColors.QuotaGreen, modifier = Modifier.size(13.dp))
                    }
                }
            }
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 11.sp, color = AgySettingsColors.TextSecondary, maxLines = 2, lineHeight = 14.sp, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun McpConfiguredServerCard(
    state: McpServerState,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onTestTool: (McpToolInfo) -> Unit
) {
    var toolsExpanded by remember { mutableStateOf(false) }
    var issueExpanded by remember { mutableStateOf(false) }

    val statusColor = when (state.status) {
        "READY" -> AgySettingsColors.QuotaGreen
        "NEEDS_AUTH" -> AgySettingsColors.Warning
        "DISABLED" -> AgySettingsColors.TextSecondary
        else -> AgySettingsColors.Danger
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
        border = BorderStroke(1.dp, if (state.isEnabled) statusColor.copy(alpha = 0.35f) else AgySettingsColors.CardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(state.serverName, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Surface(shape = RoundedCornerShape(6.dp), color = AgySettingsColors.CardBgElevated) {
                            Text(
                                text = if (state.spec.isRemote) "SSE" else "Stdio",
                                fontSize = 10.sp,
                                color = AgySettingsColors.Info,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.16f)) {
                            Text(
                                text = state.status,
                                fontSize = 10.sp,
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = if (state.spec.isRemote) state.spec.serverUrl
                        else "${state.spec.command} ${state.spec.args.joinToString(" ")}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AgySettingsColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = state.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AgySettingsColors.QuotaGreen
                    )
                )
            }

            if (!state.error.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { issueExpanded = !issueExpanded },
                    shape = RoundedCornerShape(10.dp),
                    color = AgySettingsColors.Danger.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, AgySettingsColors.Danger.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AgySettingsColors.Danger, modifier = Modifier.size(15.dp))
                            Text("Connection Issue", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AgySettingsColors.Danger, modifier = Modifier.weight(1f))
                            Icon(if (issueExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = AgySettingsColors.Danger, modifier = Modifier.size(16.dp))
                        }
                        AnimatedVisibility(visible = issueExpanded) {
                            Text(state.error.orEmpty(), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AgySettingsColors.TextPrimary, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }

            if (state.tools.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { toolsExpanded = !toolsExpanded },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.24f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = AgySettingsColors.QuotaGreen, modifier = Modifier.size(14.dp))
                            Text("${state.tools.size} Discovered Tools", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AgySettingsColors.QuotaGreen, modifier = Modifier.weight(1f))
                            Icon(if (toolsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = AgySettingsColors.TextSecondary, modifier = Modifier.size(16.dp))
                        }
                        AnimatedVisibility(visible = toolsExpanded) {
                            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.tools.forEach { tool ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AgySettingsColors.CardBg).padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(tool.name, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = AgySettingsColors.Info)
                                            if (tool.description.isNotBlank()) {
                                                Text(
                                                    text = tool.description,
                                                    fontSize = 11.sp,
                                                    lineHeight = 15.sp,
                                                    color = AgySettingsColors.TextSecondary,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                        IconButton(onClick = { onTestTool(tool) }, modifier = Modifier.size(34.dp)) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Test Tool", tint = AgySettingsColors.QuotaGreen, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp), tint = AgySettingsColors.Info)
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh", fontSize = 12.sp, color = AgySettingsColors.Info)
                }
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = AgySettingsColors.TextPrimary)
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", fontSize = 12.sp, color = AgySettingsColors.TextPrimary)
                }
                TextButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp), tint = AgySettingsColors.Danger)
                    Spacer(Modifier.width(4.dp))
                    Text("Remove", fontSize = 12.sp, color = AgySettingsColors.Danger)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditMcpServerDialog(
    initialSpec: McpServerSpec?,
    onDismiss: () -> Unit,
    onSaveSpec: (McpServerSpec) -> Unit,
    onSaveRawJson: (String, String) -> Unit
) {
    var modeTab by remember {
        mutableIntStateOf(if (initialSpec?.isRemote == true) 1 else 0) // 0=Stdio, 1=SSE, 2=Raw
    }
    var serverName by remember { mutableStateOf(initialSpec?.serverName ?: "") }
    var command by remember { mutableStateOf(initialSpec?.command ?: "npx") }
    var argsText by remember { mutableStateOf(initialSpec?.args?.joinToString("\n") ?: "") }
    var envText by remember { mutableStateOf(initialSpec?.env?.entries?.joinToString("\n") { "${it.key}=${it.value}" } ?: "") }
    var serverUrl by remember { mutableStateOf(initialSpec?.serverUrl ?: "") }
    var headersText by remember { mutableStateOf(initialSpec?.headers?.entries?.joinToString("\n") { "${it.key}=${it.value}" } ?: "") }
    var rawJson by remember { mutableStateOf("") }

    fun syncToRaw(): String {
        val obj = JSONObject()
        if (modeTab == 1 || (modeTab == 2 && serverUrl.isNotBlank())) {
            obj.put("serverUrl", serverUrl.trim())
            val hObj = JSONObject()
            headersText.lines().forEach { l ->
                val idx = l.indexOf('=')
                if (idx > 0) hObj.put(l.substring(0, idx).trim(), l.substring(idx + 1).trim())
            }
            if (hObj.length() > 0) obj.put("headers", hObj)
        } else {
            obj.put("command", command.trim())
            obj.put("args", JSONArray(argsText.lines().map { it.trim() }.filter { it.isNotEmpty() }))
            val eObj = JSONObject()
            envText.lines().forEach { l ->
                val idx = l.indexOf('=')
                if (idx > 0) eObj.put(l.substring(0, idx).trim(), l.substring(idx + 1).trim())
            }
            if (eObj.length() > 0) obj.put("env", eObj)
        }
        return obj.toString(2)
    }

    LaunchedEffect(Unit) {
        rawJson = syncToRaw()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 660.dp),
            shape = RoundedCornerShape(20.dp),
            color = AgySettingsColors.BgCanvas,
            border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (initialSpec != null) "Edit MCP Server" else "Add MCP Server",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgySettingsColors.TextPrimary
                )

                SecondaryTabRow(
                    selectedTabIndex = modeTab,
                    containerColor = AgySettingsColors.CardBg,
                    contentColor = AgySettingsColors.Terracotta
                ) {
                    Tab(selected = modeTab == 0, onClick = { modeTab = 0 }, text = { Text("Stdio") })
                    Tab(selected = modeTab == 1, onClick = { modeTab = 1 }, text = { Text("SSE / HTTP") })
                    Tab(
                        selected = modeTab == 2,
                        onClick = {
                            rawJson = syncToRaw()
                            modeTab = 2
                        },
                        text = { Text("Raw JSON") }
                    )
                }

                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    label = { Text("Server Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgySettingsColors.Terracotta,
                        unfocusedBorderColor = AgySettingsColors.CardBorder,
                        focusedTextColor = AgySettingsColors.TextPrimary,
                        unfocusedTextColor = AgySettingsColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("mcp_dialog_name")
                )

                when (modeTab) {
                    0 -> {
                        OutlinedTextField(
                            value = command,
                            onValueChange = { command = it },
                            label = { Text("Command (e.g. npx, node, python3)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgySettingsColors.Terracotta,
                                unfocusedBorderColor = AgySettingsColors.CardBorder,
                                focusedTextColor = AgySettingsColors.TextPrimary,
                                unfocusedTextColor = AgySettingsColors.TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = argsText,
                            onValueChange = { argsText = it },
                            label = { Text("Arguments (one per line)") },
                            minLines = 2,
                            maxLines = 5,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgySettingsColors.Terracotta,
                                unfocusedBorderColor = AgySettingsColors.CardBorder,
                                focusedTextColor = AgySettingsColors.TextPrimary,
                                unfocusedTextColor = AgySettingsColors.TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = envText,
                            onValueChange = { envText = it },
                            label = { Text("Environment (KEY=VALUE, one per line)") },
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgySettingsColors.Terracotta,
                                unfocusedBorderColor = AgySettingsColors.CardBorder,
                                focusedTextColor = AgySettingsColors.TextPrimary,
                                unfocusedTextColor = AgySettingsColors.TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    1 -> {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("Server URL (http://... or https://...)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgySettingsColors.Terracotta,
                                unfocusedBorderColor = AgySettingsColors.CardBorder,
                                focusedTextColor = AgySettingsColors.TextPrimary,
                                unfocusedTextColor = AgySettingsColors.TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("mcp_dialog_url")
                        )
                        OutlinedTextField(
                            value = headersText,
                            onValueChange = { headersText = it },
                            label = { Text("Headers (Key=Value, one per line)") },
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgySettingsColors.Terracotta,
                                unfocusedBorderColor = AgySettingsColors.CardBorder,
                                focusedTextColor = AgySettingsColors.TextPrimary,
                                unfocusedTextColor = AgySettingsColors.TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {
                        OutlinedTextField(
                            value = rawJson,
                            onValueChange = { rawJson = it },
                            label = { Text("Raw MCP Server Config JSON") },
                            minLines = 6,
                            maxLines = 12,
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgySettingsColors.Terracotta,
                                unfocusedBorderColor = AgySettingsColors.CardBorder,
                                focusedTextColor = AgySettingsColors.TextPrimary,
                                unfocusedTextColor = AgySettingsColors.TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = AgySettingsColors.TextSecondary) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (modeTab == 2) {
                                onSaveRawJson(serverName.trim(), rawJson)
                            } else if (modeTab == 1) {
                                val hMap = headersText.lines().mapNotNull { l ->
                                    val i = l.indexOf('=')
                                    if (i > 0) l.substring(0, i).trim() to l.substring(i + 1).trim() else null
                                }.toMap()
                                onSaveSpec(McpServerSpec(serverName = serverName.trim(), serverUrl = serverUrl.trim(), headers = hMap))
                            } else {
                                val aList = argsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                val eMap = envText.lines().mapNotNull { l ->
                                    val i = l.indexOf('=')
                                    if (i > 0) l.substring(0, i).trim() to l.substring(i + 1).trim() else null
                                }.toMap()
                                onSaveSpec(McpServerSpec(serverName = serverName.trim(), command = command.trim(), args = aList, env = eMap))
                            }
                        },
                        enabled = serverName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.Terracotta),
                        modifier = Modifier.testTag("mcp_dialog_save_btn")
                    ) {
                        Text("Save & Connect")
                    }
                }
            }
        }
    }
}
