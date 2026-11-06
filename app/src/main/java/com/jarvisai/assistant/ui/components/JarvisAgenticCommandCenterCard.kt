package com.jarvisai.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DynamicForm
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DeepAmethystPressed
import com.example.ui.theme.ElectricOrchid
import com.example.ui.theme.LiquidGlassElevated
import com.example.ui.theme.LiquidGlassPill
import com.example.ui.theme.LiquidGlassSurface
import com.example.ui.theme.PaleLavenderIce
import com.example.ui.theme.RoyalPurplePrimary
import com.example.ui.theme.SoftLavenderGlow
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusRoseRed
import com.example.ui.theme.TextLavenderMuted
import com.example.ui.theme.TextPureWhite
import com.jarvisai.assistant.model.WorkflowAction
import com.jarvisai.assistant.service.AgenticStepLog
import com.jarvisai.assistant.service.CachedNotificationReplyAction
import com.jarvisai.assistant.service.CrossAppScratchpadResult
import com.jarvisai.assistant.service.FormFillSessionState
import com.jarvisai.assistant.service.HighRiskGateRequest
import com.jarvisai.assistant.service.JarvisAccessibilityService
import com.jarvisai.assistant.service.OverlayHudState

@Composable
fun JarvisAgenticCommandCenterCard(
    agenticStepLogs: List<AgenticStepLog> = emptyList(),
    isAgenticLoopRunning: Boolean = false,
    detectedBadges: List<JarvisAccessibilityService.SetOfMarkBadge> = emptyList(),
    overlayHudState: OverlayHudState = OverlayHudState(),
    activeReplyables: List<CachedNotificationReplyAction> = emptyList(),
    activeFormSession: FormFillSessionState? = null,
    lastScratchpadResult: CrossAppScratchpadResult? = null,
    isRecordingDemonstration: Boolean = false,
    recordedDemonstrationSteps: List<WorkflowAction> = emptyList(),
    pendingHighRiskGate: HighRiskGateRequest? = null,
    onRunAutonomousGoal: (String) -> Unit = {},
    onScanSetOfMarkBadges: () -> Unit = {},
    onTapSetOfMarkBadge: (Int) -> Unit = {},
    onToggleOverlayHud: (Boolean) -> Unit = {},
    onSendInlineReply: (String, String) -> Unit = { _, _ -> },
    onAutoFillScreenForm: () -> Unit = {},
    onRunCrossAppBridge: (String, String) -> Unit = { _, _ -> },
    onStartTeachableRecording: () -> Unit = {},
    onStopAndSaveTeachableSkill: (String) -> Unit = {},
    onSpeakerCall: (String) -> Unit = {},
    onSetTimerSeconds: (Int) -> Unit = {},
    onZeroClickTeleport: (String, String) -> Unit = { _, _ -> },
    onConfirmHighRiskGate: () -> Unit = {},
    onCancelHighRiskGate: () -> Unit = {},
    onVoiceUndoRollback: () -> Unit = {}
) {
    var goalInput by remember { mutableStateOf("YouTube kholo aur Arijit Singh search karo aur pehla video play karo") }
    var inlineReplySender by remember { mutableStateOf("Rahul") }
    var inlineReplyText by remember { mutableStateOf("Main 5 minute me call karta hoon") }
    var teachableSkillName by remember { mutableStateOf("Clear Cache Routine") }
    var speakerCallTarget by remember { mutableStateOf("Rahul") }

    JarvisGlassPanel(
        accentColor = RoyalPurplePrimary,
        cornerRadius = 28.dp,
        contentPadding = PaddingValues(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agentic_command_center_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(RoyalPurplePrimary, ElectricOrchid)
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Autonomous Phone Agent",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPureWhite,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "10 Real-Time UI & Voice Capabilities Ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = SoftLavenderGlow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Feature 10: Quick Voice Undo / Rollback Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(StatusRoseRed.copy(alpha = 0.18f))
                        .border(1.dp, StatusRoseRed.copy(alpha = 0.70f), RoundedCornerShape(999.dp))
                        .clickable { onVoiceUndoRollback() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("btn_agentic_voice_undo")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo Action",
                            tint = StatusRoseRed,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Undo",
                            color = TextPureWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // FEATURE 10: High-Risk Payment / Delete Safety Gatekeeper Banner (when triggered)
            if (pendingHighRiskGate != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF3A0C1E))
                        .border(1.2.dp, StatusRoseRed, RoundedCornerShape(20.dp))
                        .padding(14.dp)
                        .testTag("high_risk_safety_gate_banner"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = StatusRoseRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "SAFETY GATEKEEPER: ${pendingHighRiskGate.targetLabel}",
                            color = TextPureWhite,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        text = pendingHighRiskGate.spokenPromptHinglish,
                        color = PaleLavenderIce,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onConfirmHighRiskGate,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalPurplePrimary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_confirm_high_risk_gate")
                        ) {
                            Text("Confirm Action", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Button(
                            onClick = onCancelHighRiskGate,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusRoseRed),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_cancel_high_risk_gate")
                        ) {
                            Text("Cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // FEATURE 1: Autonomous Multi-Step "ReAct" UI Navigation Loop
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(LiquidGlassPill.copy(alpha = 0.55f))
                    .border(1.dp, SoftLavenderGlow.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "1. Autonomous ReAct Goal Runner (Observe → Think → Act → Verify)",
                    color = PaleLavenderIce,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it },
                    label = { Text("Multi-Step Voice Goal (Hinglish / English)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF140D29),
                        unfocusedContainerColor = Color(0xFF110A22),
                        focusedTextColor = TextPureWhite,
                        unfocusedTextColor = TextPureWhite,
                        focusedBorderColor = RoyalPurplePrimary,
                        unfocusedBorderColor = SoftLavenderGlow.copy(alpha = 0.28f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_agentic_react_goal")
                )
                JarvisGradientButton(
                    text = if (isAgenticLoopRunning) "Executing ReAct Loop..." else "Run Autonomous Multi-Step Goal",
                    icon = Icons.Default.PlayArrow,
                    onClick = { onRunAutonomousGoal(goalInput) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_run_agentic_react_goal")
                )

                if (agenticStepLogs.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0E081C))
                            .border(1.dp, SoftLavenderGlow.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                            .padding(10.dp)
                            .testTag("agentic_react_step_timeline"),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        agenticStepLogs.forEach { step ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (step.succeeded) StatusEmerald else StatusAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Step ${step.stepNumber} [${step.phase}]: ${step.actionName} — ${step.summary}",
                                    color = TextPureWhite,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // FEATURE 2 & FEATURE 3: Vision Set-of-Mark Screen Tapping + Floating Cyber-HUD Overlay
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Feature 2: Vision Set-of-Mark Tapping
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(LiquidGlassPill.copy(alpha = 0.48f))
                        .border(1.dp, SoftLavenderGlow.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = SoftLavenderGlow, modifier = Modifier.size(17.dp))
                        Text("2. Set-of-Mark Tap", color = TextPureWhite, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(
                        text = "${detectedBadges.size} interactive screen badges",
                        color = TextLavenderMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(RoyalPurplePrimary.copy(alpha = 0.35f))
                                .border(1.dp, SoftLavenderGlow.copy(alpha = 0.50f), RoundedCornerShape(999.dp))
                                .clickable { onScanSetOfMarkBadges() }
                                .padding(vertical = 8.dp)
                                .testTag("btn_scan_som_badges"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Scan", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(ElectricOrchid.copy(alpha = 0.35f))
                                .border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(999.dp))
                                .clickable { onTapSetOfMarkBadge(1) }
                                .padding(vertical = 8.dp)
                                .testTag("btn_tap_som_badge_1"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Tap #1", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                // Feature 3: System-Wide Floating Cyber-HUD Bubble
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(LiquidGlassPill.copy(alpha = 0.48f))
                        .border(1.dp, SoftLavenderGlow.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Default.BubbleChart, contentDescription = null, tint = SoftLavenderGlow, modifier = Modifier.size(17.dp))
                            Text("3. Live Pill", color = TextPureWhite, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Switch(
                            checked = overlayHudState.isOverlayActive,
                            onCheckedChange = onToggleOverlayHud,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = RoyalPurplePrimary
                            ),
                            modifier = Modifier.testTag("switch_floating_cyber_hud")
                        )
                    }
                    Text(
                        text = overlayHudState.currentStepDetail,
                        color = TextLavenderMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // FEATURE 4: Background Zero-Interruption Notification Auto-Reply Engine
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(LiquidGlassPill.copy(alpha = 0.48f))
                    .border(1.dp, SoftLavenderGlow.copy(alpha = 0.26f), RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.MarkChatRead, contentDescription = null, tint = SoftLavenderGlow, modifier = Modifier.size(17.dp))
                    Text(
                        text = "4. Silent Notification Reply (${activeReplyables.size} Cached)",
                        color = TextPureWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inlineReplySender,
                        onValueChange = { inlineReplySender = it },
                        label = { Text("Sender", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(0.38f)
                            .testTag("input_bg_reply_sender")
                    )
                    OutlinedTextField(
                        value = inlineReplyText,
                        onValueChange = { inlineReplyText = it },
                        label = { Text("Inline Reply", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(0.62f)
                            .testTag("input_bg_reply_message")
                    )
                }
                Button(
                    onClick = { onSendInlineReply(inlineReplySender, inlineReplyText) },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalPurplePrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_send_bg_notification_reply")
                ) {
                    Text("Send Silent RemoteInput Reply", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            // FEATURE 5 & FEATURE 6: Conversational Form-Filling + Cross-App Data Bridge & Translator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Feature 5: Form Auto-Fill Agent
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(LiquidGlassPill.copy(alpha = 0.48f))
                        .border(1.dp, SoftLavenderGlow.copy(alpha = 0.26f), RoundedCornerShape(20.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.DynamicForm, contentDescription = null, tint = SoftLavenderGlow, modifier = Modifier.size(17.dp))
                        Text("5. Form Auto-Fill", color = TextPureWhite, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(
                        text = activeFormSession?.let {
                            "Filled ${it.fields.count { f -> f.filledValue.isNotBlank() }}/${it.fields.size} fields"
                        } ?: "Fills inputs from Memory Vault",
                        color = TextLavenderMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = onAutoFillScreenForm,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalPurplePrimary.copy(alpha = 0.38f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_autofill_screen_form")
                    ) {
                        Text("Auto-Fill Form", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // Feature 6: Cross-App Data Bridge & Screen Translator
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(LiquidGlassPill.copy(alpha = 0.48f))
                        .border(1.dp, SoftLavenderGlow.copy(alpha = 0.26f), RoundedCornerShape(20.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null, tint = SoftLavenderGlow, modifier = Modifier.size(17.dp))
                        Text("6. Screen Bridge", color = TextPureWhite, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(
                        text = lastScratchpadResult?.transformedSummaryOrTranslation
                            ?: "Translate & copy screen text",
                        color = TextLavenderMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(
                        onClick = { onRunCrossAppBridge("Hindi", "Scratchpad") },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricOrchid.copy(alpha = 0.38f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_cross_app_screen_bridge")
                    ) {
                        Text("Translate Screen", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            // FEATURE 7: Teachable "Watch Me & Learn" Macro Recorder
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(LiquidGlassPill.copy(alpha = 0.48f))
                    .border(
                        width = 1.dp,
                        color = if (isRecordingDemonstration) StatusRoseRed else SoftLavenderGlow.copy(alpha = 0.26f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isRecordingDemonstration) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = if (isRecordingDemonstration) StatusRoseRed else SoftLavenderGlow,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = "7. Teachable 'Watch Me & Learn' Recorder",
                            color = TextPureWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = if (isRecordingDemonstration) "REC (${recordedDemonstrationSteps.size})" else "READY",
                        color = if (isRecordingDemonstration) StatusRoseRed else StatusEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = teachableSkillName,
                        onValueChange = { teachableSkillName = it },
                        label = { Text("Skill Trigger Name", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_teachable_skill_name")
                    )
                    if (!isRecordingDemonstration) {
                        Button(
                            onClick = onStartTeachableRecording,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalPurplePrimary),
                            modifier = Modifier.testTag("btn_start_teachable_recording")
                        ) {
                            Text("Watch Me", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    } else {
                        Button(
                            onClick = { onStopAndSaveTeachableSkill(teachableSkillName) },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusRoseRed),
                            modifier = Modifier.testTag("btn_save_teachable_recording")
                        ) {
                            Text("Save Skill", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // FEATURE 8: Hands-Free Speakerphone Call + System Alarm / Timer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(LiquidGlassPill.copy(alpha = 0.48f))
                    .border(1.dp, SoftLavenderGlow.copy(alpha = 0.26f), RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = SoftLavenderGlow, modifier = Modifier.size(17.dp))
                    Text(
                        text = "8. Speakerphone Call & Instant Timer",
                        color = TextPureWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = speakerCallTarget,
                        onValueChange = { speakerCallTarget = it },
                        label = { Text("Contact / Number", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_speaker_call_target")
                    )
                    Button(
                        onClick = { onSpeakerCall(speakerCallTarget) },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalPurplePrimary),
                        modifier = Modifier.testTag("btn_speakerphone_call")
                    ) {
                        Text("Call", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Button(
                        onClick = { onSetTimerSeconds(300) },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricOrchid),
                        modifier = Modifier.testTag("btn_quick_5m_timer")
                    ) {
                        Text("5m Timer", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            // FEATURE 9: Zero-Click Deep-Link Teleport Engine
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(LiquidGlassPill.copy(alpha = 0.48f))
                    .border(1.dp, SoftLavenderGlow.copy(alpha = 0.26f), RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = SoftLavenderGlow, modifier = Modifier.size(17.dp))
                    Text(
                        text = "9. Zero-Click Deep-Link Teleport",
                        color = TextPureWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TeleportChip("Maps -> Home", "btn_teleport_maps_home") {
                        onZeroClickTeleport("MAPS_NAVIGATION", "Home")
                    }
                    TeleportChip("Play Arijit Hits", "btn_teleport_music") {
                        onZeroClickTeleport("PLAY_MUSIC_SEARCH", "Arijit Singh")
                    }
                    TeleportChip("YouTube Shorts", "btn_teleport_yt_shorts") {
                        onZeroClickTeleport("YOUTUBE_SHORTS", "Shorts")
                    }
                    TeleportChip("UPI / QR Scanner", "btn_teleport_upi_scanner") {
                        onZeroClickTeleport("UPI_SCANNER", "Pay")
                    }
                }
            }
        }
    }
}

@Composable
private fun TeleportChip(
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        RoyalPurplePrimary.copy(alpha = 0.30f),
                        DeepAmethystPressed.copy(alpha = 0.35f)
                    )
                )
            )
            .border(1.dp, SoftLavenderGlow.copy(alpha = 0.50f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            color = TextPureWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun JarvisInAppCyberHudBanner(
    hudState: OverlayHudState,
    onQuickVoiceClick: () -> Unit,
    onCloseHud: () -> Unit
) {
    if (!hudState.isOverlayActive) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(LiquidGlassElevated.copy(alpha = 0.96f))
            .border(1.5.dp, RoyalPurplePrimary, RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .testTag("in_app_cyber_hud_banner"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.BubbleChart,
                contentDescription = null,
                tint = SoftLavenderGlow,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = hudState.currentHeadline,
                    color = SoftLavenderGlow,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = hudState.currentStepDetail,
                    color = TextPureWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(RoyalPurplePrimary.copy(alpha = 0.32f))
                    .border(1.dp, SoftLavenderGlow, RoundedCornerShape(999.dp))
                    .clickable(onClick = onQuickVoiceClick)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text("Voice", color = TextPureWhite, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(StatusRoseRed.copy(alpha = 0.22f))
                    .border(1.dp, StatusRoseRed, RoundedCornerShape(999.dp))
                    .clickable(onClick = onCloseHud)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text("Hide", color = StatusRoseRed, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
