package com.jarvisai.assistant.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
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
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvisai.assistant.model.UserSettings

enum class SettingsModuleId(
    val index: Int,
    val title: String,
    val subtitle: String,
    val category: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    LOCAL_TOOLS_DIALOG(1, "Local CLI Toolchain Dialog", "Install Node.js, Python & CLI Agents", "Toolchain & PTY", Icons.Default.Build, Color(0xFF38BDF8)),
    FULLSCREEN_INSTALLER(2, "Full-Screen Rootfs Provisioner", "Batch bootstrap & PTY logs", "Toolchain & PTY", Icons.Default.Terminal, Color(0xFF10B981)),
    CUSTOM_SLASH_COMMANDS(3, "Custom Slash Commands", "Prompt macros synced to Chat", "AI Agents & Context", Icons.Default.Code, Color(0xFF6366F1)),
    GLOBAL_CONTEXT_RULES(4, "Global Context & Rules", "System-wide engineering constitution", "AI Agents & Context", Icons.Default.Description, Color(0xFF818CF8)),
    CASCADE_MCP_CATALOG(5, "MCP Server Catalog", "One-tap provisioning to mcp_config.json", "MCP & Extensions", Icons.Default.Hub, Color(0xFF38BDF8)),
    SKILLS_AND_PLUGINS(6, "Skills & Plugins", "SKILL.md, Google Store & GitHub repos", "MCP & Extensions", Icons.Default.Extension, Color(0xFFA855F7)),
    REA_ENGINE(7, "REA Engine", "Reactive Event Automation", "Autonomous Workflows", Icons.Default.Bolt, Color(0xFFF59E0B)),
    REA_GROUPS(8, "REA Execution Groups", "Resource isolation across DevSecOps", "Autonomous Workflows", Icons.Default.GroupWork, Color(0xFF10B981)),
    THEME_GALLERY(9, "Theme Gallery", "Live ANSI palettes & themes", "Appearance & Math", Icons.Default.Palette, Color(0xFFEC4899)),
    LATEX_MATH_ENGINE(10, "Native LaTeX & Mermaid Engine", "Formula rendering", "Appearance & Math", Icons.Default.Functions, Color(0xFFF59E0B)),
    BOUNCY_OVERSCROLL_PHYSICS(11, "Spring Bouncy Overscroll", "Rubber-band drag resistance", "Appearance & Math", Icons.Default.DeveloperMode, Color(0xFF38BDF8)),
    ANDROID_AUTOMATION(12, "Android UI Automation", "Gesture & semantic UI tree", "Device & ADB", Icons.Default.AccessibilityNew, Color(0xFF10B981)),
    WIRELESS_ADB_BRIDGE(13, "Wireless ADB Bridge", "mDNS pairing & wireless debug", "Device & ADB", Icons.Default.WifiTethering, Color(0xFF38BDF8)),
    DEVICE_ADMIN_SHIELD(14, "Anti-Uninstall Shield", "Hardware lock guard", "Device & ADB", Icons.Default.Security, Color(0xFFF59E0B)),
    MANAGE_SPACE_GUARD(15, "Storage Guard", "Granular cache cleanup", "Device & ADB", Icons.Default.DeleteSweep, Color(0xFFEF4444)),
    X11_LINUX_DESKTOP(16, "X11 Desktop Bridge", "Termux:X11 & TigerVNC server", "Toolchain & PTY", Icons.Default.DesktopWindows, Color(0xFF6366F1)),
    MULTI_AGENT_BACKENDS(17, "4-Agent Polymorphic Router", "Claude, Codex, OpenCode & AGY", "AI Agents & Context", Icons.Default.SmartToy, Color(0xFF10B981)),
    API_KEYS_VAULT(18, "API Keys & OAuth Accounts", "Gemini, Anthropic & OpenAI keys", "AI Agents & Context", Icons.Default.Key, Color(0xFFF59E0B)),
    BROWSER_CDP_AUTOMATION(19, "Autonomous Browser CDP", "WebView DevTools automation", "MCP & Extensions", Icons.Default.Public, Color(0xFF38BDF8)),
    VOICE_WAKE_WORD(20, "Voice Wake-Word Pipeline", "Whisper STT & Neural TTS", "Autonomous Workflows", Icons.Default.RecordVoiceOver, Color(0xFFA855F7)),
    CHANGELOG_RELEASES(21, "Release Notes & Changelog", "Complete build history", "System", Icons.Default.NewReleases, Color(0xFF38BDF8));

    fun toSection(): SettingsSection = when (this) {
        FULLSCREEN_INSTALLER, LOCAL_TOOLS_DIALOG -> SettingsSection.TERMINAL
        CUSTOM_SLASH_COMMANDS -> SettingsSection.SLASH_COMMANDS
        GLOBAL_CONTEXT_RULES -> SettingsSection.GLOBAL_CONTEXT
        CASCADE_MCP_CATALOG -> SettingsSection.MCP
        SKILLS_AND_PLUGINS -> SettingsSection.SKILLS_PLUGINS
        REA_ENGINE -> SettingsSection.REA
        REA_GROUPS -> SettingsSection.REA_GROUPS
        THEME_GALLERY -> SettingsSection.THEMES
        ANDROID_AUTOMATION, DEVICE_ADMIN_SHIELD -> SettingsSection.ANDROID_AUTOMATION
        WIRELESS_ADB_BRIDGE -> SettingsSection.ADB_CONNECT
        X11_LINUX_DESKTOP -> SettingsSection.X11_DESKTOP
        API_KEYS_VAULT -> SettingsSection.AGENTS
        MANAGE_SPACE_GUARD -> SettingsSection.DATA_PROTECTION
        CHANGELOG_RELEASES -> SettingsSection.CHANGELOG
        else -> SettingsSection.MAIN
    }
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    initialModule: SettingsModuleId? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = AgySettingsColors.BgCanvas) {
            SettingsPreferencesScreen(
                settings = UserSettings(),
                initialSection = initialModule?.toSection() ?: SettingsSection.MAIN,
                onBack = onDismiss,
                onOpenJarvisGeneralSettings = onDismiss,
                onOpenApiVault = onDismiss
            )
        }
    }
}
