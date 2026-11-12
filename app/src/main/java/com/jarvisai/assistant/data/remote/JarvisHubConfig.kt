package com.jarvisai.assistant.data.remote

import android.content.Context
import com.example.BuildConfig

/**
 * Centralized network and daemon configuration for Jarvis AI.
 * Supports flavor-aware port binding (`BuildConfig.HUB_PORT` / `BuildConfig.BRIDGE_PORT`)
 * and runtime custom port overrides.
 */
object JarvisHubConfig {
    val DEFAULT_HUB_PORT: Int
        get() = if (BuildConfig.IS_TERMUX_FLAVOR) BuildConfig.HUB_PORT else 2235

    val DEFAULT_BRIDGE_PORT: Int
        get() = if (BuildConfig.IS_TERMUX_FLAVOR) BuildConfig.BRIDGE_PORT else 2234

    val ISOLATED_APP_DATA_DIR: String
        get() = if (BuildConfig.IS_TERMUX_FLAVOR) "antigravity-cli" else "jarvis-antigravity"

    private const val PREFS = "jarvis_hub_config_prefs"
    private const val KEY_CUSTOM_HUB_PORT = "custom_hub_port"
    private const val KEY_CUSTOM_BRIDGE_PORT = "custom_bridge_port"

    fun isTermuxFlavor(context: Context? = null): Boolean {
        return BuildConfig.IS_TERMUX_FLAVOR || context?.packageName == "com.termux"
    }

    fun getHubPort(context: Context? = null): Int {
        val defaultPort = if (isTermuxFlavor(context)) BuildConfig.HUB_PORT else DEFAULT_HUB_PORT
        if (context == null) return defaultPort
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CUSTOM_HUB_PORT, defaultPort)
    }

    fun getBridgePort(context: Context? = null): Int {
        val defaultPort = if (isTermuxFlavor(context)) BuildConfig.BRIDGE_PORT else DEFAULT_BRIDGE_PORT
        if (context == null) return defaultPort
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CUSTOM_BRIDGE_PORT, defaultPort)
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
