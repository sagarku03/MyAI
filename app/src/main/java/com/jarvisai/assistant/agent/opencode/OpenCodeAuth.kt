package com.jarvisai.assistant.agent.opencode

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phase 3: Provider matrix & OAuth/API-key credential manager for the OpenCode agent subsystem.
 */
data class OpenCodeProviderConfig(
    val providerId: String,
    val displayName: String,
    val selectedModel: String,
    val availableModels: List<String>,
    val apiKeyOrToken: String = "",
    val isAuthenticated: Boolean = false,
    val supportsOAuth: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("provider_id", providerId)
        put("display_name", displayName)
        put("selected_model", selectedModel)
        put("available_models", JSONArray(availableModels))
        put("api_key_or_token", apiKeyOrToken)
        put("is_authenticated", isAuthenticated)
        put("supports_oauth", supportsOAuth)
    }

    companion object {
        fun fromJson(obj: JSONObject): OpenCodeProviderConfig {
            val modelsArr = obj.optJSONArray("available_models") ?: JSONArray()
            val models = buildList {
                for (i in 0 until modelsArr.length()) {
                    val m = modelsArr.optString(i)
                    if (m.isNotBlank()) add(m)
                }
            }
            return OpenCodeProviderConfig(
                providerId = obj.optString("provider_id", "anthropic"),
                displayName = obj.optString("display_name", "Anthropic"),
                selectedModel = obj.optString("selected_model", models.firstOrNull() ?: "claude-sonnet-4-20250514"),
                availableModels = models,
                apiKeyOrToken = obj.optString("api_key_or_token", ""),
                isAuthenticated = obj.optBoolean("is_authenticated", false),
                supportsOAuth = obj.optBoolean("supports_oauth", false)
            )
        }
    }
}

class OpenCodeAuth(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _providers = MutableStateFlow(loadProviders())
    val providers: StateFlow<List<OpenCodeProviderConfig>> = _providers.asStateFlow()

    private val _activeProviderId = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_PROVIDER, "anthropic") ?: "anthropic"
    )
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    private fun loadProviders(): List<OpenCodeProviderConfig> {
        val raw = prefs.getString(KEY_PROVIDERS_JSON, null)
        if (raw.isNullOrBlank()) return defaultProviders()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(OpenCodeProviderConfig.fromJson(it)) }
                }
            }.ifEmpty { defaultProviders() }
        }.getOrElse { defaultProviders() }
    }

    private fun saveProviders(list: List<OpenCodeProviderConfig>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_PROVIDERS_JSON, arr.toString()).apply()
        _providers.value = list
    }

    fun selectProvider(providerId: String) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, providerId).apply()
        _activeProviderId.value = providerId
    }

    fun updateProviderCredential(providerId: String, apiKeyOrToken: String, selectedModel: String? = null) {
        val updated = _providers.value.map { p ->
            if (p.providerId == providerId) {
                p.copy(
                    apiKeyOrToken = apiKeyOrToken.trim(),
                    isAuthenticated = apiKeyOrToken.isNotBlank(),
                    selectedModel = selectedModel ?: p.selectedModel
                )
            } else {
                p
            }
        }
        saveProviders(updated)
    }

    fun getActiveProvider(): OpenCodeProviderConfig {
        val id = _activeProviderId.value
        return _providers.value.firstOrNull { it.providerId == id } ?: _providers.value.first()
    }

    companion object {
        private const val PREFS_NAME = "jarvis_opencode_auth"
        private const val KEY_PROVIDERS_JSON = "providers_json"
        private const val KEY_ACTIVE_PROVIDER = "active_provider_id"

        fun defaultProviders(): List<OpenCodeProviderConfig> = listOf(
            OpenCodeProviderConfig(
                providerId = "anthropic",
                displayName = "Anthropic (Claude)",
                selectedModel = "claude-sonnet-4-20250514",
                availableModels = listOf("claude-sonnet-4-20250514", "claude-opus-4-20250514", "claude-3-7-sonnet-latest"),
                supportsOAuth = true
            ),
            OpenCodeProviderConfig(
                providerId = "google",
                displayName = "Google Gemini",
                selectedModel = "gemini-2.5-pro",
                availableModels = listOf("gemini-2.5-pro", "gemini-2.5-flash"),
                supportsOAuth = true
            ),
            OpenCodeProviderConfig(
                providerId = "openai",
                displayName = "OpenAI",
                selectedModel = "gpt-4.1",
                availableModels = listOf("gpt-4.1", "o4-mini", "o3"),
                supportsOAuth = false
            ),
            OpenCodeProviderConfig(
                providerId = "openrouter",
                displayName = "OpenRouter",
                selectedModel = "deepseek/deepseek-r1",
                availableModels = listOf("deepseek/deepseek-r1", "qwen/qwen3-coder", "anthropic/claude-sonnet-4"),
                supportsOAuth = false
            )
        )
    }
}
