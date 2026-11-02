package com.jarvisai.assistant.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusSpeakingAzure
import com.example.ui.theme.StatusThinkingAmber
import com.jarvisai.assistant.data.local.ReminderEntity
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.InstalledAppInfo
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.service.ActionResult
import com.jarvisai.assistant.ui.components.HudCard
import com.jarvisai.assistant.ui.components.HudConfirmationDialog
import com.jarvisai.assistant.ui.components.HudSectionHeader
import com.jarvisai.assistant.ui.components.HudTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    BackHandler(onBack = onBack)

    // Alarm state
    var alarmHourText by rememberSaveable { mutableStateOf("07") }
    var alarmMinuteText by rememberSaveable { mutableStateOf("30") }
    var alarmLabelText by rememberSaveable { mutableStateOf("JARVIS Morning Briefing") }
    var pendingAlarmConfirm by remember { mutableStateOf<Triple<Int, Int, String>?>(null) }

    // Reminder state
    var reminderTitle by rememberSaveable { mutableStateOf("") }
    var reminderNotes by rememberSaveable { mutableStateOf("") }
    var reminderMinutesOffset by rememberSaveable { mutableStateOf("60") }
    var syncReminderWithSystemCalendar by rememberSaveable { mutableStateOf(false) }

    // Call state with mandatory confirmation
    var phoneNumberInput by rememberSaveable { mutableStateOf("") }
    var pendingCallConfirmationNumber by remember { mutableStateOf<String?>(null) }

    // App launch confirmation if enabled
    var pendingAppToLaunch by remember { mutableStateOf<InstalledAppInfo?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        HudTopBar(
            title = "DEVICE & OS CONTROLS",
            subtitle = "OFFICIAL ANDROID INTENTS & HARDWARE APIS",
            status = status,
            onBackClick = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Action Verification Banner
            if (actionFeedback != null) {
                item {
                    val bannerColor = if (actionFeedback.success) StatusListeningEmerald else AlertCrimson
                    HudCard(
                        glowColor = bannerColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("phone_control_feedback_banner")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = bannerColor
                                )
                                Text(
                                    text = actionFeedback.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = bannerColor
                                )
                            }
                            IconButton(onClick = onDismissFeedback) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }
            }

            // 1. HARDWARE FLASHLIGHT & SYSTEM SETTINGS
            item {
                HudSectionHeader(
                    title = "Hardware & System Panels",
                    codeBadge = "TORCH & SETTINGS",
                    icon = Icons.Default.Settings
                )
            }

            item {
                HudCard(modifier = Modifier.fillMaxWidth()) {
                    // Flashlight Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                contentDescription = "Flashlight",
                                tint = if (isFlashlightOn) StatusThinkingAmber else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "HARDWARE FLASHLIGHT (TORCH)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (hasFlashlightHardware) {
                                        if (isFlashlightOn) "Status: ACTIVE (CameraManager Torch ON)"
                                        else "Status: STANDBY (CameraManager Torch OFF)"
                                    } else {
                                        "No camera flash unit reported by device hardware"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isFlashlightOn,
                            onCheckedChange = { onToggleFlashlight(it) },
                            modifier = Modifier.testTag("switch_flashlight")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // System Settings Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenWifiSettings,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_open_wifi")
                        ) {
                            Icon(imageVector = Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WI-FI", style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = onOpenBluetoothSettings,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_open_bluetooth")
                        ) {
                            Icon(imageVector = Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("BLUETOOTH", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenAndroidSettings,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_open_android_settings")
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("OS SETTINGS", style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = onOpenDisplaySettings,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Brightness6, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DISPLAY", style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = onOpenSoundSettings,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SOUND", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // 2. CREATE ALARMS
            item {
                HudSectionHeader(
                    title = "Create System Alarm",
                    codeBadge = "ALARMCLOCK API",
                    icon = Icons.Default.AccessAlarm
                )
            }

            item {
                HudCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = alarmHourText,
                            onValueChange = { if (it.length <= 2) alarmHourText = it.filter { c -> c.isDigit() } },
                            label = { Text("Hour (0-23)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("alarm_hour_input")
                        )
                        OutlinedTextField(
                            value = alarmMinuteText,
                            onValueChange = { if (it.length <= 2) alarmMinuteText = it.filter { c -> c.isDigit() } },
                            label = { Text("Minute (0-59)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("alarm_minute_input")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = alarmLabelText,
                        onValueChange = { alarmLabelText = it },
                        label = { Text("Alarm Label") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alarm_label_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val h = alarmHourText.toIntOrNull() ?: -1
                            val m = alarmMinuteText.toIntOrNull() ?: -1
                            if (settings.confirmConsequentialActions) {
                                pendingAlarmConfirm = Triple(h, m, alarmLabelText)
                            } else {
                                onCreateAlarm(h, m, alarmLabelText)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_create_alarm")
                    ) {
                        Icon(imageVector = Icons.Default.AccessAlarm, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SET SYSTEM ALARM", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // 3. CREATE & MANAGE REMINDERS
            item {
                HudSectionHeader(
                    title = "Tactical Reminders",
                    codeBadge = "ROOM + CALENDAR",
                    icon = Icons.Default.Event
                )
            }

            item {
                HudCard(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = reminderTitle,
                        onValueChange = { reminderTitle = it },
                        label = { Text("Reminder Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reminder_title_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = reminderNotes,
                            onValueChange = { reminderNotes = it },
                            label = { Text("Notes (optional)") },
                            singleLine = true,
                            modifier = Modifier.weight(1.6f)
                        )
                        OutlinedTextField(
                            value = reminderMinutesOffset,
                            onValueChange = { reminderMinutesOffset = it.filter { c -> c.isDigit() } },
                            label = { Text("In Mins") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Checkbox(
                            checked = syncReminderWithSystemCalendar,
                            onCheckedChange = { syncReminderWithSystemCalendar = it }
                        )
                        Text(
                            text = "Also open Android Calendar event sheet",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = {
                            val offsetMins = (reminderMinutesOffset.toLongOrNull() ?: 60L).coerceAtLeast(1L)
                            val scheduledTime = System.currentTimeMillis() + offsetMins * 60_000L
                            onCreateReminder(
                                reminderTitle,
                                reminderNotes,
                                scheduledTime,
                                syncReminderWithSystemCalendar
                            )
                            if (reminderTitle.isNotBlank()) {
                                reminderTitle = ""
                                reminderNotes = ""
                            }
                        },
                        enabled = reminderTitle.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusSpeakingAzure,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_create_reminder")
                    ) {
                        Icon(imageVector = Icons.Default.Event, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CREATE REMINDER", style = MaterialTheme.typography.labelLarge)
                    }

                    if (reminders.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "ACTIVE LOCAL REMINDERS (${reminders.size}):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        reminders.forEach { reminder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = reminder.isCompleted,
                                        onCheckedChange = { onToggleReminderCompleted(reminder) }
                                    )
                                    Column {
                                        Text(
                                            text = reminder.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else null,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val formattedTime = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                                            .format(Date(reminder.scheduledTimeMillis))
                                        Text(
                                            text = if (reminder.notes.isNotBlank()) "$formattedTime • ${reminder.notes}" else formattedTime,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onDeleteReminder(reminder.id) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Reminder",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. INITIATE PHONE CALL (WITH MANDATORY CONFIRMATION FLOW)
            item {
                HudSectionHeader(
                    title = "Initiate Phone Call",
                    codeBadge = "CONFIRMATION REQUIRED",
                    icon = Icons.Default.Call
                )
            }

            item {
                HudCard(
                    glowColor = StatusListeningEmerald,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Phone calls always require operator confirmation before opening the Android Dialer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = phoneNumberInput,
                            onValueChange = { phoneNumberInput = it },
                            label = { Text("Phone Number") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("call_phone_input")
                        )
                        Button(
                            onClick = {
                                if (phoneNumberInput.isNotBlank()) {
                                    pendingCallConfirmationNumber = phoneNumberInput.trim()
                                }
                            },
                            enabled = phoneNumberInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusListeningEmerald,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("btn_initiate_call")
                        ) {
                            Icon(imageVector = Icons.Default.Call, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DIAL", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // 5. OPEN INSTALLED APPLICATIONS
            item {
                HudSectionHeader(
                    title = "Installed Applications (${installedApps.size})",
                    codeBadge = "PACKAGEMANAGER",
                    icon = Icons.Default.Apps
                )
            }

            item {
                HudCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = appSearchQuery,
                            onValueChange = onAppSearchQueryChange,
                            placeholder = { Text("Filter installed apps...") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Search, contentDescription = null)
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("installed_apps_search")
                        )
                        IconButton(onClick = onRefreshApps) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Installed Apps",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (installedApps.isEmpty()) {
                        Text(
                            text = "No matching launchable apps found on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            installedApps.take(15).forEach { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (settings.confirmConsequentialActions) {
                                                pendingAppToLaunch = app
                                            } else {
                                                onLaunchApp(app)
                                            }
                                        },
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Launch,
                                            contentDescription = "Open ${app.appName}",
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("OPEN", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Call Confirmation Dialog
    pendingCallConfirmationNumber?.let { targetNumber ->
        HudConfirmationDialog(
            title = "CONFIRM OUTBOUND CALL",
            message = "Do you want JARVIS to open the Android Phone Dialer with number '$targetNumber'?",
            confirmLabel = "OPEN DIALER",
            onConfirm = {
                onInitiateConfirmedCall(targetNumber)
                pendingCallConfirmationNumber = null
            },
            onDismiss = { pendingCallConfirmationNumber = null }
        )
    }

    // Alarm Confirmation Dialog
    pendingAlarmConfirm?.let { (h, m, label) ->
        HudConfirmationDialog(
            title = "CONFIRM SYSTEM ALARM",
            message = "Create a system alarm for ${String.format("%02d:%02d", h.coerceAtLeast(0), m.coerceAtLeast(0))} labeled '$label'?",
            confirmLabel = "CREATE ALARM",
            onConfirm = {
                onCreateAlarm(h, m, label)
                pendingAlarmConfirm = null
            },
            onDismiss = { pendingAlarmConfirm = null }
        )
    }

    // App Launch Confirmation Dialog
    pendingAppToLaunch?.let { app ->
        HudConfirmationDialog(
            title = "LAUNCH APPLICATION",
            message = "Open '${app.appName}' (${app.packageName})?",
            confirmLabel = "LAUNCH APP",
            onConfirm = {
                onLaunchApp(app)
                pendingAppToLaunch = null
            },
            onDismiss = { pendingAppToLaunch = null }
        )
    }
}
