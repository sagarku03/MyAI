package com.jarvisai.assistant.voice.agy

import android.content.Context
import com.jarvisai.assistant.agent.core.AgyOkHttpClient
import com.jarvisai.assistant.data.pc.PcConnect
import com.jarvisai.assistant.data.remote.JarvisHubConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

/**
 * Resolves the active Antigravity hub (isolated local port 2235 or paired desktop PC hub)
 * and IDE bridge (port 2234 or paired PC bridge), and attaches Agentra's dynamic CSRF headers
 * without forcing gRPC/JSON content-type headers onto WebSocket upgrades or MP3 downloads.
 */
object AgyVoiceHubResolver {

    fun currentHubBaseUrl(context: Context? = null): String {
        val pc = PcConnect.getActivePairedPc()
        if (pc != null) {
            val host = pc.orderedAddresses().firstOrNull()
            if (!host.isNullOrBlank()) {
                return "http://$host:${pc.hubPort}"
            }
        }
        return JarvisHubConfig.getHubBaseUrl(context).trimEnd('/')
    }

    fun currentBridgeHttpUrl(context: Context? = null): String {
        val pc = PcConnect.getActivePairedPc()
        if (pc != null) {
            val host = pc.orderedAddresses().firstOrNull()
            if (!host.isNullOrBlank()) {
                return "http://$host:${pc.bridgePort}"
            }
        }
        return "http://127.0.0.1:${JarvisHubConfig.getBridgePort(context)}"
    }

    fun isRemoteHub(context: Context? = null): Boolean {
        val url = currentHubBaseUrl(context).toHttpUrlOrNull() ?: return false
        val host = url.host
        return host != "127.0.0.1" && host != "localhost" && host != "::1"
    }

    fun attachHubAuthHeaders(builder: Request.Builder, targetUrl: HttpUrl, context: Context? = null): Request.Builder {
        builder.header("User-Agent", "Agentra-Android-Native")
        val csrf = AgyOkHttpClient.getOrFetchCsrfToken(targetUrl)
        val framedHeader = AgyOkHttpClient.getFramedHeaderName()
        if (csrf.isNotBlank()) {
            if (framedHeader.isNotBlank()) {
                builder.header(framedHeader, csrf)
            }
            builder.header("x-codeium-csrf-token", csrf)
        }
        val pc = PcConnect.getActivePairedPc()
        if (pc != null && pc.token.isNotBlank() && targetUrl.host != "127.0.0.1" && targetUrl.host != "localhost") {
            builder.header("Authorization", "Bearer ${pc.token}")
        }
        return builder
    }
}
