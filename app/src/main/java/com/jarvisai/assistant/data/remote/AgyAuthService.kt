package com.jarvisai.assistant.data.remote

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.agent.core.AgyOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Dedicated authentication manager for Agentra's isolated Antigravity Hub daemon.
 * Mirrors AntiGem's LanguageServerService auth RPCs (GetAuthStatus, GetUserStatus, Login, AuthLogout).
 */
class AgyAuthService(private val context: Context) {

    companion object {
        private const val TAG = "AgyAuthService"
        private val JSON_MEDIA = "application/json".toMediaType()
        private val METADATA_PAYLOAD = """{"metadata":{"ideName":"antigravity","extensionName":"antigravity","locale":"en"}}"""
    }

    data class AuthStatus(
        val isLoggedIn: Boolean,
        val email: String? = null,
        val name: String? = null,
        val pictureUrl: String? = null,
        val tier: String? = null,
        val uiMessage: String? = null,
        val verificationUrl: String? = null,
        val headlessAuthUrl: String? = null,
        val grantedScopes: List<String> = emptyList(),
        val hasVoiceScope: Boolean? = null
    )

    data class LoginRpcResult(
        val hasValidAuth: Boolean = false,
        val authUrl: String? = null,
        val verificationUrl: String? = null,
        val uiMessage: String? = null
    )

    // Login RPC blocks until the user completes OAuth in browser, so use an extended read timeout.
    private val loginClient by lazy {
        AgyOkHttpClient.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun checkAuthStatus(): Result<AuthStatus> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = JarvisHubConfig.getLanguageServiceUrl(context)
            val req = Request.Builder()
                .url("$baseUrl/GetAuthStatus")
                .post("{}".toRequestBody(JSON_MEDIA))
                .build()

            AgyOkHttpClient.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("GetAuthStatus failed: HTTP ${resp.code}"))
                }
                val json = JSONObject(body.ifBlank { "{}" })
                val authResult = json.optJSONObject("authResult") ?: json.optJSONObject("auth_result")
                val isValid = authResult?.optBoolean("hasValidAuth", false) == true ||
                    authResult?.optBoolean("has_valid_auth", false) == true

                var email = authResult?.optString("email").orEmpty().takeIf { it.isNotBlank() && it != "null" }
                var name = authResult?.optString("name").orEmpty().takeIf { it.isNotBlank() && it != "null" }
                var pictureUrl = (authResult?.optString("pictureUrl").orEmpty()
                    .ifBlank { authResult?.optString("picture_url").orEmpty() })
                    .takeIf { it.isNotBlank() && it != "null" }
                var tier: String? = null

                // Match AntiGem's fetchDetailedAuthInfo: when signed in, query GetUserStatus for email/name/tier
                if (isValid) {
                    runCatching {
                        val userReq = Request.Builder()
                            .url("$baseUrl/GetUserStatus")
                            .post(METADATA_PAYLOAD.toRequestBody(JSON_MEDIA))
                            .build()
                        AgyOkHttpClient.client.newCall(userReq).execute().use { uResp ->
                            if (uResp.isSuccessful) {
                                val uJson = JSONObject(uResp.body?.string().orEmpty().ifBlank { "{}" })
                                val userStatus = uJson.optJSONObject("userStatus") ?: uJson.optJSONObject("user_status")
                                if (userStatus != null) {
                                    userStatus.optString("email").takeIf { it.isNotBlank() && it != "null" }?.let { email = it }
                                    userStatus.optString("name").takeIf { it.isNotBlank() && it != "null" }?.let { name = it }
                                    (userStatus.optString("profilePictureUrl").ifBlank { userStatus.optString("profile_picture_url") })
                                        .takeIf { it.isNotBlank() && it != "null" }
                                        ?.let { pictureUrl = it }
                                    val tierObj = userStatus.optJSONObject("userTier") ?: userStatus.optJSONObject("user_tier")
                                    tier = tierObj?.optString("name")?.takeIf { it.isNotBlank() }
                                        ?: tierObj?.optString("description")?.takeIf { it.isNotBlank() }
                                }
                            }
                        }
                    }
                }

                val verificationObj = authResult?.optJSONObject("verificationRequired")
                    ?: authResult?.optJSONObject("verification_required")
                    ?: authResult?.optJSONObject("ineligible")
                val verificationUrl = (verificationObj?.optString("verificationUrl").orEmpty()
                    .ifBlank { verificationObj?.optString("verification_url").orEmpty() })
                    .takeIf { it.isNotBlank() }
                val headlessObj = authResult?.optJSONObject("headlessAuthRequired")
                    ?: authResult?.optJSONObject("headless_auth_required")
                val headlessUrl = (headlessObj?.optString("authUrl").orEmpty()
                    .ifBlank { headlessObj?.optString("auth_url").orEmpty() })
                    .takeIf { it.isNotBlank() }
                val uiMsg = (authResult?.optString("uiMessage").orEmpty()
                    .ifBlank { authResult?.optString("ui_message").orEmpty() })
                    .takeIf { it.isNotBlank() }
                val grantedScopes = GoogleOAuthManager.parseGrantedScopes(authResult)
                val hasVoiceScope = if (grantedScopes.isNotEmpty()) {
                    GoogleOAuthManager.hasVoiceScope(grantedScopes)
                } else {
                    null
                }

                Result.success(
                    AuthStatus(
                        isLoggedIn = isValid,
                        email = email,
                        name = name,
                        pictureUrl = pictureUrl,
                        tier = tier,
                        uiMessage = uiMsg,
                        verificationUrl = verificationUrl,
                        headlessAuthUrl = headlessUrl,
                        grantedScopes = grantedScopes,
                        hasVoiceScope = hasVoiceScope
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "checkAuthStatus error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Invokes LanguageServerService/Login on the local agy --hub daemon.
     * Matches AntiGem's flow: agy opens its own OAuth listener and prints the Google auth URL
     * to stdout/stderr (captured by LocalServerManager.pendingHubLoginUrl) OR returns headless_auth_required.auth_url.
     */
    suspend fun executeLoginRpc(): Result<LoginRpcResult> = withContext(Dispatchers.IO) {
        try {
            val url = "${JarvisHubConfig.getLanguageServiceUrl(context)}/Login"
            val payload = JSONObject()
                .put("isGcpTos", false)
                .put("is_gcp_tos", false)
                .toString()
            val req = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()

            loginClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful && body.isBlank()) {
                    return@withContext Result.failure(Exception("Login RPC failed: HTTP ${resp.code}"))
                }
                val json = runCatching { JSONObject(body.ifBlank { "{}" }) }.getOrDefault(JSONObject())
                val authResult = json.optJSONObject("authResult") ?: json.optJSONObject("auth_result")
                val hasValidAuth = authResult?.optBoolean("hasValidAuth", false) == true ||
                    authResult?.optBoolean("has_valid_auth", false) == true

                val headlessObj = authResult?.optJSONObject("headlessAuthRequired")
                    ?: authResult?.optJSONObject("headless_auth_required")
                val authUrl = listOf(
                    headlessObj?.optString("authUrl"),
                    headlessObj?.optString("auth_url"),
                    authResult?.optString("authUrl"),
                    authResult?.optString("auth_url"),
                    json.optString("authUrl"),
                    json.optString("auth_url")
                ).firstOrNull { !it.isNullOrBlank() && it != "null" }

                val ineligibleObj = authResult?.optJSONObject("ineligible")
                val verifyObj = authResult?.optJSONObject("verificationRequired")
                    ?: authResult?.optJSONObject("verification_required")
                val verificationUrl = listOf(
                    ineligibleObj?.optString("verificationUrl"),
                    ineligibleObj?.optString("verification_url"),
                    verifyObj?.optString("verificationUrl"),
                    verifyObj?.optString("verification_url")
                ).firstOrNull { !it.isNullOrBlank() && it != "null" }

                val generalErr = authResult?.optJSONObject("generalError") ?: authResult?.optJSONObject("general_error")
                val tosErr = authResult?.optJSONObject("tosViolation") ?: authResult?.optJSONObject("tos_violation")
                val uiMsg = listOf(
                    authResult?.optString("uiMessage"),
                    authResult?.optString("ui_message"),
                    ineligibleObj?.optString("reason"),
                    generalErr?.optString("reason"),
                    tosErr?.optString("message")
                ).firstOrNull { !it.isNullOrBlank() && it != "null" }

                Result.success(
                    LoginRpcResult(
                        hasValidAuth = hasValidAuth,
                        authUrl = authUrl,
                        verificationUrl = verificationUrl,
                        uiMessage = uiMsg
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "executeLoginRpc error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun startLogin(): Result<String> {
        val res = executeLoginRpc()
        return res.mapCatching { login ->
            login.authUrl?.takeIf { it.isNotBlank() }
                ?: throw Exception(login.uiMessage ?: "No OAuth URL returned from Antigravity Hub")
        }
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${JarvisHubConfig.getLanguageServiceUrl(context)}/AuthLogout"
            val req = Request.Builder()
                .url(url)
                .post("{}".toRequestBody(JSON_MEDIA))
                .build()
            AgyOkHttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "AuthLogout returned HTTP ${resp.code}")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "logout error: ${e.message}")
            Result.failure(e)
        }
    }
}
