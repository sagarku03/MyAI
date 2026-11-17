package com.jarvisai.assistant.ui.settings

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.GroupWork
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.jarvisai.assistant.agent.CommandsPermissionManager
import com.jarvisai.assistant.data.remote.JarvisHubConfig
import com.jarvisai.assistant.mcp.McpServersManager
import com.jarvisai.assistant.mcp.server.AndroidLocalBridgeServer
import com.jarvisai.assistant.model.LLMProvider
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.security.ManageSpaceActivity
import com.jarvisai.assistant.skills.SkillsAndPluginsManager
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalServerManager
import com.jarvisai.assistant.theme.ThemeTerminal
import com.jarvisai.assistant.ui.components.ProvideBouncyOverscroll
import com.jarvisai.assistant.ui.components.bouncyOverscroll
import com.jarvisai.assistant.ui.screens.AdbConnectScreen
import com.jarvisai.assistant.ui.screens.ConnectPcScreen
import com.jarvisai.assistant.ui.screens.PluginsManagementScreen
import kotlinx.coroutines.launch

enum class SettingsSection(val title: String, val subtitle: String) {
    MAIN("Settings & Preferences", "Configure Jarvis AI, Antigravity, MCP & device tools"),
    THEMES("Themes", "Color palettes for Terminal, Code IDE & HUD"),
    APPEARANCE("Appearance", "Display mode & quick theme swatches"),
    SERVERS("Servers & Connectivity", "Local Antigravity Hub, MCP & Agent Bridges"),
    CONNECT_PC("Connect to PC", "Mirror your desktop workspace over ADB / Wi-Fi"),
    AGENTS("Agents & AI Models", "Choose active LLM backends and API Vault"),
    ANTIGRAVITY("Antigravity", "CLI, MCP servers, skills & plugins, commands & permissions"),
    AGY_CLI("Antigravity CLI", "On-device agy daemon binary and hub status"),
    AGY_CLOUD("Cloud Agents", "CloudCode PA & remote Antigravity execution"),
    MCP("MCP Servers", "Model Context Protocol tools & integrations"),
    SKILLS_PLUGINS("Skills & Plugins", "Agent SKILL.md capabilities, Google Store & extensions"),
    COMMANDS("Commands & Permissions", "Auto-run policy, sandbox mode & command rules"),
    JARVIS("Jarvis Voice & Memory", "Voice wake-word, companion mode & neural memory"),
    WEBVIEW_EXTENSIONS("WebView Extensions", "Tampermonkey userscripts, CRX host & plugin repos"),
    SLASH_COMMANDS("Slash Commands", "Custom /commands synced to ~/.claude/commands/*.md"),
    GLOBAL_CONTEXT("Global Context & Rules", "CLAUDE.md / AGENTS.md engineering instructions"),
    TERMINAL("Terminal & Shell", "Local Linux rootfs installer & PTY toolchain"),
    X11_DESKTOP("X11 Desktop Bridge", "Linux graphical desktop with Termux:X11 & VNC"),
    ADB_CONNECT("ADB Connect", "Wireless ADB debugging & mDNS pairing"),
    ANDROID_AUTOMATION("Android Automation", "Accessibility UI tree & gesture automation"),
    REA("REA · Reverse Engineering", "APK, Flutter & native binary analysis tools"),
    REA_GROUPS("REA Tool Groups", "Configure exposed REA tool groups"),
    DATA_PROTECTION("Data Loss Protection", "Granular cache cleanup & storage guard"),
    CHANGELOG("What's New", "Complete build history and release notes"),
    ABOUT("About & Info", "Version, device environment & bridge ports");

    val parent: SettingsSection
        get() = when (this) {
            AGY_CLI, AGY_CLOUD, MCP, SKILLS_PLUGINS, COMMANDS -> ANTIGRAVITY
            REA_GROUPS -> REA
            CHANGELOG -> ABOUT
            else -> MAIN
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPreferencesScreen(
    settings: UserSettings,
    userEmail: String? = null,
    initialSection: SettingsSection = SettingsSection.MAIN,
    onBack: () -> Unit,
    onOpenJarvisGeneralSettings: () -> Unit,
    onOpenApiVault: () -> Unit,
    onOpenOnboarding: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentSection by remember(initialSection) { mutableStateOf(initialSection) }
    var showLocalToolsPopup by remember { mutableStateOf(false) }

    val mcpServers by McpServersManager.servers.collectAsState()
    val skills by SkillsAndPluginsManager.skills.collectAsState()
    val plugins by SkillsAndPluginsManager.installedPlugins.collectAsState()
    val policyState by CommandsPermissionManager.state.collectAsState()
    val agyDiagnostics by LocalServerManager.agyDiagnostics.collectAsState()

    LaunchedEffect(Unit) {
        McpServersManager.refreshAll(context)
        SkillsAndPluginsManager.refreshSkills(context)
        SkillsAndPluginsManager.refreshInstalledPlugins(context)
        CommandsPermissionManager.load(context)
        LocalServerManager.refreshDiagnostics(context)
    }

    BackHandler(enabled = currentSection != SettingsSection.MAIN) {
        currentSection = currentSection.parent
    }

    // Some sub-screens provide their own top bar cleanly:
    when (currentSection) {
        SettingsSection.THEMES -> {
            ThemeGallery(onBack = { currentSection = SettingsSection.MAIN })
            return
        }
        SettingsSection.CONNECT_PC -> {
            ConnectPcScreen(onNavigateBack = { currentSection = SettingsSection.MAIN })
            return
        }
        SettingsSection.WEBVIEW_EXTENSIONS -> {
            PluginsManagementScreen(onNavigateBack = { currentSection = SettingsSection.MAIN })
            return
        }
        SettingsSection.SLASH_COMMANDS -> {
            CustomSlashCommandsScreen(onBack = { currentSection = SettingsSection.MAIN })
            return
        }
        SettingsSection.GLOBAL_CONTEXT -> {
            GlobalContextScreen(onBack = { currentSection = SettingsSection.MAIN })
            return
        }
        SettingsSection.TERMINAL -> {
            FullScreenLocalToolsInstaller(onBack = { currentSection = SettingsSection.MAIN })
            return
        }
        SettingsSection.X11_DESKTOP -> {
            X11DesktopScreen(onBack = { currentSection = SettingsSection.MAIN })
            return
        }
        SettingsSection.ADB_CONNECT -> {
            AdbConnectScreen(onNavigateBack = { currentSection = SettingsSection.MAIN })
            return
        }
        SettingsSection.ANDROID_AUTOMATION -> {
            AndroidAutomationScreen(onBack = { currentSection = SettingsSection.MAIN })
            return
        }
        SettingsSection.REA -> {
            ReaScreen(
                onBack = { currentSection = SettingsSection.MAIN },
                onOpenGroups = { currentSection = SettingsSection.REA_GROUPS }
            )
            return
        }
        SettingsSection.REA_GROUPS -> {
            ReaGroupsScreen(onBack = { currentSection = SettingsSection.REA })
            return
        }
        SettingsSection.CHANGELOG -> {
            ChangelogScreen(onBack = { currentSection = SettingsSection.ABOUT })
            return
        }
        else -> Unit
    }

    ProvideBouncyOverscroll {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            containerColor = AgySettingsColors.BgCanvas,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentSection.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgySettingsColors.TextPrimary
                            )
                            if (currentSection != SettingsSection.MAIN) {
                                Text(
                                    text = currentSection.subtitle,
                                    fontSize = 11.sp,
                                    color = AgySettingsColors.TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (currentSection == SettingsSection.MAIN) onBack()
                                else currentSection = currentSection.parent
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("settings_prefs_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = AgySettingsColors.TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AgySettingsColors.BgCanvas)
                )
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = AgySettingsColors.BgCanvas
            ) {
                when (currentSection) {
                    SettingsSection.MAIN -> MainSettingsMenu(
                        settings = settings,
                        activeMcpCount = mcpServers.count { it.isEnabled },
                        bridgeActive = AndroidLocalBridgeServer.instance.isActive(),
                        hubReachable = agyDiagnostics.agyHubReachable,
                        linuxInstalled = LocalEnvironmentManager.isEnvironmentInstalled(context),
                        userEmail = userEmail,
                        onNavigate = { sec ->
                            when (sec) {
                                SettingsSection.JARVIS -> onOpenJarvisGeneralSettings()
                                SettingsSection.DATA_PROTECTION -> {
                                    runCatching {
                                        context.startActivity(Intent(context, ManageSpaceActivity::class.java))
                                    }
                                }
                                else -> currentSection = sec
                            }
                        }
                    )

                    SettingsSection.ANTIGRAVITY -> {
                        val activeMcp = mcpServers.count { it.isEnabled }
                        val (mcpVal, mcpCol) = when {
                            mcpServers.isEmpty() -> "None" to null
                            activeMcp > 0 -> "$activeMcp active" to AgySettingsColors.QuotaGreen
                            else -> "All off" to AgySettingsColors.Warning
                        }
                        val policyVal = policyState.commandExecutionPolicy.label
                        val policyCol = when {
                            policyVal == "Auto-run" -> AgySettingsColors.QuotaGreen
                            policyVal == "Smart safety" -> AgySettingsColors.AccentTeal
                            else -> AgySettingsColors.Warning
                        }
                        SettingsRowList(
                            groups = listOf(
                                listOf(
                                    SettingsRowItem(
                                        Icons.Outlined.Terminal,
                                        AgySettingsColors.GeminiBlue,
                                        SettingsSection.AGY_CLI.title,
                                        if (agyDiagnostics.agyBinaryFound) "Installed" else "Not found",
                                        if (agyDiagnostics.agyBinaryFound) AgySettingsColors.QuotaGreen else AgySettingsColors.Warning
                                    ) { currentSection = SettingsSection.AGY_CLI },
                                    SettingsRowItem(
                                        Icons.Outlined.Cloud,
                                        AgySettingsColors.GeminiBlue,
                                        SettingsSection.AGY_CLOUD.title,
                                        if (!userEmail.isNullOrBlank()) "Signed in" else "Ready",
                                        AgySettingsColors.QuotaGreen
                                    ) { currentSection = SettingsSection.AGY_CLOUD }
                                ),
                                listOf(
                                    SettingsRowItem(
                                        Icons.Outlined.Hub,
                                        AgySettingsColors.GeminiBlue,
                                        SettingsSection.MCP.title,
                                        mcpVal,
                                        mcpCol
                                    ) { currentSection = SettingsSection.MCP },
                                    SettingsRowItem(
                                        Icons.Outlined.AutoAwesome,
                                        AgySettingsColors.GeminiPurple,
                                        SettingsSection.SKILLS_PLUGINS.title,
                                        "${skills.size} skills · ${plugins.size} plugins"
                                    ) { currentSection = SettingsSection.SKILLS_PLUGINS },
                                    SettingsRowItem(
                                        Icons.Outlined.Security,
                                        AgySettingsColors.Warning,
                                        SettingsSection.COMMANDS.title,
                                        policyVal,
                                        policyCol
                                    ) { currentSection = SettingsSection.COMMANDS }
                                )
                            )
                        )
                    }

                    SettingsSection.MCP -> McpSettingsSubScreen(onOpenRea = { currentSection = SettingsSection.REA })
                    SettingsSection.SKILLS_PLUGINS -> SkillsAndPluginsSubScreen(onBack = { currentSection = SettingsSection.ANTIGRAVITY }, embedded = true)
                    SettingsSection.COMMANDS -> CommandsSettingsSubScreen()
                    SettingsSection.APPEARANCE -> AppearanceSubScreen()
                    SettingsSection.SERVERS -> ServersConnectivitySubScreen(
                        agyDiagnostics = agyDiagnostics,
                        onRefresh = { scope.launch { LocalServerManager.refreshDiagnostics(context) } },
                        onStartBridge = {
                            AndroidLocalBridgeServer.instance.start(context)
                            Toast.makeText(context, "Embedded MCP Bridge started on :${JarvisHubConfig.ISOLATED_MCP_PORT}", Toast.LENGTH_SHORT).show()
                        },
                        onStartHub = {
                            scope.launch {
                                LocalServerManager.ensureAgyHubAvailable(context)
                                LocalServerManager.refreshDiagnostics(context)
                            }
                        }
                    )
                    SettingsSection.AGENTS -> AgentsHubSubScreen(
                        settings = settings,
                        onOpenApiVault = onOpenApiVault,
                        onOpenLocalTools = { showLocalToolsPopup = true }
                    )
                    SettingsSection.AGY_CLI -> AgyCliStatusSubScreen(
                        diagnostics = agyDiagnostics,
                        onRefresh = { scope.launch { LocalServerManager.refreshDiagnostics(context) } },
                        onOpenInstaller = { currentSection = SettingsSection.TERMINAL }
                    )
                    SettingsSection.AGY_CLOUD -> AgyCloudSubScreen(userEmail = userEmail, onOpenApiVault = onOpenApiVault)
                    SettingsSection.ABOUT -> AboutInfoSubScreen(
                        onOpenChangelog = { currentSection = SettingsSection.CHANGELOG },
                        onOpenOnboarding = onOpenOnboarding
                    )
                    else -> Unit
                }
            }
        }
    }

    if (showLocalToolsPopup) {
        LocalToolsInstallDialog(
            onDismiss = { showLocalToolsPopup = false },
            onExpandFullScreen = {
                showLocalToolsPopup = false
                currentSection = SettingsSection.TERMINAL
            }
        )
    }
}

@Composable
private fun MainSettingsMenu(
    settings: UserSettings,
    activeMcpCount: Int,
    bridgeActive: Boolean,
    hubReachable: Boolean,
    linuxInstalled: Boolean,
    userEmail: String?,
    onNavigate: (SettingsSection) -> Unit
) {
    fun row(
        sec: SettingsSection,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        tint: Color,
        value: String? = null,
        valueColor: Color? = null
    ) = SettingsRowItem(icon = icon, tint = tint, title = sec.title, value = value, valueColor = valueColor) {
        onNavigate(sec)
    }

    val serverLabel = when {
        hubReachable && bridgeActive -> "Hub + MCP"
        bridgeActive -> "Local MCP"
        hubReachable -> "AGY Hub"
        else -> "Standby"
    }
    val serverColor = if (bridgeActive || hubReachable) AgySettingsColors.QuotaGreen else AgySettingsColors.Warning

    val providerLabel = when (settings.selectedEngine) {
        LLMProvider.ANTIGRAVITY -> "Antigravity"
        LLMProvider.GEMINI_API -> "Gemini API"
    }

    SettingsRowList(
        groups = listOf(
            listOf(
                row(SettingsSection.THEMES, Icons.Outlined.Palette, AgySettingsColors.AccentPink, ThemeTerminal.currentPalette.name),
                row(SettingsSection.APPEARANCE, Icons.Outlined.Tune, AgySettingsColors.GeminiPurple),
                row(SettingsSection.SERVERS, Icons.Outlined.Dns, AgySettingsColors.Info, serverLabel, serverColor),
                row(SettingsSection.CONNECT_PC, Icons.Outlined.Computer, AgySettingsColors.AccentIndigo)
            ),
            listOf(
                row(SettingsSection.AGENTS, Icons.Outlined.SmartToy, AgySettingsColors.GeminiBlue, providerLabel),
                row(
                    SettingsSection.ANTIGRAVITY,
                    Icons.Outlined.RocketLaunch,
                    AgySettingsColors.GeminiBlue,
                    if (activeMcpCount > 0) "$activeMcpCount MCP" else "Ready",
                    if (activeMcpCount > 0) AgySettingsColors.QuotaGreen else null
                ),
                row(
                    SettingsSection.JARVIS,
                    Icons.Outlined.RecordVoiceOver,
                    AgySettingsColors.AccentTeal,
                    settings.assistantName
                )
            ),
            listOf(
                row(SettingsSection.WEBVIEW_EXTENSIONS, Icons.Outlined.Extension, AgySettingsColors.GeminiPurple),
                row(SettingsSection.SLASH_COMMANDS, Icons.Outlined.Bolt, AgySettingsColors.Warning),
                row(SettingsSection.GLOBAL_CONTEXT, Icons.Outlined.Description, AgySettingsColors.Info)
            ),
            listOf(
                row(
                    SettingsSection.TERMINAL,
                    Icons.Outlined.Terminal,
                    AgySettingsColors.AccentIndigo,
                    if (linuxInstalled) "Installed" else "Setup",
                    if (linuxInstalled) AgySettingsColors.QuotaGreen else AgySettingsColors.Warning
                ),
                row(SettingsSection.X11_DESKTOP, Icons.Outlined.DesktopWindows, AgySettingsColors.AccentIndigo),
                row(SettingsSection.ADB_CONNECT, Icons.Outlined.WifiTethering, AgySettingsColors.Info),
                row(SettingsSection.ANDROID_AUTOMATION, Icons.Outlined.AccessibilityNew, AgySettingsColors.QuotaGreen),
                row(SettingsSection.REA, Icons.Outlined.BugReport, AgySettingsColors.AccentPink)
            ),
            listOf(
                row(SettingsSection.DATA_PROTECTION, Icons.Outlined.Security, AgySettingsColors.QuotaGreen),
                row(SettingsSection.ABOUT, Icons.Outlined.Info, AgySettingsColors.GeminiPurple, "v${BuildConfig.VERSION_NAME}")
            )
        )
    )
}

@Composable
private fun AppearanceSubScreen() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ThemeQuickPanel()
    }
}

@Composable
private fun ServersConnectivitySubScreen(
    agyDiagnostics: com.jarvisai.assistant.terminal.AgyDaemonDiagnostics,
    onRefresh: () -> Unit,
    onStartBridge: () -> Unit,
    onStartHub: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Embedded Jarvis Ports (Isolated)", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                Text("• Local MCP Bridge: 127.0.0.1:${JarvisHubConfig.ISOLATED_MCP_PORT}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AgySettingsColors.QuotaGreen)
                Text("• Browser CDP MCP: 127.0.0.1:${JarvisHubConfig.ISOLATED_BROWSER_MCP_PORT}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AgySettingsColors.Info)
                Text("• Antigravity Hub: 127.0.0.1:${agyDiagnostics.hubPort} (${if (agyDiagnostics.agyHubReachable) "Reachable" else "Offline"})", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = if (agyDiagnostics.agyHubReachable) AgySettingsColors.QuotaGreen else AgySettingsColors.Warning)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Button(onClick = onStartBridge, colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.QuotaGreen), modifier = Modifier.weight(1f)) {
                        Text("Restart MCP", fontSize = 12.sp)
                    }
                    Button(onClick = onStartHub, colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.GeminiBlue), modifier = Modifier.weight(1f)) {
                        Text("Wake AGY Hub", fontSize = 12.sp)
                    }
                }
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh Diagnostics", color = AgySettingsColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun AgentsHubSubScreen(
    settings: UserSettings,
    onOpenApiVault: () -> Unit,
    onOpenLocalTools: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Active LLM Provider: ${settings.selectedEngine.displayName}", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                Text("Model: ${settings.textModel}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AgySettingsColors.Info)
                Button(onClick = onOpenApiVault, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp), colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.GeminiBlue)) {
                    Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open API Vault & Model Switcher")
                }
                OutlinedButton(onClick = onOpenLocalTools, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)) {
                    Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Install Local CLI Agents (Claude, Codex, Node)")
                }
            }
        }
    }
}

@Composable
private fun AgyCliStatusSubScreen(
    diagnostics: com.jarvisai.assistant.terminal.AgyDaemonDiagnostics,
    onRefresh: () -> Unit,
    onOpenInstaller: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Antigravity CLI & Binary", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                Text("Binary Found: ${if (diagnostics.agyBinaryFound) "Yes (${diagnostics.agyBinaryPath})" else "No (using Native CloudCode + Kotlin MCP Engine)"}", fontSize = 12.sp, color = AgySettingsColors.TextSecondary)
                Text("MCP Config Provisioned: ${diagnostics.mcpConfigured}", fontSize = 12.sp, color = AgySettingsColors.TextSecondary)
                Text("Linux Core Installed: ${diagnostics.linuxCoreInstalled}", fontSize = 12.sp, color = AgySettingsColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) { Text("Refresh") }
                    Button(onClick = onOpenInstaller, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.GeminiBlue)) {
                        Text("Linux Toolchain")
                    }
                }
            }
        }
    }
}

@Composable
private fun AgyCloudSubScreen(userEmail: String?, onOpenApiVault: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Google CloudCode PA & Antigravity OAuth", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                Text(
                    text = if (!userEmail.isNullOrBlank()) "Signed in as $userEmail" else "Sign in with Google Antigravity OAuth or configure an API key in API Vault.",
                    fontSize = 12.5.sp,
                    color = AgySettingsColors.TextSecondary
                )
                Button(onClick = onOpenApiVault, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp), colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.GeminiBlue)) {
                    Text("Configure Auth & Models")
                }
            }
        }
    }
}

@Composable
private fun AboutInfoSubScreen(
    onOpenChangelog: () -> Unit,
    onOpenOnboarding: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Jarvis AI", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontSize = 12.5.sp, color = AgySettingsColors.Info)
                Text("Device: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}", fontSize = 12.sp, color = AgySettingsColors.TextSecondary)
                Spacer(Modifier.height(6.dp))
                Button(onClick = onOpenChangelog, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp), colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.AccentIndigo)) {
                    Icon(Icons.Outlined.NewReleases, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Release Notes & Changelog")
                }
                OutlinedButton(onClick = onOpenOnboarding, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)) {
                    Text("Launch Onboarding Wizard", color = AgySettingsColors.TextPrimary)
                }
            }
        }
    }
}
