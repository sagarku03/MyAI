package com.jarvisai.assistant.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.AccentChampagneGold
import com.example.ui.theme.AccentMintEmerald
import com.example.ui.theme.AccentRosePink
import com.example.ui.theme.AccentSkyCyan
import com.example.ui.theme.RoyalIndigoAccent
import com.example.ui.theme.RoyalPurplePrimary
import com.example.ui.theme.TextLavenderMuted
import com.example.ui.theme.TextPureWhite
import com.jarvisai.assistant.browser.NativeWebPageSnapshot
import com.jarvisai.assistant.browser.NativeWebReaderEngine
import com.jarvisai.assistant.browser.NativeWebSnapshotContent
import com.jarvisai.assistant.service.JarvisTtsManager
import com.jarvisai.assistant.ui.components.JarvisGlassPanel
import kotlinx.coroutines.launch
import java.util.UUID

private data class ChromeBrowserTab(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    val url: String = "https://www.google.com",
    val title: String = "New Tab"
)

private const val MOBILE_CHROME_UA =
    "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
private const val DESKTOP_CHROME_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    initialUrl: String = "https://www.google.com",
    onBack: () -> Unit,
    onSummarizeContent: suspend (String) -> String = { "Page summary generated by JARVIS Neural Core." }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    val startNormalized = remember(initialUrl) { NativeWebReaderEngine.normalizeUrl(initialUrl) }
    val tabs = remember {
        mutableStateListOf(
            ChromeBrowserTab(url = startNormalized, title = "Google")
        )
    }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }

    var currentUrl by remember { mutableStateOf(startNormalized) }
    var inputUrl by remember { mutableStateOf(startNormalized) }
    var currentTitle by remember { mutableStateOf("JARVIS Chrome Browser") }
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isDesktopMode by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var webViewInitFailed by remember { mutableStateOf(false) }
    var latestDomText by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf<NativeWebPageSnapshot?>(null) }
    val historyStack = remember { mutableStateListOf<String>() }

    // AI Summary Dialog State
    var isSummarizing by remember { mutableStateOf(false) }
    var summaryResult by remember { mutableStateOf<String?>(null) }
    var isSummaryModalOpen by remember { mutableStateOf(false) }

    val quickBookmarks = listOf(
        "Google" to "https://www.google.com",
        "YouTube" to "https://m.youtube.com",
        "GitHub" to "https://github.com",
        "Wikipedia" to "https://en.wikipedia.org",
        "Gemini" to "https://gemini.google.com",
        "HackerNews" to "https://news.ycombinator.com",
        "StackOverflow" to "https://stackoverflow.com"
    )

    fun updateActiveTabMeta(newUrl: String? = null, newTitle: String? = null) {
        val idx = tabs.indexOfFirst { it.id == activeTabId }
        if (idx >= 0) {
            val existing = tabs[idx]
            tabs[idx] = existing.copy(
                url = newUrl ?: existing.url,
                title = newTitle?.takeIf { it.isNotBlank() } ?: existing.title
            )
        }
    }

    fun navigateToUrl(target: String, pushHistory: Boolean = true) {
        val finalUrl = NativeWebReaderEngine.normalizeUrl(target)
        focusManager.clearFocus()
        if (pushHistory && finalUrl != currentUrl) {
            historyStack.add(currentUrl)
        }
        currentUrl = finalUrl
        inputUrl = finalUrl
        updateActiveTabMeta(newUrl = finalUrl)
        webViewInstance?.let { wv ->
            runCatching { wv.loadUrl(finalUrl) }
        }
    }

    // Background snapshot for fallback / AI Page Summary
    LaunchedEffect(currentUrl) {
        val fetched = NativeWebReaderEngine.fetchAndParseUrl(currentUrl)
        snapshot = fetched
        if (currentTitle == "JARVIS Chrome Browser" || currentTitle == "New Tab") {
            currentTitle = fetched.title
            updateActiveTabMeta(newTitle = fetched.title)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webViewInstance?.stopLoading()
                webViewInstance?.destroy()
            }
        }
    }

    BackHandler {
        val wv = webViewInstance
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else if (historyStack.isNotEmpty()) {
            val prev = historyStack.removeAt(historyStack.lastIndex)
            currentUrl = prev
            inputUrl = prev
            runCatching { wv?.loadUrl(prev) }
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. CHROME MULTI-TAB & OMNIBOX HEADER
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF141A33),
                                Color(0xFF0D1224)
                            )
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                // Chrome Tab Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tabs.forEach { tab ->
                        val isSelected = tab.id == activeTabId
                        Row(
                            modifier = Modifier
                                .widthIn(min = 110.dp, max = 185.dp)
                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                                .background(
                                    if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) AccentSkyCyan.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
                                )
                                .clickable {
                                    if (activeTabId != tab.id) {
                                        activeTabId = tab.id
                                        currentUrl = tab.url
                                        inputUrl = tab.url
                                        currentTitle = tab.title
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = tab.title.ifBlank { "New Tab" },
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) TextPureWhite else TextLavenderMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (tabs.size > 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Tab",
                                    tint = TextLavenderMuted,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            val idx = tabs.indexOfFirst { it.id == tab.id }
                                            if (idx >= 0 && tabs.size > 1) {
                                                tabs.removeAt(idx)
                                                if (activeTabId == tab.id) {
                                                    val next = tabs[idx.coerceAtMost(tabs.lastIndex)]
                                                    activeTabId = next.id
                                                    currentUrl = next.url
                                                    inputUrl = next.url
                                                    currentTitle = next.title
                                                }
                                            }
                                        }
                                )
                            }
                        }
                    }

                    // + New Tab Button
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .border(1.dp, AccentSkyCyan.copy(alpha = 0.4f), CircleShape)
                            .clickable {
                                val newTab = ChromeBrowserTab(
                                    url = "https://www.google.com",
                                    title = "Google"
                                )
                                tabs.add(newTab)
                                activeTabId = newTab.id
                                currentUrl = newTab.url
                                inputUrl = newTab.url
                                currentTitle = newTab.title
                            }
                            .testTag("browser_new_tab_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Tab",
                            tint = AccentSkyCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Chrome Navigation Controls + Omnibox Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            val wv = webViewInstance
                            if (wv != null && wv.canGoBack()) {
                                wv.goBack()
                            } else if (historyStack.isNotEmpty()) {
                                val prev = historyStack.removeAt(historyStack.lastIndex)
                                currentUrl = prev
                                inputUrl = prev
                                runCatching { wv?.loadUrl(prev) }
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("browser_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AccentSkyCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val wv = webViewInstance
                            if (wv != null && wv.canGoForward()) {
                                wv.goForward()
                            }
                        },
                        enabled = canGoForward,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            tint = if (canGoForward) AccentSkyCyan else Color(0xFF475569),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = { navigateToUrl("https://www.google.com") },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("browser_home_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = RoyalPurplePrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val wv = webViewInstance
                            if (wv != null) {
                                runCatching { wv.reload() }
                            } else {
                                navigateToUrl(currentUrl, pushHistory = false)
                            }
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("browser_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint = AccentMintEmerald,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Editable Chrome Omnibox URL / Search Field
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("browser_url_input"),
                        placeholder = {
                            Text(
                                text = "Search Google or type URL...",
                                fontSize = 11.5.sp,
                                color = TextLavenderMuted,
                                maxLines = 1
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "HTTPS",
                                tint = if (currentUrl.startsWith("https")) AccentMintEmerald else AccentSkyCyan,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (inputUrl.isNotBlank()) {
                                    IconButton(
                                        onClick = { inputUrl = "" },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = TextLavenderMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(RoyalPurplePrimary, AccentSkyCyan)
                                            )
                                        )
                                        .clickable { navigateToUrl(inputUrl) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                        .testTag("browser_go_btn")
                                ) {
                                    Text(
                                        text = "GO",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigateToUrl(inputUrl) }),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentSkyCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                            focusedContainerColor = Color(0xFF0B1424),
                            unfocusedContainerColor = Color(0xFF0B1424),
                            cursorColor = AccentSkyCyan
                        )
                    )

                    // Desktop Site Toggle
                    IconButton(
                        onClick = {
                            isDesktopMode = !isDesktopMode
                            webViewInstance?.let { wv ->
                                runCatching {
                                    wv.settings.userAgentString = if (isDesktopMode) DESKTOP_CHROME_UA else MOBILE_CHROME_UA
                                    wv.settings.useWideViewPort = true
                                    wv.settings.loadWithOverviewMode = true
                                    wv.reload()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("browser_desktop_mode_btn")
                    ) {
                        Icon(
                            imageVector = if (isDesktopMode) Icons.Default.DesktopWindows else Icons.Default.PhoneAndroid,
                            contentDescription = "Toggle Desktop Mode",
                            tint = if (isDesktopMode) AccentSkyCyan else TextLavenderMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // External Chrome Browser Button
                    IconButton(
                        onClick = {
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                context.startActivity(browserIntent)
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("browser_open_external_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open in External Browser",
                            tint = AccentChampagneGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quick Bookmarks Strip
                val chipAccents = listOf(
                    AccentSkyCyan,
                    AccentRosePink,
                    AccentMintEmerald,
                    RoyalPurplePrimary,
                    AccentChampagneGold,
                    RoyalIndigoAccent
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickBookmarks.forEachIndexed { idx, (name, url) ->
                        val accent = chipAccents[idx % chipAccents.size]
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(accent.copy(alpha = 0.16f))
                                .border(1.dp, accent.copy(alpha = 0.50f), RoundedCornerShape(999.dp))
                                .clickable {
                                    inputUrl = url
                                    navigateToUrl(url)
                                }
                                .padding(horizontal = 11.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Loading Progress Bar
            if (isLoading || loadProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { (loadProgress.coerceIn(5, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentSkyCyan,
                    trackColor = Color(0xFF0F1A2E)
                )
            }

            // 2. REAL INTERACTIVE CHROME-GRADE ANDROID WEBVIEW
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("jarvis_browser_webview")
            ) {
                if (!webViewInitFailed) {
                    key(activeTabId) {
                        AndroidView(
                            factory = { ctx ->
                                try {
                                    WebView(ctx).apply {
                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            databaseEnabled = true
                                            allowFileAccess = true
                                            allowContentAccess = true
                                            mediaPlaybackRequiresUserGesture = false
                                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            useWideViewPort = true
                                            loadWithOverviewMode = true
                                            setSupportZoom(true)
                                            builtInZoomControls = true
                                            displayZoomControls = false
                                            javaScriptCanOpenWindowsAutomatically = true
                                            userAgentString = if (isDesktopMode) DESKTOP_CHROME_UA else MOBILE_CHROME_UA
                                        }
                                        runCatching {
                                            CookieManager.getInstance().setAcceptCookie(true)
                                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                        }
                                        setDownloadListener { url, _, _, _, _ ->
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            }
                                        }
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                super.onPageStarted(view, url, favicon)
                                                isLoading = true
                                                if (!url.isNullOrBlank()) {
                                                    currentUrl = url
                                                    inputUrl = url
                                                    updateActiveTabMeta(newUrl = url)
                                                }
                                                canGoBack = view?.canGoBack() == true
                                                canGoForward = view?.canGoForward() == true
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                isLoading = false
                                                loadProgress = 100
                                                if (!url.isNullOrBlank()) {
                                                    currentUrl = url
                                                    inputUrl = url
                                                }
                                                val pageTitle = view?.title?.takeIf { it.isNotBlank() }
                                                if (pageTitle != null) {
                                                    currentTitle = pageTitle
                                                    updateActiveTabMeta(newUrl = url, newTitle = pageTitle)
                                                }
                                                canGoBack = view?.canGoBack() == true
                                                canGoForward = view?.canGoForward() == true
                                                view?.evaluateJavascript(
                                                    "(function(){return document.body ? document.body.innerText.substring(0, 3000) : '';})()"
                                                ) { rawJs ->
                                                    val cleaned = rawJs
                                                        ?.removeSurrounding("\"")
                                                        ?.replace("\\n", " ")
                                                        ?.replace("\\\"", "\"")
                                                        ?.trim()
                                                        .orEmpty()
                                                    if (cleaned.isNotBlank() && cleaned != "null") {
                                                        latestDomText = cleaned
                                                    }
                                                }
                                            }

                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                val targetUri = request?.url ?: return false
                                                val scheme = targetUri.scheme?.lowercase().orEmpty()
                                                if (scheme in setOf("http", "https", "file", "data", "about", "javascript")) {
                                                    return false
                                                }
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, targetUri))
                                                }
                                                return true
                                            }
                                        }
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                loadProgress = newProgress
                                                isLoading = newProgress in 1..99
                                            }

                                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                                if (!title.isNullOrBlank()) {
                                                    currentTitle = title
                                                    updateActiveTabMeta(newTitle = title)
                                                }
                                            }
                                        }
                                        loadUrl(currentUrl)
                                        webViewInstance = this
                                    }
                                } catch (e: Throwable) {
                                    webViewInitFailed = true
                                    android.view.View(ctx)
                                }
                            },
                            update = { view ->
                                if (view is WebView) {
                                    webViewInstance = view
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    NativeWebSnapshotContent(
                        snapshot = snapshot,
                        onNavigateLink = { linkUrl -> navigateToUrl(linkUrl) },
                        onOpenExternal = { extUrl ->
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(extUrl)))
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 3. BOTTOM AI FLOATING ACTION DOCK
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xEE11162B))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // AI Summarize Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(RoyalPurplePrimary, AccentRosePink)
                            )
                        )
                        .clickable {
                            val clean = latestDomText.ifBlank { snapshot?.rawPlainText.orEmpty() }.take(1800)
                            if (clean.isNotBlank()) {
                                isSummarizing = true
                                isSummaryModalOpen = true
                                coroutineScope.launch {
                                    summaryResult = onSummarizeContent(clean)
                                    isSummarizing = false
                                }
                            }
                        }
                        .padding(vertical = 10.dp)
                        .testTag("browser_ai_summarize_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "AI Page Summary",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Read Aloud TTS Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(AccentSkyCyan, AccentMintEmerald)
                            )
                        )
                        .clickable {
                            val clean = latestDomText.ifBlank { snapshot?.rawPlainText.orEmpty() }.take(450)
                            if (clean.isNotBlank()) {
                                JarvisTtsManager.speak(context, "Page content: $clean")
                            }
                        }
                        .padding(vertical = 10.dp)
                        .testTag("browser_read_aloud_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Listen / Read Aloud",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 4. FLOATING AI SUMMARY BOTTOM SHEET
        AnimatedVisibility(
            visible = isSummaryModalOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            JarvisGlassPanel(
                accentColor = RoyalPurplePrimary,
                cornerRadius = 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AccentSkyCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Neural Page Intelligence",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        IconButton(
                            onClick = { isSummaryModalOpen = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (isSummarizing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = AccentSkyCyan,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Analyzing page content...",
                                color = TextLavenderMuted,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        val summary = summaryResult ?: "No summary available."
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F172A))
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                                lineHeight = 18.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(summary))
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = AccentSkyCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(RoyalPurplePrimary, AccentRosePink)
                                        )
                                    )
                                    .clickable {
                                        JarvisTtsManager.speak(context, summary)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Speak Summary",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
