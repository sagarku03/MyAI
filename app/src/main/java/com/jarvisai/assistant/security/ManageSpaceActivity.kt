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
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

/**
 * ManageSpaceActivity — Android Settings "Clear Storage" Interceptor (`android:manageSpaceActivity`).
 * When a user taps "Manage Space" / "Clear Storage" in Android System Settings, Android launches
 * this screen instead of blindly wiping the multi-gigabyte Termux Linux rootfs, coding workspaces,
 * and cognitive databases.
 */
class ManageSpaceActivity : ComponentActivity() {

    companion object {
        const val CONFIRM_FACTORY_WIPE_PHRASE = "DELETE ALL"

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
                    primary = Color(0xFF00E5FF),
                    error = Color(0xFFFF5252),
                    onBackground = Color(0xFFF3F4F6),
                    onSurface = Color(0xFFF3F4F6)
                )
            ) {
                ManageSpaceScreen(
                    onBack = { finish() },
                    onFactoryReset = {
                        performFullFactoryWipe(this@ManageSpaceActivity)
                    }
                )
            }
        }
    }

    private fun performFullFactoryWipe(context: Context) {
        try {
            val prefixDir = LocalEnvironmentManager.getPrefixDir(context)
            val homeDir = LocalEnvironmentManager.getHomeDir(context)
            runCatching { prefixDir.deleteRecursively() }
            runCatching { homeDir.deleteRecursively() }
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.clearApplicationUserData()
        } catch (e: Exception) {
            Toast.makeText(context, "Factory wipe error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageSpaceScreen(
    onBack: () -> Unit,
    onFactoryReset: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupManager = remember { BackupRestoreManager(context) }

    var apkBaseBytes by remember { mutableLongStateOf(0L) }
    var dbAndCacheBytes by remember { mutableLongStateOf(0L) }
    var linuxEnvBytes by remember { mutableLongStateOf(0L) }
    var modelGrpcCacheBytes by remember { mutableLongStateOf(0L) }
    var totalFootprintBytes by remember { mutableLongStateOf(0L) }
    var isCalculating by remember { mutableStateOf(true) }

    var isDeviceAdminActive by remember { mutableStateOf(JarvisDeviceAdminReceiver.isAdminActive(context)) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var showResetPasswordDialog by remember { mutableStateOf(false) }
    var resetPasswordInput by remember { mutableStateOf("") }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var isBackingUp by remember { mutableStateOf(false) }

    fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var sum = 0L
        try {
            dir.walkTopDown()
                .onEnter { file ->
                    try {
                        !Files.isSymbolicLink(file.toPath())
                    } catch (_: Exception) {
                        true
                    }
                }
                .forEach { file ->
                    try {
                        if (file.isFile && !Files.isSymbolicLink(file.toPath())) {
                            sum += file.length()
                        }
                    } catch (_: Exception) {
                    }
                }
        } catch (_: Exception) {
        }
        return sum
    }

    fun refreshSizes() {
        scope.launch(Dispatchers.IO) {
            isCalculating = true
            var apkSize = 0L
            var dbAndCache = 0L
            var linuxSize = 0L
            var modelAndGrpc = 0L

            try {
                // 1. Application Base APK Size
                val sourceDir = context.applicationInfo.sourceDir
                if (!sourceDir.isNullOrBlank()) {
                    apkSize = File(sourceDir).length()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                    val stats = storageStatsManager?.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        context.packageName,
                        Process.myUserHandle()
                    )
                    if (stats != null && stats.appBytes > 0L) {
                        apkSize = stats.appBytes
                    }
                }

                // 2. Internal Database & Cache Size
                val cacheSize = dirSize(context.cacheDir) + dirSize(context.codeCacheDir)
                val dbParent = context.getDatabasePath("jarvis_database").parentFile
                val dbSize = dirSize(dbParent)
                dbAndCache = cacheSize + dbSize

                // 3. Termux Linux Environment ($PREFIX and $HOME) Size
                val prefixDir = LocalEnvironmentManager.getPrefixDir(context)
                val homeDir = LocalEnvironmentManager.getHomeDir(context)
                linuxSize = dirSize(prefixDir) + dirSize(homeDir)

                // 4. Downloaded Model & gRPC Cache Size
                val modelsDir = File(context.filesDir, "models")
                val grpcCacheDir = File(context.cacheDir, "grpc")
                val geminiCacheDir = File(homeDir, ".gemini")
                modelAndGrpc = dirSize(modelsDir) + dirSize(grpcCacheDir) + dirSize(geminiCacheDir)
            } catch (_: Exception) {
            }

            val total = apkSize + dbAndCache + linuxSize + modelAndGrpc
            withContext(Dispatchers.Main) {
                apkBaseBytes = apkSize
                dbAndCacheBytes = dbAndCache
                linuxEnvBytes = linuxSize
                modelGrpcCacheBytes = modelAndGrpc
                totalFootprintBytes = total
                isCalculating = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshSizes()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
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
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("manage_space_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Jarvis Space & Safety Shield",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Clear-Storage Interceptor & Linux Rootfs Guard",
                        fontSize = 12.sp,
                        color = Color.LightGray.copy(alpha = 0.8f)
                    )
                }
                IconButton(
                    onClick = { refreshSizes() },
                    modifier = Modifier.testTag("manage_space_refresh_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Storage Breakdown", tint = Color(0xFF00E5FF))
                }
            }

            // Real-Time Storage Breakdown Card
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
                            Icon(
                                Icons.Default.Storage,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Total Protected Footprint", fontSize = 13.sp, color = Color.Gray)
                            Text(
                                text = if (isCalculating) {
                                    "Scanning storage..."
                                } else {
                                    Formatter.formatFileSize(context, totalFootprintBytes)
                                },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(10.dp))

                    StorageMetricRow(
                        label = "Application Base APK Size",
                        value = Formatter.formatFileSize(context, apkBaseBytes)
                    )
                    StorageMetricRow(
                        label = "Internal Database & Cache Size",
                        value = Formatter.formatFileSize(context, dbAndCacheBytes)
                    )
                    StorageMetricRow(
                        label = "Termux Linux Environment (\$PREFIX & \$HOME)",
                        value = Formatter.formatFileSize(context, linuxEnvBytes)
                    )
                    StorageMetricRow(
                        label = "Downloaded Model & gRPC Cache Size",
                        value = Formatter.formatFileSize(context, modelGrpcCacheBytes)
                    )
                }
            }

            // Tier 1: Quick Clean (Caches, Logs, Temporary Web Files)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CleaningServices,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Tier 1: Quick Clean (Cache & Temp Web)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Reclaims temporary cache & web storage safely",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Wipes temporary caches, daemon logs, and WebView storage without touching your Termux Linux rootfs, coding workspaces, or chat databases.",
                        fontSize = 12.sp,
                        color = Color.LightGray.copy(alpha = 0.75f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                runCatching { context.cacheDir.deleteRecursively() }
                                runCatching { context.codeCacheDir.deleteRecursively() }
                                runCatching { LocalEnvironmentManager.getTmpDir(context).deleteRecursively() }
                                runCatching { LocalEnvironmentManager.getTmpDir(context).mkdirs() }
                                withContext(Dispatchers.Main) {
                                    runCatching { WebStorage.getInstance().deleteAllData() }
                                    runCatching { CookieManager.getInstance().removeSessionCookies(null) }
                                    Toast.makeText(
                                        context,
                                        "Quick Clean complete — Linux rootfs & databases preserved!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                refreshSizes()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manage_space_quick_clean_button")
                    ) {
                        Text("Run Quick Clean", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Tier 2: Reset Network & Auth (API Keys, OAuth Tokens, CSRF Cookies)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.KeyOff,
                            contentDescription = null,
                            tint = Color(0xFFFFCA28),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Tier 2: Reset Network & Auth",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Clears saved API keys, OAuth tokens & CSRF cookies",
                                fontSize = 12.sp,
                                color = Color(0xFFFFCA28)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Revokes local Keystore API keys, clears Antigravity/Claude/OpenCode OAuth session files, and purges browser authentication cookies while keeping all Linux packages and code intact.",
                        fontSize = 12.sp,
                        color = Color.LightGray.copy(alpha = 0.75f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                runCatching { SecureApiKeyManager(context).deleteApiKey() }
                                val homeDir = LocalEnvironmentManager.getHomeDir(context)
                                runCatching { File(homeDir, ".gemini/jarvis-antigravity").deleteRecursively() }
                                runCatching { File(homeDir, ".claude/.credentials.json").delete() }
                                runCatching { File(homeDir, ".local/share/opencode/auth.json").delete() }
                                withContext(Dispatchers.Main) {
                                    runCatching {
                                        CookieManager.getInstance().removeAllCookies(null)
                                        CookieManager.getInstance().flush()
                                    }
                                    Toast.makeText(
                                        context,
                                        "Network & Auth credentials reset!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                refreshSizes()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFCA28)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manage_space_reset_auth_button")
                    ) {
                        Text("Reset Network & Auth Tokens", color = Color(0xFFFFCA28), fontWeight = FontWeight.Bold)
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
                        Icon(
                            Icons.Default.Backup,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Snapshot Backup Vault",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Export a standalone .zip backup containing chats, memories, and Git projects to /sdcard/Download/Jarvis/backups/ before modifying storage.",
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
                                        Toast.makeText(
                                            context,
                                            "Backup exported to ${progress.resultFile?.name}",
                                            Toast.LENGTH_LONG
                                        ).show()
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                backupStatusMessage ?: "Backing up...",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
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
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Accidental Uninstall Lock",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = if (isDeviceAdminActive) {
                                    "Active (Device Admin Enabled)"
                                } else {
                                    "Disabled (Standard Protection)"
                                },
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
                        text = "When active, Android OS blocks accidental home-screen uninstallation until Device Administrator is explicitly deactivated.",
                        fontSize = 11.5.sp,
                        color = Color.LightGray.copy(alpha = 0.6f)
                    )
                }
            }

            // Tier 3: Dangerous Factory Reset
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Tier 3: Dangerous Factory Reset",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Permanently destroys the internal Termux Linux rootfs (\$PREFIX & \$HOME), compiled toolchains, Git workspaces, and databases. Requires typing \"${ManageSpaceActivity.CONFIRM_FACTORY_WIPE_PHRASE}\".",
                        fontSize = 12.sp,
                        color = Color.LightGray.copy(alpha = 0.75f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            showResetConfirmation = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manage_space_factory_reset_button")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dangerous Factory Reset", fontWeight = FontWeight.Bold, color = Color.White)
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
            title = { Text("Destroy Linux Rootfs & All Data?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will permanently wipe the Termux Linux environment, installed SDKs, local Git repositories, and AI memory databases. This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmation = false
                        resetPasswordInput = ""
                        showResetPasswordDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Proceed to Confirmation")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Step 2: Typed Safeguard Dialog ("DELETE ALL")
    if (showResetPasswordDialog) {
        val isMatch = resetPasswordInput.trim() == ManageSpaceActivity.CONFIRM_FACTORY_WIPE_PHRASE
        AlertDialog(
            onDismissRequest = {
                showResetPasswordDialog = false
                resetPasswordInput = ""
            },
            icon = {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Type Confirmation Phrase", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "To unlock Factory Reset and wipe the internal rootfs, type the exact phrase below:",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = ManageSpaceActivity.CONFIRM_FACTORY_WIPE_PHRASE,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252),
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    OutlinedTextField(
                        value = resetPasswordInput,
                        onValueChange = { resetPasswordInput = it },
                        singleLine = true,
                        placeholder = { Text(ManageSpaceActivity.CONFIRM_FACTORY_WIPE_PHRASE) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manage_space_confirm_phrase_input"),
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
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("manage_space_confirm_wipe_button")
                ) {
                    Text("Wipe Everything", fontWeight = FontWeight.Bold, color = Color.White)
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
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = Color.LightGray.copy(alpha = 0.88f))
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}
