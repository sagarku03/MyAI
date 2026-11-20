package com.jarvisai.assistant.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.jarvisai.assistant.data.remote.AntigravityModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent Store for Google Antigravity, Agent Credentials, Model Catalog,
 * and AntiGem-style Terminal Customization Preferences.
 */
class AuthPreferences private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "jarvis_antigravity_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "antigravity_access_token"
        private const val KEY_REFRESH_TOKEN = "antigravity_refresh_token"
        private const val KEY_EXPIRES_AT = "antigravity_expires_at_ms"
        private const val KEY_EMAIL = "antigravity_email"
        private const val KEY_USER_NAME = "antigravity_user_name"
        private const val KEY_PICTURE_URL = "antigravity_picture_url"
        private const val KEY_PROJECT_ID = "antigravity_project_id"
        private const val KEY_TIER = "antigravity_tier"
        private const val KEY_CACHED_MODELS = "antigravity_cached_models_json"

        // Terminal Customization Keys
        private const val KEY_TERMINAL_FONT_SIZE = "terminal_font_size"
        private const val KEY_TERMINAL_CURSOR_STYLE = "terminal_cursor_style"
        private const val KEY_TERMINAL_BUFFER_SIZE = "terminal_buffer_size"
        private const val KEY_TERMINAL_THEME = "terminal_theme"
        private const val KEY_TERMINAL_LAUNCHER_ONBOARDED = "terminal_launcher_onboarded"

        @Volatile
        private var INSTANCE: AuthPreferences? = null

        fun getInstance(context: Context): AuthPreferences =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthPreferences(context.applicationContext).also { INSTANCE = it }
            }

        operator fun invoke(context: Context): AuthPreferences = getInstance(context)

        fun isConsumerOrPlaceholderProject(projectId: String): Boolean {
            val p = projectId.trim().lowercase()
            return p.isEmpty() ||
                p == "aicode-consumers" ||
                p == "rising-fact-p41fc" ||
                p == "cloudcode-default" ||
                p == "none"
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(getAccessToken().isNotBlank())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userEmail = MutableStateFlow(getUserEmail())
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _tier = MutableStateFlow(getTier())
    val tier: StateFlow<String> = _tier.asStateFlow()

    // Reactive Terminal Preferences (matching AntiGem defaults: 14sp, BAR cursor, 20000 lines)
    private val _terminalFontSize = MutableStateFlow(getTerminalFontSizeSync())
    val terminalFontSize: StateFlow<Int> = _terminalFontSize.asStateFlow()

    private val _terminalCursorStyle = MutableStateFlow(getTerminalCursorStyleSync())
    val terminalCursorStyle: StateFlow<String> = _terminalCursorStyle.asStateFlow()

    private val _terminalBufferSize = MutableStateFlow(getTerminalBufferSizeSync())
    val terminalBufferSize: StateFlow<Int> = _terminalBufferSize.asStateFlow()

    private val _terminalTheme = MutableStateFlow(getTerminalThemeSync())
    val terminalTheme: StateFlow<String> = _terminalTheme.asStateFlow()

    private val _hasSeenTerminalLauncherOnboarding = MutableStateFlow(hasSeenTerminalLauncherOnboardingSync())
    val hasSeenTerminalLauncherOnboarding: StateFlow<Boolean> = _hasSeenTerminalLauncherOnboarding.asStateFlow()

    fun getTerminalFontSizeSync(): Int = prefs.getInt(KEY_TERMINAL_FONT_SIZE, 14).coerceIn(8, 28)
    fun getTerminalCursorStyleSync(): String = prefs.getString(KEY_TERMINAL_CURSOR_STYLE, "BAR") ?: "BAR"
    fun getTerminalBufferSizeSync(): Int = prefs.getInt(KEY_TERMINAL_BUFFER_SIZE, 20000)
    fun getTerminalThemeSync(): String = prefs.getString(KEY_TERMINAL_THEME, "DEFAULT") ?: "DEFAULT"
    fun hasSeenTerminalLauncherOnboardingSync(): Boolean = prefs.getBoolean(KEY_TERMINAL_LAUNCHER_ONBOARDED, false)

    fun saveTerminalPreferencesSync(
        fontSize: Int,
        cursorStyle: String,
        bufferSize: Int,
        theme: String = getTerminalThemeSync()
    ) {
        val safeFont = fontSize.coerceIn(8, 28)
        prefs.edit().apply {
            putInt(KEY_TERMINAL_FONT_SIZE, safeFont)
            putString(KEY_TERMINAL_CURSOR_STYLE, cursorStyle)
            putInt(KEY_TERMINAL_BUFFER_SIZE, bufferSize)
            putString(KEY_TERMINAL_THEME, theme)
            apply()
        }
        _terminalFontSize.value = safeFont
        _terminalCursorStyle.value = cursorStyle
        _terminalBufferSize.value = bufferSize
        _terminalTheme.value = theme
    }

    suspend fun saveTerminalPreferences(
        fontSize: Int,
        cursorStyle: String,
        bufferSize: Int,
        theme: String = getTerminalThemeSync()
    ) {
        saveTerminalPreferencesSync(fontSize, cursorStyle, bufferSize, theme)
    }

    fun setHasSeenTerminalLauncherOnboarding(seen: Boolean = true) {
        prefs.edit().putBoolean(KEY_TERMINAL_LAUNCHER_ONBOARDED, seen).apply()
        _hasSeenTerminalLauncherOnboarding.value = seen
    }

    fun getAccessToken(): String = prefs.getString(KEY_ACCESS_TOKEN, "").orEmpty()
    fun getRefreshToken(): String = prefs.getString(KEY_REFRESH_TOKEN, "").orEmpty()
    fun getUserEmail(): String = prefs.getString(KEY_EMAIL, "").orEmpty()
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "Antigravity Dev").orEmpty()
    fun getPictureUrl(): String = prefs.getString(KEY_PICTURE_URL, "").orEmpty()

    fun getProjectId(): String {
        val p = prefs.getString(KEY_PROJECT_ID, "").orEmpty()
        return if (isConsumerOrPlaceholderProject(p)) "" else p
    }

    fun getRawProjectId(): String = prefs.getString(KEY_PROJECT_ID, "").orEmpty()

    fun getTier(): String = prefs.getString(KEY_TIER, "free").orEmpty()

    fun getExpiresAtMs(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)

    fun isTokenExpired(): Boolean {
        val expiresAt = getExpiresAtMs()
        return System.currentTimeMillis() >= (expiresAt - 180_000L)
    }

    fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        email: String,
        name: String = "Antigravity Dev",
        pictureUrl: String? = null,
        expiresInSeconds: Long = 3600
    ) {
        val expiresAtMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            if (!refreshToken.isNullOrBlank()) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            putLong(KEY_EXPIRES_AT, expiresAtMs)
            putString(KEY_EMAIL, email)
            putString(KEY_USER_NAME, name)
            putString(KEY_PICTURE_URL, pictureUrl.orEmpty())
            apply()
        }
        _isLoggedIn.value = true
        _userEmail.value = email
    }

    fun saveProjectInfo(projectId: String, tier: String) {
        val cleanProject = if (isConsumerOrPlaceholderProject(projectId)) "" else projectId
        prefs.edit().apply {
            putString(KEY_PROJECT_ID, cleanProject)
            putString(KEY_TIER, tier)
            apply()
        }
        _tier.value = tier
    }

    fun saveAvailableModels(models: List<AntigravityModelInfo>) {
        val arr = JSONArray()
        models.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("displayName", m.displayName)
                    put("remainingFraction", m.remainingFraction)
                    put("resetTime", m.resetTime)
                    put("supportsThinking", m.supportsThinking)
                    put("isRecommended", m.isRecommended)
                    put("maxTokens", m.maxTokens)
                    put("tagTitle", m.tagTitle)
                    put("supportsImages", m.supportsImages)
                    put(
                        "thinkingVariantIds",
                        JSONObject().apply {
                            m.thinkingVariantIds.forEach { (k, v) -> put(k, v) }
                        }
                    )
                    put("supportedThinkingLevels", JSONArray(m.supportedThinkingLevels))
                }
            )
        }
        prefs.edit().putString(KEY_CACHED_MODELS, arr.toString()).apply()
    }

    fun getCachedAvailableModels(): List<AntigravityModelInfo> {
        val raw = prefs.getString(KEY_CACHED_MODELS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id", "").trim()
                    if (id.isNotBlank()) {
                        val variantObj = o.optJSONObject("thinkingVariantIds")
                        val variants = buildMap {
                            variantObj?.keys()?.forEach { key ->
                                val value = variantObj.optString(key, "").trim()
                                if (value.isNotBlank()) put(key, value)
                            }
                        }
                        val levelsArr = o.optJSONArray("supportedThinkingLevels")
                        val levels = buildList {
                            if (levelsArr != null) {
                                for (li in 0 until levelsArr.length()) {
                                    val lv = levelsArr.optString(li, "").trim()
                                    if (lv.isNotBlank()) add(lv)
                                }
                            }
                        }
                        add(
                            AntigravityModelInfo(
                                id = id,
                                displayName = o.optString("displayName", id),
                                remainingFraction = o.optDouble("remainingFraction", 1.0),
                                resetTime = o.optString("resetTime", ""),
                                supportsThinking = o.optBoolean("supportsThinking", true),
                                isRecommended = o.optBoolean("isRecommended", false),
                                maxTokens = o.optInt("maxTokens", 0),
                                tagTitle = o.optString("tagTitle", ""),
                                supportsImages = o.optBoolean("supportsImages", true),
                                thinkingVariantIds = variants,
                                supportedThinkingLevels = levels
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun clearAuth() {
        prefs.edit().clear().apply()
        _isLoggedIn.value = false
        _userEmail.value = ""
        _tier.value = "free"
    }
}
