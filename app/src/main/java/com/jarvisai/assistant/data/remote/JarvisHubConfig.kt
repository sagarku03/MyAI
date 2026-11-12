package com.jarvisai.assistant.data.remote

import android.content.Context

/**
 * Centralized network and daemon configuration for Jarvis AI.
 * Completely isolates Jarvis AI from AntiGem (which runs on ports 1234/1235).
 */
object JarvisHubConfig {
    const val DEFAULT_HUB_PORT = 2235
    const val DEFAULT_BRIDGE_PORT = 2234
    const val ISOLATED_APP_DATA_DIR = "jarvis-antigravity"

    private const val PREFS = "jarvis_hub_config_prefs"
    private const val KEY_CUSTOM_HUB_PORT = "custom_hub_port"
    private const val KEY_CUSTOM_BRIDGE_PORT = "custom_bridge_port"

    fun getHubPort(context: Context? = null): Int {
        if (context == null) return DEFAULT_HUB_PORT
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CUSTOM_HUB_PORT, DEFAULT_HUB_PORT)
    }

    fun getBridgePort(context: Context? = null): Int {
        if (context == null) return DEFAULT_BRIDGE_PORT
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CUSTOM_BRIDGE_PORT, DEFAULT_BRIDGE_PORT)
    }

    fun getHubBaseUrl(context: Context? = null): String {
        return "http://127.0.0.1:${getHubPort(context)}"
    }

    fun getLanguageServiceUrl(context: Context? = null): String {
        return "${getHubBaseUrl(context)}/exa.language_server_pb.LanguageServerService"
    }

    fun setCustomPorts(context: Context, hubPort: Int, bridgePort: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_CUSTOM_HUB_PORT, hubPort)
            .putInt(KEY_CUSTOM_BRIDGE_PORT, bridgePort)
            .apply()
    }
}
