package com.jarvisai.assistant.agent.core

import android.util.Log
import com.jarvisai.assistant.data.remote.JarvisHubConfig
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * High-performance HTTP client for the local Google Antigravity Jetski Daemon (127.0.0.1:2235).
 * Automatically acquires dynamic CSRF tokens per target origin and attaches both the framed
 * security header (x-ag<token>_9qx) and standard x-codeium-csrf-token header.
 */
object AgyOkHttpClient {
    private const val TAG = "AgyOkHttpClient"
    private val CSRF_PATTERN = Pattern.compile(""""csrfToken"\s*:\s*"([^"]+)"""")

    // Map of "host:port" -> csrfToken so port 2235 and PC Connect hubs never collide
    private val csrfCache = ConcurrentHashMap<String, String>()

    @Volatile
    private var cachedHeaderName: String = ""

    private val tokenFetchClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    val client: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 64
        }
        val connectionPool = ConnectionPool(16, 5, TimeUnit.MINUTES)

        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url
                val host = url.host
                val path = url.encodedPath

                // Intercept ALL local hub or LanguageServerService RPC calls regardless of port
                val isAgyRpc = host == "127.0.0.1" ||
                    host == "localhost" ||
                    path.contains("LanguageServerService") ||
                    path.contains("StartCascade") ||
                    path.contains("SendUserCascadeMessage") ||
                    path.contains("GetCascadeTrajectory")

                if (isAgyRpc) {
                    val originKey = "${url.scheme}://${url.host}:${url.port}"
                    val csrf = getOrFetchCsrfToken(url)
                    val headerName = getFramedHeaderName()

                    val reqBuilder = request.newBuilder()
                        .header("User-Agent", "antiGem-Android-Native")
                        .header("Content-Type", "application/json")
                        .header("X-Grpc-Web", "1")

                    if (csrf.isNotBlank()) {
                        if (headerName.isNotBlank()) {
                            reqBuilder.header(headerName, csrf)
                        }
                        // Also attach standard Codeium/Cascade CSRF header for backwards & forwards compatibility
                        reqBuilder.header("x-codeium-csrf-token", csrf)
                    }

                    val response = chain.proceed(reqBuilder.build())
                    if (response.code == 401 || response.code == 403 || response.code == 400) {
                        Log.w(TAG, "Hub returned HTTP ${response.code} on $originKey. Refreshing CSRF cache.")
                        csrfCache.remove(originKey)
                    }
                    response
                } else {
                    chain.proceed(request)
                }
            }
            .build()
    }

    /**
     * Resolves the 12-char framed security header: x-ag<token>_9qx
     */
    fun getFramedHeaderName(): String {
        if (cachedHeaderName.isNotBlank()) return cachedHeaderName

        val tokenFiles = listOf(
            File("/data/user/0/com.termux/files/home/.antigem_bridge/csrf_token.txt"),
            File("/data/data/com.termux/files/home/.antigem_bridge/csrf_token.txt"),
            File("/sdcard/.antigem_bridge/csrf_token.txt")
        )

        for (f in tokenFiles) {
            try {
                if (f.exists() && f.canRead()) {
                    val raw = f.readText().trim()
                    if (raw.isNotBlank()) {
                        val clean = raw.take(12).padEnd(12, '0')
                        cachedHeaderName = "x-ag${clean}_9qx"
                        return cachedHeaderName
                    }
                }
            } catch (_: Exception) {}
        }

        // Standard Antigravity binary default framed header
        cachedHeaderName = "x-agnfan7m2r00wy_9qx"
        return cachedHeaderName
    }

    /**
     * Dynamically extracts the active CSRF token from the exact target hub URL (e.g., http://127.0.0.1:2235/)
     */
    fun getOrFetchCsrfToken(targetUrl: HttpUrl): String {
        val originUrl = "${targetUrl.scheme}://${targetUrl.host}:${targetUrl.port}"
        val existing = csrfCache[originUrl]
        if (!existing.isNullOrBlank()) return existing

        try {
            val req = Request.Builder().url("$originUrl/").get().build()
            tokenFetchClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val matcher = CSRF_PATTERN.matcher(body)
                if (matcher.find()) {
                    val token = matcher.group(1).orEmpty()
                    if (token.isNotBlank()) {
                        csrfCache[originUrl] = token
                        Log.i(TAG, "Acquired Hub CSRF token from $originUrl: ${token.take(8)}...")
                        return token
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch CSRF token from $originUrl/: ${e.message}")
        }
        return ""
    }

    /**
     * Convenience overload that fetches the CSRF token for the default configured Hub base URL.
     */
    fun getOrFetchCsrfToken(): String {
        val defaultUrl = "${JarvisHubConfig.getHubBaseUrl()}/".toHttpUrlOrNull() ?: return ""
        return getOrFetchCsrfToken(defaultUrl)
    }
}
