package com.jarvisai.assistant.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.data.pc.PcConnect
import com.jarvisai.assistant.data.preferences.AuthPreferences
import com.jarvisai.assistant.data.ssh.PinnedHostKeys
import com.jarvisai.assistant.data.ssh.SshSessionFactory
import com.jarvisai.assistant.security.ManageSpaceActivity
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalTerminalManager
import com.jarvisai.assistant.terminal.TermuxActivity
import com.jarvisai.assistant.terminal.TermuxStorageSetupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TerminalSettingsSubScreen(
    onOpenInstaller: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authPrefs = remember { AuthPreferences.getInstance(context) }
    val installProgress by LocalEnvironmentManager.installProgress.collectAsState()

    var fontSizeState by remember { mutableIntStateOf(authPrefs.getTerminalFontSizeSync()) }
    var cursorStyleState by remember { mutableStateOf(authPrefs.getTerminalCursorStyleSync()) }
    var bufferSizeState by remember { mutableIntStateOf(authPrefs.getTerminalBufferSizeSync()) }
    var themeState by remember { mutableStateOf(authPrefs.getTerminalThemeSync()) }

    // SSH & Tmux Control Mode state
    var useSshState by remember { mutableStateOf(authPrefs.isUseSshTerminalSync()) }
    var sshHostState by remember { mutableStateOf(authPrefs.getSshHostSync()) }
    var sshPortState by remember { mutableStateOf(authPrefs.getSshPortSync().toString()) }
    var sshUserState by remember { mutableStateOf(authPrefs.getSshUserSync()) }
    var sshPassState by remember { mutableStateOf(authPrefs.getSshPassSync()) }
    var sshTestBusy by remember { mutableStateOf(false) }
    var sshTestStatus by remember { mutableStateOf<String?>(null) }
    val pairedPc = remember { PcConnect.getActivePairedPc() }

    // Termux:API Provider state
    var apiProviderState by remember { mutableStateOf(LocalEnvironmentManager.getTermuxApiProvider(context)) }
    var apiAppInstalled by remember { mutableStateOf(LocalEnvironmentManager.isTermuxApiAppInstalled(context)) }
    var apiPkgInstalled by remember { mutableStateOf(LocalEnvironmentManager.isTermuxApiPackageInstalled(context)) }
    var apiInstallBusy by remember { mutableStateOf(false) }
    var apiStatusMsg by remember { mutableStateOf<String?>(null) }

    var hasStoragePerm by remember { mutableStateOf(TermuxStorageSetupManager.hasStoragePermission(context)) }
    var diagRefreshKey by remember { mutableIntStateOf(0) }
    val diagnostics = remember(installProgress, diagRefreshKey) {
        LocalEnvironmentManager.getDiagnostics(context)
    }

    var showResetWarningDialog by remember { mutableStateOf(false) }
    var showResetPhraseDialog by remember { mutableStateOf(false) }
    var resetPhraseInput by remember { mutableStateOf("") }

    fun savePrefs(fontSize: Int, cursor: String, buffer: Int, theme: String) {
        authPrefs.saveTerminalPreferencesSync(fontSize, cursor, buffer, theme)
        LocalTerminalManager.updatePreferences(cursor, buffer)
    }

    fun persistSsh(enabled: Boolean = useSshState) {
        val port = sshPortState.toIntOrNull()?.coerceIn(1, 65535) ?: 8022
        authPrefs.saveSshConfigSync(
            enabled = enabled,
            host = sshHostState,
            port = port,
            user = sshUserState,
            pass = sshPassState
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Environment Status & Launcher Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(
                1.dp,
                if (diagnostics.isInstalled) AgySettingsColors.QuotaGreen.copy(alpha = 0.45f)
                else AgySettingsColors.Warning.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Terminal,
                        contentDescription = "Terminal Environment Status",
                        tint = if (diagnostics.isInstalled) AgySettingsColors.QuotaGreen else AgySettingsColors.Warning,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (diagnostics.isInstalled) "Agentra Native Linux Active" else "Linux Bootstrap Needed",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgySettingsColors.TextPrimary
                        )
                        Text(
                            text = "Disk: ${diagnostics.diskUsageFormatted} · Reloc [${if (diagnostics.hasRelocateHooks) "✓" else "✗"}] · am [${if (diagnostics.hasAmApk) "✓" else "✗"}] · Glibc [${if (diagnostics.hasGlibc) "✓" else "✗"}]",
                            fontSize = 11.5.sp,
                            color = AgySettingsColors.TextSecondary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(context, TermuxActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.GeminiBlue),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("terminal_settings_launch_btn")
                    ) {
                        Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Launch Terminal", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onOpenInstaller,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AgySettingsColors.CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("terminal_settings_installer_btn")
                    ) {
                        Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(16.dp), tint = AgySettingsColors.TextPrimary)
                        Spacer(Modifier.width(6.dp))
                        Text("Toolchain Setup", fontSize = 12.5.sp, color = AgySettingsColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 2. Terminal Customization Card (Font Size, Cursor Style, Scrollback Buffer)
        Text(
            text = "Terminal Customization",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AgySettingsColors.TextPrimary
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Font Size",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AgySettingsColors.TextPrimary
                    )
                    Text(
                        text = "${fontSizeState}pt",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgySettingsColors.GeminiBlue,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Slider(
                    value = fontSizeState.toFloat(),
                    onValueChange = {
                        fontSizeState = it.toInt().coerceIn(10, 24)
                        savePrefs(fontSizeState, cursorStyleState, bufferSizeState, themeState)
                    },
                    valueRange = 10f..24f,
                    steps = 13,
                    modifier = Modifier.testTag("terminal_font_size_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = AgySettingsColors.GeminiBlue,
                        activeTrackColor = AgySettingsColors.GeminiBlue
                    )
                )

                Text(
                    text = "Cursor Style",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AgySettingsColors.TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val cursorOptions = listOf(
                        "BLOCK" to "Block █",
                        "UNDERLINE" to "Underline _",
                        "BAR" to "Bar |"
                    )
                    cursorOptions.forEach { (style, label) ->
                        val isSel = cursorStyleState == style
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    cursorStyleState = style
                                    savePrefs(fontSizeState, style, bufferSizeState, themeState)
                                }
                                .testTag("terminal_cursor_${style.lowercase()}_btn"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) AgySettingsColors.GeminiBlue else AgySettingsColors.CardBgElevated,
                            border = BorderStroke(
                                1.dp,
                                if (isSel) AgySettingsColors.GeminiBlue else AgySettingsColors.CardBorder
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else AgySettingsColors.TextPrimary
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Scrollback Buffer",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AgySettingsColors.TextPrimary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val buffers = listOf(
                        1000 to "1k",
                        5000 to "5k",
                        10000 to "10k",
                        20000 to "20k",
                        50000 to "50k",
                        100000 to "Unlimited"
                    )
                    buffers.forEach { (buf, label) ->
                        val isSel = bufferSizeState == buf || (buf == 100000 && bufferSizeState >= 100000)
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    bufferSizeState = buf
                                    savePrefs(fontSizeState, cursorStyleState, buf, themeState)
                                    Toast.makeText(context, "Buffer set to $label (applies to new tabs)", Toast.LENGTH_SHORT).show()
                                }
                                .testTag("terminal_buffer_${label.lowercase()}_btn"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) AgySettingsColors.GeminiBlue else AgySettingsColors.CardBgElevated,
                            border = BorderStroke(
                                1.dp,
                                if (isSel) AgySettingsColors.GeminiBlue else AgySettingsColors.CardBorder
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else AgySettingsColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. SSH & Tmux Control Mode Card
        Text(
            text = "SSH & Tmux Control Mode",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AgySettingsColors.TextPrimary
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(
                1.dp,
                if (useSshState) AgySettingsColors.Info.copy(alpha = 0.5f) else AgySettingsColors.CardBorder
            )
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lan,
                            contentDescription = "SSH & Tmux Control Mode",
                            tint = if (useSshState) AgySettingsColors.Info else AgySettingsColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Use Remote SSH / tmux -CC",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AgySettingsColors.TextPrimary
                            )
                            Text(
                                text = "Auto-restores remote tmux windows with TOFU host key pinning.",
                                fontSize = 11.5.sp,
                                color = AgySettingsColors.TextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = useSshState,
                        onCheckedChange = { checked ->
                            useSshState = checked
                            persistSsh(checked)
                        },
                        modifier = Modifier.testTag("terminal_use_ssh_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AgySettingsColors.Info
                        )
                    )
                }

                if (pairedPc != null) {
                    OutlinedButton(
                        onClick = {
                            sshHostState = pairedPc.orderedAddresses().firstOrNull() ?: sshHostState
                            sshPortState = pairedPc.sshPort.toString()
                            if (pairedPc.user.isNotBlank()) sshUserState = pairedPc.user
                            persistSsh()
                            Toast.makeText(context, "Filled from paired PC: ${pairedPc.name}", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AgySettingsColors.Info.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("terminal_ssh_fill_pc_btn")
                    ) {
                        Text(
                            text = "Fill from Paired PC (${pairedPc.name})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AgySettingsColors.Info
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = sshHostState,
                        onValueChange = {
                            sshHostState = it
                            persistSsh()
                        },
                        label = { Text("Host / IP") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(2f)
                            .testTag("terminal_ssh_host_input")
                    )
                    OutlinedTextField(
                        value = sshPortState,
                        onValueChange = {
                            sshPortState = it.filter { ch -> ch.isDigit() }.take(5)
                            persistSsh()
                        },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("terminal_ssh_port_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = sshUserState,
                        onValueChange = {
                            sshUserState = it
                            persistSsh()
                        },
                        label = { Text("User") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("terminal_ssh_user_input")
                    )
                    OutlinedTextField(
                        value = sshPassState,
                        onValueChange = {
                            sshPassState = it
                            persistSsh()
                        },
                        label = { Text("Password (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("terminal_ssh_pass_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (sshTestBusy) return@Button
                            persistSsh()
                            sshTestBusy = true
                            sshTestStatus = "Testing SSH & probing tmux..."
                            scope.launch {
                                val port = sshPortState.toIntOrNull()?.coerceIn(1, 65535) ?: 8022
                                val res = withContext(Dispatchers.IO) {
                                    SshSessionFactory.testConnection(context, sshHostState, port, sshUserState, sshPassState)
                                }
                                val msg = res.fold(
                                    onSuccess = { out ->
                                        val wins = LocalTerminalManager.probeRemoteTmuxWindows(
                                            context, sshHostState, port, sshUserState, sshPassState
                                        )
                                        "Connected ✓ | $out | tmux windows: ${wins.size}"
                                    },
                                    onFailure = { err ->
                                        "Failed: ${err.message ?: "Connection refused"}"
                                    }
                                )
                                sshTestStatus = msg
                                sshTestBusy = false
                            }
                        },
                        enabled = !sshTestBusy,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.Info),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("terminal_ssh_test_btn")
                    ) {
                        if (sshTestBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = if (sshTestBusy) "Testing..." else "Test SSH & Tmux",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            PinnedHostKeys.clearAll(context)
                            sshTestStatus = "Cleared pinned TOFU SSH host keys."
                            Toast.makeText(context, "Pinned SSH host keys cleared", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AgySettingsColors.CardBorder),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("terminal_ssh_clear_keys_btn")
                    ) {
                        Text("Clear Keys", fontSize = 12.sp, color = AgySettingsColors.TextPrimary)
                    }
                }

                if (!sshTestStatus.isNullOrBlank()) {
                    Text(
                        text = sshTestStatus.orEmpty(),
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (sshTestStatus!!.contains("Failed")) AgySettingsColors.Danger else AgySettingsColors.QuotaGreen
                    )
                }
            }
        }

        // 4. Termux:API Provider Card (Hybrid Built-in vs External)
        Text(
            text = "Termux:API Provider",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AgySettingsColors.TextPrimary
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Api,
                        contentDescription = "Termux API Provider",
                        tint = AgySettingsColors.AccentTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Device Hardware Bridge (termux-* commands)",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AgySettingsColors.TextPrimary
                        )
                        Text(
                            text = "pkg: [${if (apiPkgInstalled) "✓ Installed" else "Missing"}] · External App (${LocalEnvironmentManager.termuxApiAppPackage(context)}): [${if (apiAppInstalled) "✓" else "Not installed"}]",
                            fontSize = 11.sp,
                            color = AgySettingsColors.TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val providerOptions = listOf(
                        "builtin" to "Built-in Socket",
                        "external" to "External App"
                    )
                    providerOptions.forEach { (id, label) ->
                        val isSel = apiProviderState == id
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    apiProviderState = id
                                    LocalEnvironmentManager.setTermuxApiProvider(context, id)
                                    scope.launch(Dispatchers.IO) {
                                        LocalEnvironmentManager.ensureTermuxApiDispatcher(context)
                                    }
                                    Toast.makeText(context, "Termux:API provider set to $label", Toast.LENGTH_SHORT).show()
                                }
                                .testTag("termux_api_provider_${id}_btn"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) AgySettingsColors.AccentTeal else AgySettingsColors.CardBgElevated,
                            border = BorderStroke(
                                1.dp,
                                if (isSel) AgySettingsColors.AccentTeal else AgySettingsColors.CardBorder
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.Black else AgySettingsColors.TextPrimary
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (apiInstallBusy) return@OutlinedButton
                        apiInstallBusy = true
                        apiStatusMsg = "Configuring termux-api package..."
                        scope.launch {
                            val ok = LocalEnvironmentManager.ensureTermuxApiPackage(context) { line ->
                                apiStatusMsg = line.take(72)
                            }
                            apiAppInstalled = LocalEnvironmentManager.isTermuxApiAppInstalled(context)
                            apiPkgInstalled = LocalEnvironmentManager.isTermuxApiPackageInstalled(context)
                            apiStatusMsg = if (ok) {
                                "✓ termux-api bridge ready"
                            } else {
                                "✗ termux-api setup failed or rootfs not installed"
                            }
                            apiInstallBusy = false
                        }
                    },
                    enabled = !apiInstallBusy,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AgySettingsColors.AccentTeal.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("termux_api_install_pkg_btn")
                ) {
                    Text(
                        text = if (apiInstallBusy) "Installing termux-api..." else "Install / Sync termux-api Package",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AgySettingsColors.AccentTeal
                    )
                }

                if (!apiStatusMsg.isNullOrBlank()) {
                    Text(
                        text = apiStatusMsg.orEmpty(),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (apiStatusMsg!!.startsWith("✗")) AgySettingsColors.Danger else AgySettingsColors.QuotaGreen
                    )
                }
            }
        }

        // 5. Shared Storage & ~/storage/* Symlinks (termux-setup-storage)
        Text(
            text = "Shared Storage Access",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AgySettingsColors.TextPrimary
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (hasStoragePerm) Icons.Outlined.CheckCircle else Icons.Outlined.Folder,
                        contentDescription = "Shared Storage Permission",
                        tint = if (hasStoragePerm) AgySettingsColors.QuotaGreen else AgySettingsColors.Warning,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasStoragePerm) "All Files Access Granted" else "All Files Access Required",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AgySettingsColors.TextPrimary
                        )
                        Text(
                            text = "Maps ~/storage/shared, downloads, dcim, pictures & documents inside terminal.",
                            fontSize = 11.5.sp,
                            color = AgySettingsColors.TextSecondary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!hasStoragePerm) {
                        Button(
                            onClick = {
                                TermuxStorageSetupManager.requestStoragePermission(context)
                                hasStoragePerm = TermuxStorageSetupManager.hasStoragePermission(context)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.Warning),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("terminal_grant_storage_btn")
                        ) {
                            Text("Grant Access", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            hasStoragePerm = TermuxStorageSetupManager.hasStoragePermission(context)
                            scope.launch(Dispatchers.IO) {
                                val ok = TermuxStorageSetupManager.setupStorageSymlinks(context)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        if (ok) "~/storage/* symlinks created!" else "Grant All Files Access first.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AgySettingsColors.GeminiBlue.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("terminal_setup_storage_symlinks_btn")
                    ) {
                        Text(
                            text = "Setup ~/storage Symlinks",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AgySettingsColors.GeminiBlue
                        )
                    }
                }
            }
        }

        // 6. Environment Safety & Reset
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Storage Shield & Reset",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgySettingsColors.TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(context, ManageSpaceActivity::class.java))
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AgySettingsColors.CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("terminal_manage_space_btn")
                    ) {
                        Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(16.dp), tint = AgySettingsColors.QuotaGreen)
                        Spacer(Modifier.width(6.dp))
                        Text("Manage Space", fontSize = 12.sp, color = AgySettingsColors.TextPrimary)
                    }

                    OutlinedButton(
                        onClick = { showResetWarningDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("terminal_reset_rootfs_btn")
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFEF4444))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset Rootfs", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF4444))
                    }
                }
            }
        }
    }

    if (showResetWarningDialog) {
        AlertDialog(
            onDismissRequest = { showResetWarningDialog = false },
            icon = {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(28.dp))
            },
            title = {
                Text("Reset Local Linux Rootfs?", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Warning: All installed Linux packages, dotfiles, and terminal files in \$PREFIX and \$HOME will be wiped. Your Agentra chats and settings remain safe unless wiped in Manage Space.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetWarningDialog = false
                        resetPhraseInput = ""
                        showResetPhraseDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Continue", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showResetWarningDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetPhraseDialog) {
        val isMatch = resetPhraseInput.trim() == "iknowwhatiamdoing"
        AlertDialog(
            onDismissRequest = { showResetPhraseDialog = false },
            icon = {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(28.dp))
            },
            title = {
                Text("Confirm Rootfs Wipe", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Type `iknowwhatiamdoing` to confirm wiping local Linux rootfs:", fontSize = 13.sp)
                    OutlinedTextField(
                        value = resetPhraseInput,
                        onValueChange = { resetPhraseInput = it },
                        singleLine = true,
                        placeholder = { Text("iknowwhatiamdoing") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("terminal_reset_phrase_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetPhraseDialog = false
                        scope.launch {
                            LocalEnvironmentManager.resetEnvironment(context)
                            diagRefreshKey++
                            Toast.makeText(context, "Local Linux rootfs reset cleanly.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = isMatch,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("terminal_reset_confirm_btn")
                ) {
                    Text("Wipe Rootfs", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showResetPhraseDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
