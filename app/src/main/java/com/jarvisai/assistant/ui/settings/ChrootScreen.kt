package com.jarvisai.assistant.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.terminal.chroot.ChrootManager
import kotlinx.coroutines.launch

@Composable
fun ChrootScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by ChrootManager.state.collectAsState()

    LaunchedEffect(Unit) {
        ChrootManager.probeAndScan(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(
                1.dp,
                when (state.rootAvailable) {
                    true -> AgySettingsColors.QuotaGreen.copy(alpha = 0.5f)
                    false -> AgySettingsColors.Warning.copy(alpha = 0.5f)
                    null -> AgySettingsColors.CardBorder
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AdminPanelSettings,
                        contentDescription = null,
                        tint = if (state.rootAvailable == true) AgySettingsColors.QuotaGreen else AgySettingsColors.Warning,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (state.rootAvailable) {
                                true -> "Root (su) Available"
                                false -> "Root (su) Not Detected"
                                null -> "Checking Root Access..."
                            },
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgySettingsColors.TextPrimary
                        )
                        Text(
                            text = "Isolated rootfs path: ${ChrootManager.BASE_DIR}",
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AgySettingsColors.Info
                        )
                    }
                    IconButton(
                        onClick = { scope.launch { ChrootManager.probeAndScan(context) } },
                        modifier = Modifier.testTag("chroot_rescan_btn")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Rescan chroot containers", tint = AgySettingsColors.TextSecondary)
                    }
                }
                if (state.suDetails.isNotBlank()) {
                    Text(state.suDetails, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, color = AgySettingsColors.TextSecondary)
                }
                Text(
                    text = "Optional advanced mode for rooted devices. Non-rooted devices continue using Agentra's embedded Option B & PRoot Linux engine without root.",
                    fontSize = 12.sp,
                    color = AgySettingsColors.TextSecondary
                )
            }
        }

        if (state.rootAvailable == true) {
            if (state.containers.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
                    border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No Chroot Containers Found", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                        Text(
                            "Place any ARM64 Debian/Ubuntu rootfs directory under ${ChrootManager.BASE_DIR}/<name> and tap Refresh to mount & manage it with chrootctl.sh.",
                            fontSize = 12.sp,
                            color = AgySettingsColors.TextSecondary
                        )
                    }
                }
            } else {
                state.containers.forEach { container ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
                        border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Storage, contentDescription = null, tint = AgySettingsColors.GeminiBlue, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(container.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                                    Text(container.path, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AgySettingsColors.TextSecondary)
                                }
                                Text(
                                    text = if (container.mounted) "Mounted" else "Stopped",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (container.mounted) AgySettingsColors.QuotaGreen else AgySettingsColors.TextSecondary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { scope.launch { ChrootManager.runAction(context, "mount", container.path) } },
                                    enabled = !state.busy,
                                    colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.QuotaGreen),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Mount", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { scope.launch { ChrootManager.runAction(context, "unmount", container.path) } },
                                    enabled = !state.busy,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Unmount", fontSize = 12.sp, color = AgySettingsColors.TextPrimary)
                                }
                                OutlinedButton(
                                    onClick = { scope.launch { ChrootManager.runAction(context, "status", container.path) } },
                                    enabled = !state.busy,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Status", fontSize = 12.sp, color = AgySettingsColors.GeminiBlue)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.lastOutput.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF050810), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = state.lastOutput,
                    color = Color(0xFFCBD5E1),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}
