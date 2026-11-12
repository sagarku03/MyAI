package com.jarvisai.assistant.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.ui.components.bouncyOverscroll

data class ReleaseChangelogEntry(
    val version: String,
    val date: String,
    val title: String,
    val highlights: List<String>
)

private val RELEASE_HISTORY = listOf(
    ReleaseChangelogEntry(
        version = "v4.7.0 — Phase 7 Scientific & UI-UX Pro Max",
        date = "2026-11-12",
        title = "Native Canvas LaTeX Math, Bouncy Overscroll & 21 Modular Settings",
        highlights = listOf(
            "Zero-WebView native Android Canvas LaTeX equation renderer (JLatexMathDrawable + 150-slot LruCache).",
            "Inline ($...$) and Display ($$...$$) mathematical formulas with zero Cumulative Layout Shift.",
            "Spring-physics rubber-band BouncyOverscroll across chat streams and settings lists.",
            "Interactive ChoiceQuestionnaireCard, AgentWorkingSpinner radar, and 21 modular settings screens."
        )
    ),
    ReleaseChangelogEntry(
        version = "v4.6.0 — Phase 6 Device Protection & Wireless ADB",
        date = "2026-11-11",
        title = "Anti-Wipe Storage Guard, Device Admin & Loopback ADB Pairing",
        highlights = listOf(
            "ManageSpaceActivity Clear-Data interceptor protecting /files/usr and /files/home from accidental wipes.",
            "JarvisDeviceAdminReceiver anti-uninstall shield and screen lock automation.",
            "AdbPairingService & AdbConnectManager with mDNS discovery (_adb-tls-pairing._tcp) and notification code entry."
        )
    ),
    ReleaseChangelogEntry(
        version = "v4.5.0 — Phase 5 Autonomous Browser MCP & Extension Host",
        date = "2026-11-10",
        title = "CDP Socket Engine, Lease Controller & WebExtension Host",
        highlights = listOf(
            "60-second timed BrowserAutomationController lease engine with automatic touch lock recovery.",
            "Native Chrome DevTools Protocol (CDP) socket client with 10 MCP browser tools.",
            "Google OAuth WebView identity spoofing and on-device Eruda developer console."
        )
    ),
    ReleaseChangelogEntry(
        version = "v4.4.0 — Phase 4 Sora IDE & Embedded Code Editor",
        date = "2026-11-09",
        title = "Universal Code Highlighting, Launch.json Runner & Unified Git Diffs",
        highlights = listOf(
            "Interactive IdeExecutionRunner bottom sheet with live PTY terminal and modifier pills.",
            "RunConfigManager (.ide/launch.json) auto-detecting Node.js, Python, Rust, Go, and C/C++.",
            "UnifiedDiffViewer with line-by-line additions/deletions and Project Scaffolder."
        )
    ),
    ReleaseChangelogEntry(
        version = "v4.3.0 — Phase 1–3 Multi-Flavor Termux & 4-Agent Engine",
        date = "2026-11-08",
        title = "Dual-Flavor Build (standard + termux), Native PTY & Multi-Agent Backends",
        highlights = listOf(
            "Authentic com.termux.view.TerminalView with pinch-to-zoom and selection handles.",
            "Polymorphic AgentChatBackend supporting Claude Code, OpenAI Codex, OpenCode, and Antigravity."
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Release Notes & Architecture Changelog",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFFF8FAFC),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Jarvis AI Workstation Build History",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8))
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("changelog_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFF8FAFC)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0F19))
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(RELEASE_HISTORY, key = { it.version }) { release ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF111827),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NewReleases,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = release.version,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = Color(0xFFF8FAFC),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Text(
                                text = release.date,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF94A3B8),
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }

                        Text(
                            text = release.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF818CF8),
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        release.highlights.forEach { bullet ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "•",
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFE2E8F0),
                                        lineHeight = 18.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
