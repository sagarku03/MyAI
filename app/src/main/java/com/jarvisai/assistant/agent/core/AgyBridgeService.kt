package com.jarvisai.assistant.agent.core

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.terminal.LocalServerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AgyBridgeService private constructor() {

    companion object {
        private const val TAG = "AgyBridgeService"
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val instance: AgyBridgeService by lazy { AgyBridgeService() }
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var appContext: Context? = null
    private var webSocket: WebSocket? = null

    private val _streamEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val streamEvents: SharedFlow<String> = _streamEvents.asSharedFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getBaseUrl(): String {
        val ctx = appContext ?: throw IllegalStateException("AgyBridgeService not initialized")
        return LocalServerManager.getBridgeUrl(ctx)
    }

    suspend fun get(endpoint: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${getBaseUrl()}$endpoint"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.body?.string()}")
                resp.body?.string().orEmpty()
            }
        }
    }

    suspend fun post(endpoint: String, jsonBody: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${getBaseUrl()}$endpoint"
            val req = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.body?.string()}")
                resp.body?.string().orEmpty()
            }
        }
    }

    fun connectWebSocket() {
        val ctx = appContext ?: return
        val port = LocalServerManager.getBridgePort(ctx)
        val wsUrl = "ws://127.0.0.1:$port/ws"

        val req = Request.Builder().url(wsUrl).build()
        webSocket = httpClient.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Connected to Go Bridge WebSocket: $wsUrl")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                _streamEvents.tryEmit(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
            }
        })
    }

    fun sendWebSocketMessage(msg: String) {
        webSocket?.send(msg)
    }

    fun disconnectWebSocket() {
        webSocket?.close(1000, "App closed")
        webSocket = null
    }
}
