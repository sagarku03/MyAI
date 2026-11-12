package com.jarvisai.assistant.data.pc

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

object PcConnect {

    private const val TAG = "PcConnect"
    const val SERVICE_TYPE = "_antigem-pc._tcp"
    private const val PREFS = "jarvis_pc_connect_prefs"
    private const val KEY_PCS = "pc_paired_json"
    private const val KEY_ACTIVE = "pc_active_id"
    private const val KEY_PHONE_ID = "pc_phone_id"

    data class PairedPc(
        val id: String,
        val name: String,
        val os: String,
        val addresses: List<String>,
        val lastAddress: String,
        val sshPort: Int,
        val user: String,
        val bridgePort: Int,
        val hubPort: Int,
        val hostKeyType: String,
        val hostKey: String,
        val token: String,
        val pairedAt: Long,
        val lastUsedAt: Long,
        val shellAllowed: Boolean = false
    ) {
        fun orderedAddresses(): List<String> = (listOf(lastAddress) + addresses).filter { it.isNotBlank() }.distinct()

        fun toJson(): JSONObject = JSONObject()
            .put("id", id).put("name", name).put("os", os)
            .put("addresses", JSONArray(addresses)).put("lastAddress", lastAddress)
            .put("sshPort", sshPort).put("user", user)
            .put("bridgePort", bridgePort).put("hubPort", hubPort)
            .put("hostKeyType", hostKeyType).put("hostKey", hostKey)
            .put("token", token).put("pairedAt", pairedAt).put("lastUsedAt", lastUsedAt)
            .put("shellAllowed", shellAllowed)

        companion object {
            fun fromJson(o: JSONObject): PairedPc? = runCatching {
                val addrs = mutableListOf<String>()
                val arr = o.optJSONArray("addresses")
                if (arr != null) {
                    for (i in 0 until arr.length()) addrs.add(arr.getString(i))
                }
                PairedPc(
                    id = o.getString("id"),
                    name = o.optString("name", "Desktop PC"),
                    os = o.optString("os", "linux"),
                    addresses = addrs,
                    lastAddress = o.optString("lastAddress", ""),
                    sshPort = o.optInt("sshPort", 22),
                    user = o.optString("user", ""),
                    bridgePort = o.optInt("bridgePort", 1234),
                    hubPort = o.optInt("hubPort", 1235),
                    hostKeyType = o.optString("hostKeyType", "ssh-ed25519"),
                    hostKey = o.optString("hostKey", ""),
                    token = o.optString("token", ""),
                    pairedAt = o.optLong("pairedAt", 0L),
                    lastUsedAt = o.optLong("lastUsedAt", 0L),
                    shellAllowed = o.optBoolean("shellAllowed", false)
                )
            }.getOrNull()
        }
    }

    private val _pairedPcs = MutableStateFlow<List<PairedPc>>(emptyList())
    val pairedPcs: StateFlow<List<PairedPc>> = _pairedPcs.asStateFlow()

    private val _activePcId = MutableStateFlow<String?>(null)
    val activePcId: StateFlow<String?> = _activePcId.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PCS, "[]") ?: "[]"
        val pcs = mutableListOf<PairedPc>()
        runCatching {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                PairedPc.fromJson(arr.getJSONObject(i))?.let { pcs.add(it) }
            }
        }
        _pairedPcs.value = pcs
        _activePcId.value = prefs.getString(KEY_ACTIVE, null)
    }

    fun getActivePairedPc(): PairedPc? {
        val activeId = _activePcId.value ?: return null
        return _pairedPcs.value.firstOrNull { it.id == activeId }
    }

    fun getActiveHubBaseUrl(): String? {
        val pc = getActivePairedPc() ?: return null
        val host = pc.orderedAddresses().firstOrNull() ?: return null
        return "http://$host:${pc.hubPort}/exa.language_server_pb.LanguageServerService"
    }

    fun getPhoneId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_PHONE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString().take(8)
            prefs.edit().putString(KEY_PHONE_ID, id).apply()
        }
        return id
    }

    suspend fun reachableAddress(pc: PairedPc, timeoutMs: Int = 1200): String? = withContext(Dispatchers.IO) {
        for (addr in pc.orderedAddresses()) {
            try {
                Socket().use { sock ->
                    sock.connect(InetSocketAddress(addr, pc.hubPort), timeoutMs)
                    return@withContext addr
                }
            } catch (e: Exception) {
                // try next
            }
        }
        null
    }

    fun savePairedPc(context: Context, pc: PairedPc) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = _pairedPcs.value.filter { it.id != pc.id } + pc
        _pairedPcs.value = list
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_PCS, arr.toString()).apply()
    }

    fun setActivePc(context: Context, pcId: String?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _activePcId.value = pcId
        prefs.edit().putString(KEY_ACTIVE, pcId).apply()
    }

    fun deletePairedPc(context: Context, pcId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = _pairedPcs.value.filter { it.id != pcId }
        _pairedPcs.value = list
        if (_activePcId.value == pcId) {
            setActivePc(context, null)
        }
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_PCS, arr.toString()).apply()
    }

    /** Discovers nearby desktop PCs advertising _antigem-pc._tcp */
    fun discoverNearby(context: Context): Flow<List<NsdServiceInfo>> = callbackFlow {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        val found = mutableMapOf<String, NsdServiceInfo>()

        if (nsd == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("antigem-pc")) {
                    nsd.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            found[serviceInfo.serviceName] = serviceInfo
                            trySend(found.values.toList())
                        }
                    })
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                found.remove(service.serviceName)
                trySend(found.values.toList())
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
        }

        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            close(e)
        }

        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }
}
