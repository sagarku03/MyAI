package com.jarvisai.assistant.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvisai.assistant.security.ManageSpaceActivity
import com.jarvisai.assistant.ui.components.ProvideBouncyOverscroll
import com.jarvisai.assistant.ui.components.bouncyOverscroll

enum class SettingsModuleId(
    val index: Int,
    val title: String,
    val subtitle: String,
    val category: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    LOCAL_TOOLS_DIALOG(
        1,
        "Local CLI Toolchain Dialog",
        "Install Node.js, Python, Claude Code, Codex & Ripgrep",
        "Toolchain & PTY",
        Icons.Default.Build,
        Color(0xFF38BDF8)
    ),
    FULLSCREEN_INSTALLER(
        2,
        "Full-Screen Rootfs & Agent Provisioner",
        "Batch bootstrap extraction, SYMLINKS.txt & live PTY logs",
        "Toolchain & PTY",
        Icons.Default.Terminal,
        Color(0xFF10B981)
    ),
    CUSTOM_SLASH_COMMANDS(
        3,
        "Custom Slash Commands (/audit, /refactor)",
        "Prompt macros synced to Chat & ~/.claude/commands/*.md",
        "AI Agents & Context",
        Icons.Default.Code,
        Color(0xFF6366F1)
    ),
    GLOBAL_CONTEXT_RULES(
        4,
        "Global Context & Rules (CLAUDE.md / AGENTS.md)",
        "System-wide engineering constitution mirrored across agents",
        "AI Agents & Context",
        Icons.Default.Description,
        Color(0xFF818CF8)
    ),
    CASCADE_MCP_CATALOG(
        5,
        "Cascade & Claude MCP Server Catalog",
        "One-tap provisioning of Filesystem, GitHub, Postgres & Brave MCP",
        "MCP & Extensions",
        Icons.Default.Hub,
        Color(0xFF38BDF8)
    ),
    SKILLS_AND_PLUGINS(
        6,
        "Skills, WebExtensions & Plugin Repositories",
        "GitHub skill sync, Tampermonkey userscripts & CRX manifest host",
        "MCP & Extensions",
        Icons.Default.Extension,
        Color(0xFFA855F7)
    ),
    REA_ENGINE(
        7,
        "REA (Reactive Event Automation) Engine",
        "Event-driven pre-commit audits, build failure & thermal triggers",
        "Autonomous Workflows",
        Icons.Default.Bolt,
        Color(0xFFF59E0B)
    ),
    REA_GROUPS(
        8,
        "REA Execution Groups & Concurrency",
        "Resource isolation across DevSecOps, CI/CD & BrowserSwarm",
        "Autonomous Workflows",
        Icons.Default.GroupWork,
        Color(0xFF10B981)
    ),
    THEME_GALLERY(
        9,
        "UI-UX Pro Max & PTY Theme Gallery",
        "Cyberpunk, Dracula & Monokai 16-color ANSI live palettes",
        "Appearance & Math",
        Icons.Default.Palette,
        Color(0xFFEC4899)
    ),
    LATEX_MATH_ENGINE(
        10,
        "Native Canvas LaTeX & Mermaid Engine",
        "Zero-WebView JLatexMathDrawable LRU cache & formula scaling",
        "Appearance & Math",
        Icons.Default.Functions,
        Color(0xFFF59E0B)
    ),
    BOUNCY_OVERSCROLL_PHYSICS(
        11,
        "Spring Bouncy Overscroll Physics",
        "Rubber-band drag resistance (0.55) & MediumBouncy snap-back",
        "Appearance & Math",
        Icons.Default.DeveloperMode,
        Color(0xFF38BDF8)
    ),
    ANDROID_AUTOMATION(
        12,
        "Android UI Automation & Accessibility",
        "Multi-touch GestureDescription & semantic UI node tree",
        "Device & ADB",
        Icons.Default.AccessibilityNew,
        Color(0xFF10B981)
    ),
    WIRELESS_ADB_BRIDGE(
        13,
        "Loopback Wireless ADB & mDNS Pairing",
        "Auto-discover _adb-tls-pairing._tcp & notification code entry",
        "Device & ADB",
        Icons.Default.WifiTethering,
        Color(0xFF38BDF8)
    ),
    DEVICE_ADMIN_SHIELD(
        14,
        "Anti-Uninstall Device Administrator Shield",
        "Hardware lock & accidental app uninstallation guard",
        "Device & ADB",
        Icons.Default.Security,
        Color(0xFFF59E0B)
    ),
    MANAGE_SPACE_GUARD(
        15,
        "Storage Guard & Clear-Data Interceptor",
        "Granular cache cleanup preserving /files/usr & /files/home",
        "Device & ADB",
        Icons.Default.DeleteSweep,
        Color(0xFFEF4444)
    ),
    X11_LINUX_DESKTOP(
        16,
        "X11 & Linux Desktop Bridge",
        "Termux:X11 socket, TigerVNC server & VirGL Adreno GPU",
        "Toolchain & PTY",
        Icons.Default.DesktopWindows,
        Color(0xFF6366F1)
    ),
    MULTI_AGENT_BACKENDS(
        17,
        "4-Agent Polymorphic Router",
        "Claude Code, OpenAI Codex, OpenCode & Antigravity port 2235",
        "AI Agents & Context",
        Icons.Default.SmartToy,
        Color(0xFF10B981)
    ),
    API_KEYS_VAULT(
        18,
        "Encrypted API Keys & OAuth Accounts",
        "Anthropic, OpenAI, Gemini & multi-account token switcher",
        "AI Agents & Context",
        Icons.Default.Key,
        Color(0xFFF59E0B)
    ),
    BROWSER_CDP_AUTOMATION(
        19,
        "Autonomous Browser CDP & 60s Lease Lock",
        "WebView DevTools socket, OAuth spoofing & Eruda console",
        "MCP & Extensions",
        Icons.Default.Public,
        Color(0xFF38BDF8)
    ),
    VOICE_WAKE_WORD(
        20,
        "Voice Wake-Word & Neural TTS Pipeline",
        "Barge-in voice interruption, Whisper STT & pitch controls",
        "Autonomous Workflows",
        Icons.Default.RecordVoiceOver,
        Color(0xFFA855F7)
    ),
    CHANGELOG_RELEASES(
        21,
        "Release Notes & Architecture Changelog",
        "Complete build history across Phases 1 through 7",
        "System",
        Icons.Default.NewReleases,
        Color(0xFF38BDF8)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    initialModule: SettingsModuleId? = null
) {
    val context = LocalContext.current
    var activeSubScreen by remember { mutableStateOf(initialModule) }
    var showLocalToolsPopup by remember { mutableStateOf(false) }
    var showMcpCatalogPopup by remember { mutableStateOf(false) }

    val inlineToggles = remember {
        mutableStateMapOf(
            SettingsModuleId.LATEX_MATH_ENGINE to true,
            SettingsModuleId.BOUNCY_OVERSCROLL_PHYSICS to true,
            SettingsModuleId.MULTI_AGENT_BACKENDS to true,
            SettingsModuleId.BROWSER_CDP_AUTOMATION to true,
            SettingsModuleId.VOICE_WAKE_WORD to true
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ProvideBouncyOverscroll {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0B0F19)
            ) {
                BackHandler(enabled = activeSubScreen != null) {
                    activeSubScreen = null
                }

                when (activeSubScreen) {
                    SettingsModuleId.FULLSCREEN_INSTALLER -> {
                        FullScreenLocalToolsInstaller(onBack = { activeSubScreen = null })
                    }
                    SettingsModuleId.CUSTOM_SLASH_COMMANDS -> {
                        CustomSlashCommandsScreen(onBack = { activeSubScreen = null })
                    }
                    SettingsModuleId.GLOBAL_CONTEXT_RULES -> {
                        GlobalContextScreen(onBack = { activeSubScreen = null })
                    }
                    SettingsModuleId.SKILLS_AND_PLUGINS -> {
                        SkillsAndPluginsSubScreen(onBack = { activeSubScreen = null })
                    }
                    SettingsModuleId.REA_ENGINE -> {
                        ReaScreen(
                            onBack = { activeSubScreen = null },
                            onOpenGroups = { activeSubScreen = SettingsModuleId.REA_GROUPS }
                        )
                    }
                    SettingsModuleId.REA_GROUPS -> {
                        ReaGroupsScreen(onBack = { activeSubScreen = SettingsModuleId.REA_ENGINE })
                    }
                    SettingsModuleId.THEME_GALLERY -> {
                        ThemeGallery(onBack = { activeSubScreen = null })
                    }
                    SettingsModuleId.ANDROID_AUTOMATION,
                    SettingsModuleId.WIRELESS_ADB_BRIDGE,
                    SettingsModuleId.DEVICE_ADMIN_SHIELD -> {
                        AndroidAutomationScreen(onBack = { activeSubScreen = null })
                    }
                    SettingsModuleId.X11_LINUX_DESKTOP -> {
                        X11DesktopScreen(onBack = { activeSubScreen = null })
                    }
                    SettingsModuleId.CHANGELOG_RELEASES -> {
                        ChangelogScreen(onBack = { activeSubScreen = null })
                    }
                    else -> {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Column {
                                            Text(
                                                text = "Workstation Settings Hub (21 Modules)",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    color = Color(0xFFF8FAFC),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Text(
                                                text = "Scientific LaTeX • Bouncy Overscroll • Termux PTY • Multi-Agent",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color(0xFF38BDF8)
                                                )
                                            )
                                        }
                                    },
                                    actions = {
                                        IconButton(
                                            onClick = onDismiss,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("settings_hub_close_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close Settings Hub",
                                                tint = Color(0xFFF8FAFC)
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = Color(0xFF0B0F19)
                                    )
                                )
                            },
                            containerColor = Color(0xFF0B0F19)
                        ) { innerPadding ->
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(horizontal = 16.dp)
                                    .bouncyOverscroll(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                item {
                                    ThemeQuickPanel()
                                }

                                items(SettingsModuleId.entries, key = { it.name }) { module ->
                                    val hasToggle = inlineToggles.containsKey(module)
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 64.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                when (module) {
                                                    SettingsModuleId.LOCAL_TOOLS_DIALOG -> {
                                                        showLocalToolsPopup = true
                                                    }
                                                    SettingsModuleId.CASCADE_MCP_CATALOG -> {
                                                        showMcpCatalogPopup = true
                                                    }
                                                    SettingsModuleId.MANAGE_SPACE_GUARD -> {
                                                        val intent = Intent(context, ManageSpaceActivity::class.java).apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        runCatching { context.startActivity(intent) }
                                                    }
                                                    else -> {
                                                        if (hasToggle) {
                                                            inlineToggles[module] = !(inlineToggles[module] ?: true)
                                                        } else {
                                                            activeSubScreen = module
                                                        }
                                                    }
                                                }
                                            }
                                            .testTag("settings_module_${module.index}"),
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF111827),
                                        border = BorderStroke(1.dp, Color(0xFF1E293B))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = module.accentColor.copy(alpha = 0.16f),
                                                    modifier = Modifier.size(42.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = module.icon,
                                                            contentDescription = null,
                                                            tint = module.accentColor,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }

                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "${module.index}. ${module.title}",
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                color = Color(0xFFF8FAFC),
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        )
                                                    }
                                                    Text(
                                                        text = module.subtitle,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            color = Color(0xFF94A3B8),
                                                            fontSize = 11.sp
                                                        )
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            if (hasToggle) {
                                                Switch(
                                                    checked = inlineToggles[module] == true,
                                                    onCheckedChange = { inlineToggles[module] = it },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = Color.White,
                                                        checkedTrackColor = Color(0xFF6366F1)
                                                    )
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = "Open ${module.title}",
                                                    tint = Color(0xFF64748B)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showLocalToolsPopup) {
                    LocalToolsInstallDialog(
                        onDismiss = { showLocalToolsPopup = false },
                        onExpandFullScreen = {
                            showLocalToolsPopup = false
                            activeSubScreen = SettingsModuleId.FULLSCREEN_INSTALLER
                        }
                    )
                }

                if (showMcpCatalogPopup) {
                    CascadeMcpCatalogDialog(onDismiss = { showMcpCatalogPopup = false })
                }
            }
        }
    }
}
