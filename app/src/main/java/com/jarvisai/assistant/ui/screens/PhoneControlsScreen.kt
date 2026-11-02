package com.jarvisai.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisGlassBorder
import com.example.ui.theme.JarvisGlassSurface
import com.example.ui.theme.JarvisNeonCyan
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet
import com.example.ui.theme.JarvisVioletBright
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusThinkingAmber
import com.jarvisai.assistant.data.local.ReminderEntity
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.InstalledAppInfo
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.service.ActionResult
import com.jarvisai.assistant.ui.components.JarvisGlassPanel
import com.jarvisai.assistant.ui.components.JarvisModernDialog
import com.jarvisai.assistant.ui.components.JarvisPremiumTopBar
import com.jarvisai.assistant.ui.components.JarvisPrimaryActionButton
import com.jarvisai.assistant.ui.components.JarvisSearchBar

@Composable
fun PhoneControlsScreen(
    settings: UserSettings,
    status: AssistantStatus,
    isFlashlightOn: Boolean,
    hasFlashlightHardware: Boolean,
    installedApps: List<InstalledAppInfo>,
    appSearchQuery: String,
    reminders: List<ReminderEntity>,
    actionFeedback: ActionResult?,
    onBack: () -> Unit,
    onDismissFeedback: () -> Unit,
    onToggleFlashlight: (Boolean) -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenAndroidSettings: () -> Unit,
    onOpenDisplaySettings: () -> Unit,
    onOpenSoundSettings: () -> Unit,
    onCreateAlarm: (hour: Int, minute: Int, label: String) -> Unit,
    onCreateReminder: (title: String, notes: String, scheduledTimeMillis: Long, openCalendar: Boolean) -> Unit,
    onToggleReminderCompleted: (ReminderEntity) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onInitiateConfirmedCall: (String) -> Unit,
    onAppSearchQueryChange: (String) -> Unit,
    onRefreshApps: () -> Unit,
    onLaunchApp: (InstalledAppInfo) -> Unit
) {
    var alarmHour by remember { mutableStateOf("07") }
    var alarmMinute by remember { mutableStateOf("30") }
    var alarmLabel by remember { mutableStateOf("JARVIS Wakeup") }

    var reminderTitle by remember { mutableStateOf("") }
    var reminderNotes by remember { mutableStateOf("") }
    var reminderMinutesOffset by remember { mutableStateOf("60") }
    var openCalendarOnSave by remember { mutableStateOf(false) }

    var callPhoneNumber by remember { mutableStateOf("") }
    var showCallConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            JarvisPremiumTopBar(
                title = "Control Hub",
                subtitle = "Native Hardware, Dialer & ${installedApps.size} Apps",
                status = status,
                onBackClick = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Action Feedback Banner
            if (actionFeedback != null) {
                item {
                    val accent = if (actionFeedback.success) StatusListeningEmerald else AlertCrimson
                    JarvisGlassPanel(
                        accentColor = accent,
                        contentPadding = PaddingValues(14.dp),
                        modifier = Modifier.testTag("phone_control_feedback_banner")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )
                                Text(
                                    text = actionFeedback.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = onDismissFeedback,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 1. IMAGE 3 (iOS WIDGY 2x2 BEVELED DARK WIDGET ON LEFT + 2x2 DARK SQUIRCLE ICON GRID ON RIGHT)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left 2x2 Beveled Dark Neumorphic Widget (Image 3 Top-Left Calendar/Clock Widget style!)
                    Box(
                        modifier = Modifier
                            .weight(1.05f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF232631),
                                        Color(0xFF171922)
                                    )
                                )
                            )
                            .border(
                                width = 1.2.dp,
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.28f),
                                        Color.White.copy(alpha = 0.06f)
                                    )
                                ),
                                shape = RoundedCornerShape(28.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Top Row: Red Pill Badge + Circular Torch Dial (Image 3 "THU 26 + Clock" layout)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Inner Beveled Box
                                Column(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFF2B2F3C))
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(if (isFlashlightOn) StatusThinkingAmber else AlertCrimson)
                                            .padding(horizontal = 8.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "LED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isFlashlightOn) "ON" else "OFF",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                // Right Inner Beveled Torch Dial
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFF2B2F3C))
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                                        .clickable(enabled = hasFlashlightHardware) {
                                            onToggleFlashlight(!isFlashlightOn)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                        contentDescription = null,
                                        tint = if (isFlashlightOn) StatusThinkingAmber else JarvisNeonCyan,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Bottom Pill Capsule (Image 3 "3 Events For today" orange badge pill!)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xFF2B2F3C))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(StatusThinkingAmber),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isFlashlightOn) "1" else "0",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Black,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Torch",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = JarvisTextSecondary,
                                            fontSize = 9.sp
                                        )
                                        Text(
                                            text = if (isFlashlightOn) "Beam Active" else "Standby",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Switch(
                                    checked = isFlashlightOn,
                                    onCheckedChange = onToggleFlashlight,
                                    enabled = hasFlashlightHardware,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = StatusThinkingAmber
                                    ),
                                    modifier = Modifier.testTag("switch_flashlight")
                                )
                            }
                        }
                    }

                    // Right 2x2 Dark Squircle App Icon Grid (Image 3 Right-Side 2x2 App Icons!)
                    Column(
                        modifier = Modifier.weight(0.95f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            WidgySquircleIcon(
                                label = "Wi-Fi",
                                icon = Icons.Default.Wifi,
                                iconColor = JarvisNeonCyan,
                                onClick = onOpenWifiSettings,
                                testTag = "btn_open_wifi"
                            )
                            WidgySquircleIcon(
                                label = "Bluetooth",
                                icon = Icons.Default.Bluetooth,
                                iconColor = JarvisElectricBlue,
                                onClick = onOpenBluetoothSettings,
                                testTag = "btn_open_bluetooth"
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            WidgySquircleIcon(
                                label = "Settings",
                                icon = Icons.Default.Settings,
                                iconColor = JarvisTextPrimary,
                                onClick = onOpenAndroidSettings,
                                testTag = "btn_open_android_settings"
                            )
                            WidgySquircleIcon(
                                label = "Display",
                                icon = Icons.Default.Brightness6,
                                iconColor = StatusThinkingAmber,
                                onClick = onOpenDisplaySettings,
                                testTag = "btn_open_display"
                            )
                        }
                    }
                }
            }

            // 2. APPLE ARCADE "LIBRARY" STACKED CATEGORY ROWS (Image 7: Sound, Alarm & Dialer)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF161924))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenSoundSettings)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("btn_open_sound"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(StatusListeningEmerald, JarvisNeonCyan)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = Color(0xFF04120C),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Audio & Volume Mixer",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Open Android Sound & Vibration panel",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextSecondary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = JarvisTextSecondary
                    )
                }
            }

            // 3. MONEX-INSPIRED DIGITAL DIALER CARD WITH NEON LIME-GREEN CTA PILL (Image 5 "Send Money")
            item {
                JarvisGlassPanel(
                    accentColor = JarvisNeonGreen,
                    cornerRadius = 26.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(JarvisNeonGreen.copy(alpha = 0.20f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = null,
                                        tint = JarvisNeonGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = "Direct Voice Dialer",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Text(
                                text = if (settings.confirmConsequentialActions) "SAFEGUARD ON" else "DIRECT",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisNeonGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedTextField(
                            value = callPhoneNumber,
                            onValueChange = { callPhoneNumber = it },
                            label = { Text("Enter Phone Number") },
                            placeholder = { Text("+91 98765 43210") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF101524),
                                unfocusedContainerColor = Color(0xFF0E1320),
                                focusedBorderColor = JarvisNeonGreen,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.14f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("call_phone_input")
                        )

                        // Image 5 Monex Full-Width Neon Lime-Green Pill Button
                        JarvisPrimaryActionButton(
                            text = "Place Voice Call",
                            icon = Icons.Default.Call,
                            enabled = callPhoneNumber.isNotBlank(),
                            accentColor = JarvisNeonGreen,
                            onClick = {
                                if (settings.confirmConsequentialActions) {
                                    showCallConfirmDialog = true
                                } else {
                                    onInitiateConfirmedCall(callPhoneNumber)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_initiate_call")
                        )
                    }
                }
            }

            // 4. IMAGE 3 WEATHER WIDGET STYLE SYSTEM ALARM CARD
            item {
                JarvisGlassPanel(
                    accentColor = JarvisVioletBright,
                    cornerRadius = 26.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "$alarmHour:$alarmMinute",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "System Clock Alarm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.AccessAlarm,
                                contentDescription = null,
                                tint = JarvisVioletBright,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        // Image 3 Weather Widget Capsule Range Bar (`00:00 ---o--- 23:59`)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFF22273A))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "00:00",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextSecondary
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(JarvisNeonGreen, StatusThinkingAmber, JarvisVioletBright)
                                        )
                                    )
                            )
                            Text(
                                text = "23:59",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextSecondary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = alarmHour,
                                onValueChange = { if (it.length <= 2) alarmHour = it.filter(Char::isDigit) },
                                label = { Text("HH") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(0.26f)
                                    .testTag("alarm_hour_input")
                            )
                            OutlinedTextField(
                                value = alarmMinute,
                                onValueChange = { if (it.length <= 2) alarmMinute = it.filter(Char::isDigit) },
                                label = { Text("MM") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(0.26f)
                                    .testTag("alarm_minute_input")
                            )
                            OutlinedTextField(
                                value = alarmLabel,
                                onValueChange = { alarmLabel = it },
                                label = { Text("Label") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(0.48f)
                                    .testTag("alarm_label_input")
                            )
                        }

                        JarvisPrimaryActionButton(
                            text = "Set System Alarm",
                            icon = Icons.Default.AccessAlarm,
                            accentColor = JarvisNeonCyan,
                            onClick = {
                                val h = alarmHour.toIntOrNull() ?: 7
                                val m = alarmMinute.toIntOrNull() ?: 30
                                onCreateAlarm(h, m, alarmLabel)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_create_alarm")
                        )
                    }
                }
            }

            // 5. TACTICAL REMINDERS CARD
            item {
                JarvisGlassPanel(
                    accentColor = JarvisElectricBlue,
                    cornerRadius = 24.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tactical Reminders (${reminders.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = null,
                                tint = JarvisNeonCyan
                            )
                        }

                        OutlinedTextField(
                            value = reminderTitle,
                            onValueChange = { reminderTitle = it },
                            label = { Text("Reminder Title") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reminder_title_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = reminderNotes,
                                onValueChange = { reminderNotes = it },
                                label = { Text("Notes") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(0.65f)
                            )
                            OutlinedTextField(
                                value = reminderMinutesOffset,
                                onValueChange = { reminderMinutesOffset = it.filter(Char::isDigit) },
                                label = { Text("Mins") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(0.35f)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { openCalendarOnSave = !openCalendarOnSave }
                        ) {
                            Checkbox(
                                checked = openCalendarOnSave,
                                onCheckedChange = { openCalendarOnSave = it },
                                colors = CheckboxDefaults.colors(checkedColor = JarvisNeonCyan)
                            )
                            Text(
                                text = "Sync with Android Calendar app",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextSecondary
                            )
                        }

                        JarvisPrimaryActionButton(
                            text = "Save Reminder",
                            icon = Icons.Default.Event,
                            enabled = reminderTitle.isNotBlank(),
                            accentColor = JarvisVioletBright,
                            onClick = {
                                val offsetMins = reminderMinutesOffset.toLongOrNull() ?: 60L
                                val scheduledTime = System.currentTimeMillis() + (offsetMins * 60_000L)
                                onCreateReminder(reminderTitle, reminderNotes, scheduledTime, openCalendarOnSave)
                                reminderTitle = ""
                                reminderNotes = ""
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_create_reminder")
                        )

                        reminders.take(4).forEach { rem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF151A2C))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onToggleReminderCompleted(rem) }
                                ) {
                                    Icon(
                                        imageVector = if (rem.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                                        contentDescription = null,
                                        tint = if (rem.isCompleted) StatusListeningEmerald else JarvisNeonCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = rem.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (rem.isCompleted) JarvisTextMuted else Color.White,
                                        textDecoration = if (rem.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteReminder(rem.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = AlertCrimson,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. IMAGE 3 GOOGLE WIDGET SEARCH + IMAGE 7 APPLE ARCADE "YOUR GAMES" APP DIRECTORY
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Applications (${installedApps.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1B2032))
                                .clickable(onClick = onRefreshApps),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan Apps",
                                tint = JarvisTextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Image 3 Bottom Google Widget Search Box with 4 Neon Color Bars
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF171A24))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        JarvisSearchBar(
                            query = appSearchQuery,
                            onQueryChange = onAppSearchQueryChange,
                            placeholder = "SEARCH APPS",
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "installed_apps_search"
                        )

                        // Image 3 4-Color Neon Glow Strip (Blue, Pink, Amber, Green)
                        Row(
                            modifier = Modifier.fillMaxWidth(0.75f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Color(0xFF3B82F6),
                                Color(0xFFEC4899),
                                Color(0xFFFBBF24),
                                Color(0xFF10B981)
                            ).forEach { barColor ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(barColor)
                                )
                            }
                        }
                    }
                }
            }

            // Image 7 Apple Arcade "Your Games" Style App Rows (Squircle Icon + Title/Subtitle + "Open" Pill)
            items(installedApps.take(40), key = { it.packageName }) { app ->
                val monogram = app.appName.firstOrNull()?.uppercaseChar()?.toString() ?: "A"
                val palette = listOf(
                    listOf(Color(0xFF6366F1), Color(0xFFA855F7)),
                    listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)),
                    listOf(Color(0xFF10B981), Color(0xFF059669)),
                    listOf(Color(0xFFF43F5E), Color(0xFFFB923C))
                )
                val colors = palette[kotlin.math.abs(app.packageName.hashCode()) % palette.size]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLaunchApp(app) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // 52.dp Squircle App Icon (Image 7 Stardew Valley / Hello Kitty icon style)
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(colors))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = monogram,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Image 7 Apple Arcade Dark-Gray Pill "Play" / "Open" Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF262936))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                            .clickable { onLaunchApp(app) }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Open",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showCallConfirmDialog) {
        JarvisModernDialog(
            title = "Confirm Voice Call",
            message = "Open phone dialer for $callPhoneNumber?",
            confirmLabel = "Launch Dialer",
            onConfirm = {
                showCallConfirmDialog = false
                onInitiateConfirmedCall(callPhoneNumber)
            },
            onDismiss = { showCallConfirmDialog = false }
        )
    }
}

@Composable
private fun WidgySquircleIcon(
    label: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF242834), Color(0xFF151821))
                    )
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.18f),
                    RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
