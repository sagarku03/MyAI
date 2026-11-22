package com.jarvisai.assistant.ui.settings

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.jarvisai.assistant.agent.CommandsPermissionManager
import com.jarvisai.assistant.data.preferences.TerminalLauncherManager
import com.jarvisai.assistant.data.remote.JarvisHubConfig
import com.jarvisai.assistant.mcp.McpServersManager
import com.jarvisai.assistant.mcp.server.AndroidLocalBridgeServer
import com.jarvisai.assistant.model.AntigravityExecutionMode
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
    MAIN("Settings & Preferences", "Configure Agentra, Antigravity, MCP & device tools"),
    THEMES("Themes", "Color palettes for Terminal, Code IDE & HUD"),
    APPEARANCE("Appearance", "Display mode, dual launcher icon & quick themes"),
    SERVERS("Servers & Connectivity", "Local Antigravity Hub, MCP & Agent Bridges"),
    CONNECT_PC("Connect to PC", "Mirror your desktop workspace over ADB / Wi-Fi"),
    AGENTS("Agents & AI Models", "Choose active LLM backends and API Vault"),
    ANTIGRAVITY("Antigravity", "CLI, MCP servers, skills & plugins, commands & permissions"),
    AGY_CLI("Antigravity CLI", "agy version and install, on this device or the server"),
    AGY_CLOUD("Cloud Agents", "CloudCode PA & remote Antigravity execution"),
    MCP("MCP Servers", "Model Context Protocol tools & integrations"),
    SKILLS_PLUGINS("Skills & Plugins", "Agent SKILL.md capabilities, Google Store & extensions"),
    COMMANDS("Commands & Permissions", "Auto-run policy, sandbox mode & command rules"),
    JARVIS("Agentra Voice & Memory", "Voice wake-word, companion mode & neural memory"),
    WEBVIEW_EXTENSIONS("WebView Extensions", "Tampermonkey userscripts, CRX host & plugin repos"),
    SLASH_COMMANDS("Slash Commands", "Custom /commands synced to ~/.claude/commands/*.md"),
    GLOBAL_CONTEXT("Global Context & Rules", "CLAUDE.md / AGENTS.md engineering instructions"),
    TERMINAL("Terminal & Shell", "Font, cursor, buffer, storage symlinks & Linux rootfs"),
    TERMINAL_INSTALLER("Toolchain Provisioner", "Automated bootstrap & local CLI packages"),
    X11_DESKTOP("X11 Desktop Bridge", "Linux graphical desktop with Termux:X11 & VNC"),
    CHROOT("Rooted Chroot", "Manage isolated Linux containers under /data/local/agentra"),
    GITHUB("GitHub & Git Clone", "Sign in to gh CLI, git identity & clone repositories"),
    STEM_RENDERING("STEM & Diagrams", "Chemistry, SMILES molecules & function plots"),
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
            TERMINAL_INSTALLER -> TERMINAL
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
    onOpenOnboarding: () -> Unit = {},
    onUpdateAntigravityExecutionMode: (AntigravityExecutionMode) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentSection by remember(initialSection) { mutableStateOf(initialSection) }
    var showLocalToolsPopup by remember { mutableStateOf(false) }
    var showNetworkInspectorDialog by remember { mutableStateOf(false) }

    val mcpServers by McpServersManager.servers.collectAsState()
    val skills by SkillsAndPluginsManager.skills.collectAsState()
    val plugins by SkillsAndPluginsManager.installedPlugins.collectAsState()
    val policyState by CommandsPermissionManager.state.collectAsState()
    val agyDiagnostics by LocalServerManager.agyDiagnostics.collectAsState()
    val agyCli by com.jarvisai.assistant.terminal.AgyCliManager.cliState.collectAsState()

    LaunchedEffect(Unit) {
        McpServersManager.refreshAll(context)
        SkillsAndPluginsManager.refreshSkills(context)
        SkillsAndPluginsManager.refreshInstalledPlugins(context)
        CommandsPermissionManager.load(context)
        LocalServerManager.refreshDiagnostics(context)
        com.jarvisai.assistant.terminal.AgyCliManager.refreshAgyCli(context)
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
        SettingsSection.TERMINAL_INSTALLER -> {
            FullScreenLocalToolsInstaller(onBack = { currentSection = SettingsSection.TERMINAL })
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
                    actions = {
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("settings_prefs_done_btn")
                        ) {
                            Text(
                                text = "Done",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AgySettingsColors.GeminiBlue
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
                        val execMode = settings.antigravityExecutionMode
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
                        val cliInstalled = agyCli?.installed == true || agyDiagnostics.agyBinaryFound
                        val cliVersionLabel = agyCli?.version?.takeIf { it.isNotBlank() }
                            ?: agyDiagnostics.agyVersion.takeIf { it.isNotBlank() }
                            ?: if (cliInstalled) "Installed" else "Not installed"
                        val cliRowVal = if (execMode.cliEnabled) cliVersionLabel else "OFF"
                        val cliColor = when {
                            !execMode.cliEnabled -> AgySettingsColors.TextSecondary.copy(alpha = 0.6f)
                            cliInstalled -> AgySettingsColors.QuotaGreen
                            else -> AgySettingsColors.Warning
                        }
                        val cloudRowVal = when {
                            !execMode.cloudCodePaEnabled -> "OFF"
                            !userEmail.isNullOrBlank() -> "Signed in"
                            else -> "Ready"
                        }
                        val cloudColor = if (execMode.cloudCodePaEnabled) AgySettingsColors.QuotaGreen else AgySettingsColors.TextSecondary.copy(alpha = 0.6f)

                        SettingsRowList(
                            header = {
                                AntigravityExecutionEngineCard(
                                    executionMode = execMode,
                                    hubReachable = agyDiagnostics.agyHubReachable,
                                    cliInstalled = cliInstalled,
                                    userEmail = userEmail,
                                    onModeSelected = { newMode ->
                                        onUpdateAntigravityExecutionMode(newMode)
                                        Toast.makeText(
                                            context,
                                            "Antigravity Engine: ${newMode.displayName}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onStartLocalHub = {
                                        scope.launch {
                                            LocalServerManager.ensureAgyHubAvailable(context)
                                            LocalServerManager.refreshDiagnostics(context)
                                        }
                                    },
                                    onOpenAuthVault = onOpenApiVault
                                )
                            },
                            groups = listOf(
                                listOf(
                                    SettingsRowItem(
                                        Icons.Outlined.Terminal,
                                        AgySettingsColors.GeminiBlue,
                                        SettingsSection.AGY_CLI.title,
                                        cliRowVal,
                                        cliColor
                                    ) { currentSection = SettingsSection.AGY_CLI },
                                    SettingsRowItem(
                                        Icons.Outlined.Cloud,
                                        AgySettingsColors.GeminiBlue,
                                        SettingsSection.AGY_CLOUD.title,
                                        cloudRowVal,
                                        cloudColor
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
                        },
                        onOpenNetworkInspector = { showNetworkInspectorDialog = true }
                    )
                    SettingsSection.GITHUB -> GitHubScreen()
                    SettingsSection.STEM_RENDERING -> StemRenderingScreen()
                    SettingsSection.CHROOT -> ChrootScreen()
                    SettingsSection.AGENTS -> AgentsHubSubScreen(
                        settings = settings,
                        onOpenApiVault = onOpenApiVault,
                        onOpenLocalTools = { showLocalToolsPopup = true }
                    )
                    SettingsSection.TERMINAL -> TerminalSettingsSubScreen(
                        onOpenInstaller = { currentSection = SettingsSection.TERMINAL_INSTALLER }
                    )
                    SettingsSection.AGY_CLI -> AgyCliStatusSubScreen(
                        executionMode = settings.antigravityExecutionMode,
                        diagnostics = agyDiagnostics,
                        onUpdateExecutionMode = onUpdateAntigravityExecutionMode,
                        onRefresh = { scope.launch { LocalServerManager.refreshDiagnostics(context) } },
                        onOpenInstaller = { currentSection = SettingsSection.TERMINAL_INSTALLER }
                    )
                    SettingsSection.AGY_CLOUD -> AgyCloudSubScreen(
                        executionMode = settings.antigravityExecutionMode,
                        userEmail = userEmail,
                        onUpdateExecutionMode = onUpdateAntigravityExecutionMode,
                        onOpenApiVault = onOpenApiVault
                    )
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
                currentSection = SettingsSection.TERMINAL_INSTALLER
            }
        )
    }

    if (showNetworkInspectorDialog) {
        com.jarvisai.assistant.ui.components.NetworkInspectorDialog(
            onDismiss = { showNetworkInspectorDialog = false }
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
                    settings.antigravityExecutionMode.shortBadge,
                    when (settings.antigravityExecutionMode) {
                        AntigravityExecutionMode.CLI_ONLY -> AgySettingsColors.QuotaGreen
                        AntigravityExecutionMode.CLOUDCODE_PA_ONLY -> AgySettingsColors.GeminiBlue
                        AntigravityExecutionMode.HYBRID_BOTH -> AgySettingsColors.AccentTeal
                    }
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
                row(SettingsSection.GITHUB, Icons.Outlined.Code, AgySettingsColors.GeminiBlue),
                row(SettingsSection.STEM_RENDERING, Icons.Outlined.Science, AgySettingsColors.AccentTeal),
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
                row(SettingsSection.CHROOT, Icons.Outlined.AdminPanelSettings, AgySettingsColors.Warning, "Root"),
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
    val context = LocalContext.current
    var launcherEnabled by remember { mutableStateOf(TerminalLauncherManager.isLauncherEnabled(context)) }
    var launcherStyle by remember { mutableStateOf(TerminalLauncherManager.getLauncherStyle(context)) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ThemeQuickPanel()

        // Dual App Icon · Standalone Terminal Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(
                1.dp,
                if (launcherEnabled) AgySettingsColors.QuotaGreen.copy(alpha = 0.5f) else AgySettingsColors.CardBorder
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dual App Icon · Standalone Terminal",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgySettingsColors.TextPrimary
                        )
                        Text(
                            text = "Add a separate terminal icon to your app drawer (shares ai.agentra native rootfs)",
                            fontSize = 11.5.sp,
                            color = AgySettingsColors.TextSecondary
                        )
                    }
                    Switch(
                        checked = launcherEnabled,
                        onCheckedChange = { checked ->
                            launcherEnabled = checked
                            TerminalLauncherManager.applyLauncherSetting(context, checked, launcherStyle)
                            TerminalLauncherManager.markPromptShown(context)
                            val msg = if (checked) "Standalone Terminal icon enabled" else "Standalone Terminal icon hidden"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("settings_terminal_launcher_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AgySettingsColors.QuotaGreen,
                            uncheckedThumbColor = AgySettingsColors.TextSecondary,
                            uncheckedTrackColor = AgySettingsColors.CardBgElevated
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isAgentraSelected = launcherStyle == TerminalLauncherManager.STYLE_AGENTRA_TERM
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clickable(enabled = launcherEnabled) {
                                launcherStyle = TerminalLauncherManager.STYLE_AGENTRA_TERM
                                TerminalLauncherManager.applyLauncherSetting(context, true, TerminalLauncherManager.STYLE_AGENTRA_TERM)
                                Toast.makeText(context, "Launcher icon set to Agentra Term", Toast.LENGTH_SHORT).show()
                            }
                            .testTag("settings_style_agentra_btn"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAgentraSelected && launcherEnabled) AgySettingsColors.GeminiBlue.copy(alpha = 0.14f) else AgySettingsColors.CardBgElevated,
                        border = BorderStroke(
                            1.dp,
                            if (isAgentraSelected && launcherEnabled) AgySettingsColors.GeminiBlue else AgySettingsColors.CardBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.jarvis_app_logo),
                                contentDescription = "Agentra Term icon",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Text(
                                text = "Agentra Term",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (launcherEnabled) AgySettingsColors.TextPrimary else AgySettingsColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val isTermuxSelected = launcherStyle == TerminalLauncherManager.STYLE_TERMUX
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clickable(enabled = launcherEnabled) {
                                launcherStyle = TerminalLauncherManager.STYLE_TERMUX
                                TerminalLauncherManager.applyLauncherSetting(context, true, TerminalLauncherManager.STYLE_TERMUX)
                                Toast.makeText(context, "Launcher icon set to Termux", Toast.LENGTH_SHORT).show()
                            }
                            .testTag("settings_style_termux_btn"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isTermuxSelected && launcherEnabled) AgySettingsColors.QuotaGreen.copy(alpha = 0.14f) else AgySettingsColors.CardBgElevated,
                        border = BorderStroke(
                            1.dp,
                            if (isTermuxSelected && launcherEnabled) AgySettingsColors.QuotaGreen else AgySettingsColors.CardBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_termux_foreground),
                                    contentDescription = "Classic Termux icon",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Text(
                                text = "Termux",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (launcherEnabled) AgySettingsColors.TextPrimary else AgySettingsColors.TextSecondary,
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

@Composable
private fun ServersConnectivitySubScreen(
    agyDiagnostics: com.jarvisai.assistant.terminal.AgyDaemonDiagnostics,
    onRefresh: () -> Unit,
    onStartBridge: () -> Unit,
    onStartHub: () -> Unit,
    onOpenNetworkInspector: () -> Unit
) {
    val inspectorEnabled by com.jarvisai.assistant.data.remote.inspector.NetworkInspectorManager.enabledFlow.collectAsState()
    val inspectorLogs by com.jarvisai.assistant.data.remote.inspector.NetworkInspectorManager.logs.collectAsState()

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
                Text("Embedded Agentra Ports (Isolated)", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                Text("• Local MCP Bridge: 127.0.0.1:${JarvisHubConfig.ISOLATED_MCP_PORT}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AgySettingsColors.QuotaGreen)
                Text("• Browser CDP MCP: 127.0.0.1:${JarvisHubConfig.ISOLATED_BROWSER_MCP_PORT}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AgySettingsColors.Info)
                Text("• Web Preview Port: 127.0.0.1:${JarvisHubConfig.ISOLATED_WEB_PREVIEW_PORT}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AgySettingsColors.AccentTeal)
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

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(1.dp, if (inspectorEnabled) AgySettingsColors.GeminiBlue.copy(alpha = 0.5f) else AgySettingsColors.CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.NetworkCheck, contentDescription = null, tint = AgySettingsColors.GeminiBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("HTTP / gRPC Network Inspector", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                            Text("${inspectorLogs.size} captured requests · Live OkHttp trace", fontSize = 11.5.sp, color = AgySettingsColors.TextSecondary)
                        }
                    }
                    Switch(
                        checked = inspectorEnabled,
                        onCheckedChange = { com.jarvisai.assistant.data.remote.inspector.NetworkInspectorManager.setCaptureEnabled(it) },
                        modifier = Modifier.testTag("settings_network_inspector_switch")
                    )
                }
                OutlinedButton(
                    onClick = onOpenNetworkInspector,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_open_network_inspector_btn")
                ) {
                    Text("Open Network Inspector", color = AgySettingsColors.GeminiBlue)
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

private fun resolveNextExecutionMode(
    current: AntigravityExecutionMode,
    cliChecked: Boolean = current.cliEnabled,
    cloudChecked: Boolean = current.cloudCodePaEnabled
): AntigravityExecutionMode = when {
    cliChecked && cloudChecked -> AntigravityExecutionMode.HYBRID_BOTH
    cliChecked && !cloudChecked -> AntigravityExecutionMode.CLI_ONLY
    !cliChecked && cloudChecked -> AntigravityExecutionMode.CLOUDCODE_PA_ONLY
    else -> if (current == AntigravityExecutionMode.CLI_ONLY) {
        AntigravityExecutionMode.CLOUDCODE_PA_ONLY
    } else {
        AntigravityExecutionMode.CLI_ONLY
    }
}

@Composable
private fun AntigravityExecutionEngineCard(
    executionMode: AntigravityExecutionMode,
    hubReachable: Boolean,
    cliInstalled: Boolean,
    userEmail: String?,
    onModeSelected: (AntigravityExecutionMode) -> Unit,
    onStartLocalHub: () -> Unit,
    onOpenAuthVault: () -> Unit
) {
    val activeAccent = when (executionMode) {
        AntigravityExecutionMode.CLI_ONLY -> AgySettingsColors.QuotaGreen
        AntigravityExecutionMode.CLOUDCODE_PA_ONLY -> AgySettingsColors.GeminiBlue
        AntigravityExecutionMode.HYBRID_BOTH -> AgySettingsColors.AccentTeal
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = AgySettingsColors.CardBg,
        border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("antigravity_execution_engine_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Execution Engine",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgySettingsColors.TextPrimary
                    )
                    Text(
                        text = "Control Antigravity CLI & CloudCode PA",
                        fontSize = 12.sp,
                        color = AgySettingsColors.TextSecondary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = activeAccent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(activeAccent)
                        )
                        Text(
                            text = executionMode.shortBadge,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = activeAccent
                        )
                    }
                }
            }

            // Switch 1: Antigravity CLI (Local Hub)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Terminal,
                    contentDescription = "Antigravity CLI Engine",
                    tint = if (executionMode.cliEnabled) AgySettingsColors.QuotaGreen else AgySettingsColors.TextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Antigravity CLI (Local Hub)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AgySettingsColors.TextPrimary
                    )
                    val cliSub = buildString {
                        append(
                            when {
                                hubReachable -> "Local Hub Online · Port 2235"
                                cliInstalled -> "CLI installed · Hub standby"
                                else -> "CLI binary not installed"
                            }
                        )
                        append(if (executionMode.cliEnabled) " · ON" else " · OFF")
                    }
                    Text(
                        text = cliSub,
                        fontSize = 11.5.sp,
                        color = if (executionMode.cliEnabled) AgySettingsColors.TextSecondary else AgySettingsColors.TextSecondary.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = executionMode.cliEnabled,
                    onCheckedChange = { checked ->
                        onModeSelected(
                            resolveNextExecutionMode(
                                current = executionMode,
                                cliChecked = checked
                            )
                        )
                    },
                    modifier = Modifier.testTag("switch_agy_cli_engine"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AgySettingsColors.QuotaGreen,
                        uncheckedThumbColor = AgySettingsColors.TextSecondary,
                        uncheckedTrackColor = AgySettingsColors.CardBgElevated
                    )
                )
            }

            HorizontalDivider(thickness = 0.6.dp, color = Color.White.copy(alpha = 0.08f))

            // Switch 2: CloudCode PA (Google Cloud)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Cloud,
                    contentDescription = "CloudCode PA Engine",
                    tint = if (executionMode.cloudCodePaEnabled) AgySettingsColors.GeminiBlue else AgySettingsColors.TextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CloudCode PA (Google Cloud)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AgySettingsColors.TextPrimary
                    )
                    val cloudSub = buildString {
                        append(if (!userEmail.isNullOrBlank()) userEmail else "Google OAuth needed")
                        append(if (executionMode.cloudCodePaEnabled) " · ON" else " · OFF")
                    }
                    Text(
                        text = cloudSub,
                        fontSize = 11.5.sp,
                        color = if (executionMode.cloudCodePaEnabled) AgySettingsColors.TextSecondary else AgySettingsColors.TextSecondary.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = executionMode.cloudCodePaEnabled,
                    onCheckedChange = { checked ->
                        onModeSelected(
                            resolveNextExecutionMode(
                                current = executionMode,
                                cloudChecked = checked
                            )
                        )
                    },
                    modifier = Modifier.testTag("switch_agy_cloudcode_engine"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AgySettingsColors.GeminiBlue,
                        uncheckedThumbColor = AgySettingsColors.TextSecondary,
                        uncheckedTrackColor = AgySettingsColors.CardBgElevated
                    )
                )
            }

            HorizontalDivider(thickness = 0.6.dp, color = Color.White.copy(alpha = 0.08f))

            // 3-Way Direct Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf(
                    Triple(AntigravityExecutionMode.CLI_ONLY, "CLI Only", "mode_agy_cli_only"),
                    Triple(AntigravityExecutionMode.CLOUDCODE_PA_ONLY, "CloudCode PA", "mode_agy_cloud_only"),
                    Triple(AntigravityExecutionMode.HYBRID_BOTH, "Both", "mode_agy_hybrid_both")
                )
                options.forEach { (mode, label, tag) ->
                    val selected = executionMode == mode
                    val pillAccent = when (mode) {
                        AntigravityExecutionMode.CLI_ONLY -> AgySettingsColors.QuotaGreen
                        AntigravityExecutionMode.CLOUDCODE_PA_ONLY -> AgySettingsColors.GeminiBlue
                        AntigravityExecutionMode.HYBRID_BOTH -> AgySettingsColors.AccentTeal
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) pillAccent.copy(alpha = 0.18f) else AgySettingsColors.CardBgElevated,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) pillAccent else AgySettingsColors.CardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onModeSelected(mode) }
                            .testTag(tag)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) pillAccent else AgySettingsColors.TextSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Active rule explanation box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = activeAccent.copy(alpha = 0.09f),
                border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when (executionMode) {
                        AntigravityExecutionMode.CLI_ONLY ->
                            "Strict CLI Mode: Sirf Local Antigravity CLI Hub chalega. CloudCode PA 100% disabled hai."
                        AntigravityExecutionMode.CLOUDCODE_PA_ONLY ->
                            "Strict CloudCode PA Mode: Sirf Google CloudCode PA chalega. Local Antigravity CLI 100% bypassed hai."
                        AntigravityExecutionMode.HYBRID_BOTH ->
                            "Both Active: Local Antigravity CLI primary chalega aur fallback/media ke liye CloudCode PA saath kaam karega."
                    },
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = AgySettingsColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }

            // Quick readiness action if active engine needs starting / sign-in
            if (executionMode == AntigravityExecutionMode.CLI_ONLY && !hubReachable) {
                OutlinedButton(
                    onClick = onStartLocalHub,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AgySettingsColors.Warning.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_start_local_hub_inline")
                ) {
                    Text(
                        text = "Local AGY Hub Offline · Tap to Start Hub",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AgySettingsColors.Warning
                    )
                }
            } else if (executionMode == AntigravityExecutionMode.CLOUDCODE_PA_ONLY && userEmail.isNullOrBlank()) {
                OutlinedButton(
                    onClick = onOpenAuthVault,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AgySettingsColors.GeminiBlue.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_open_cloudcode_auth_inline")
                ) {
                    Text(
                        text = "CloudCode PA Sign-In Required · Configure Auth",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AgySettingsColors.GeminiBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun AgyCliStatusSubScreen(
    executionMode: AntigravityExecutionMode,
    diagnostics: com.jarvisai.assistant.terminal.AgyDaemonDiagnostics,
    onUpdateExecutionMode: (AntigravityExecutionMode) -> Unit,
    onRefresh: () -> Unit,
    onOpenInstaller: () -> Unit
) {
    val context = LocalContext.current
    val cli by com.jarvisai.assistant.terminal.AgyCliManager.cliState.collectAsState()
    val error by com.jarvisai.assistant.terminal.AgyCliManager.error.collectAsState()
    val job by com.jarvisai.assistant.terminal.AgyCliManager.jobState.collectAsState()
    val checking by com.jarvisai.assistant.terminal.AgyCliManager.checking.collectAsState()
    val updatedTo by com.jarvisai.assistant.terminal.AgyCliManager.updatedTo.collectAsState()

    LaunchedEffect(Unit) {
        com.jarvisai.assistant.terminal.AgyCliManager.refreshAgyCli(context)
    }

    val running = job?.running == true
    val isInstalled = cli?.installed == true || diagnostics.agyBinaryFound
    val resolvedVersion = cli?.version?.takeIf { it.isNotBlank() }
        ?: diagnostics.agyVersion.takeIf { it.isNotBlank() }
        ?: ""
    val resolvedPath = cli?.path?.takeIf { it.isNotBlank() }
        ?: diagnostics.agyBinaryPath?.takeIf { it.isNotBlank() }
        ?: com.jarvisai.assistant.terminal.AgyCliManager.getInstallerAgyFile(context).absolutePath

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = AgySettingsColors.CardBg,
            border = BorderStroke(
                1.dp,
                if (executionMode.cliEnabled) AgySettingsColors.QuotaGreen.copy(alpha = 0.45f)
                else AgySettingsColors.CardBorder
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use Antigravity CLI Engine",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgySettingsColors.TextPrimary
                    )
                    Text(
                        text = "Current mode: ${executionMode.displayName}",
                        fontSize = 12.sp,
                        color = AgySettingsColors.TextSecondary
                    )
                }
                Switch(
                    checked = executionMode.cliEnabled,
                    onCheckedChange = { checked ->
                        onUpdateExecutionMode(
                            resolveNextExecutionMode(current = executionMode, cliChecked = checked)
                        )
                    },
                    modifier = Modifier.testTag("agy_cli_subscreen_toggle"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AgySettingsColors.QuotaGreen,
                        uncheckedThumbColor = AgySettingsColors.TextSecondary,
                        uncheckedTrackColor = AgySettingsColors.CardBgElevated
                    )
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = AgySettingsColors.CardBg,
            border = BorderStroke(1.dp, AgySettingsColors.CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("agy_cli_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Antigravity CLI (agy)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AgySettingsColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = AgySettingsColors.GeminiBlue
                        )
                    } else {
                        IconButton(
                            onClick = {
                                com.jarvisai.assistant.terminal.AgyCliManager.refreshAgyCli(context)
                                onRefresh()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("agy_cli_refresh_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Refresh agy CLI status",
                                tint = AgySettingsColors.TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                when {
                    error != null -> {
                        Text(
                            text = error!!,
                            fontSize = 13.sp,
                            color = Color(0xFFEF4444)
                        )
                    }
                    cli == null && checking -> {
                        Text(
                            text = "Checking…",
                            fontSize = 13.5.sp,
                            color = AgySettingsColors.TextSecondary
                        )
                    }
                    isInstalled -> {
                        Text(
                            text = resolvedVersion.ifBlank { "Installed" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgySettingsColors.TextPrimary,
                            modifier = Modifier.testTag("agy_cli_version_text")
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = resolvedPath,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AgySettingsColors.TextSecondary,
                            modifier = Modifier.testTag("agy_cli_path_text")
                        )
                    }
                    else -> {
                        Text(
                            text = "agy is not installed on this device.",
                            fontSize = 13.5.sp,
                            color = AgySettingsColors.Warning,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = resolvedPath,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AgySettingsColors.TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (!isInstalled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { com.jarvisai.assistant.terminal.AgyCliManager.installAgy(context) },
                            enabled = !running,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.GeminiBlue),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("agy_cli_install_btn")
                        ) {
                            Text(
                                text = if (running && job?.kind == "install") "Installing…" else "Install agy",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(
                            onClick = { com.jarvisai.assistant.terminal.AgyCliManager.updateAgy(context) },
                            enabled = !running,
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, AgySettingsColors.CardBorder),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("agy_cli_check_updates_btn")
                        ) {
                            Text(
                                text = "Check for updates",
                                fontSize = 14.sp,
                                color = AgySettingsColors.TextPrimary
                            )
                        }
                        if (running) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = AgySettingsColors.GeminiBlue
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { com.jarvisai.assistant.terminal.AgyCliManager.updateAgy(context) },
                            enabled = !running,
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, AgySettingsColors.CardBorder),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("agy_cli_check_updates_btn")
                        ) {
                            Text(
                                text = if (running && job?.kind == "update") "Checking…" else "Check for updates",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = AgySettingsColors.TextPrimary
                            )
                        }
                        if (running) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = AgySettingsColors.GeminiBlue
                            )
                        }
                    }
                }

                if (!updatedTo.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Updated to agy $updatedTo. The server restarted to use it.",
                        fontSize = 13.5.sp,
                        color = AgySettingsColors.QuotaGreen,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("agy_cli_updated_banner")
                    )
                }

                job?.let { j ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${j.kind}: " + if (j.running) "running…" else "finished (exit ${j.exitCode ?: 0})",
                        fontSize = 13.sp,
                        color = if (!j.running && (j.exitCode ?: 0) != 0) Color(0xFFEF4444) else AgySettingsColors.TextSecondary,
                        modifier = Modifier.testTag("agy_cli_job_status")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E1F23),
                        border = BorderStroke(1.dp, AgySettingsColors.CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("agy_cli_job_log_box")
                    ) {
                        val displayLines = j.log.lines()
                            .filter { it.isNotBlank() && !it.startsWith("Updated agy ") && !it.startsWith("The running hub keeps") }
                            .takeLast(30)
                        Column(
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (displayLines.isEmpty()) {
                                Text(
                                    text = if (j.running) "⟳ Starting ${j.kind}…" else "✓ Done.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.5.sp,
                                    color = AgySettingsColors.TextPrimary
                                )
                            } else {
                                displayLines.forEach { line ->
                                    Text(
                                        text = line,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.sp,
                                        color = AgySettingsColors.TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Installed with Google's official installer (antigravity.google/cli/install.sh) into ~/.local/bin on this device. agy updates itself when you run it in a terminal; Check for updates runs `agy update` and, when a newer agy was installed, restarts the server so it is used (voice calls need agy 1.5 or newer).",
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = AgySettingsColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun AgyCloudSubScreen(
    executionMode: AntigravityExecutionMode,
    userEmail: String?,
    onUpdateExecutionMode: (AntigravityExecutionMode) -> Unit,
    onOpenApiVault: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = AgySettingsColors.CardBg,
            border = BorderStroke(
                1.dp,
                if (executionMode.cloudCodePaEnabled) AgySettingsColors.GeminiBlue.copy(alpha = 0.45f)
                else AgySettingsColors.CardBorder
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use CloudCode PA Engine",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgySettingsColors.TextPrimary
                    )
                    Text(
                        text = "Current mode: ${executionMode.displayName}",
                        fontSize = 12.sp,
                        color = AgySettingsColors.TextSecondary
                    )
                }
                Switch(
                    checked = executionMode.cloudCodePaEnabled,
                    onCheckedChange = { checked ->
                        onUpdateExecutionMode(
                            resolveNextExecutionMode(current = executionMode, cloudChecked = checked)
                        )
                    },
                    modifier = Modifier.testTag("agy_cloud_subscreen_toggle"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AgySettingsColors.GeminiBlue,
                        uncheckedThumbColor = AgySettingsColors.TextSecondary,
                        uncheckedTrackColor = AgySettingsColors.CardBgElevated
                    )
                )
            }
        }

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
                Button(onClick = onOpenApiVault, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.GeminiBlue)) {
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
                Text("Agentra", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.APPLICATION_ID}", fontSize = 12.5.sp, color = AgySettingsColors.Info)
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
