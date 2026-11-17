package com.jarvisai.assistant.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.jarvisai.assistant.browser.BrowserAdBlockEngine
import com.jarvisai.assistant.browser.BrowserDataStore
import com.jarvisai.assistant.browser.BrowserIdentity
import com.jarvisai.assistant.browser.BrowserSessionManager
import com.jarvisai.assistant.browser.ErudaHelper
import com.jarvisai.assistant.browser.NativeWebPageSnapshot
import com.jarvisai.assistant.browser.NativeWebReaderEngine
import com.jarvisai.assistant.browser.NativeWebSnapshotContent
import com.jarvisai.assistant.browser.PlaylistMediaItem
import com.jarvisai.assistant.extension.BrowserExtension
import com.jarvisai.assistant.extension.BrowserExtensionManager
import com.jarvisai.assistant.extension.CrxExtensionInstaller
import com.jarvisai.assistant.extension.ExtensionPageInjector
import com.jarvisai.assistant.service.JarvisTtsManager
import com.jarvisai.assistant.ui.screens.browser.AddSpeedDialDialog
import com.jarvisai.assistant.ui.screens.browser.BrowserOverlayMode
import com.jarvisai.assistant.ui.screens.browser.BrowserSettingsScreen
import com.jarvisai.assistant.ui.screens.browser.ChromeWebStoreInstallBanner
import com.jarvisai.assistant.ui.screens.browser.DeveloperExtensionsScreen
import com.jarvisai.assistant.ui.screens.browser.ExtensionDetailsDialog
import com.jarvisai.assistant.ui.screens.browser.ExtensionPopupSheet
import com.jarvisai.assistant.ui.screens.browser.ExtensionWorkerInspectorDialog
import com.jarvisai.assistant.ui.screens.browser.FindInPageBar
import com.jarvisai.assistant.ui.screens.browser.LibraryTabType
import com.jarvisai.assistant.ui.screens.browser.ManageExtensionsScreen
import com.jarvisai.assistant.ui.screens.browser.OpenInExternalAppDialog
import com.jarvisai.assistant.ui.screens.browser.PlaylistMediaPlayerOverlay
import com.jarvisai.assistant.ui.screens.browser.QuettaBottomMenuSheet
import com.jarvisai.assistant.ui.screens.browser.QuettaBottomOmniboxDock
import com.jarvisai.assistant.ui.screens.browser.QuettaColors
import com.jarvisai.assistant.ui.screens.browser.QuettaExtensionsQuickSheet
import com.jarvisai.assistant.ui.screens.browser.QuettaFocusedOmniboxOverlay
import com.jarvisai.assistant.ui.screens.browser.QuettaLibraryScreen
import com.jarvisai.assistant.ui.screens.browser.QuettaStartPage
import com.jarvisai.assistant.ui.screens.browser.QuettaTab
import com.jarvisai.assistant.ui.screens.browser.BrowserTabSwitcherScreen
import com.jarvisai.assistant.ui.screens.browser.ReaderModeOverlay
import com.jarvisai.assistant.ui.screens.browser.TabSwitcherMode
import com.jarvisai.assistant.ui.screens.browser.TranslateLanguageDialog
import com.jarvisai.assistant.ui.screens.browser.captureWebViewThumbnail
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File

private const val QUETTA_MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
private const val QUETTA_DESKTOP_UA =
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
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        BrowserDataStore.init(context)
        BrowserExtensionManager.init(context)
        BrowserSessionManager.instance.init(context)
    }

    val startOnHome = initialUrl == "https://www.google.com" || initialUrl.isBlank()
    val startNormalized = remember(initialUrl) { NativeWebReaderEngine.normalizeUrl(initialUrl) }

    val tabs = remember {
        mutableStateListOf(
            QuettaTab(
                url = startNormalized,
                title = if (startOnHome) "Start Page" else "Loading...",
                isHomePage = startOnHome
            )
        )
    }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }
    val webViews = remember { mutableStateMapOf<String, WebView>() }
    var webViewInitFailed by remember { mutableStateOf(false) }

    val activeTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
    val activeWebView = webViews[activeTab.id]

    // Collected Store State
    val prefs by BrowserDataStore.preferences.collectAsState()
    val speedDials by BrowserDataStore.speedDials.collectAsState()
    val bookmarks by BrowserDataStore.bookmarks.collectAsState()
    val history by BrowserDataStore.history.collectAsState()
    val tabGroups by BrowserDataStore.tabGroups.collectAsState()
    val extensions by BrowserExtensionManager.extensions.collectAsState()

    // Interactive Browser States
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var omniboxFocused by remember { mutableStateOf(false) }
    var overlayMode by remember { mutableStateOf(BrowserOverlayMode.NONE) }
    var libraryInitialTab by remember { mutableStateOf(LibraryTabType.FAVORITES) }
    var findInPageOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findMatchSummary by remember { mutableStateOf("") }

    var showAddSpeedDial by remember { mutableStateOf(false) }
    var pendingExternalIntentUri by remember { mutableStateOf<Uri?>(null) }
    var selectedExtension by remember { mutableStateOf<BrowserExtension?>(null) }
    var selectedPlaylistMedia by remember { mutableStateOf<PlaylistMediaItem?>(null) }
    var isWebStoreInstalling by remember { mutableStateOf(false) }

    var latestDomText by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf<NativeWebPageSnapshot?>(null) }
    var isSummarizing by remember { mutableStateOf(false) }
    var summaryResult by remember { mutableStateOf<String?>(null) }
    var isSummaryModalOpen by remember { mutableStateOf(false) }
    var toastBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toastBanner) {
        if (toastBanner != null) {
            delay(2600L)
            toastBanner = null
        }
    }

    fun updateTab(tabId: String, transform: (QuettaTab) -> QuettaTab) {
        val idx = tabs.indexOfFirst { it.id == tabId }
        if (idx >= 0) {
            tabs[idx] = transform(tabs[idx])
        }
    }

    fun captureThumbnailForActiveTab() {
        val current = tabs.firstOrNull { it.id == activeTabId } ?: return
        if (!current.isHomePage) {
            val bmp = captureWebViewThumbnail(webViews[current.id])
            if (bmp != null) {
                updateTab(current.id) { it.copy(thumbnail = bmp) }
            }
        }
    }

    fun navigateInActiveTab(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.equals("jarvis://extensions", ignoreCase = true)) {
            omniboxFocused = false
            overlayMode = BrowserOverlayMode.EXTENSIONS_MANAGE
            return
        }
        val finalUrl = BrowserDataStore.resolveOmniboxInput(trimmed)
        omniboxFocused = false
        val targetId = activeTab.id
        updateTab(targetId) {
            it.copy(
                url = finalUrl,
                isHomePage = false,
                updatedAt = System.currentTimeMillis()
            )
        }
        webViews[targetId]?.loadUrl(finalUrl)
    }

    fun openNewTab(
        url: String? = null,
        isPrivate: Boolean = false,
        groupId: String? = null
    ) {
        captureThumbnailForActiveTab()
        val isHome = url.isNullOrBlank()
        val targetUrl = if (isHome) "https://www.google.com" else BrowserDataStore.resolveOmniboxInput(url!!)
        val created = QuettaTab(
            url = targetUrl,
            title = if (isHome) "Start Page" else BrowserAdBlockEngine.extractHost(targetUrl),
            isPrivate = isPrivate,
            isHomePage = isHome,
            desktopMode = prefs.desktopModeDefault,
            groupId = groupId
        )
        tabs.add(created)
        activeTabId = created.id
        overlayMode = BrowserOverlayMode.NONE
    }

    fun closeTab(tabId: String) {
        val wv = webViews.remove(tabId)
        runCatching {
            wv?.stopLoading()
            wv?.destroy()
        }
        BrowserAdBlockEngine.resetTabCount(tabId)
        BrowserSessionManager.instance.unbindLiveTab(tabId)

        val idx = tabs.indexOfFirst { it.id == tabId }
        if (idx >= 0) {
            if (tabs.size == 1) {
                // Reset last tab to clean Start Page
                val replacement = QuettaTab(isPrivate = tabs[0].isPrivate, isHomePage = true)
                tabs[0] = replacement
                activeTabId = replacement.id
            } else {
                tabs.removeAt(idx)
                if (activeTabId == tabId) {
                    val next = tabs[idx.coerceAtMost(tabs.lastIndex)]
                    activeTabId = next.id
                }
            }
        }
    }

    // Sync active tab settings (Darkening, JavaScript, SessionManager)
    LaunchedEffect(activeTab.id, activeTab.url, activeTab.isHomePage, prefs.forceDarkPages, prefs.javaScriptEnabled) {
        val wv = webViews[activeTab.id]
        if (wv != null) {
            runCatching {
                wv.settings.javaScriptEnabled = prefs.javaScriptEnabled
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, prefs.forceDarkPages)
                }
            }
        }
        BrowserSessionManager.instance.bindLiveWebViewTab(
            tabId = activeTab.id,
            url = activeTab.url,
            title = activeTab.title,
            webView = wv,
            desktopMode = activeTab.desktopMode
        )
        if (!activeTab.isHomePage && activeTab.url.startsWith("http")) {
            snapshot = NativeWebReaderEngine.fetchAndParseUrl(activeTab.url)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViews.values.forEach { wv ->
                runCatching {
                    wv.stopLoading()
                    wv.destroy()
                }
            }
            webViews.clear()
        }
    }

    BackHandler {
        when {
            overlayMode == BrowserOverlayMode.EXTENSION_DETAILS ||
                overlayMode == BrowserOverlayMode.WORKER_INSPECTOR -> {
                overlayMode = BrowserOverlayMode.EXTENSIONS_DEV
            }
            overlayMode == BrowserOverlayMode.EXTENSIONS_DEV -> {
                overlayMode = BrowserOverlayMode.EXTENSIONS_MANAGE
            }
            overlayMode == BrowserOverlayMode.EXTENSIONS_MANAGE -> {
                overlayMode = BrowserOverlayMode.EXTENSIONS_QUICK
            }
            overlayMode == BrowserOverlayMode.EXTENSION_POPUP -> {
                overlayMode = BrowserOverlayMode.NONE
            }
            overlayMode != BrowserOverlayMode.NONE -> {
                overlayMode = BrowserOverlayMode.NONE
            }
            omniboxFocused -> omniboxFocused = false
            findInPageOpen -> {
                findInPageOpen = false
                activeWebView?.clearMatches()
            }
            !activeTab.isHomePage && activeWebView?.canGoBack() == true -> {
                activeWebView.goBack()
            }
            !activeTab.isHomePage -> {
                updateTab(activeTab.id) { it.copy(isHomePage = true, title = "Start Page") }
            }
            else -> onBack()
        }
    }

    val detectedWebStoreId = remember(activeTab.url, activeTab.isHomePage) {
        if (activeTab.isHomePage) null else CrxExtensionInstaller.extractChromeWebStoreId(activeTab.url)
    }

    val groupTabs = remember(tabs.toList(), activeTab.groupId) {
        val gid = activeTab.groupId
        if (gid == null) emptyList() else tabs.filter { it.groupId == gid }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (activeTab.isPrivate) Color(0xFF0E0C14) else QuettaColors.BgDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Find on Page Bar (top overlay strip only when active)
            if (findInPageOpen && !activeTab.isHomePage) {
                Box(modifier = Modifier.statusBarsPadding()) {
                    FindInPageBar(
                        query = findQuery,
                        matchSummary = findMatchSummary,
                        onQueryChange = { q ->
                            findQuery = q
                            if (q.isBlank()) {
                                activeWebView?.clearMatches()
                                findMatchSummary = ""
                            } else {
                                activeWebView?.findAllAsync(q)
                            }
                        },
                        onPrev = { activeWebView?.findNext(false) },
                        onNext = { activeWebView?.findNext(true) },
                        onClose = {
                            findInPageOpen = false
                            findQuery = ""
                            findMatchSummary = ""
                            activeWebView?.clearMatches()
                        }
                    )
                }
            }

            // Main Viewport: Start Page OR Clean Full-Screen WebView
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("jarvis_browser_webview")
            ) {
                if (activeTab.isHomePage) {
                    QuettaStartPage(
                        isPrivate = activeTab.isPrivate,
                        speedDials = speedDials,
                        onExitBrowser = onBack,
                        onClickSpeedDial = { dial ->
                            when (dial.specialAction) {
                                "EXTENSIONS" -> overlayMode = BrowserOverlayMode.EXTENSIONS_QUICK
                                else -> navigateInActiveTab(dial.url)
                            }
                        },
                        onDeleteSpeedDial = { BrowserDataStore.removeSpeedDial(it) },
                        onAddSpeedDialClick = { showAddSpeedDial = true }
                    )
                } else if (!webViewInitFailed) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (!findInPageOpen) Modifier.statusBarsPadding() else Modifier)
                    ) {
                        key(activeTab.id) {
                            AndroidView(
                                factory = { ctx ->
                                    val existing = webViews[activeTab.id]
                                    if (existing != null) {
                                        (existing.parent as? ViewGroup)?.removeView(existing)
                                        existing
                                    } else {
                                        try {
                                            WebView(ctx).apply {
                                                settings.apply {
                                                    javaScriptEnabled = prefs.javaScriptEnabled
                                                    domStorageEnabled = !activeTab.isPrivate
                                                    databaseEnabled = !activeTab.isPrivate
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
                                                    cacheMode = if (activeTab.isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                                                    userAgentString = if (activeTab.desktopMode) QUETTA_DESKTOP_UA else QUETTA_MOBILE_UA
                                                    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                                                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, prefs.forceDarkPages)
                                                    }
                                                }
                                                runCatching {
                                                    CookieManager.getInstance().setAcceptCookie(!activeTab.isPrivate)
                                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, !activeTab.isPrivate)
                                                }
                                                addJavascriptInterface(
                                                    ExtensionPageInjector.PageExtensionBridge(context),
                                                    "__jarvis_ext_page_bridge"
                                                )
                                                setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                                                    findMatchSummary = if (numberOfMatches == 0) "0/0" else "${activeMatchOrdinal + 1}/$numberOfMatches"
                                                }
                                                setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                                                    val dl = BrowserDataStore.enqueueSystemDownload(context, url, contentDisposition, mimeType)
                                                    toastBanner = "Downloading ${dl.fileName}"
                                                }
                                                webViewClient = object : WebViewClient() {
                                                    override fun shouldInterceptRequest(
                                                        view: WebView?,
                                                        request: WebResourceRequest?
                                                    ): WebResourceResponse? {
                                                        val reqUrl = request?.url?.toString()
                                                        if (BrowserAdBlockEngine.shouldBlockRequest(reqUrl, prefs.adBlockEnabled)) {
                                                            val count = BrowserAdBlockEngine.recordBlocked(activeTab.id)
                                                            view?.post {
                                                                updateTab(activeTab.id) { it.copy(blockedCount = count) }
                                                            }
                                                            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                                                        }
                                                        return super.shouldInterceptRequest(view, request)
                                                    }

                                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                        super.onPageStarted(view, url, favicon)
                                                        isLoading = true
                                                        if (!url.isNullOrBlank()) {
                                                            updateTab(activeTab.id) {
                                                                it.copy(
                                                                    url = url,
                                                                    isHomePage = false,
                                                                    canGoBack = view?.canGoBack() == true,
                                                                    canGoForward = view?.canGoForward() == true
                                                                )
                                                            }
                                                        }
                                                        if (!BrowserIdentity.isGoogleSignInUrl(url)) {
                                                            ExtensionPageInjector.injectForPage(
                                                                webView = view,
                                                                url = url,
                                                                extensions = BrowserExtensionManager.extensions.value,
                                                                runAt = "document_start"
                                                            )
                                                        }
                                                    }

                                                    override fun onPageFinished(view: WebView?, url: String?) {
                                                        super.onPageFinished(view, url)
                                                        isLoading = false
                                                        loadProgress = 100
                                                        val pageTitle = view?.title?.takeIf { it.isNotBlank() }
                                                        if (!url.isNullOrBlank()) {
                                                            updateTab(activeTab.id) {
                                                                it.copy(
                                                                    url = url,
                                                                    title = pageTitle ?: it.title,
                                                                    isHomePage = false,
                                                                    canGoBack = view?.canGoBack() == true,
                                                                    canGoForward = view?.canGoForward() == true,
                                                                    updatedAt = System.currentTimeMillis()
                                                                )
                                                            }
                                                            BrowserDataStore.recordHistory(
                                                                title = pageTitle ?: BrowserAdBlockEngine.extractHost(url),
                                                                url = url,
                                                                isPrivate = activeTab.isPrivate
                                                            )
                                                        }
                                                        if (!BrowserIdentity.isGoogleSignInUrl(url)) {
                                                            ExtensionPageInjector.injectForPage(
                                                                webView = view,
                                                                url = url,
                                                                extensions = BrowserExtensionManager.extensions.value,
                                                                runAt = "document_idle"
                                                            )
                                                        }
                                                        view?.evaluateJavascript(
                                                            "(function(){return document.body ? document.body.innerText.substring(0, 3500) : '';})()"
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
                                                        if (prefs.askBeforeOpeningApp) {
                                                            pendingExternalIntentUri = targetUri
                                                        } else {
                                                            runCatching {
                                                                context.startActivity(Intent(Intent.ACTION_VIEW, targetUri))
                                                            }
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
                                                            updateTab(activeTab.id) { it.copy(title = title) }
                                                        }
                                                    }
                                                }
                                                webViews[activeTab.id] = this
                                                loadUrl(activeTab.url)
                                            }
                                        } catch (e: Throwable) {
                                            webViewInitFailed = true
                                            android.view.View(ctx)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    NativeWebSnapshotContent(
                        snapshot = snapshot,
                        onNavigateLink = { linkUrl -> navigateInActiveTab(linkUrl) },
                        onOpenExternal = { extUrl ->
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(extUrl))) }
                        },
                        modifier = Modifier.fillMaxSize().statusBarsPadding()
                    )
                }
            }

            // Chrome Web Store 1-Tap Extension Install Banner
            if (detectedWebStoreId != null) {
                val installed = extensions.any { it.id == detectedWebStoreId }
                ChromeWebStoreInstallBanner(
                    extensionId = detectedWebStoreId,
                    isAlreadyInstalled = installed,
                    isInstalling = isWebStoreInstalling,
                    onInstallClick = {
                        isWebStoreInstalling = true
                        coroutineScope.launch {
                            val res = BrowserExtensionManager.installFromChromeWebStore(context, detectedWebStoreId)
                            isWebStoreInstalling = false
                            toastBanner = res.fold(
                                onSuccess = { "Extension '${it.name}' installed!" },
                                onFailure = { "Install failed: ${it.message}" }
                            )
                        }
                    },
                    onOpenExtensions = { overlayMode = BrowserOverlayMode.EXTENSIONS_DEV }
                )
            }

            // Bottom Quetta Omnibox Dock (`[ 🏠 | 🛡️ URL | [N] | ⋮ ]`)
            QuettaBottomOmniboxDock(
                activeTab = activeTab,
                tabCount = tabs.count { it.isPrivate == activeTab.isPrivate },
                isLoading = isLoading,
                loadProgress = loadProgress,
                groupTabs = groupTabs,
                onHomeClick = {
                    captureThumbnailForActiveTab()
                    updateTab(activeTab.id) { it.copy(isHomePage = true, title = "Start Page") }
                },
                onOmniboxTap = { omniboxFocused = true },
                onTabsClick = {
                    captureThumbnailForActiveTab()
                    overlayMode = BrowserOverlayMode.TAB_SWITCHER
                },
                onMenuClick = { overlayMode = BrowserOverlayMode.MENU },
                onSelectGroupTab = { gidTabId ->
                    captureThumbnailForActiveTab()
                    activeTabId = gidTabId
                },
                onAddTabToGroup = { openNewTab(groupId = activeTab.groupId) }
            )
        }

        // Focused Omnibox Overlay (Search + Suggestions + Search Engine Switcher)
        if (omniboxFocused) {
            Box(modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
                QuettaFocusedOmniboxOverlay(
                    initialQuery = if (activeTab.isHomePage) "" else activeTab.url,
                    searchEngine = prefs.searchEngine,
                    history = history,
                    speedDials = speedDials,
                    onSelectSearchEngine = { engine ->
                        BrowserDataStore.updatePreferences { it.copy(searchEngine = engine) }
                    },
                    onSubmitUrlOrSearch = { input -> navigateInActiveTab(input) },
                    onDismiss = { omniboxFocused = false }
                )
            }
        }

        // Toast Feedback Pill
        AnimatedVisibility(
            visible = toastBanner != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(QuettaColors.SurfaceElevated)
                    .border(1.dp, QuettaColors.Coral.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(toastBanner.orEmpty(), color = QuettaColors.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Quetta Bottom Menu Sheet
        if (overlayMode == BrowserOverlayMode.MENU) {
            QuettaBottomMenuSheet(
                activeTab = activeTab,
                isBookmarked = !activeTab.isHomePage && BrowserDataStore.isBookmarked(activeTab.url),
                onDismiss = { overlayMode = BrowserOverlayMode.NONE },
                onOpenLibrary = { libTab ->
                    libraryInitialTab = libTab
                    overlayMode = BrowserOverlayMode.LIBRARY
                },
                onOpenExtensions = { overlayMode = BrowserOverlayMode.EXTENSIONS_QUICK },
                onOpenSettings = { overlayMode = BrowserOverlayMode.SETTINGS },
                onToggleBookmark = {
                    val added = BrowserDataStore.toggleBookmark(activeTab.title, activeTab.url)
                    toastBanner = if (added) "Saved to Favorites" else "Removed from Favorites"
                },
                onToggleDesktopMode = {
                    val nextDesktop = !activeTab.desktopMode
                    updateTab(activeTab.id) { it.copy(desktopMode = nextDesktop) }
                    activeWebView?.let { wv ->
                        wv.settings.userAgentString = if (nextDesktop) QUETTA_DESKTOP_UA else QUETTA_MOBILE_UA
                        wv.reload()
                    }
                    overlayMode = BrowserOverlayMode.NONE
                    toastBanner = if (nextDesktop) "Desktop Site enabled" else "Mobile Site enabled"
                },
                onFindInPage = {
                    overlayMode = BrowserOverlayMode.NONE
                    findInPageOpen = true
                },
                onShowReaderMode = { overlayMode = BrowserOverlayMode.READER_MODE },
                onTranslatePage = { overlayMode = BrowserOverlayMode.TRANSLATE_PICKER },
                onAddToSpeedDial = {
                    overlayMode = BrowserOverlayMode.NONE
                    showAddSpeedDial = true
                },
                onAddToPlaylist = {
                    overlayMode = BrowserOverlayMode.NONE
                    val wv = activeWebView
                    if (wv != null) {
                        wv.evaluateJavascript(
                            "(function(){var v=document.querySelector('video,audio');return v?(v.currentSrc||v.src||''):'';})()"
                        ) { raw ->
                            val mediaSrc = raw?.removeSurrounding("\"")?.takeIf { it.startsWith("http") }
                            BrowserDataStore.addToPlaylist(
                                title = activeTab.title,
                                mediaUrl = mediaSrc ?: activeTab.url,
                                pageUrl = activeTab.url
                            )
                            toastBanner = "Added to Watch Later Playlist"
                        }
                    } else {
                        BrowserDataStore.addToPlaylist(activeTab.title, activeTab.url, activeTab.url)
                        toastBanner = "Added to Watch Later Playlist"
                    }
                },
                onToggleDevTools = {
                    overlayMode = BrowserOverlayMode.NONE
                    ErudaHelper.toggle(activeWebView)
                },
                onSummarizePage = {
                    val clean = latestDomText.ifBlank { snapshot?.rawPlainText.orEmpty() }.take(2000)
                    if (clean.isNotBlank()) {
                        isSummarizing = true
                        isSummaryModalOpen = true
                        coroutineScope.launch {
                            summaryResult = onSummarizeContent(clean)
                            isSummarizing = false
                        }
                    } else {
                        toastBanner = "Open a webpage first to summarize"
                    }
                },
                onReadAloud = {
                    val clean = latestDomText.ifBlank { snapshot?.rawPlainText.orEmpty() }.take(500)
                    if (clean.isNotBlank()) {
                        JarvisTtsManager.speak(context, clean)
                    } else {
                        toastBanner = "No readable text found on page"
                    }
                },
                onCopyEmailMask = { alias -> toastBanner = "Copied mask: $alias" },
                onGoBack = { activeWebView?.goBack() },
                onGoForward = { activeWebView?.goForward() },
                onReload = { activeWebView?.reload() },
                onTogglePrivateTab = { openNewTab(isPrivate = !activeTab.isPrivate) }
            )
        }

        // Tab Switcher Overlay
        if (overlayMode == BrowserOverlayMode.TAB_SWITCHER) {
            BrowserTabSwitcherScreen(
                tabs = tabs,
                activeTabId = activeTab.id,
                tabGroups = tabGroups,
                initialMode = if (activeTab.isPrivate) TabSwitcherMode.PRIVATE else TabSwitcherMode.NORMAL,
                onSelectTab = { id ->
                    activeTabId = id
                    overlayMode = BrowserOverlayMode.NONE
                },
                onCloseTab = { id -> closeTab(id) },
                onNewTab = { priv, gid -> openNewTab(isPrivate = priv, groupId = gid) },
                onCloseAllInMode = { priv ->
                    val matching = tabs.filter { it.isPrivate == priv }.map { it.id }
                    matching.forEach { closeTab(it) }
                },
                onCreateGroup = { title, tabIds ->
                    val grp = BrowserDataStore.createOrUpdateTabGroup(title)
                    tabIds.forEach { tid -> updateTab(tid) { it.copy(groupId = grp.id) } }
                },
                onAssignTabToGroup = { tid, gid -> updateTab(tid) { it.copy(groupId = gid) } },
                onDeleteGroup = { gid ->
                    tabs.filter { it.groupId == gid }.forEach { t -> updateTab(t.id) { it.copy(groupId = null) } }
                    BrowserDataStore.removeTabGroup(gid)
                },
                onDismiss = { overlayMode = BrowserOverlayMode.NONE }
            )
        }

        // 4-Tab Library Sheet
        if (overlayMode == BrowserOverlayMode.LIBRARY) {
            QuettaLibraryScreen(
                initialTab = libraryInitialTab,
                activeTab = activeTab,
                onOpenUrl = { url ->
                    overlayMode = BrowserOverlayMode.NONE
                    navigateInActiveTab(url)
                },
                onSaveOfflinePage = {
                    val wv = activeWebView
                    if (wv != null && !activeTab.isHomePage) {
                        val offlineDir = File(context.filesDir, "offline_pages").apply { mkdirs() }
                        val archiveFile = File(offlineDir, "page_${System.currentTimeMillis()}.mht")
                        wv.saveWebArchive(archiveFile.absolutePath, false) { savedPath ->
                            if (!savedPath.isNullOrBlank()) {
                                BrowserDataStore.recordOfflinePage(activeTab.title, activeTab.url, File(savedPath))
                                toastBanner = "Saved offline page"
                            }
                        }
                    }
                },
                onPlayMediaItem = { media ->
                    selectedPlaylistMedia = media
                    overlayMode = BrowserOverlayMode.PLAYLIST_PLAYER
                },
                onClose = { overlayMode = BrowserOverlayMode.NONE }
            )
        }

        // Extensions Quick Sheet -> Manage -> Developer -> Popup / Worker / Details
        if (overlayMode == BrowserOverlayMode.EXTENSIONS_QUICK) {
            QuettaExtensionsQuickSheet(
                extensions = extensions,
                onDismiss = { overlayMode = BrowserOverlayMode.NONE },
                onOpenExtensionPopup = { ext ->
                    selectedExtension = ext
                    overlayMode = if (ext.popupFile.isNotBlank()) BrowserOverlayMode.EXTENSION_POPUP else BrowserOverlayMode.EXTENSION_DETAILS
                },
                onOpenManage = { overlayMode = BrowserOverlayMode.EXTENSIONS_MANAGE },
                onOpenDeveloper = { overlayMode = BrowserOverlayMode.EXTENSIONS_DEV },
                onOpenWebStore = {
                    overlayMode = BrowserOverlayMode.NONE
                    navigateInActiveTab("https://chromewebstore.google.com/")
                }
            )
        }

        if (overlayMode == BrowserOverlayMode.EXTENSIONS_MANAGE) {
            ManageExtensionsScreen(
                extensions = extensions,
                onBack = { overlayMode = BrowserOverlayMode.EXTENSIONS_QUICK },
                onOpenDeveloperOptions = { overlayMode = BrowserOverlayMode.EXTENSIONS_DEV },
                onOpenPopup = { ext ->
                    selectedExtension = ext
                    overlayMode = BrowserOverlayMode.EXTENSION_POPUP
                },
                onShowDetails = { ext ->
                    selectedExtension = ext
                    overlayMode = BrowserOverlayMode.EXTENSION_DETAILS
                }
            )
        }

        if (overlayMode == BrowserOverlayMode.EXTENSIONS_DEV) {
            DeveloperExtensionsScreen(
                extensions = extensions,
                onBack = { overlayMode = BrowserOverlayMode.EXTENSIONS_MANAGE },
                onInspectWorker = { ext ->
                    selectedExtension = ext
                    overlayMode = BrowserOverlayMode.WORKER_INSPECTOR
                },
                onShowDetails = { ext ->
                    selectedExtension = ext
                    overlayMode = BrowserOverlayMode.EXTENSION_DETAILS
                },
                onOpenPopup = { ext ->
                    selectedExtension = ext
                    overlayMode = BrowserOverlayMode.EXTENSION_POPUP
                }
            )
        }

        if (overlayMode == BrowserOverlayMode.EXTENSION_POPUP && selectedExtension != null) {
            ExtensionPopupSheet(
                extension = selectedExtension!!,
                activeTabId = activeTab.id,
                activeTabUrl = activeTab.url,
                activeTabTitle = activeTab.title,
                onEvalInActiveTab = { code -> activeWebView?.post { activeWebView.evaluateJavascript(code, null) } },
                onOpenNewTab = { url -> openNewTab(url = url) },
                onDismiss = { overlayMode = BrowserOverlayMode.NONE }
            )
        }

        if (overlayMode == BrowserOverlayMode.WORKER_INSPECTOR && selectedExtension != null) {
            ExtensionWorkerInspectorDialog(
                extension = selectedExtension!!,
                onDismiss = { overlayMode = BrowserOverlayMode.EXTENSIONS_DEV }
            )
        }

        if (overlayMode == BrowserOverlayMode.EXTENSION_DETAILS && selectedExtension != null) {
            ExtensionDetailsDialog(
                extension = selectedExtension!!,
                onDismiss = { overlayMode = BrowserOverlayMode.EXTENSIONS_DEV }
            )
        }

        if (overlayMode == BrowserOverlayMode.SETTINGS) {
            BrowserSettingsScreen(
                onBack = { overlayMode = BrowserOverlayMode.NONE },
                onOpenDeveloperExtensions = { overlayMode = BrowserOverlayMode.EXTENSIONS_DEV }
            )
        }

        if (overlayMode == BrowserOverlayMode.READER_MODE) {
            ReaderModeOverlay(
                title = activeTab.title,
                url = activeTab.url,
                domText = latestDomText,
                snapshot = snapshot,
                onReadAloud = { text -> JarvisTtsManager.speak(context, text) },
                onClose = { overlayMode = BrowserOverlayMode.NONE }
            )
        }

        if (overlayMode == BrowserOverlayMode.PLAYLIST_PLAYER && selectedPlaylistMedia != null) {
            PlaylistMediaPlayerOverlay(
                item = selectedPlaylistMedia!!,
                onClose = { overlayMode = BrowserOverlayMode.LIBRARY }
            )
        }

        if (overlayMode == BrowserOverlayMode.TRANSLATE_PICKER) {
            TranslateLanguageDialog(
                currentUrl = activeTab.url,
                onSelectLanguage = { lang ->
                    overlayMode = BrowserOverlayMode.NONE
                    val translatedUrl = "https://translate.google.com/translate?sl=auto&tl=$lang&u=${Uri.encode(activeTab.url)}"
                    navigateInActiveTab(translatedUrl)
                },
                onDismiss = { overlayMode = BrowserOverlayMode.NONE }
            )
        }

        // AI Page Summary Sheet
        AnimatedVisibility(
            visible = isSummaryModalOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(QuettaColors.Surface)
                    .border(1.dp, QuettaColors.Coral.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, tint = QuettaColors.Coral, modifier = Modifier.size(18.dp))
                        Text("Jarvis AI Page Summary", style = MaterialTheme.typography.titleSmall, color = QuettaColors.TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { isSummaryModalOpen = false }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Close", tint = QuettaColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                if (isSummarizing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = QuettaColors.Coral, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Analyzing webpage...", color = QuettaColors.TextSecondary, fontSize = 12.5.sp)
                    }
                } else {
                    val summary = summaryResult ?: "No summary available."
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(QuettaColors.BgDark)
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(summary, color = QuettaColors.TextPrimary, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(summary))
                            toastBanner = "Summary copied"
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = QuettaColors.SkyBlue, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(QuettaColors.Coral)
                                .clickable { JarvisTtsManager.speak(context, summary) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Speak", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showAddSpeedDial) {
        AddSpeedDialDialog(
            defaultTitle = if (activeTab.isHomePage) "" else activeTab.title,
            defaultUrl = if (activeTab.isHomePage) "" else activeTab.url,
            onConfirm = { t, u ->
                BrowserDataStore.addSpeedDial(t, u)
                showAddSpeedDial = false
                toastBanner = "Added to Start Page"
            },
            onDismiss = { showAddSpeedDial = false }
        )
    }

    pendingExternalIntentUri?.let { uri ->
        OpenInExternalAppDialog(
            targetUrl = uri.toString(),
            onStay = { pendingExternalIntentUri = null },
            onOpenApp = {
                pendingExternalIntentUri = null
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            }
        )
    }
}
