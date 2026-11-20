package com.jarvisai.assistant.ui.onboarding

import android.widget.Toast
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
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.jarvisai.assistant.data.preferences.TerminalLauncherManager

@Composable
fun TerminalLauncherOnboardingDialog(
    onDismiss: () -> Unit,
    onApplied: () -> Unit = onDismiss
) {
    val context = LocalContext.current
    var isEnabled by remember {
        mutableStateOf(
            if (TerminalLauncherManager.isPromptShown(context)) {
                TerminalLauncherManager.isLauncherEnabled(context)
            } else {
                true
            }
        )
    }
    var selectedStyle by remember {
        mutableStateOf(TerminalLauncherManager.getLauncherStyle(context))
    }

    Dialog(
        onDismissRequest = {
            TerminalLauncherManager.markPromptShown(context)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.45f), RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0B101B)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Apps,
                            contentDescription = "Dual App Icon Setup",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Standalone Terminal App Icon",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "1 APK • Dual Home Screen Launcher",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF38BDF8),
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }

                Text(
                    text = "Add a dedicated Terminal icon directly to your phone's app drawer alongside Agentra. Both apps share the same native relocated Linux rootfs (ai.agentra) with zero extra APK install needed.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFFCBD5E1),
                        lineHeight = 18.sp
                    )
                )

                // Home screen preview
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF111827),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "APP DRAWER PREVIEW",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Primary Agentra Icon
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.jarvis_app_logo),
                                    contentDescription = "Agentra main app icon",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                )
                                Text(
                                    text = "Agentra",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = "+",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isEnabled) Color(0xFF10B981) else Color(0xFF475569)
                            )

                            // Secondary Terminal Icon
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.alpha(if (isEnabled) 1f else 0.35f)
                            ) {
                                if (selectedStyle == TerminalLauncherManager.STYLE_TERMUX) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.Black)
                                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.65f), RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_termux_foreground),
                                            contentDescription = "Classic Termux icon",
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.jarvis_app_logo),
                                        contentDescription = "Agentra Term icon",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                                    )
                                }
                                Text(
                                    text = if (selectedStyle == TerminalLauncherManager.STYLE_TERMUX) "Termux" else "Agentra Term",
                                    fontSize = 11.sp,
                                    color = if (isEnabled) Color(0xFF10B981) else Color(0xFF64748B),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Enable switch row
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isEnabled = !isEnabled },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF111827),
                    border = BorderStroke(
                        1.dp,
                        if (isEnabled) Color(0xFF10B981).copy(alpha = 0.55f) else Color(0xFF1E293B)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Standalone Terminal Icon",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = if (isEnabled) "Shown in launcher alongside Agentra" else "Hidden (terminal stays accessible inside app)",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("terminal_launcher_enable_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                // Style selector options
                if (isEnabled) {
                    Text(
                        text = "Choose Launcher Icon Style",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )

                    StyleChoiceCard(
                        title = "Agentra Term",
                        subtitle = "Branded Agentra terminal icon in your app drawer",
                        selected = selectedStyle == TerminalLauncherManager.STYLE_AGENTRA_TERM,
                        testTag = "terminal_style_agentra_card",
                        iconContent = {
                            Image(
                                painter = painterResource(id = R.drawable.jarvis_app_logo),
                                contentDescription = "Agentra Term style preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        },
                        onClick = { selectedStyle = TerminalLauncherManager.STYLE_AGENTRA_TERM }
                    )

                    StyleChoiceCard(
                        title = "Termux",
                        subtitle = "Classic black >_ Termux badge icon",
                        selected = selectedStyle == TerminalLauncherManager.STYLE_TERMUX,
                        testTag = "terminal_style_termux_card",
                        iconContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black)
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_termux_foreground),
                                    contentDescription = "Termux classic style preview",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        },
                        onClick = { selectedStyle = TerminalLauncherManager.STYLE_TERMUX }
                    )
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            TerminalLauncherManager.markPromptShown(context)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("terminal_launcher_skip_btn")
                    ) {
                        Text(
                            text = "Skip",
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = {
                            TerminalLauncherManager.applyLauncherSetting(context, isEnabled, selectedStyle)
                            TerminalLauncherManager.markPromptShown(context)
                            val msg = if (isEnabled) {
                                val name = if (selectedStyle == TerminalLauncherManager.STYLE_TERMUX) "Termux" else "Agentra Term"
                                "Added \"$name\" icon to your app drawer!"
                            } else {
                                "Standalone Terminal icon disabled."
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            onApplied()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color(0xFF050A14)
                        ),
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = 48.dp)
                            .testTag("terminal_launcher_apply_btn")
                    ) {
                        Icon(
                            imageVector = if (isEnabled) Icons.Outlined.CheckCircle else Icons.Outlined.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEnabled) "Apply Icon" else "Save",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    testTag: String,
    iconContent: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFF00E5FF).copy(alpha = 0.10f) else Color(0xFF111827),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) Color(0xFF00E5FF) else Color(0xFF1E293B)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            iconContent()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            RadioButton(
                selected = selected,
                onClick = onClick,
                modifier = Modifier.minimumInteractiveComponentSize(),
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFF00E5FF),
                    unselectedColor = Color(0xFF64748B)
                )
            )
        }
    }
}
