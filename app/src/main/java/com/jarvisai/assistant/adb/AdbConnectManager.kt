package com.jarvisai.assistant.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.jarvisai.assistant.terminal.LocalTerminalBridge
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

sealed class AdbPairingState {
    object Idle : AdbPairingState()
    object Searching : AdbPairingState()
    data class Ready(val port: Int) : AdbPairingState()
    data class InProgress(val port: Int) : AdbPairingState()
    object Paired : AdbPairingState()
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

object AdbConnectManager {

    private const val TAG = "AdbConnectManager"
    private const val PAIRING_SERVICE = "_adb-tls-pairing._tcp"
    private const val CONNECT_SERVICE = "_adb-tls-connect._tcp"

    private var appContext: Context? = null
    private var nsdManager: NsdManager? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _deviceState = MutableStateFlow(AdbDeviceState())
    val deviceState: StateFlow<AdbDeviceState> = _deviceState.asStateFlow()

    private var discoveredPairingPort: Int? = null
    private var discoveredConnectPort: Int? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        checkAdbInstalled()
        startConnectServiceDiscovery()
    }

    fun isDeveloperOptionsEnabled(context: Context): Boolean {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
    }

    fun checkAdbInstalled() {
        scope.launch {
            val res = LocalTerminalBridge.instance.executeCommand("which adb")
            val installed = res.exitCode == 0 && res.output.isNotBlank()
            _deviceState.value = _deviceState.value.copy(isAdbInstalled = installed)
            if (installed) refreshConnectedDevices()
        }
    }

    suspend fun refreshConnectedDevices() {
        val res = LocalTerminalBridge.instance.executeCommand("adb devices")
        val lines = res.output.lines().filter { it.isNotBlank() && !it.startsWith("List of devices") }
        val deviceLine = lines.firstOrNull { it.contains("device") && !it.contains("offline") }

        if (deviceLine != null) {
            val serial = deviceLine.split("\t").firstOrNull()?.trim() ?: "localhost"
            _deviceState.value = _deviceState.value.copy(
                isConnected = true,
                deviceSerial = serial,
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
        _deviceState.value = _deviceState.value.copy(pairingState = AdbPairingState.Searching)
        AdbPairingService.start(context)

        nsdManager?.discoverServices(PAIRING_SERVICE, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) { Log.i(TAG, "Pairing mDNS discovery started") }
            override fun onServiceFound(service: NsdServiceInfo) {
                nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        discoveredPairingPort = serviceInfo.port
                        _deviceState.value = _deviceState.value.copy(
                            pairingState = AdbPairingState.Ready(serviceInfo.port)
                        )
                    }
                })
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        })
    }

    fun stopPairing() {
        _deviceState.value = _deviceState.value.copy(pairingState = AdbPairingState.Idle)
    }

    fun submitPairingCode(code: String) {
        val port = discoveredPairingPort ?: return
        _deviceState.value = _deviceState.value.copy(pairingState = AdbPairingState.InProgress(port))

        scope.launch {
            val pairRes = LocalTerminalBridge.instance.executeCommand("adb pair 127.0.0.1:$port $code")
            if (pairRes.exitCode == 0 && pairRes.output.contains("Successfully paired")) {
                _deviceState.value = _deviceState.value.copy(pairingState = AdbPairingState.Paired)
                // Connect immediately if connect port is already known
                discoveredConnectPort?.let { connectPort ->
                    connectToPort(connectPort)
                }
            } else {
                _deviceState.value = _deviceState.value.copy(
                    pairingState = AdbPairingState.Failed(pairRes.output.take(120))
                )
            }
        }
    }

    private fun startConnectServiceDiscovery() {
        nsdManager?.discoverServices(CONNECT_SERVICE, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        discoveredConnectPort = serviceInfo.port
                        scope.launch { connectToPort(serviceInfo.port) }
                    }
                })
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        })
    }

    suspend fun connectToPort(port: Int): Boolean = withContext(Dispatchers.IO) {
        val res = LocalTerminalBridge.instance.executeCommand("adb connect 127.0.0.1:$port")
        if (res.exitCode == 0 && res.output.contains("connected")) {
            refreshConnectedDevices()
            true
        } else {
            false
        }
    }

    /**
     * GOD-MODE UNBLOCKER: Automatically unblocks and enables JarvisAccessibilityService
     * on Android 13+ bypassing Restricted Settings via local ADB shell commands!
     */
    suspend fun unblockAndEnableAccessibility(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val serviceName = "$pkg/com.jarvisai.assistant.service.JarvisAccessibilityService"

        try {
            // 1. Fetch current accessibility services
            val currentServices = LocalTerminalBridge.instance.executeCommand("adb shell settings get secure enabled_accessibility_services").output.trim()
            val list = currentServices.split(':').filter { it.isNotBlank() && it != "null" }.toMutableList()
            if (list.none { it.equals(serviceName, ignoreCase = true) }) {
                list.add(serviceName)
            }

            // 2. Enable service & accessibility subsystem
            val updated = list.joinToString(":")
            LocalTerminalBridge.instance.executeCommand("adb shell settings put secure enabled_accessibility_services $updated")
            LocalTerminalBridge.instance.executeCommand("adb shell settings put secure accessibility_enabled 1")

            // 3. Grant secure permissions
            LocalTerminalBridge.instance.executeCommand("adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")
            LocalTerminalBridge.instance.executeCommand("adb shell pm grant $pkg android.permission.PACKAGE_USAGE_STATS")

            Result.success("Accessibility & Secure Settings successfully unlocked via local ADB!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
