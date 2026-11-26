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
        private const val KEY_GRANTED_SCOPES = "antigravity_granted_scopes"
        private const val KEY_CACHED_MODELS = "antigravity_cached_models_json"

        // Terminal Customization Keys
        private const val KEY_TERMINAL_FONT_SIZE = "terminal_font_size"
        private const val KEY_TERMINAL_CURSOR_STYLE = "terminal_cursor_style"
        private const val KEY_TERMINAL_BUFFER_SIZE = "terminal_buffer_size"
        private const val KEY_TERMINAL_THEME = "terminal_theme"
        private const val KEY_TERMINAL_LAUNCHER_ONBOARDED = "terminal_launcher_onboarded"

        // SSH & Tmux Control Mode Keys
        private const val KEY_USE_SSH_TERMINAL = "terminal_use_ssh"
        private const val KEY_SSH_HOST = "terminal_ssh_host"
        private const val KEY_SSH_PORT = "terminal_ssh_port"
        private const val KEY_SSH_USER = "terminal_ssh_user"
        private const val KEY_SSH_PASS = "terminal_ssh_pass"
        private const val KEY_SSH_PRIVATE_KEY = "terminal_ssh_private_key"
        private const val KEY_SSH_KEY_PASSPHRASE = "terminal_ssh_key_passphrase"

        // Bootstrap Mirror & Termux:API Provider Keys
        private const val KEY_ROOTFS_MIRROR = "env_rootfs_mirror"
        private const val KEY_TERMUX_API_PROVIDER = "env_termux_api_provider"

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

    // Reactive Terminal Preferences (14sp, BAR cursor, 20000 lines)
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

    private val _useSshTerminal = MutableStateFlow(isUseSshTerminalSync())
    val useSshTerminal: StateFlow<Boolean> = _useSshTerminal.asStateFlow()

    private val _rootfsMirror = MutableStateFlow(getRootfsMirrorSync())
    val rootfsMirror: StateFlow<String> = _rootfsMirror.asStateFlow()

    private val _termuxApiProvider = MutableStateFlow(getTermuxApiProviderSync())
    val termuxApiProvider: StateFlow<String> = _termuxApiProvider.asStateFlow()

    fun getTerminalFontSizeSync(): Int = prefs.getInt(KEY_TERMINAL_FONT_SIZE, 14).coerceIn(8, 28)
    fun getTerminalCursorStyleSync(): String = prefs.getString(KEY_TERMINAL_CURSOR_STYLE, "BAR") ?: "BAR"
    fun getTerminalBufferSizeSync(): Int = prefs.getInt(KEY_TERMINAL_BUFFER_SIZE, 20000)
    fun getTerminalThemeSync(): String = prefs.getString(KEY_TERMINAL_THEME, "DEFAULT") ?: "DEFAULT"
    fun hasSeenTerminalLauncherOnboardingSync(): Boolean = prefs.getBoolean(KEY_TERMINAL_LAUNCHER_ONBOARDED, false)

    fun isUseSshTerminalSync(): Boolean = prefs.getBoolean(KEY_USE_SSH_TERMINAL, false)
    fun getSshHostSync(): String = prefs.getString(KEY_SSH_HOST, "127.0.0.1") ?: "127.0.0.1"
    fun getSshPortSync(): Int = prefs.getInt(KEY_SSH_PORT, 8022).coerceIn(1, 65535)
    fun getSshUserSync(): String = prefs.getString(KEY_SSH_USER, "root") ?: "root"
    fun getSshPassSync(): String = com.jarvisai.assistant.security.SecurePrefsCipher.decrypt(prefs.getString(KEY_SSH_PASS, ""))
    fun getSshPrivateKeySync(): String = com.jarvisai.assistant.security.SecurePrefsCipher.decrypt(prefs.getString(KEY_SSH_PRIVATE_KEY, ""))
    fun getSshKeyPassphraseSync(): String = com.jarvisai.assistant.security.SecurePrefsCipher.decrypt(prefs.getString(KEY_SSH_KEY_PASSPHRASE, ""))

    fun getRootfsMirrorSync(): String = prefs.getString(KEY_ROOTFS_MIRROR, "GITHUB_MAIN") ?: "GITHUB_MAIN"
    fun getTermuxApiProviderSync(): String = prefs.getString(KEY_TERMUX_API_PROVIDER, "builtin") ?: "builtin"

    fun saveSshConfigSync(
        enabled: Boolean,
        host: String,
        port: Int,
        user: String,
        pass: String,
        privateKey: String = getSshPrivateKeySync(),
        passphrase: String = getSshKeyPassphraseSync()
    ) {
        val cleanHost = host.trim().ifBlank { "127.0.0.1" }
        val cleanPort = port.coerceIn(1, 65535)
        val cleanUser = user.trim().ifBlank { "root" }
        prefs.edit().apply {
            putBoolean(KEY_USE_SSH_TERMINAL, enabled)
            putString(KEY_SSH_HOST, cleanHost)
            putInt(KEY_SSH_PORT, cleanPort)
            putString(KEY_SSH_USER, cleanUser)
            putString(KEY_SSH_PASS, com.jarvisai.assistant.security.SecurePrefsCipher.encrypt(pass))
            putString(KEY_SSH_PRIVATE_KEY, com.jarvisai.assistant.security.SecurePrefsCipher.encrypt(privateKey))
            putString(KEY_SSH_KEY_PASSPHRASE, com.jarvisai.assistant.security.SecurePrefsCipher.encrypt(passphrase))
            apply()
        }
        _useSshTerminal.value = enabled
    }

    fun setUseSshTerminal(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_SSH_TERMINAL, enabled).apply()
        _useSshTerminal.value = enabled
    }

    fun setRootfsMirror(mirrorId: String) {
        prefs.edit().putString(KEY_ROOTFS_MIRROR, mirrorId).apply()
        _rootfsMirror.value = mirrorId
    }

    fun setTermuxApiProvider(provider: String) {
        val clean = if (provider.equals("external", ignoreCase = true)) "external" else "builtin"
        prefs.edit().putString(KEY_TERMUX_API_PROVIDER, clean).apply()
        _termuxApiProvider.value = clean
    }

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

    fun getAccessToken(): String = com.jarvisai.assistant.security.SecurePrefsCipher.decrypt(prefs.getString(KEY_ACCESS_TOKEN, ""))
    fun getRefreshToken(): String = com.jarvisai.assistant.security.SecurePrefsCipher.decrypt(prefs.getString(KEY_REFRESH_TOKEN, ""))
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

    fun getGrantedScopes(): Set<String> =
        com.jarvisai.assistant.data.remote.GoogleOAuthManager.parseScopes(
            prefs.getString(KEY_GRANTED_SCOPES, "")
        )

    fun setGrantedScopes(scopes: Collection<String>) {
        val cleaned = scopes.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" ")
        prefs.edit().putString(KEY_GRANTED_SCOPES, cleaned).apply()
    }

    /** Returns null if granted scopes have not been recorded yet, or whether the `aicode` voice scope is present. */
    fun hasVoiceAiCodeScope(): Boolean? {
        val scopes = getGrantedScopes()
        if (scopes.isEmpty()) return null
        return com.jarvisai.assistant.data.remote.GoogleOAuthManager.hasVoiceScope(scopes)
    }

    fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        email: String,
        name: String = "Antigravity Dev",
        pictureUrl: String? = null,
        expiresInSeconds: Long = 3600,
        grantedScopes: String? = null
    ) {
        val expiresAtMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, com.jarvisai.assistant.security.SecurePrefsCipher.encrypt(accessToken))
            if (!refreshToken.isNullOrBlank()) {
                putString(KEY_REFRESH_TOKEN, com.jarvisai.assistant.security.SecurePrefsCipher.encrypt(refreshToken))
            }
            if (!grantedScopes.isNullOrBlank()) {
                putString(KEY_GRANTED_SCOPES, grantedScopes.trim())
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
