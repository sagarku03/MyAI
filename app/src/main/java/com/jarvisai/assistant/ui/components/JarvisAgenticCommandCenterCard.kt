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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DynamicForm
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvisNeonCyan
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisVioletBright
import com.example.ui.theme.StatusThinkingAmber
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
        accentColor = JarvisNeonCyan,
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(18.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = JarvisNeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "AGENTIC AI MOBILE CONTROLLER",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "10 Autonomous Voice & UI Agent Capabilities Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisNeonGreen,
                            fontSize = 10.sp
                        )
                    }
                }

                // Feature 10: Quick Voice Undo / Rollback Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(999.dp))
                        .clickable { onVoiceUndoRollback() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("btn_agentic_voice_undo")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "Undo Action",
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Ruko / Wapas",
                            color = Color(0xFFFCA5A5),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // FEATURE 10: High-Risk Payment / Delete Safety Gatekeeper Banner (when triggered)
            if (pendingHighRiskGate != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF450A0A))
                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(16.dp))
                        .padding(12.dp)
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
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "SAFETY GATEKEEPER: ${pendingHighRiskGate.targetLabel}",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = pendingHighRiskGate.spokenPromptHinglish,
                        color = Color(0xFFFECACA),
                        fontSize = 11.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onConfirmHighRiskGate,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisNeonGreen),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_confirm_high_risk_gate")
                        ) {
                            Text("Haan, Confirm Karo", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onCancelHighRiskGate,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_cancel_high_risk_gate")
                        ) {
                            Text("Nahi, Cancel Karo", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // FEATURE 1: Autonomous Multi-Step "ReAct" UI Navigation Loop
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                    .border(1.dp, JarvisNeonCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "1. Autonomous Multi-Step ReAct Loop (Observe → Think → Act → Verify)",
                    color = JarvisNeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it },
                    label = { Text("Multi-Step Voice Goal (Hinglish / English)", fontSize = 10.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = JarvisNeonCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_agentic_react_goal")
                )
                Button(
                    onClick = { onRunAutonomousGoal(goalInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisNeonCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_run_agentic_react_goal")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAgenticLoopRunning) "Executing ReAct Loop..." else "Run Autonomous Multi-Step Goal",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }

                if (agenticStepLogs.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF090D16))
                            .padding(8.dp)
                            .testTag("agentic_react_step_timeline"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        agenticStepLogs.forEach { step ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (step.succeeded) JarvisNeonGreen else StatusThinkingAmber,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Step ${step.stepNumber} [${step.phase}]: ${step.actionName} — ${step.summary}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                        .border(1.dp, JarvisVioletBright.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = JarvisVioletBright, modifier = Modifier.size(16.dp))
                        Text("2. Set-of-Mark Tap", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Badges: ${detectedBadges.size} interactive targets",
                        color = JarvisTextSecondary,
                        fontSize = 10.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(JarvisVioletBright.copy(alpha = 0.22f))
                                .clickable { onScanSetOfMarkBadges() }
                                .padding(vertical = 7.dp)
                                .testTag("btn_scan_som_badges"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Scan [1..N]", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(JarvisNeonCyan.copy(alpha = 0.22f))
                                .clickable { onTapSetOfMarkBadge(1) }
                                .padding(vertical = 7.dp)
                                .testTag("btn_tap_som_badge_1"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Tap #1", color = JarvisNeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Feature 3: System-Wide Floating Cyber-HUD Bubble
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                        .border(1.dp, JarvisNeonGreen.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.BubbleChart, contentDescription = null, tint = JarvisNeonGreen, modifier = Modifier.size(16.dp))
                            Text("3. Cyber-HUD", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = overlayHudState.isOverlayActive,
                            onCheckedChange = onToggleOverlayHud,
                            colors = SwitchDefaults.colors(checkedThumbColor = JarvisNeonGreen),
                            modifier = Modifier.testTag("switch_floating_cyber_hud")
                        )
                    }
                    Text(
                        text = overlayHudState.currentStepDetail,
                        color = JarvisTextSecondary,
                        fontSize = 9.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // FEATURE 4: Background Zero-Interruption Notification Auto-Reply Engine
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.MarkChatRead, contentDescription = null, tint = StatusThinkingAmber, modifier = Modifier.size(16.dp))
                    Text(
                        text = "4. Background Zero-Interruption Notification Reply (${activeReplyables.size} Cached)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
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
                        label = { Text("Sender", fontSize = 9.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.38f)
                            .testTag("input_bg_reply_sender")
                    )
                    OutlinedTextField(
                        value = inlineReplyText,
                        onValueChange = { inlineReplyText = it },
                        label = { Text("Inline Reply", fontSize = 9.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.62f)
                            .testTag("input_bg_reply_message")
                    )
                }
                Button(
                    onClick = { onSendInlineReply(inlineReplySender, inlineReplyText) },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusThinkingAmber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_send_bg_notification_reply")
                ) {
                    Text("Send Silent RemoteInput Reply", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.DynamicForm, contentDescription = null, tint = JarvisNeonCyan, modifier = Modifier.size(16.dp))
                        Text("5. Form Auto-Fill", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = activeFormSession?.let {
                            "Filled ${it.fields.count { f -> f.filledValue.isNotBlank() }}/${it.fields.size} fields"
                        } ?: "Fills EditTexts from Memory Vault",
                        color = JarvisTextSecondary,
                        fontSize = 9.sp
                    )
                    Button(
                        onClick = onAutoFillScreenForm,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisNeonCyan.copy(alpha = 0.25f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_autofill_screen_form")
                    ) {
                        Text("Auto-Fill Form", color = JarvisNeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Feature 6: Cross-App Data Bridge & Screen Translator
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null, tint = JarvisNeonGreen, modifier = Modifier.size(16.dp))
                        Text("6. Screen Bridge", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = lastScratchpadResult?.transformedSummaryOrTranslation
                            ?: "Translate & copy screen text",
                        color = JarvisTextSecondary,
                        fontSize = 9.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(
                        onClick = { onRunCrossAppBridge("Hindi", "Scratchpad") },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisNeonGreen.copy(alpha = 0.25f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_cross_app_screen_bridge")
                    ) {
                        Text("Translate Screen", color = JarvisNeonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // FEATURE 7: Teachable "Watch Me & Learn" Macro Recorder
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                    .border(
                        width = 1.dp,
                        color = if (isRecordingDemonstration) Color(0xFFEF4444) else Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp),
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
                            tint = if (isRecordingDemonstration) Color(0xFFEF4444) else JarvisVioletBright,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "7. Teachable 'Watch Me & Learn' Skill Recorder",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (isRecordingDemonstration) "REC (${recordedDemonstrationSteps.size} steps)" else "READY",
                        color = if (isRecordingDemonstration) Color(0xFFF87171) else JarvisNeonGreen,
                        fontSize = 10.sp,
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
                        label = { Text("Voice Skill Trigger Name", fontSize = 9.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_teachable_skill_name")
                    )
                    if (!isRecordingDemonstration) {
                        Button(
                            onClick = onStartTeachableRecording,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisVioletBright),
                            modifier = Modifier.testTag("btn_start_teachable_recording")
                        ) {
                            Text("Watch Me", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { onStopAndSaveTeachableSkill(teachableSkillName) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier.testTag("btn_save_teachable_recording")
                        ) {
                            Text("Save Skill", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // FEATURE 8: Hands-Free Speakerphone Call + System Alarm / Timer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = JarvisNeonCyan, modifier = Modifier.size(16.dp))
                    Text(
                        text = "8. Hands-Free Speakerphone Call & Voice Timer",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
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
                        label = { Text("Contact / Number", fontSize = 9.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_speaker_call_target")
                    )
                    Button(
                        onClick = { onSpeakerCall(speakerCallTarget) },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisNeonCyan),
                        modifier = Modifier.testTag("btn_speakerphone_call")
                    ) {
                        Text("Speaker Call", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onSetTimerSeconds(300) },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisNeonGreen),
                        modifier = Modifier.testTag("btn_quick_5m_timer")
                    ) {
                        Text("5m Timer", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // FEATURE 9: Zero-Click Deep-Link Teleport Engine
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = JarvisNeonGreen, modifier = Modifier.size(16.dp))
                    Text(
                        text = "9. Zero-Click Deep-Link Teleport (Skip Home Screens)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
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
            .background(Color(0xFF1E293B))
            .border(1.dp, JarvisNeonCyan.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
