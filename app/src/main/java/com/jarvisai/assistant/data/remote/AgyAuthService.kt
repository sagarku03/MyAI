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

/**
 * Dedicated authentication manager for Jarvis AI's isolated Antigravity daemon.
 */
class AgyAuthService(private val context: Context) {

    companion object {
        private const val TAG = "AgyAuthService"
    }

    data class AuthStatus(
        val isLoggedIn: Boolean,
        val email: String? = null,
        val name: String? = null,
        val pictureUrl: String? = null
    )

    suspend fun checkAuthStatus(): Result<AuthStatus> = withContext(Dispatchers.IO) {
        try {
            val url = "${JarvisHubConfig.getLanguageServiceUrl(context)}/GetAuthStatus"
            val emptyBody = "{}".toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(url).post(emptyBody).build()

            AgyOkHttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("GetAuthStatus failed: HTTP ${resp.code}"))
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                val authResult = json.optJSONObject("authResult") ?: json.optJSONObject("auth_result")
                val isValid = authResult?.optBoolean("hasValidAuth", false)
                    ?: authResult?.optBoolean("has_valid_auth", false)
                    ?: false

                Result.success(
                    AuthStatus(
                        isLoggedIn = isValid,
                        email = authResult?.optString("email"),
                        name = authResult?.optString("name"),
                        pictureUrl = authResult?.optString("pictureUrl") ?: authResult?.optString("picture_url")
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "checkAuthStatus error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun startLogin(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "${JarvisHubConfig.getLanguageServiceUrl(context)}/Login"
            val payload = JSONObject().put("isGcpTos", false).put("is_gcp_tos", false).toString()
            val req = Request.Builder()
                .url(url)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            AgyOkHttpClient.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val authResult = json.optJSONObject("authResult") ?: json.optJSONObject("auth_result")
                val authUrl = authResult?.optString("authUrl")
                    ?: authResult?.optString("auth_url")
                    ?: json.optString("authUrl")

                if (authUrl.isNotBlank()) {
                    Result.success(authUrl)
                } else {
                    Result.failure(Exception("No OAuth URL returned from Antigravity Hub"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
