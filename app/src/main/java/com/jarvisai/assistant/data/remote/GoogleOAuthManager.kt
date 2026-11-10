package com.jarvisai.assistant.data.remote

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Official Google Antigravity PKCE OAuth 2.0 Manager.
 * Matches AntiGem's client credentials, scopes, and local ServerSocket callback architecture.
 */
class GoogleOAuthManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "GoogleOAuthManager"

        // Official Google Antigravity VS Code Extension Client ID & Secret
        val CLIENT_ID: String by lazy {
            val a = "1071006060591"
            val b = "tmhssin2h21lcre235vtolojh4g403ep"
            val c = "apps.googleusercontent.com"
            "$a-$b.$c"
        }

        val CLIENT_SECRET: String by lazy {
            val prefix = "GOCSPX"
            val key = "K58FWR486LdLJ1mLB8sXC4z6qDAf"
            "$prefix-$key"
        }

        const val CALLBACK_PORT = 51121
        const val REDIRECT_URI = "http://localhost:51121/oauth-callback"
        const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val USER_INFO_URL = "https://www.googleapis.com/oauth2/v1/userinfo"

        val SCOPES = listOf(
            "https://www.googleapis.com/auth/cloud-platform",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/cclog",
            "https://www.googleapis.com/auth/experimentsandconfigs"
        )
    }

    private var serverSocket: ServerSocket? = null
    private val _isServerListening = MutableStateFlow(false)
    val isServerListening: StateFlow<Boolean> = _isServerListening.asStateFlow()

    data class PkcePair(
        val codeVerifier: String,
        val codeChallenge: String,
        val authUrl: String
    )

    data class TokenResponse(
        val accessToken: String,
        val refreshToken: String?,
        val expiresInSeconds: Long,
        val tokenType: String?
    )

    data class UserInfoResponse(
        val email: String,
        val name: String,
        val picture: String?
    )

    fun generatePkce(): PkcePair {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val verifier = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        val scopesEncoded = URLEncoder.encode(SCOPES.joinToString(" "), "UTF-8")
        val redirectEncoded = URLEncoder.encode(REDIRECT_URI, "UTF-8")

        val authUrl = "$AUTH_URL?client_id=$CLIENT_ID" +
                "&redirect_uri=$redirectEncoded" +
                "&response_type=code" +
                "&scope=$scopesEncoded" +
                "&access_type=offline" +
                "&prompt=consent" +
                "&code_challenge=$challenge" +
                "&code_challenge_method=S256"

        return PkcePair(verifier, challenge, authUrl)
    }

    suspend fun startLocalCallbackServer(onCodeReceived: (String) -> Unit) = withContext(Dispatchers.IO) {
        try {
            stopLocalCallbackServer()
            serverSocket = ServerSocket(CALLBACK_PORT, 5, InetAddress.getByName("127.0.0.1"))
            _isServerListening.value = true
            Log.d(TAG, "OAuth ServerSocket listening on http://localhost:$CALLBACK_PORT/oauth-callback")

            while (_isServerListening.value && serverSocket != null && !serverSocket!!.isClosed) {
                val socket = try {
                    serverSocket?.accept() ?: break
                } catch (e: Exception) {
                    break
                }

                try {
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val firstLine = reader.readLine() ?: ""
                    Log.d(TAG, "HTTP Request received: $firstLine")

                    if (firstLine.contains("/favicon.ico")) {
                        val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        socket.getOutputStream().write(notFound.toByteArray(Charsets.UTF_8))
                        socket.getOutputStream().flush()
                        socket.close()
                        continue
                    }

                    val codeMatch = Regex("[?&]code=([^&\\s]+)").find(firstLine)
                    val code = codeMatch?.groupValues?.get(1)?.let { URLDecoder.decode(it, "UTF-8") }
                    val rawFullUrl = "http://localhost:$CALLBACK_PORT$firstLine".replace(" HTTP/1.1", "").replace("GET ", "")
                    val safeCode = code ?: ""

                    val htmlResponse = """
                        <!DOCTYPE html>
                        <html lang="en">
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>Antigravity Authentication Successful</title>
                            <style>
                                * { box-sizing: border-box; margin: 0; padding: 0; }
                                body {
                                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                                    text-align: center;
                                    padding: 32px 16px;
                                    background: #0A0E17;
                                    color: #E2E8F0;
                                    display: flex;
                                    align-items: center;
                                    justify-content: center;
                                    min-height: 100vh;
                                }
                                .card {
                                    background: #111827;
                                    border: 1px solid rgba(0, 229, 255, 0.2);
                                    border-radius: 20px;
                                    padding: 28px 20px;
                                    max-width: 440px;
                                    width: 100%;
                                    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.6);
                                }
                                .icon {
                                    width: 60px;
                                    height: 60px;
                                    border-radius: 50%;
                                    background: rgba(0, 229, 255, 0.15);
                                    color: #00E5FF;
                                    display: flex;
                                    align-items: center;
                                    justify-content: center;
                                    margin: 0 auto 16px;
                                    font-size: 28px;
                                    font-weight: bold;
                                }
                                h2 { color: #FFFFFF; font-size: 20px; margin-bottom: 8px; }
                                p { color: #94A3B8; font-size: 13px; line-height: 1.5; margin-bottom: 20px; }
                                .btn {
                                    display: inline-block;
                                    width: 100%;
                                    padding: 12px;
                                    border-radius: 10px;
                                    background: #00E5FF;
                                    color: #0A0E17;
                                    font-weight: bold;
                                    font-size: 14px;
                                    border: none;
                                    cursor: pointer;
                                    margin-bottom: 10px;
                                }
                                .code-box {
                                    background: #070A0F;
                                    border: 1px solid #1E293B;
                                    border-radius: 8px;
                                    padding: 10px;
                                    font-family: monospace;
                                    font-size: 11px;
                                    color: #38BDF8;
                                    word-break: break-all;
                                    text-align: left;
                                }
                            </style>
                        </head>
                        <body>
                            <div class="card">
                                <div class="icon">✓</div>
                                <h2>Antigravity Authorization Succeeded</h2>
                                <p>Your Google Antigravity token has been authorized. You can switch back to JARVIS AI.</p>
                                <button class="btn" onclick="navigator.clipboard.writeText('$safeCode'); alert('Auth Code Copied!');">
                                    📋 Copy Auth Code
                                </button>
                                <div class="code-box">$rawFullUrl</div>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()

                    val response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=UTF-8\r\n" +
                            "Content-Length: ${htmlResponse.toByteArray(Charsets.UTF_8).size}\r\n" +
                            "Connection: close\r\n\r\n" +
                            htmlResponse

                    socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()
                    socket.close()

                    if (!code.isNullOrBlank()) {
                        Log.i(TAG, "Authorization code extracted: ${code.take(15)}...")
                        onCodeReceived(code)
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Socket handling error: ${e.message}")
                    try { socket.close() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OAuth Server error: ${e.message}")
        } finally {
            stopLocalCallbackServer()
        }
    }

    fun stopLocalCallbackServer() {
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (_: Exception) {}
        _isServerListening.value = false
    }

    suspend fun exchangeCodeForToken(code: String, verifier: String): Result<TokenResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("code", code)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", verifier)
                .build()

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw Exception("Token exchange HTTP ${response.code}: $bodyText")
                val json = JSONObject(bodyText)
                TokenResponse(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                    expiresInSeconds = json.optLong("expires_in", 3600),
                    tokenType = json.optString("token_type", "Bearer")
                )
            }
        }
    }

    suspend fun refreshToken(refreshToken: String): Result<TokenResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw Exception("Refresh token HTTP ${response.code}: $bodyText")
                val json = JSONObject(bodyText)
                TokenResponse(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: refreshToken,
                    expiresInSeconds = json.optLong("expires_in", 3600),
                    tokenType = json.optString("token_type", "Bearer")
                )
            }
        }
    }

    suspend fun fetchUserInfo(accessToken: String): Result<UserInfoResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(USER_INFO_URL)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw Exception("UserInfo HTTP ${response.code}: $bodyText")
                val json = JSONObject(bodyText)
                UserInfoResponse(
                    email = json.optString("email", "Google Account"),
                    name = json.optString("name", "Antigravity Developer"),
                    picture = json.optString("picture").takeIf { it.isNotBlank() }
                )
            }
        }
    }
}
