package com.jarvisai.assistant.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvisai.assistant.ui.components.MarkdownContent
import com.jarvisai.assistant.ui.components.StemBlockView
import com.jarvisai.assistant.ui.components.StemKind
import com.jarvisai.assistant.ui.components.StemRenderPreferences

@Composable
fun StemRenderingScreen() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { StemRenderPreferences.init(context) }
    val enabledKinds by StemRenderPreferences.enabledKinds.collectAsState()
    var showGuide by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Render chemistry equations, SMILES molecular graphs, mathematical function plots, and STEM diagrams directly in Agentra chats — 100% offline with zero WebView overhead.",
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = AgySettingsColors.TextSecondary
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Enabled STEM Block Renderers", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                StemKind.entries.forEachIndexed { idx, kind ->
                    val checked = kind in enabledKinds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(kind.label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = AgySettingsColors.TextPrimary)
                            Text("Fenced block: ```${kind.lang}", fontSize = 11.5.sp, color = AgySettingsColors.TextSecondary)
                        }
                        Switch(
                            checked = checked,
                            onCheckedChange = { StemRenderPreferences.setEnabled(context, kind, it) },
                            modifier = Modifier.testTag("stem_toggle_${kind.prefKey}"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AgySettingsColors.QuotaGreen,
                                uncheckedThumbColor = AgySettingsColors.TextSecondary,
                                uncheckedTrackColor = AgySettingsColors.CardBgElevated
                            )
                        )
                    }
                    if (idx < StemKind.entries.lastIndex) {
                        HorizontalDivider(color = AgySettingsColors.CardBorder)
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { showGuide = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("stem_open_guide_btn")
        ) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open STEM Authoring Guide (render/GUIDE.md)", color = AgySettingsColors.TextPrimary)
        }

        Text("Live Interactive Previews", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
        StemBlockView(kind = StemKind.CHEM, code = "2 H2(g) + O2(g) -> 2 H2O(l)")
        StemBlockView(kind = StemKind.SMILES, code = "CC(=O)Oc1ccccc1C(=O)O")
        StemBlockView(kind = StemKind.FUNCTION_PLOT, code = "sin(x)\n0.25*x^2 - 1")
    }

    if (showGuide) {
        val guideText = remember {
            runCatching {
                context.assets.open("render/GUIDE.md").bufferedReader().use { it.readText() }
            }.getOrElse { "# STEM Rendering Guide\nUse ```chem, ```smiles, and ```functionplot blocks in chats." }
        }
        Dialog(
            onDismissRequest = { showGuide = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(vertical = 24.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0A0F1A),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("STEM Rendering Guide", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showGuide = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close guide", tint = Color.White)
                        }
                    }
                    HorizontalDivider(color = Color(0xFF1F2937))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 12.dp)
                    ) {
                        MarkdownContent(content = guideText)
                    }
                }
            }
        }
    }
}
