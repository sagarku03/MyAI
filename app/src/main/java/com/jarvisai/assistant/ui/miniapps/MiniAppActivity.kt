package com.jarvisai.assistant.ui.miniapps

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarvisai.assistant.data.miniapps.MiniAppInfo
import com.jarvisai.assistant.data.miniapps.MiniAppsManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Sandboxed WebView host for Agentra Mini-Apps.
 * Serves files from https://<slug>.agentra.invalid/ and exposes the bundled miniapp-sdk
 * under both _agentra/ and _antigem/ paths for seamless compatibility.
 */
class MiniAppActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_SLUG = "mini_app_slug"

        fun createIntent(context: Context, slug: String): Intent =
            Intent(context, MiniAppActivity::class.java).putExtra(EXTRA_SLUG, slug)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val slug = intent.getStringExtra(EXTRA_SLUG).orEmpty()
        val app = MiniAppsManager.getById(this, slug)
        if (app == null) {
            Toast.makeText(this, "Mini-App '$slug' not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            MiniAppHostScreen(
                app = app,
                onClose = { finish() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MiniAppHostScreen(
    app: MiniAppInfo,
    onClose: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val virtualHost = "${app.slug}.agentra.invalid"
    val startUrl = "https://$virtualHost/${app.entry.trimStart('/')}"

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = Color(0xFF0A0F1A),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${app.iconEmoji} ${app.name}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("v${app.version} · Sandboxed Mini-App", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Mini-App", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { webViewRef?.reload() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload Mini-App", tint = Color(0xFF38BDF8))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0E1525))
            )
        }
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.databaseEnabled = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    settings.mediaPlaybackRequiresUserGesture = false

                    val bridge = MiniAppJsBridge(ctx.applicationContext, app)
                    addJavascriptInterface(bridge, "AgentraBridge")
                    addJavascriptInterface(bridge, "AntiGemBridge")

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val targetUri = request?.url ?: return true
                            if (targetUri.host == virtualHost && targetUri.scheme.equals("https", ignoreCase = true)) {
                                return false
                            }
                            if (targetUri.scheme.equals("http", ignoreCase = true) || targetUri.scheme.equals("https", ignoreCase = true)) {
                                runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, targetUri))
                                }
                            }
                            return true
                        }

                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val uri = request.url
                            if (uri.host != virtualHost) return null
                            val reqPath = (uri.path ?: "/").trimStart('/')

                            // Serve bundled miniapp-sdk under /_agentra/* or /_antigem/*
                            if (reqPath.startsWith("_agentra/") || reqPath.startsWith("_antigem/")) {
                                val assetRel = reqPath.substringAfter("/")
                                val mapped = when (assetRel) {
                                    "sdk.js", "core.js" -> "miniapp-sdk/core.js"
                                    "ui.css" -> "miniapp-sdk/ui.css"
                                    "idb.js" -> "miniapp-sdk/idb.js"
                                    "net.js" -> "miniapp-sdk/net.js"
                                    "threads.js" -> "miniapp-sdk/threads.js"
                                    "worker-core.js" -> "miniapp-sdk/worker-core.js"
                                    else -> "miniapp-sdk/$assetRel"
                                }
                                return runCatching {
                                    val bytes = ctx.assets.open(mapped).use { it.readBytes() }
                                    WebResourceResponse(guessMimeType(mapped), "utf-8", ByteArrayInputStream(bytes))
                                }.getOrNull()
                            }

                            val rel = reqPath.ifBlank { app.entry }
                            val file = MiniAppsManager.resolveSafeAppFile(app, rel)
                            if (file != null && file.isFile) {
                                return runCatching {
                                    WebResourceResponse(guessMimeType(file.name), "utf-8", FileInputStream(file))
                                }.getOrNull()
                            }
                            return WebResourceResponse(
                                "text/plain",
                                "utf-8",
                                404,
                                "Not Found",
                                emptyMap(),
                                ByteArrayInputStream("Not found: $rel".toByteArray())
                            )
                        }
                    }
                    loadUrl(startUrl)
                    webViewRef = this
                }
            }
        )
    }
}

private class MiniAppJsBridge(
    private val context: Context,
    private val app: MiniAppInfo
) {
    private val sandboxFilesDir: File
        get() = File(MiniAppsManager.getAppDataDir(context, app.id), "files").apply { mkdirs() }

    private fun resolveDataFile(path: String): File? {
        val clean = path.trimStart('/')
        val target = File(sandboxFilesDir, clean).canonicalFile
        val root = sandboxFilesDir.canonicalFile
        return if (target.path.startsWith(root.path + File.separator)) target else null
    }

    @JavascriptInterface
    fun getAppInfo(): String = JSONObject()
        .put("id", app.id)
        .put("slug", app.slug)
        .put("name", app.name)
        .put("version", app.version)
        .put("host", "Agentra")
        .toString()

    @JavascriptInterface
    fun kvGet(key: String): String = MiniAppsManager.kvGet(context, app.id, key)

    @JavascriptInterface
    fun kvSet(key: String, value: String) {
        MiniAppsManager.kvSet(context, app.id, key, value)
    }

    @JavascriptInterface
    fun kvRemove(key: String) {
        MiniAppsManager.kvRemove(context, app.id, key)
    }

    @JavascriptInterface
    fun kvKeys(): String = MiniAppsManager.kvKeysJson(context, app.id)

    @JavascriptInterface
    fun fsRead(path: String): String {
        val f = resolveDataFile(path) ?: return ""
        return if (f.isFile) runCatching { f.readText() }.getOrDefault("") else ""
    }

    @JavascriptInterface
    fun fsWrite(path: String, content: String): Boolean {
        val f = resolveDataFile(path) ?: return false
        return runCatching {
            f.parentFile?.mkdirs()
            f.writeText(content)
            true
        }.getOrDefault(false)
    }

    @JavascriptInterface
    fun fsList(): String {
        val arr = JSONArray()
        sandboxFilesDir.listFiles()?.forEach { arr.put(it.name) }
        return arr.toString()
    }

    @JavascriptInterface
    fun toast(message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message.take(180), Toast.LENGTH_SHORT).show()
        }
    }
}

private fun guessMimeType(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "html", "htm" -> "text/html"
        "js", "mjs" -> "application/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
