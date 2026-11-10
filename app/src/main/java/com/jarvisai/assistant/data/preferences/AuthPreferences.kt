package com.jarvisai.assistant.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent Store for Google Antigravity & Agent Credentials.
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

        @Volatile
        private var INSTANCE: AuthPreferences? = null

        fun getInstance(context: Context): AuthPreferences =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthPreferences(context.applicationContext).also { INSTANCE = it }
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

    fun getAccessToken(): String = prefs.getString(KEY_ACCESS_TOKEN, "").orEmpty()
    fun getRefreshToken(): String = prefs.getString(KEY_REFRESH_TOKEN, "").orEmpty()
    fun getUserEmail(): String = prefs.getString(KEY_EMAIL, "").orEmpty()
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "Antigravity Dev").orEmpty()
    fun getPictureUrl(): String = prefs.getString(KEY_PICTURE_URL, "").orEmpty()
    fun getProjectId(): String = prefs.getString(KEY_PROJECT_ID, "rising-fact-p41fc").orEmpty()
    fun getTier(): String = prefs.getString(KEY_TIER, "pro").orEmpty()

    fun isTokenExpired(): Boolean {
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        // Buffer of 3 minutes before real expiry
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
        prefs.edit().apply {
            putString(KEY_PROJECT_ID, projectId)
            putString(KEY_TIER, tier)
            apply()
        }
        _tier.value = tier
    }

    fun clearAuth() {
        prefs.edit().clear().apply()
        _isLoggedIn.value = false
        _userEmail.value = ""
        _tier.value = "pro"
    }
}
