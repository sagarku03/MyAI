package com.jarvisai.assistant.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.provider.Settings
import android.util.Log
import com.jarvisai.assistant.terminal.LocalTerminalBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class AdbPairingState {
    data object Idle : AdbPairingState()
    data object Searching : AdbPairingState()
    data class Ready(val port: Int) : AdbPairingState()
    data class InProgress(val port: Int) : AdbPairingState()
    data object Paired : AdbPairingState()
    data class Failed(val message: String) : AdbPairingState()
}

data class AdbDeviceState(
    val isConnected: Boolean = false,
    val deviceSerial: String? = null,
    val isAdbInstalled: Boolean = false,
    val pairingState: AdbPairingState = AdbPairingState.Idle,
    val activePort: Int? = null,
    val statusMessage: String = "Disconnected"
)

/**
 * Shizuku-style Wireless ADB mDNS Discovery & Loopback Connection Manager.
 * Uses Android's [NsdManager] to discover `_adb-tls-pairing._tcp` and `_adb-tls-connect._tcp`
 * services on `127.0.0.1`, pairs via `adb pair 127.0.0.1:<pairing_port> <code>`, and automatically
 * connects via `adb connect 127.0.0.1:<connect_port>`.
 */
object AdbConnectManager {

    private const val TAG = "AdbConnectManager"
    const val PAIRING_SERVICE = "_adb-tls-pairing._tcp"
    const val CONNECT_SERVICE = "_adb-tls-connect._tcp"

    private var appContext: Context? = null
    private var nsdManager: NsdManager? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _deviceState = MutableStateFlow(AdbDeviceState())
    val deviceState: StateFlow<AdbDeviceState> = _deviceState.asStateFlow()

    @Volatile
    private var discoveredPairingPort: Int? = null

    @Volatile
    private var discoveredConnectPort: Int? = null

    private var pairingDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var connectDiscoveryListener: NsdManager.DiscoveryListener? = null

    fun init(context: Context) {
        if (appContext != null) return
        val appCtx = context.applicationContext
        appContext = appCtx
        LocalTerminalBridge.instance.init(appCtx)
        nsdManager = appCtx.getSystemService(Context.NSD_SERVICE) as? NsdManager
        checkAdbInstalled()
        startConnectServiceDiscovery()
    }

    fun isDeveloperOptionsEnabled(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) != 0
    }

    fun checkAdbInstalled() {
        scope.launch {
            val res = runCatching {
                LocalTerminalBridge.instance.executeCommand("command -v adb || which adb")
            }.getOrNull()
            val installed = res != null && res.exitCode == 0 && res.output.isNotBlank()
            _deviceState.value = _deviceState.value.copy(isAdbInstalled = installed)
            if (installed) {
                refreshConnectedDevices()
            }
        }
    }

    suspend fun refreshConnectedDevices() {
        val res = runCatching {
            LocalTerminalBridge.instance.executeCommand("adb devices")
        }.getOrNull() ?: return

        val lines = res.output
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("List of devices") && !it.startsWith("*") }

        val deviceLine = lines.firstOrNull { line ->
            val parts = line.split(Regex("\\s+"))
            parts.size >= 2 && parts[1] == "device"
        }

        if (deviceLine != null) {
            val serial = deviceLine.split(Regex("\\s+")).firstOrNull()?.trim() ?: "127.0.0.1"
            val port = serial.substringAfter(":", "").toIntOrNull() ?: discoveredConnectPort
            _deviceState.value = _deviceState.value.copy(
                isConnected = true,
                deviceSerial = serial,
                activePort = port,
                statusMessage = "Connected ($serial)"
            )
        } else {
            _deviceState.value = _deviceState.value.copy(
                isConnected = false,
                deviceSerial = null,
                statusMessage = "Disconnected"
            )
        }
    }

    fun startPairingDiscovery(context: Context) {
        init(context)
        stopPairingDiscoveryInternal()

        _deviceState.value = _deviceState.value.copy(
            pairingState = AdbPairingState.Searching,
            statusMessage = "Listening for Wireless Debugging pairing service..."
        )
        AdbPairingService.start(context)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.i(TAG, "mDNS pairing discovery started for $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "Pairing service resolve failed: $errorCode")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val port = serviceInfo.port
                        discoveredPairingPort = port
                        _deviceState.value = _deviceState.value.copy(
                            pairingState = AdbPairingState.Ready(port),
                            statusMessage = "Pairing service found on 127.0.0.1:$port"
                        )
                        appContext?.let { ctx ->
                            AdbPairingService.updateNotification(
                                context = ctx,
                                title = "Pairing Port Found (127.0.0.1:$port)",
                                text = "Tap 'Enter 6-digit code' below and type the pairing code shown in Developer Options.",
                                showInput = true
                            )
                        }
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Start pairing discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        pairingDiscoveryListener = listener
        runCatching {
            nsdManager?.discoverServices(PAIRING_SERVICE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun stopPairingDiscoveryInternal() {
        pairingDiscoveryListener?.let { listener ->
            runCatching { nsdManager?.stopServiceDiscovery(listener) }
        }
        pairingDiscoveryListener = null
    }

    fun stopPairing() {
        stopPairingDiscoveryInternal()
        _deviceState.value = _deviceState.value.copy(pairingState = AdbPairingState.Idle)
    }

    fun submitPairingCode(code: String) {
        val sanitizedCode = code.trim().filter { it.isDigit() }
        val port = discoveredPairingPort
        if (port == null) {
            _deviceState.value = _deviceState.value.copy(
                pairingState = AdbPairingState.Failed("No pairing port discovered yet. Open 'Pair device with pairing code' in Developer Options.")
            )
            appContext?.let { ctx ->
                AdbPairingService.updateNotification(
                    context = ctx,
                    title = "Pairing Port Not Found",
                    text = "Make sure the 'Pair with device' dialog is open in Developer Options, then try again.",
                    showInput = true
                )
            }
            return
        }

        _deviceState.value = _deviceState.value.copy(
            pairingState = AdbPairingState.InProgress(port),
            statusMessage = "Pairing with 127.0.0.1:$port..."
        )

        scope.launch {
            val pairCmd = "adb pair 127.0.0.1:$port $sanitizedCode"
            val pairRes = runCatching {
                LocalTerminalBridge.instance.executeCommand(pairCmd, timeoutMs = 20_000L)
            }.getOrNull()

            val output = pairRes?.output.orEmpty()
            val success = pairRes != null &&
                (pairRes.exitCode == 0 || output.contains("Successfully paired", ignoreCase = true))

            if (success) {
                _deviceState.value = _deviceState.value.copy(
                    pairingState = AdbPairingState.Paired,
                    statusMessage = "Paired with 127.0.0.1:$port"
                )
                stopPairingDiscoveryInternal()
                appContext?.let { ctx ->
                    AdbPairingService.updateNotification(
                        context = ctx,
                        title = "Wireless ADB Paired!",
                        text = "Successfully paired to 127.0.0.1:$port. Connecting loopback session...",
                        showInput = false
                    )
                }
                val connectPort = discoveredConnectPort
                if (connectPort != null) {
                    connectToPort(connectPort)
                } else {
                    startConnectServiceDiscovery()
                }
                delay(2500)
                appContext?.let { AdbPairingService.stop(it) }
            } else {
                val errSummary = output.ifBlank { "adb pair exited with ${pairRes?.exitCode ?: -1}" }.take(140)
                _deviceState.value = _deviceState.value.copy(
                    pairingState = AdbPairingState.Failed(errSummary),
                    statusMessage = "Pairing failed: $errSummary"
                )
                appContext?.let { ctx ->
                    AdbPairingService.updateNotification(
                        context = ctx,
                        title = "Pairing Failed — Try Again",
                        text = errSummary,
                        showInput = true
                    )
                }
            }
        }
    }

    private fun startConnectServiceDiscovery() {
        connectDiscoveryListener?.let { listener ->
            runCatching { nsdManager?.stopServiceDiscovery(listener) }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.i(TAG, "mDNS connect discovery started for $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val port = serviceInfo.port
                        discoveredConnectPort = port
                        scope.launch {
                            connectToPort(port)
                        }
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        connectDiscoveryListener = listener
        runCatching {
            nsdManager?.discoverServices(CONNECT_SERVICE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    suspend fun connectToPort(port: Int): Boolean = withContext(Dispatchers.IO) {
        val res = runCatching {
            LocalTerminalBridge.instance.executeCommand("adb connect 127.0.0.1:$port", timeoutMs = 15_000L)
        }.getOrNull() ?: return@withContext false

        if (res.exitCode == 0 && (res.output.contains("connected", ignoreCase = true) || res.output.contains("already connected", ignoreCase = true))) {
            discoveredConnectPort = port
            refreshConnectedDevices()
            true
        } else {
            false
        }
    }

    /**
     * Automatically unblocks and enables both JarvisAccessibilityService and
     * AutomationAccessibilityService on Android 13+ via local loopback ADB shell commands.
     */
    suspend fun unblockAndEnableAccessibility(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val primaryService = "$pkg/com.jarvisai.assistant.service.JarvisAccessibilityService"
        val automationService = "$pkg/com.jarvisai.assistant.service.AutomationAccessibilityService"

        try {
            val currentServices = LocalTerminalBridge.instance
                .executeCommand("adb shell settings get secure enabled_accessibility_services")
                .output
                .trim()

            val list = currentServices
                .split(':')
                .filter { it.isNotBlank() && it != "null" }
                .toMutableList()

            if (list.none { it.equals(primaryService, ignoreCase = true) }) {
                list.add(primaryService)
            }
            if (list.none { it.equals(automationService, ignoreCase = true) }) {
                list.add(automationService)
            }

            val updated = list.joinToString(":")
            LocalTerminalBridge.instance.executeCommand(
                "adb shell settings put secure enabled_accessibility_services \"$updated\""
            )
            LocalTerminalBridge.instance.executeCommand(
                "adb shell settings put secure accessibility_enabled 1"
            )
            LocalTerminalBridge.instance.executeCommand(
                "adb shell appops set $pkg ACCESS_RESTRICTED_SETTINGS allow"
            )
            LocalTerminalBridge.instance.executeCommand(
                "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"
            )
            LocalTerminalBridge.instance.executeCommand(
                "adb shell pm grant $pkg com.termux.permission.RUN_COMMAND"
            )

            Result.success("Accessibility Services, Restricted Settings & Termux RUN_COMMAND unlocked via local ADB!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
