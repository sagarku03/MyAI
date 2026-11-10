package com.jarvisai.assistant.security

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.storage.StorageManager
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

/**
 * ManageSpaceActivity — Android Settings "Clear Storage" Interceptor.
 * When a user attempts to wipe app storage from Android System Settings,
 * this Activity is launched instead of blindly deleting the Linux environment and Git repos.
 */
class ManageSpaceActivity : ComponentActivity() {

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, ManageSpaceActivity::class.java)
        }

        fun isShieldEnabled(context: Context): Boolean {
            return try {
                val state = context.packageManager.getComponentEnabledSetting(getComponentName(context))
                state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                        state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            } catch (_: Exception) {
                true
            }
        }

        fun setShieldEnabled(context: Context, enabled: Boolean) {
            try {
                val newState = if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                context.packageManager.setComponentEnabledSetting(
                    getComponentName(context),
                    newState,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                android.util.Log.e("ManageSpaceActivity", "Failed to update ManageSpaceActivity state: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0A0E17),
                    surface = Color(0xFF111827),
                    surfaceVariant = Color(0xFF1F2937),
                    primary = Color(0xFF00E5FF), // Jarvis Cyan
                    error = Color(0xFFFF5252),
                    onBackground = Color(0xFFF3F4F6),
                    onSurface = Color(0xFFF3F4F6)
                )
            ) {
                ManageSpaceScreen(
                    onBack = { finish() },
                    onFactoryReset = {
                        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                        am?.clearApplicationUserData()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageSpaceScreen(
    onBack: () -> Unit,
    onFactoryReset: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val backupManager = remember { BackupRestoreManager(context) }

    var cacheBytes by remember { mutableLongStateOf(0L) }
    var totalDataBytes by remember { mutableLongStateOf(0L) }
    var rootfsBytes by remember { mutableLongStateOf(0L) }
    var projectsBytes by remember { mutableLongStateOf(0L) }
    var memoryDbBytes by remember { mutableLongStateOf(0L) }
    var isCalculating by remember { mutableStateOf(true) }

    var isDeviceAdminActive by remember { mutableStateOf(JarvisDeviceAdminReceiver.isAdminActive(context)) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var showResetPasswordDialog by remember { mutableStateOf(false) }
    var resetPasswordInput by remember { mutableStateOf("") }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var isBackingUp by remember { mutableStateOf(false) }

    fun refreshSizes() {
        scope.launch(Dispatchers.IO) {
            isCalculating = true
            var cache = 0L
            var total = 0L
            var rootfs = 0L
            var projects = 0L
            var memoryDb = 0L

            fun dirSize(dir: File?): Long {
                if (dir == null || !dir.exists()) return 0L
                var s = 0L
                try {
                    dir.walkTopDown()
                        .onEnter { file ->
                            try { !Files.isSymbolicLink(file.toPath()) } catch (_: Exception) { true }
                        }
                        .forEach { file ->
                            try {
                                if (file.isFile && !Files.isSymbolicLink(file.toPath())) {
                                    s += file.length()
                                }
                            } catch (_: Exception) {}
                        }
                } catch (_: Exception) {}
                return s
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                    val stats = storageStatsManager?.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        context.packageName,
                        Process.myUserHandle()
                    )
                    if (stats != null) {
                        cache = stats.cacheBytes
                        total = stats.dataBytes + stats.appBytes
                    }
                }

                if (cache == 0L && total == 0L) {
                    cache = dirSize(context.cacheDir) + dirSize(context.codeCacheDir)
                    val dataDir = context.dataDir
                    total = if (dataDir != null && dataDir.exists()) dirSize(dataDir) else dirSize(context.filesDir) + cache
                }

                val termuxPrefix = File("/data/data/com.termux/files/usr")
                if (termuxPrefix.exists()) {
                    rootfs = dirSize(termuxPrefix)
                }

                val projectsDir = File("/data/data/com.termux/files/home/projects")
                if (projectsDir.exists()) {
                    projects = dirSize(projectsDir)
                }

                val dbDir = context.getDatabasePath("jarvis_database").parentFile
                val geminiDir = File("/data/data/com.termux/files/home/.gemini")
                memoryDb = dirSize(dbDir) + dirSize(geminiDir)
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                cacheBytes = cache
                totalDataBytes = total
                rootfsBytes = rootfs
                projectsBytes = projects
                memoryDbBytes = memoryDb
                isCalculating = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshSizes()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Jarvis Space & Safety Shield",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Enterprise Data Protection & Anti-Wipe Interceptor",
                        fontSize = 12.sp,
                        color = Color.LightGray.copy(alpha = 0.8f)
                    )
                }
            }

            // Total Storage Overview Card with Real-Time Breakdown
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Total App Footprint", fontSize = 13.sp, color = Color.Gray)
                            Text(
                                if (isCalculating) "Calculating footprint..." else Formatter.formatFileSize(context, totalDataBytes),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(10.dp))

                    StorageMetricRow("Temporary Cache", Formatter.formatFileSize(context, cacheBytes))
                    StorageMetricRow("Linux Rootfs & Toolchain", Formatter.formatFileSize(context, rootfsBytes))
                    StorageMetricRow("Workspaces & Git Repos", Formatter.formatFileSize(context, projectsBytes))
                    StorageMetricRow("Cognitive Memory & Chat DBs", Formatter.formatFileSize(context, memoryDbBytes))
                }
            }

            // Safe Cache Purge
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Temporary Cache Only", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text(
                                text = "Size: ${Formatter.formatFileSize(context, cacheBytes)}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "100% Safe to clear. Cleans network cache, webview assets, and temporary logs without affecting your code, terminal packages, or memory.",
                        fontSize = 12.sp,
                        color = Color.LightGray.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    context.cacheDir.deleteRecursively()
                                    context.codeCacheDir.deleteRecursively()
                                } catch (_: Exception) {}
                                refreshSizes()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Temporary cache cleared safely!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear Temporary Cache", color = Color(0xFF00E5FF))
                    }
                }
            }

            // 1-Tap Snapshot Backup Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Snapshot Backup Vault", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Export a standalone .zip backup containing all chats, memories, and git projects to /sdcard/Download/Jarvis/backups/ before modifying storage.",
                        fontSize = 12.sp,
                        color = Color.LightGray.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                isBackingUp = true
                                backupManager.createFullBackup().collect { progress ->
                                    backupStatusMessage = "${progress.stage} (${progress.percentage}%)"
                                    if (progress.isComplete) {
                                        isBackingUp = false
                                        Toast.makeText(context, "Backup exported to ${progress.resultFile?.name}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !isBackingUp,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                    ) {
                        if (isBackingUp) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(backupStatusMessage ?: "Backing up...", color = Color.Black, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Export Full Backup Archive", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Device Admin Protection Toggle
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Accidental Uninstall Lock", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text(
                                text = if (isDeviceAdminActive) "Active (Device Admin Enabled)" else "Disabled (Standard Protection)",
                                fontSize = 12.sp,
                                color = if (isDeviceAdminActive) Color(0xFF00E5FF) else Color.Gray
                            )
                        }
                        Switch(
                            checked = isDeviceAdminActive,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    context.startActivity(JarvisDeviceAdminReceiver.createActivationIntent(context))
                                } else {
                                    JarvisDeviceAdminReceiver.deactivateAdmin(context)
                                    isDeviceAdminActive = false
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "When active, Android requires explicit deactivation in system settings before uninstalling, preventing accidental wipe.",
                        fontSize = 11.5.sp,
                        color = Color.LightGray.copy(alpha = 0.6f)
                    )
                }
            }

            // Danger Zone: Full Factory Reset
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Danger Zone: Factory Reset", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Permanently wipes all app data, including installed Linux packages, git projects, and memory vaults. Protected by Biometrics and Passphrase.",
                        fontSize = 12.sp,
                        color = Color.LightGray.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            BiometricAuthHelper.authenticateHighRiskAction(
                                context = context,
                                title = "Confirm Data Destruction",
                                subtitle = "Authenticate to access the factory reset vault",
                                onLaunchCredentialIntent = { context.startActivity(it) }
                            ) { result ->
                                if (result is BiometricAuthResult.Authenticated) {
                                    showResetConfirmation = true
                                } else {
                                    Toast.makeText(context, "Biometric authentication required!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Authorize Full Wipe", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }

    // Step 1: Confirmation Alert Dialog
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Are you absolutely sure?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently destroy all Linux packages, compiled binaries, local Git repos, and Jarvis cognitive memories. This action CANNOT be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmation = false
                        resetPasswordInput = ""
                        showResetPasswordDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Proceed to Passphrase")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Step 2: Passphrase Confirmation Dialog ("iknowwhatiamdoing")
    if (showResetPasswordDialog) {
        val isMatch = resetPasswordInput.trim() == "iknowwhatiamdoing"
        AlertDialog(
            onDismissRequest = {
                showResetPasswordDialog = false
                resetPasswordInput = ""
            },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp)) },
            title = { Text("Confirm Destruction", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "To confirm permanent destruction, type the phrase below exactly:",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "iknowwhatiamdoing",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    OutlinedTextField(
                        value = resetPasswordInput,
                        onValueChange = { resetPasswordInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        isError = resetPasswordInput.isNotEmpty() && !isMatch
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isMatch) {
                            showResetPasswordDialog = false
                            onFactoryReset()
                        }
                    },
                    enabled = isMatch,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete Everything", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showResetPasswordDialog = false
                        resetPasswordInput = ""
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StorageMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = Color.LightGray.copy(alpha = 0.85f))
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}
