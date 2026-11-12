package com.jarvisai.assistant.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jarvisai.assistant.data.pc.PcConnect
import com.jarvisai.assistant.data.pc.PcConnect.PairedPc
import kotlinx.coroutines.delay

private val PcTeal = Color(0xFF00FFCC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectPcScreen(
    onNavigateBack: () -> Unit
) {
    BackHandler(onBack = onNavigateBack)

    val context = LocalContext.current
    val pairedPcs by PcConnect.pairedPcs.collectAsState()
    val activePcId by PcConnect.activePcId.collectAsState()

    var showPairDialog by remember { mutableStateOf(false) }
    var reachabilityMap by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    LaunchedEffect(Unit) {
        PcConnect.init(context)
    }

    LaunchedEffect(pairedPcs) {
        while (true) {
            val map = mutableMapOf<String, Boolean>()
            for (pc in pairedPcs) {
                map[pc.id] = PcConnect.reachableAddress(pc, 1200) != null
            }
            reachabilityMap = map
            delay(10000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect to Desktop PC", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B0D11),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPairDialog = true },
                containerColor = PcTeal,
                contentColor = Color(0xFF0B0D11)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Pair New PC")
            }
        },
        containerColor = Color(0xFF0B0D11)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Mode Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF13151A),
                border = BorderStroke(1.dp, PcTeal.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (activePcId != null) PcTeal else Color(0xFF6B7280))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activePcId != null) "Active: Remote PC Bridge" else "Active: Local Phone Engine (Termux)",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = if (activePcId != null) "Antigravity tasks run on your desktop computer" else "Antigravity tasks run inside your phone's Linux rootfs",
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "PAIRED DESKTOP COMPUTERS",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = PcTeal,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (pairedPcs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No Desktop PCs paired yet.\nTap + to pair via 6-digit code.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        lineHeight = 18.sp
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(pairedPcs, key = { it.id }) { pc ->
                        val isActive = pc.id == activePcId
                        val isOnline = reachabilityMap[pc.id] == true

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF13151A),
                            border = BorderStroke(1.dp, if (isActive) PcTeal else Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clickable {
                                    PcConnect.setActivePc(context, if (isActive) null else pc.id)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (pc.os == "darwin") Icons.Outlined.LaptopMac else Icons.Outlined.DesktopWindows,
                                    contentDescription = null,
                                    tint = if (isActive) PcTeal else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pc.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${pc.user}@${pc.lastAddress.ifBlank { "mDNS" }}:${pc.hubPort} • ${if (isOnline) "🟢 Online" else "⚪ Offline"}",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                                IconButton(onClick = { PcConnect.deletePairedPc(context, pc.id) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPairDialog) {
        PairingModalDialog(
            onDismiss = { showPairDialog = false },
            onPaired = { newPc ->
                PcConnect.savePairedPc(context, newPc)
                PcConnect.setActivePc(context, newPc.id)
                showPairDialog = false
            }
        )
    }
}

@Composable
private fun PairingModalDialog(
    onDismiss: () -> Unit,
    onPaired: (PairedPc) -> Unit
) {
    var pcAddress by remember { mutableStateOf("192.168.1.") }
    var port by remember { mutableStateOf("1235") }
    var code by remember { mutableStateOf("") }
    var pcName by remember { mutableStateOf("My Desktop") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF13151A),
            border = BorderStroke(1.dp, PcTeal.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Pair Desktop Antigravity PC",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enter the IP address and 6-digit PIN shown in your Desktop Antigravity terminal / VS Code:",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = pcName,
                    onValueChange = { pcName = it },
                    label = { Text("PC Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pcAddress,
                    onValueChange = { pcAddress = it },
                    label = { Text("IP Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Hub Port (Default 1235)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it },
                    label = { Text("6-Digit PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(alpha = 0.7f)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val pc = PairedPc(
                                id = "pc_${System.currentTimeMillis()}",
                                name = pcName.ifBlank { "Desktop PC" },
                                os = "linux",
                                addresses = listOf(pcAddress.trim()),
                                lastAddress = pcAddress.trim(),
                                sshPort = 22,
                                user = "user",
                                bridgePort = 1234,
                                hubPort = port.toIntOrNull() ?: 1235,
                                hostKeyType = "ssh-ed25519",
                                hostKey = "",
                                token = code.trim(),
                                pairedAt = System.currentTimeMillis(),
                                lastUsedAt = System.currentTimeMillis()
                            )
                            onPaired(pc)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PcTeal, contentColor = Color(0xFF0B0D11)),
                        enabled = pcAddress.isNotBlank() && code.length >= 4
                    ) {
                        Text("Connect & Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
