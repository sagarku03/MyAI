package com.jarvisai.assistant.browser

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarvisai.assistant.service.JarvisTtsManager

class JarvisBrowserActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialUrl = intent.getStringExtra(EXTRA_URL) ?: "https://www.google.com"

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF070E1A)
                ) {
                    JarvisBrowserScreen(
                        initialUrl = initialUrl,
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        activeWebViewRef = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"

        @Volatile
        var activeWebViewRef: WebView? = null

        /**
         * Feature 7: Builds the JavaScript snippet that extracts interactive DOM elements
         * (`<button>`, `<input>`, `<a>`, `<select>`, `[role=button]`) as a JSON array.
         */
        fun buildDomInspectionJsScript(): String {
            return """
                (function() {
                    var nodes = document.querySelectorAll('button, input, a, select, textarea, [role="button"]');
                    var out = [];
                    for (var i = 0; i < Math.min(nodes.length, 60); i++) {
                        var el = nodes[i];
                        var label = (el.innerText || el.value || el.placeholder || el.getAttribute('aria-label') || el.id || '').trim();
                        if (!label && el.tagName !== 'INPUT') continue;
                        out.push({
                            index: i + 1,
                            tag: el.tagName.toLowerCase(),
                            id: el.id || '',
                            label: label.substring(0, 80)
                        });
                    }
                    return JSON.stringify(out);
                })();
            """.trimIndent()
        }

        /**
         * Feature 7: Builds the JavaScript snippet that clicks a DOM element or fills an `<input>`/`<textarea>`
         * by CSS selector, ID, placeholder, or visible text.
         */
        fun buildDomClickOrFillJsScript(targetSelectorOrText: String, valueToFill: String = ""): String {
            val safeTarget = targetSelectorOrText.replace("\\", "\\\\").replace("'", "\\'")
            val safeValue = valueToFill.replace("\\", "\\\\").replace("'", "\\'")
            return """
                (function() {
                    var target = '$safeTarget';
                    var val = '$safeValue';
                    var el = null;
                    try { el = document.querySelector(target); } catch(e) {}
                    if (!el) el = document.getElementById(target);
                    if (!el) {
                        var candidates = document.querySelectorAll('input, textarea, button, a, select, [role="button"]');
                        var lower = target.toLowerCase();
                        for (var i = 0; i < candidates.length; i++) {
                            var c = candidates[i];
                            var txt = ((c.innerText || '') + ' ' + (c.placeholder || '') + ' ' + (c.value || '') + ' ' + (c.getAttribute('aria-label') || '') + ' ' + (c.id || '')).toLowerCase();
                            if (txt.indexOf(lower) !== -1) { el = c; break; }
                        }
                    }
                    if (!el) return 'NOT_FOUND:' + target;
                    el.scrollIntoView({behavior: 'smooth', block: 'center'});
                    if (val.length > 0) {
                        el.focus();
                        el.value = val;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        return 'FILLED:' + (el.id || el.tagName) + '=' + val;
                    } else {
                        el.click();
                        return 'CLICKED:' + (el.innerText || el.id || el.tagName).trim().substring(0, 40);
                    }
                })();
            """.trimIndent()
        }

        fun executeWebDomAgentAction(
            targetSelectorOrText: String,
            valueToFill: String = "",
            onResult: (String) -> Unit = {}
        ): String {
            val script = buildDomClickOrFillJsScript(targetSelectorOrText, valueToFill)
            val view = activeWebViewRef
            if (view != null) {
                view.post {
                    view.evaluateJavascript(script) { res ->
                        onResult(res?.trim('"').orEmpty())
                    }
                }
                return if (valueToFill.isNotBlank()) {
                    "Dispatched DOM fill '$valueToFill' into '$targetSelectorOrText'."
                } else {
                    "Dispatched DOM click on '$targetSelectorOrText'."
                }
            }
            return "Prepared DOM script for '$targetSelectorOrText' (In-App WebView ready on launch)."
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JarvisBrowserScreen(
    initialUrl: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var currentTitle by remember { mutableStateOf("JARVIS Secure Web Portal") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top Cyber Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1A2E))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (webViewRef?.canGoBack() == true) {
                        webViewRef?.goBack()
                    } else {
                        onClose()
                    }
                },
                modifier = Modifier.testTag("browser_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF00E5FF)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = currentTitle,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentUrl,
                    color = Color(0xFF80D8FF),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = {
                    webViewRef?.evaluateJavascript(
                        "(function() { return document.body.innerText; })();"
                    ) { result ->
                        val cleanText = result?.trim('"')?.replace("\\n", " ")?.take(400)
                        if (!cleanText.isNullOrBlank()) {
                            JarvisTtsManager.speak(context, "Page content: $cleanText")
                        }
                    }
                },
                modifier = Modifier.testTag("browser_read_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Read Aloud",
                    tint = Color(0xFF00E5FF)
                )
            }

            IconButton(
                onClick = { webViewRef?.reload() },
                modifier = Modifier.testTag("browser_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color(0xFF80D8FF)
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("browser_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFFFF5252)
                )
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF00E5FF),
                trackColor = Color(0xFF0F1A2E)
            )
        }

        // WebView Container
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            url?.let { currentUrl = it }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            url?.let { currentUrl = it }
                            view?.title?.let { currentTitle = it }
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: android.webkit.RenderProcessGoneDetail?
                        ): Boolean {
                            view?.let {
                                (it.parent as? android.view.ViewGroup)?.removeView(it)
                                it.destroy()
                            }
                            webViewRef = null
                            JarvisBrowserActivity.activeWebViewRef = null
                            isLoading = false
                            return true
                        }
                    }
                    loadUrl(initialUrl)
                    webViewRef = this
                    JarvisBrowserActivity.activeWebViewRef = this
                }
            }
        )
    }
}
