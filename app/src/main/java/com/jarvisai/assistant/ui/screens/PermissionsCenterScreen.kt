package com.jarvisai.assistant.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CancelPresentation
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.MainActivity
import com.jarvisai.assistant.service.AutomationAccessibilityService
import com.jarvisai.assistant.service.JarvisAccessibilityService
import com.jarvisai.assistant.service.LiveVoiceForegroundService
import com.jarvisai.assistant.termux.JarvisTermuxBridge

private data class AppPermissionSnapshot(
    val micGranted: Boolean,
    val notificationsGranted: Boolean,
    val cameraGranted: Boolean,
    val accessibilityTouchGranted: Boolean,
    val accessibilityTouchMalfunctioning: Boolean,
    val automationAccessibilityGranted: Boolean,
    val overlayGranted: Boolean,
    val notificationListenerGranted: Boolean,
    val allFilesGranted: Boolean,
    val batteryUnrestricted: Boolean,
    val contactsGranted: Boolean,
    val phoneGranted: Boolean,
    val locationGranted: Boolean,
    val bluetoothGranted: Boolean,
    val termuxReady: Boolean,
    val termuxSubtitle: String
)

private fun readPermissionSnapshot(context: Context): AppPermissionSnapshot {
    fun hasPerm(perm: String): Boolean =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    val mic = hasPerm(Manifest.permission.RECORD_AUDIO)
    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        hasPerm(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        true
    }
    val camera = hasPerm(Manifest.permission.CAMERA)
    val a11yTouch = JarvisAccessibilityService.isConnectedFlow.value ||
        JarvisAccessibilityService.isAccessibilityEnabled(context)
    val a11yBroken = !a11yTouch && JarvisAccessibilityService.isAccessibilityMalfunctioning(context)
    val autoA11y = a11yTouch || AutomationAccessibilityService.instance != null
    val overlay = LiveVoiceForegroundService.canDrawOverlays(context)
    val notifListener = runCatching {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }.getOrDefault(false)
    val allFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        hasPerm(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val battery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    val contacts = hasPerm(Manifest.permission.READ_CONTACTS)
    val phone = hasPerm(Manifest.permission.CALL_PHONE) && hasPerm(Manifest.permission.READ_PHONE_STATE)
    val location = hasPerm(Manifest.permission.ACCESS_FINE_LOCATION) ||
        hasPerm(Manifest.permission.ACCESS_COARSE_LOCATION)
    val bluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        hasPerm(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        true
    }
    val termuxDiag = runCatching { JarvisTermuxBridge(context).getDiagnostics() }.getOrNull()
    val termuxReady = termuxDiag?.let { it.isLocalBridgeActive || it.hasRunCommandPermission } ?: false
    val termuxDesc = when {
        termuxDiag == null -> "Connect external Termux or manage RUN_COMMAND bridge"
        termuxDiag.isLocalBridgeActive -> "Local Termux Bridge (:2238) active and connected"
        termuxDiag.hasRunCommandPermission -> "Termux RUN_COMMAND permission granted"
        !termuxDiag.isTermuxInstalled -> "External Termux not installed • Embedded Linux engine active"
        else -> "Optional bridge for running scripts inside external Termux app"
    }

    return AppPermissionSnapshot(
        micGranted = mic,
        notificationsGranted = notifications,
        cameraGranted = camera,
        accessibilityTouchGranted = a11yTouch,
        accessibilityTouchMalfunctioning = a11yBroken,
        automationAccessibilityGranted = autoA11y,
        overlayGranted = overlay,
        notificationListenerGranted = notifListener,
        allFilesGranted = allFiles,
        batteryUnrestricted = battery,
        contactsGranted = contacts,
        phoneGranted = phone,
        locationGranted = location,
        bluetoothGranted = bluetooth,
        termuxReady = termuxReady,
        termuxSubtitle = termuxDesc
    )
}

@Composable
fun PermissionsCenterScreen(
    isScreenSharing: Boolean = false,
    screenFramesSent: Long = 0L,
    onStartScreenShare: (Int, Intent) -> Unit = { _, _ -> },
    onStopScreenShare: () -> Unit = {},
    onScreenShareDenied: () -> Unit = {},
    onOpenTermuxSetup: () -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val a11yConnectedFlow by JarvisAccessibilityService.isConnectedFlow.collectAsState()

    var refreshTick by remember { mutableIntStateOf(0) }
    var snapshot by remember(refreshTick, a11yConnectedFlow) {
        mutableStateOf(readPermissionSnapshot(context))
    }
    val permanentlyDeniedHints = remember { mutableStateMapOf<String, Boolean>() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var lastRequestedKey by remember { mutableStateOf("") }
    val trackedSingleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && lastRequestedKey.isNotEmpty()) {
            permanentlyDeniedHints[lastRequestedKey] = true
        } else if (granted && lastRequestedKey.isNotEmpty()) {
            permanentlyDeniedHints[lastRequestedKey] = false
        }
        refreshTick++
    }

    val multiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        if (resultMap.values.any { !it } && lastRequestedKey.isNotEmpty()) {
            permanentlyDeniedHints[lastRequestedKey] = true
        } else if (lastRequestedKey.isNotEmpty()) {
            permanentlyDeniedHints[lastRequestedKey] = false
        }
        refreshTick++
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            onStartScreenShare(result.resultCode, data)
        } else {
            onScreenShareDenied()
        }
        refreshTick++
    }

    fun requestSingleRuntimePermission(key: String, permission: String) {
        if (permanentlyDeniedHints[key] == true) {
            Toast.makeText(
                context,
                "System Permissions page khul raha hai — wahan Allow select karein.",
                Toast.LENGTH_SHORT
            ).show()
            MainActivity.openTermuxPermissionSettings(context)
        } else {
            lastRequestedKey = key
            trackedSingleLauncher.launch(permission)
        }
    }

    fun requestMultiRuntimePermissions(key: String, permissions: Array<String>) {
        if (permanentlyDeniedHints[key] == true) {
            Toast.makeText(
                context,
                "System Permissions page khul raha hai — wahan Allow select karein.",
                Toast.LENGTH_SHORT
            ).show()
            MainActivity.openTermuxPermissionSettings(context)
        } else {
            lastRequestedKey = key
            multiPermissionLauncher.launch(permissions)
        }
    }

    val coreGrantedList = listOf(
        snapshot.micGranted,
        snapshot.notificationsGranted,
        snapshot.cameraGranted,
        snapshot.accessibilityTouchGranted,
        snapshot.overlayGranted,
        snapshot.notificationListenerGranted,
        snapshot.allFilesGranted,
        snapshot.batteryUnrestricted,
        snapshot.contactsGranted,
        snapshot.phoneGranted,
        snapshot.locationGranted,
        snapshot.bluetoothGranted
    )
    val grantedCount = coreGrantedList.count { it }
    val totalCount = coreGrantedList.size
    val progress = (grantedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070913),
                        Color(0xFF0A0F1F),
                        Color(0xFF070912)
                    )
                )
            )
            .testTag("permissions_center_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF131A2C))
                            .border(1.dp, Color(0xFF283552), CircleShape)
                            .testTag("permissions_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = "Permissions",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Allow app permissions one by one",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    onClick = { refreshTick++ },
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF131B2E),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("permissions_refresh_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh status",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Refresh",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary Progress Card
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF101629),
                        border = BorderStroke(
                            1.2.dp,
                            Brush.horizontalGradient(
                                listOf(Color(0xFF8B5CF6), Color(0xFF06B6D4))
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF7C3AED), Color(0xFF06B6D4))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Permission Status",
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "$grantedCount of $totalCount permissions enabled",
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (grantedCount == totalCount) Color(0xFF10B981).copy(alpha = 0.2f)
                                    else Color(0xFF8B5CF6).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (grantedCount == totalCount) "ALL READY" else "${(progress * 100).toInt()}%",
                                        color = if (grantedCount == totalCount) Color(0xFF34D399) else Color(0xFFC4B5FD),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { progress },
                                color = Color(0xFF22D3EE),
                                trackColor = Color(0xFF1E293B),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(999.dp))
                            )
                        }
                    }
                }

                // SECTION 1: LIVE VOICE, SCREEN & AGENT PERMISSIONS (Transferred from Live Voice screen)
                item { PermissionSectionHeader("VOICE, SCREEN & AGENT ACCESS") }

                item {
                    PermissionItemCard(
                        icon = Icons.Default.Mic,
                        accentColor = Color(0xFFA855F7),
                        title = "Microphone",
                        description = "Live Voice calls, voice dictation & hands-free speech",
                        isGranted = snapshot.micGranted,
                        actionLabel = if (snapshot.micGranted) "Granted" else "Allow",
                        testTag = "perm_btn_microphone",
                        onActionClick = {
                            if (!snapshot.micGranted) {
                                requestSingleRuntimePermission("mic", Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }

                item {
                    PermissionItemCard(
                        icon = Icons.Default.TouchApp,
                        accentColor = Color(0xFFF59E0B),
                        title = "Screen Touch Agent (Accessibility)",
                        description = when {
                            snapshot.accessibilityTouchGranted -> "Active — JARVIS can tap, scroll, type & automate UI"
                            snapshot.accessibilityTouchMalfunctioning -> "Needs reset — Accessibility me jaakar OFF karke ON karein"
                            else -> "Required for hands-free UI clicks, scrolling & typing"
                        },
                        isGranted = snapshot.accessibilityTouchGranted,
                        actionLabel = when {
                            snapshot.accessibilityTouchGranted -> "Manage"
                            snapshot.accessibilityTouchMalfunctioning -> "Reset"
                            else -> "Enable"
                        },
                        allowActionWhenGranted = true,
                        testTag = "btn_open_accessibility_settings",
                        onActionClick = {
                            JarvisAccessibilityService.openAccessibilitySettings(context)
                        }
                    )
                }

                item {
                    PermissionItemCard(
                        icon = Icons.Default.PictureInPictureAlt,
                        accentColor = Color(0xFF8B5CF6),
                        title = "Display Over Other Apps",
                        description = "Show floating call bubble & cyber HUD across apps",
                        isGranted = snapshot.overlayGranted,
                        actionLabel = if (snapshot.overlayGranted) "Manage" else "Allow",
                        allowActionWhenGranted = true,
                        testTag = "btn_grant_overlay_permission",
                        onActionClick = {
                            runCatching {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                context.startActivity(intent)
                            }
                        }
                    )
                }

                item {
                    PermissionItemCard(
                        icon = if (isScreenSharing) Icons.Default.CancelPresentation else Icons.Default.PresentToAll,
                        accentColor = Color(0xFF06B6D4),
                        title = "Live Screen Vision (Screen Capture)",
                        description = when {
                            isScreenSharing -> "Streaming live screen ($screenFramesSent frames sent)"
                            snapshot.accessibilityTouchGranted -> "1s Auto-Vision ready • Tap Share for HD MediaProjection stream"
                            else -> "Let JARVIS see your screen in real time during calls"
                        },
                        isGranted = isScreenSharing,
                        statusBadgeOverride = when {
                            isScreenSharing -> "ACTIVE"
                            snapshot.accessibilityTouchGranted -> "AUTO READY"
                            else -> "OPTIONAL"
                        },
                        statusColorOverride = when {
                            isScreenSharing -> Color(0xFF10B981)
                            snapshot.accessibilityTouchGranted -> Color(0xFF38BDF8)
                            else -> Color(0xFF94A3B8)
                        },
                        actionLabel = if (isScreenSharing) "Stop" else "Share",
                        allowActionWhenGranted = true,
                        testTag = if (isScreenSharing) "btn_stop_screen_share" else "btn_start_screen_share",
                        onActionClick = {
                            if (isScreenSharing) {
                                onStopScreenShare()
                            } else {
                                val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                                if (mpm != null) {
                                    screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
                                }
                            }
                        }
                    )
                }

                item {
                    PermissionItemCard(
                        icon = Icons.Default.NotificationsActive,
                        accentColor = Color(0xFF38BDF8),
                        title = "Notification Listener Access",
                        description = "Read alerts, harvest OTPs & send smart background replies",
                        isGranted = snapshot.notificationListenerGranted,
                        actionLabel = if (snapshot.notificationListenerGranted) "Manage" else "Allow",
                        allowActionWhenGranted = true,
                        testTag = "perm_btn_notification_listener",
                        onActionClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        }
                    )
                }

                item {
                    PermissionItemCard(
                        icon = Icons.Default.NotificationsNone,
                        accentColor = Color(0xFF60A5FA),
                        title = "App Notifications (Android 13+)",
                        description = "Show ongoing call controls, timers & agent alerts",
                        isGranted = snapshot.notificationsGranted,
                        actionLabel = if (snapshot.notificationsGranted) "Granted" else "Allow",
                        testTag = "perm_btn_post_notifications",
                        onActionClick = {
                            if (!snapshot.notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestSingleRuntimePermission("notif", Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }

                // SECTION 2: FILES, CAMERA & SYSTEM STABILITY
                item { PermissionSectionHeader("FILES, CAMERA & SYSTEM") }

                item {
                    PermissionItemCard(
                        icon = Icons.Default.FolderOpen,
                        accentColor = Color(0xFF22D3EE),
                        title = "All Files Access",
                        description = "Read & manage local projects, scripts, downloads & backups",
                        isGranted = snapshot.allFilesGranted,
                        actionLabel = if (snapshot.allFilesGranted) "Manage" else "Allow",
                        allowActionWhenGranted = true,
                        testTag = "perm_btn_all_files",
                        onActionClick = {
                            MainActivity.openAllFilesAccessSettings(context)
                        }
                    )
                }

                item {
                    PermissionItemCard(
                        icon = Icons.Default.CameraAlt,
                        accentColor = Color(0xFFF472B6),
                        title = "Camera",
                        description = "Vision analysis, optical math solver & air gestures",
                        isGranted = snapshot.cameraGranted,
                        actionLabel = if (snapshot.cameraGranted) "Granted" else "Allow",
                        testTag = "perm_btn_camera",
                        onActionClick = {
                            if (!snapshot.cameraGranted) {
                                requestSingleRuntimePermission("camera", Manifest.permission.CAMERA)
                            }
                        }
                    )
                }

                item {
                    PermissionItemCard(
                        icon = Icons.Default.BatteryChargingFull,
                        accentColor = Color(0xFF10B981),
                        title = "Unrestricted Battery",
                        description = "Keep Live Voice & background agents from being killed",
                        isGranted = snapshot.batteryUnrestricted,
                        actionLabel = if (snapshot.batteryUnrestricted) "Granted" else "Allow",
                        testTag = "perm_btn_battery",
                        onActionClick = {
                            if (!snapshot.batteryUnrestricted) {
                                runCatching {
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    context.startActivity(intent)
                                }.onFailure {
                                    MainActivity.openTermuxPermissionSettings(context)
                                }
                            }
                        }
                    )
                }

                item {
                    PermissionItemCard(
                        icon = Icons.Default.Terminal,
                        accentColor = Color(0xFF34D399),
                        title = "Termux Bridge & RUN_COMMAND",
                        description = snapshot.termuxSubtitle,
                        isGranted = snapshot.termuxReady,
                        statusBadgeOverride = if (snapshot.termuxReady) "READY" else "SETUP",
                        statusColorOverride = if (snapshot.termuxReady) Color(0xFF10B981) else Color(0xFF38BDF8),
                        actionLabel = "Configure",
                        allowActionWhenGranted = true,
                        testTag = "perm_btn_termux",
                        onActionClick = onOpenTermuxSetup
                    )
                }

                item {
                    PermissionItemCard(
                        icon = Icons.Default.SettingsApplications,
                        accentColor = Color(0xFF94A3B8),
                        title = "App Info & Restricted Settings (⋮)",
                        description = "Android 13–16: Top-right (⋮) -> Allow restricted settings",
                        isGranted = snapshot.accessibilityTouchGranted && snapshot.notificationListenerGranted,
                        statusBadgeOverride = "SHORTCUT",
                        statusColorOverride = Color(0xFF38BDF8),
                        actionLabel = "Open App Info",
                        allowActionWhenGranted = true,
                        testTag = "perm_btn_app_info",
                        onActionClick = {
                            Toast.makeText(
                                context,
                                "Top-right (⋮) par tap karke 'Allow restricted settings' enable karein.",
                                Toast.LENGTH_LONG
                            ).show()
                            MainActivity.openTermuxPermissionSettings(context)
                        }
                    )
                }

                // SECTION 3: PHONE, CONTACTS, LOCATION & CONNECTIVITY
                item { PermissionSectionHeader("PHONE, CONTACTS & SENSORS") }

                item {
                    PermissionItemCard(
                        icon = Icons.Default.Contacts,
                        accentColor = Color(0xFFA78BFA),
                        title = "Contacts",
                        description = "Look up people by name for hands-free calls & messages",
                        isGranted = snapshot.contactsGranted,
                        actionLabel = if (snapshot.contactsGranted) "Granted" else "Allow",
                        testTag = "perm_btn_contacts",
                        onActionClick = {
                            if (!snapshot.contactsGranted) {
                                requestSingleRuntimePermission("contacts", Manifest.permission.READ_CONTACTS)
                            }
                        }
                    )
                }

                item {
                    PermissionItemCard(
                        icon = Icons.Default.Call,
                        accentColor = Color(0xFF22C55E),
                        title = "Phone & Call Control",
                        description = "Place voice calls & detect active call state",
                        isGranted = snapshot.phoneGranted,
                        actionLabel = if (snapshot.phoneGranted) "Granted" else "Allow",
                        testTag = "perm_btn_phone",
                        onActionClick = {
                            if (!snapshot.phoneGranted) {
                                requestMultiRuntimePermissions(
                                    "phone",
                                    arrayOf(
                                        Manifest.permission.CALL_PHONE,
                                        Manifest.permission.READ_PHONE_STATE
                                    )
                                )
                            }
                        }
                    )
                }

                item {
                    PermissionItemCard(
                        icon = Icons.Default.LocationOn,
                        accentColor = Color(0xFFF59E0B),
                        title = "Location (GPS)",
                        description = "ADS-B aircraft airspace radar & local weather/place queries",
                        isGranted = snapshot.locationGranted,
                        actionLabel = if (snapshot.locationGranted) "Granted" else "Allow",
                        testTag = "perm_btn_location",
                        onActionClick = {
                            if (!snapshot.locationGranted) {
                                requestMultiRuntimePermissions(
                                    "location",
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }
                    )
                }

                item {
                    PermissionItemCard(
                        icon = Icons.Default.Bluetooth,
                        accentColor = Color(0xFF38BDF8),
                        title = "Bluetooth Devices",
                        description = "Route voice audio cleanly to Bluetooth earbuds & speakers",
                        isGranted = snapshot.bluetoothGranted,
                        actionLabel = if (snapshot.bluetoothGranted) "Granted" else "Allow",
                        testTag = "perm_btn_bluetooth",
                        onActionClick = {
                            if (!snapshot.bluetoothGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                requestSingleRuntimePermission("bluetooth", Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun PermissionSectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF64748B),
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun PermissionItemCard(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    allowActionWhenGranted: Boolean = false,
    statusBadgeOverride: String? = null,
    statusColorOverride: Color? = null,
    testTag: String,
    onActionClick: () -> Unit
) {
    val statusText = statusBadgeOverride ?: if (isGranted) "GRANTED" else "NEEDED"
    val statusColor = statusColorOverride ?: if (isGranted) Color(0xFF10B981) else Color(0xFFF59E0B)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0F1526),
        border = BorderStroke(
            width = 1.dp,
            color = if (isGranted) Color(0xFF10B981).copy(alpha = 0.38f) else accentColor.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        if (isGranted) Color(0xFF10B981).copy(alpha = 0.18f)
                        else accentColor.copy(alpha = 0.16f)
                    )
                    .border(
                        1.dp,
                        if (isGranted) Color(0xFF10B981).copy(alpha = 0.5f)
                        else accentColor.copy(alpha = 0.45f),
                        RoundedCornerShape(13.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted && !allowActionWhenGranted) Icons.Default.CheckCircle else icon,
                    contentDescription = title,
                    tint = if (isGranted) Color(0xFF34D399) else accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = statusColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = description,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isGranted || allowActionWhenGranted) {
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGranted) Color(0xFF1E293B) else accentColor,
                        contentColor = if (isGranted) Color.White else Color(0xFF060812)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag(testTag)
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
