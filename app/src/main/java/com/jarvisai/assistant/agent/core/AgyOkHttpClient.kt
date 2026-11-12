package com.jarvisai.assistant.agent.core

import android.util.Log
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * High-performance HTTP client for the local Google Antigravity Jetski Daemon (127.0.0.1:2235).
 * Automatically acquires dynamic CSRF tokens and attaches the required framed security header.
 */
object AgyOkHttpClient {
    private const val TAG = "AgyOkHttpClient"
    private val HUB_ROOT_URL get() = "${com.jarvisai.assistant.data.remote.JarvisHubConfig.getHubBaseUrl()}/"
    private val CSRF_PATTERN = Pattern.compile(""""csrfToken"\s*:\s*"([^"]+)"""")

    @Volatile
    private var cachedCsrfToken: String = ""

    @Volatile
    private var cachedHeaderName: String = ""

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
                val requestUrl = request.url.toString()

                // Only attach security headers for calls to local isolated hub port
                val hubPort = com.jarvisai.assistant.data.remote.JarvisHubConfig.getHubPort()
                if (requestUrl.contains("127.0.0.1:$hubPort") || requestUrl.contains("localhost:$hubPort")) {
                    val csrf = getOrFetchCsrfToken()
                    val headerName = getFramedHeaderName()

                    val reqBuilder = request.newBuilder()
                        .header("User-Agent", "antiGem-Android-Native")
                        .header("Content-Type", "application/json")
                        .header("X-Grpc-Web", "1")

                    if (headerName.isNotBlank() && csrf.isNotBlank()) {
                        reqBuilder.header(headerName, csrf)
                    }

                    val response = chain.proceed(reqBuilder.build())
                    if (response.code == 401 || response.code == 403) {
                        Log.w(TAG, "CSRF Token rejected by Hub (HTTP ${response.code}). Invalidating cache.")
                        cachedCsrfToken = ""
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

        // 1. Try reading from ~/.antigem_bridge/csrf_token.txt
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

        // Fallback default token
        cachedHeaderName = "x-agnfan7m2r00wy_9qx"
        return cachedHeaderName
    }

    /**
     * Dynamically extracts the active CSRF token from http://127.0.0.1:1235/
     */
    fun getOrFetchCsrfToken(): String {
        if (cachedCsrfToken.isNotBlank()) return cachedCsrfToken

        try {
            val req = Request.Builder().url(HUB_ROOT_URL).get().build()
            val simpleClient = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).build()
            simpleClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val matcher = CSRF_PATTERN.matcher(body)
                if (matcher.find()) {
                    val token = matcher.group(1).orEmpty()
                    if (token.isNotBlank()) {
                        cachedCsrfToken = token
                        Log.i(TAG, "Successfully acquired active Hub CSRF token: ${token.take(8)}...")
                        return cachedCsrfToken
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch CSRF token from $HUB_ROOT_URL: ${e.message}")
        }
        return cachedCsrfToken
    }
}
