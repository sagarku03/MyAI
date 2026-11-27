package com.jarvisai.assistant.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.jarvisai.assistant.browser.BrowserAdBlockEngine
import com.jarvisai.assistant.browser.BrowserDataStore
import com.jarvisai.assistant.browser.BrowserHostUiCallbacks
import com.jarvisai.assistant.browser.BrowserIdentity
import com.jarvisai.assistant.browser.BrowserSessionManager
import com.jarvisai.assistant.browser.BrowserTabSnapshot
import com.jarvisai.assistant.browser.ErudaHelper
import com.jarvisai.assistant.browser.mcp.BrowserAgentCursorOverlay
import com.jarvisai.assistant.browser.mcp.BrowserAgentIndicator
import com.jarvisai.assistant.browser.mcp.BrowserAgentLockOverlay
import com.jarvisai.assistant.browser.mcp.BrowserJsDialogCard
import com.jarvisai.assistant.browser.NativeWebPageSnapshot
import com.jarvisai.assistant.browser.NativeWebReaderEngine
import com.jarvisai.assistant.browser.NativeWebSnapshotContent
import com.jarvisai.assistant.browser.PlaylistMediaItem
import com.jarvisai.assistant.extension.BrowserExtension
import com.jarvisai.assistant.extension.BrowserExtensionManager
import com.jarvisai.assistant.extension.BrowserExtensionRuntimeBridge
import com.jarvisai.assistant.extension.CrxExtensionInstaller
import com.jarvisai.assistant.extension.ExtensionPageInjector
import com.jarvisai.assistant.service.JarvisTtsManager
import com.jarvisai.assistant.ui.screens.browser.AddSpeedDialDialog
import com.jarvisai.assistant.ui.screens.browser.BrowserOverlayMode
import com.jarvisai.assistant.ui.screens.browser.BrowserSettingsScreen
import com.jarvisai.assistant.ui.screens.browser.ChromeWebStoreInstallBanner
import com.jarvisai.assistant.ui.screens.browser.DeveloperExtensionsScreen
import com.jarvisai.assistant.ui.screens.browser.ExtensionBadgeIcon
import com.jarvisai.assistant.ui.screens.browser.ExtensionDetailsDialog
import com.jarvisai.assistant.ui.screens.browser.ExtensionInstallConfirmDialog
import com.jarvisai.assistant.ui.screens.browser.ExtensionPopupSheet
import com.jarvisai.assistant.ui.screens.browser.ExtensionWorkerInspectorDialog
import com.jarvisai.assistant.ui.screens.browser.FindInPageBar
import com.jarvisai.assistant.ui.screens.browser.LibraryTabType
import com.jarvisai.assistant.ui.screens.browser.ManageExtensionsScreen
import com.jarvisai.assistant.ui.screens.browser.OpenInExternalAppDialog
import com.jarvisai.assistant.ui.screens.browser.PlaylistMediaPlayerOverlay
import com.jarvisai.assistant.ui.screens.browser.JarvisBottomMenuSheet
import com.jarvisai.assistant.ui.screens.browser.JarvisBottomOmniboxDock
import com.jarvisai.assistant.ui.screens.browser.JarvisBrowserColors
import com.jarvisai.assistant.ui.screens.browser.JarvisExtensionsQuickSheet
import com.jarvisai.assistant.ui.screens.browser.JarvisFocusedOmniboxOverlay
import com.jarvisai.assistant.ui.screens.browser.JarvisLibraryScreen
import com.jarvisai.assistant.ui.screens.browser.JarvisStartPage
import com.jarvisai.assistant.ui.screens.browser.JarvisTab
import com.jarvisai.assistant.ui.screens.browser.BrowserTabSwitcherScreen
import com.jarvisai.assistant.ui.screens.browser.ReaderModeOverlay
import com.jarvisai.assistant.ui.screens.browser.TabSwitcherMode
import com.jarvisai.assistant.ui.screens.browser.TranslateLanguageDialog
import com.jarvisai.assistant.ui.screens.browser.captureWebViewThumbnail
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File

private const val JARVIS_MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
private const val JARVIS_DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    initialUrl: String = "https://www.google.com",
    onBack: () -> Unit,
    onSummarizeContent: suspend (String) -> String = { "Page summary generated by Agentra Neural Core." }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // Synchronously initialize browser stores on first composition so no phantom bootstrap tab flashes
    remember(context) {
        BrowserDataStore.init(context)
        BrowserExtensionManager.init(context)
        BrowserSessionManager.instance.init(context)
        BrowserSessionManager.instance.attachUiContext(context)
        true
    }

    LaunchedEffect(initialUrl) {
        BrowserDataStore.init(context)
        BrowserExtensionManager.init(context)
        BrowserSessionManager.instance.init(context)
        BrowserSessionManager.instance.attachUiContext(context)
        BrowserSessionManager.instance.ensureInitialTab(initialUrl)
    }

    var pendingFileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val cb = pendingFileChooserCallback
        pendingFileChooserCallback = null
        runCatching {
            cb?.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
        }
    }

    val sessionTabs by BrowserSessionManager.instance.tabsState.collectAsState()
    val sessionActiveTabId by BrowserSessionManager.instance.activeTabIdState.collectAsState()
    val isAgentActive by BrowserSessionManager.instance.isAgentActive.collectAsState()
    val agentActionSummary by BrowserSessionManager.instance.agentActionSummary.collectAsState()
    val agentCursorPosition by BrowserSessionManager.instance.agentCursorPosition.collectAsState()
    val isTouchLocked by BrowserSessionManager.instance.isTouchLocked.collectAsState()
    val fullscreenVideo by BrowserSessionManager.instance.fullscreenVideoState.collectAsState()

    val hostActivity = remember(context) {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) break
            ctx = ctx.baseContext
        }
        ctx as? Activity
    }

    DisposableEffect(fullscreenVideo, hostActivity) {
        val fs = fullscreenVideo
        val act = hostActivity
        val win = act?.window
        if (fs != null && !fs.attachedToDecorView && act != null && win != null) {
            val previousOrientation = act.requestedOrientation
            runCatching {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val controller = WindowCompat.getInsetsController(win, win.decorView)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                runCatching {
                    val controller = WindowCompat.getInsetsController(win, win.decorView)
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    act.requestedOrientation = if (previousOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } else {
                        previousOrientation
                    }
                }
            }
        } else {
            onDispose { }
        }
    }

    val tabs = remember(sessionTabs) {
        if (sessionTabs.isEmpty()) {
            listOf(
                JarvisTab(
                    id = "bootstrap_home",
                    url = "https://www.google.com",
                    title = "Start Page",
                    isHomePage = true
                )
            )
        } else {
            sessionTabs.map { snap ->
                JarvisTab(
                    id = snap.id,
                    url = snap.url,
                    title = snap.title,
                    isPrivate = snap.isPrivate,
                    isHomePage = snap.isHomePage,
                    isExtensionPage = snap.isExtensionPage,
                    extensionId = snap.extensionId,
                    desktopMode = snap.desktopMode,
                    groupId = snap.groupId,
                    blockedCount = snap.blockedCount,
                    canGoBack = snap.canGoBack,
                    canGoForward = snap.canGoForward,
                    thumbnail = snap.thumbnail,
                    updatedAt = snap.updatedAt
                )
            }
        }
    }

    var webViewInitFailed by remember { mutableStateOf(false) }

    val activeTab = tabs.firstOrNull { it.id == sessionActiveTabId } ?: tabs.first()
    val activeSessionTab = BrowserSessionManager.instance.getTab(activeTab.id)
    val activeWebView: WebView? = activeSessionTab?.webView

    // Collected Store State
    val prefs by BrowserDataStore.preferences.collectAsState()
    val speedDials by BrowserDataStore.speedDials.collectAsState()
    val bookmarks by BrowserDataStore.bookmarks.collectAsState()
    val history by BrowserDataStore.history.collectAsState()
    val tabGroups by BrowserDataStore.tabGroups.collectAsState()
    val extensions by BrowserExtensionManager.extensions.collectAsState()
    val actionUiStates by BrowserExtensionManager.actionUiStates.collectAsState()
    val pinnedExtensions = remember(extensions) { extensions.filter { it.isEnabled && it.pinned } }

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
    var selectedPopupEntryOverride by remember { mutableStateOf<String?>(null) }
    var pendingWebStorePreview by remember { mutableStateOf<CrxExtensionInstaller.StagedExtensionPreview?>(null) }
    var lastInstalledExt by remember { mutableStateOf<BrowserExtension?>(null) }
    var selectedPlaylistMedia by remember { mutableStateOf<PlaylistMediaItem?>(null) }
    var isWebStoreInstalling by remember { mutableStateOf(false) }

    var latestDomText by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf<NativeWebPageSnapshot?>(null) }
    var isSummarizing by remember { mutableStateOf(false) }
    var summaryResult by remember { mutableStateOf<String?>(null) }
    var isSummaryModalOpen by remember { mutableStateOf(false) }
    var toastBanner by remember { mutableStateOf<String?>(null) }
    var lastClosedTab by remember { mutableStateOf<BrowserTabSnapshot?>(null) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    LaunchedEffect(toastBanner) {
        if (toastBanner != null) {
            delay(3600L)
            toastBanner = null
            lastClosedTab = null
            lastInstalledExt = null
        }
    }

    fun updateTab(tabId: String, transform: (JarvisTab) -> JarvisTab) {
        val current = tabs.firstOrNull { it.id == tabId } ?: return
        val updated = transform(current)
        BrowserSessionManager.instance.updateTabMeta(tabId) { t ->
            t.url = updated.url
            t.title = updated.title
            t.isHomePage = updated.isHomePage
            t.isPrivate = updated.isPrivate
            t.isExtensionPage = updated.isExtensionPage
            t.extensionId = updated.extensionId
            t.desktopMode = updated.desktopMode
            t.groupId = updated.groupId
            t.blockedCount = updated.blockedCount
            t.canGoBack = updated.canGoBack
            t.canGoForward = updated.canGoForward
            t.thumbnail = updated.thumbnail
            t.updatedAt = updated.updatedAt
        }
    }

    fun captureThumbnailForActiveTab() {
        val current = tabs.firstOrNull { it.id == activeTab.id } ?: return
        if (!current.isHomePage) {
            val bmp = captureWebViewThumbnail(BrowserSessionManager.instance.getTab(current.id)?.webView)
            if (bmp != null) {
                updateTab(current.id) { it.copy(thumbnail = bmp) }
            }
        }
    }

    fun navigateInActiveTab(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.equals("agentra://extensions", ignoreCase = true) ||
            trimmed.equals("jarvis://extensions", ignoreCase = true)
        ) {
            omniboxFocused = false
            overlayMode = BrowserOverlayMode.EXTENSIONS_MANAGE
            return
        }
        val finalUrl = BrowserDataStore.resolveOmniboxInput(trimmed)
        omniboxFocused = false
        val targetId = activeTab.id
        if (BrowserSessionManager.instance.getTab(targetId) == null) {
            BrowserSessionManager.instance.openUrlFromUi(finalUrl, switchToBrowser = false)
        } else {
            BrowserSessionManager.instance.navigateTab(targetId, finalUrl)
        }
    }

    fun startStagedWebStoreInstall(extensionId: String) {
        if (isWebStoreInstalling) return
        isWebStoreInstalling = true
        toastBanner = "Fetching extension details..."
        coroutineScope.launch {
            val res = CrxExtensionInstaller.stageFromChromeWebStore(context, extensionId)
            isWebStoreInstalling = false
            res.onSuccess { staged ->
                pendingWebStorePreview = staged
                toastBanner = null
            }.onFailure { err ->
                toastBanner = "Install failed: ${err.message}"
            }
        }
    }

    fun openNewTab(
        url: String? = null,
        isPrivate: Boolean = false,
        groupId: String? = null
    ) {
        captureThumbnailForActiveTab()
        val isHome = url.isNullOrBlank()
        val parsedExt = if (!isHome) BrowserExtensionRuntimeBridge.parseExtensionResourceUrl(url) else null
        val matchedExt = parsedExt?.let { (eid, _) -> extensions.firstOrNull { it.id == eid || it.safeHostId() == eid } }
        when {
            isHome -> {
                val newTabOverrideExt = if (!isPrivate) BrowserExtensionManager.getActiveNewTabOverride() else null
                if (newTabOverrideExt != null) {
                    BrowserSessionManager.instance.createExtensionTab(
                        url = "${newTabOverrideExt.extensionOrigin()}/${newTabOverrideExt.newTabOverridePage}",
                        title = newTabOverrideExt.name,
                        extensionId = newTabOverrideExt.id,
                        makeActive = true
                    )
                } else {
                    BrowserSessionManager.instance.createHomeTab(isPrivate = isPrivate, groupId = groupId)
                }
            }
            parsedExt != null -> {
                val cleanUrl = url!!.trim()
                val targetUrl = if (cleanUrl.startsWith("chrome-extension://", ignoreCase = true) && matchedExt != null) {
                    "${matchedExt.extensionOrigin()}/${parsedExt.second}"
                } else {
                    cleanUrl
                }
                BrowserSessionManager.instance.createExtensionTab(
                    url = targetUrl,
                    title = matchedExt?.name ?: BrowserAdBlockEngine.extractHost(targetUrl),
                    extensionId = matchedExt?.id ?: parsedExt.first,
                    makeActive = true
                )
            }
            else -> {
                val targetUrl = BrowserDataStore.resolveOmniboxInput(url!!)
                BrowserSessionManager.instance.openUrlFromUi(
                    rawUrl = targetUrl,
                    switchToBrowser = false,
                    newTab = true,
                    isPrivate = isPrivate,
                    groupId = groupId
                )
            }
        }
        overlayMode = BrowserOverlayMode.NONE
    }

    fun launchExtension(
        ext: BrowserExtension,
        preferTab: Boolean = false,
        entryPageOverride: String? = null
    ) {
        val latestExt = BrowserExtensionManager.extensions.value.firstOrNull { it.id == ext.id } ?: ext
        BrowserExtensionManager.dispatchActionClickedToWorker(latestExt.id)
        val hasPopupSurface = latestExt.popupFile.isNotBlank() || latestExt.sidePanelPath.isNotBlank() || !entryPageOverride.isNullOrBlank()
        if (!preferTab && hasPopupSurface) {
            selectedExtension = latestExt
            selectedPopupEntryOverride = entryPageOverride
            overlayMode = BrowserOverlayMode.EXTENSION_POPUP
            return
        }
        val entryPage = entryPageOverride?.takeIf { it.isNotBlank() }
            ?: latestExt.uiEntryPage.ifBlank { "__jarvis_ext_dashboard.html" }
        val extUrl = "${latestExt.extensionOrigin()}/$entryPage"

        captureThumbnailForActiveTab()
        BrowserSessionManager.instance.createExtensionTab(
            url = extUrl,
            title = latestExt.name,
            extensionId = latestExt.id,
            makeActive = true
        )
        overlayMode = BrowserOverlayMode.NONE
    }

    fun closeTab(tabId: String) {
        val existingSnap = sessionTabs.firstOrNull { it.id == tabId }
        val wasPrivate = existingSnap?.isPrivate == true
        BrowserAdBlockEngine.resetTabCount(tabId)
        BrowserExtensionRuntimeBridge.unregisterTab(tabId)
        BrowserSessionManager.instance.closeTab(tabId)

        if (existingSnap != null) {
            lastClosedTab = existingSnap.copy(thumbnail = null)
            toastBanner = "Closed ${existingSnap.title}"
        }
        if (BrowserSessionManager.instance.listTabs().isEmpty()) {
            BrowserSessionManager.instance.createHomeTab(isPrivate = wasPrivate)
        }
    }

    // Wire Chromium TabActionCallbacks so Chrome Extensions can open/switch/close/reload tabs seamlessly
    LaunchedEffect(Unit) {
        BrowserExtensionRuntimeBridge.setTabCallbacks(
            object : BrowserExtensionRuntimeBridge.TabActionCallbacks {
                override fun onCreateTab(url: String, makeActive: Boolean): BrowserExtensionRuntimeBridge.LiveTabRecord {
                    val parsedExt = BrowserExtensionRuntimeBridge.parseExtensionResourceUrl(url)
                    val matchedExt = parsedExt?.let { (eid, _) ->
                        val normId = eid.lowercase().replace(Regex("[^a-z0-9-]"), "-")
                        BrowserExtensionManager.extensions.value.firstOrNull {
                            it.id.equals(eid, ignoreCase = true) || it.safeHostId().equals(normId, ignoreCase = true)
                        }
                    }
                    val finalUrl = if (parsedExt != null && matchedExt != null) {
                        val pagePath = parsedExt.second.ifBlank { matchedExt.uiEntryPage }
                        "${matchedExt.extensionOrigin()}/$pagePath"
                    } else if (url.startsWith("http") || url.startsWith("https")) {
                        url
                    } else {
                        BrowserDataStore.resolveOmniboxInput(url)
                    }
                    val created = if (parsedExt != null) {
                        BrowserSessionManager.instance.createExtensionTab(
                            url = finalUrl,
                            title = matchedExt?.name ?: BrowserAdBlockEngine.extractHost(finalUrl),
                            extensionId = matchedExt?.id ?: parsedExt.first,
                            makeActive = makeActive
                        )
                    } else {
                        BrowserSessionManager.instance.openUrlFromUi(
                            rawUrl = finalUrl,
                            switchToBrowser = false,
                            newTab = true
                        )
                    }
                    val numId = BrowserExtensionRuntimeBridge.registerOrUpdateTab(
                        tabId = created.id,
                        url = finalUrl,
                        title = created.title,
                        isActive = makeActive,
                        isPrivate = false,
                        isExtensionPage = created.isExtensionPage,
                        webView = created.webView
                    )
                    mainHandler.post {
                        if (makeActive) {
                            BrowserSessionManager.instance.switchTab(created.id)
                            overlayMode = BrowserOverlayMode.NONE
                        }
                    }
                    return BrowserExtensionRuntimeBridge.findTabByNumericId(numId)
                        ?: BrowserExtensionRuntimeBridge.LiveTabRecord(
                            created.id, numId, finalUrl, created.title, makeActive, false, created.isExtensionPage, created.webView
                        )
                }

                override fun onSelectTab(tabId: String) {
                    if (BrowserSessionManager.instance.switchTab(tabId)) {
                        overlayMode = BrowserOverlayMode.NONE
                    }
                }

                override fun onCloseTab(tabId: String) {
                    closeTab(tabId)
                }

                override fun onUpdateTabUrl(tabId: String, url: String) {
                    val finalUrl = BrowserDataStore.resolveOmniboxInput(url)
                    BrowserSessionManager.instance.navigateTab(tabId, finalUrl)
                }

                override fun onReloadTab(tabId: String) {
                    BrowserSessionManager.instance.getTab(tabId)?.webView?.reload()
                }

                override fun onToast(message: String) {
                    toastBanner = message
                }

                override fun onRequestWebStoreInstall(extensionId: String, suggestedTitle: String) {
                    startStagedWebStoreInstall(extensionId)
                }

                override fun onRequestOpenPopup(extensionId: String) {
                    val target = BrowserExtensionManager.extensions.value.firstOrNull { it.id == extensionId && it.isEnabled }
                    if (target != null) {
                        launchExtension(target, preferTab = false)
                    }
                }
            }
        )
    }

    // Reset transient loading indicator when switching tabs or returning to Start Page
    LaunchedEffect(activeTab.id, activeTab.isHomePage) {
        if (activeTab.isHomePage) {
            isLoading = false
            loadProgress = 0
        } else {
            val wv = BrowserSessionManager.instance.getTab(activeTab.id)?.webView
            val prog = wv?.progress ?: 100
            loadProgress = prog
            isLoading = prog in 1..99
        }
    }

    // Sync active tab settings (Darkening, JavaScript, ExtensionRuntimeBridge) without hammering OkHttp
    LaunchedEffect(tabs, activeTab.id, activeTab.url, activeTab.title, activeTab.isHomePage, prefs.forceDarkPages, prefs.javaScriptEnabled) {
        tabs.forEach { t ->
            val wv = BrowserSessionManager.instance.getTab(t.id)?.webView
            BrowserExtensionRuntimeBridge.registerOrUpdateTab(
                tabId = t.id,
                url = if (t.isHomePage) "about:blank" else t.url,
                title = t.title,
                isActive = t.id == activeTab.id,
                isPrivate = t.isPrivate,
                isExtensionPage = t.isExtensionPage,
                webView = wv
            )
        }
        val wv = BrowserSessionManager.instance.getTab(activeTab.id)?.webView
        if (wv != null) {
            runCatching {
                wv.settings.javaScriptEnabled = prefs.javaScriptEnabled || activeTab.isExtensionPage || (activeSessionTab?.isAgentControlled == true)
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, prefs.forceDarkPages && !activeTab.isExtensionPage)
                }
            }
        }
    }

    // Only fetch NativeWebReaderEngine HTTP snapshot when WebView fallback or Reader Mode needs it
    LaunchedEffect(activeTab.url, activeTab.isHomePage, webViewInitFailed, overlayMode) {
        val needsSnapshot = webViewInitFailed ||
            (overlayMode == BrowserOverlayMode.READER_MODE && latestDomText.isBlank())
        if (needsSnapshot && !activeTab.isHomePage && !activeTab.isExtensionPage && activeTab.url.startsWith("http")) {
            snapshot = NativeWebReaderEngine.fetchAndParseUrl(activeTab.url)
        }
    }

    // Attach BrowserScreen UI callbacks to the shared WebView pool WITHOUT destroying WebViews on dispose
    DisposableEffect(Unit) {
        val uiCallbacks = object : BrowserHostUiCallbacks {
            override fun attachAdditionalJsInterfaces(tabId: String, webView: WebView) {
                webView.addJavascriptInterface(
                    BrowserExtensionRuntimeBridge.ChromeJsBridge(
                        context = context,
                        ownerWebViewProvider = { BrowserSessionManager.instance.getTab(tabId)?.webView },
                        currentTabNumericIdProvider = {
                            BrowserExtensionRuntimeBridge.numericIdFor(tabId)
                        }
                    ),
                    "__jarvis_chrome_bridge"
                )
            }

            override fun shouldInterceptRequest(
                tabId: String,
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val tabObj = BrowserSessionManager.instance.getTab(tabId)
                val isExt = tabObj?.isExtensionPage == true
                val extResp = BrowserExtensionRuntimeBridge.interceptExtensionRequest(
                    context = context,
                    request = request,
                    defaultTabNumericId = BrowserExtensionRuntimeBridge.numericIdFor(tabId)
                )
                if (extResp != null) return extResp

                val reqUrl = request?.url?.toString()
                if (!isExt && reqUrl != null) {
                    val dnr = BrowserExtensionManager.evaluateDeclarativeNetRequest(reqUrl, tabObj?.url)
                    if (dnr.actionType == "block") {
                        val count = BrowserAdBlockEngine.recordBlocked(tabId)
                        view?.post {
                            BrowserSessionManager.instance.updateTabMeta(tabId) { it.blockedCount = count }
                        }
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    if (dnr.actionType == "redirect" && dnr.redirectUrl.startsWith("http") &&
                        request.isForMainFrame && request.method.equals("GET", ignoreCase = true)
                    ) {
                        val redirectTarget = dnr.redirectUrl
                        view?.post { view.loadUrl(redirectTarget) }
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    if (dnr.actionType != "allow" &&
                        BrowserAdBlockEngine.shouldBlockRequest(reqUrl, BrowserDataStore.preferences.value.adBlockEnabled)
                    ) {
                        val count = BrowserAdBlockEngine.recordBlocked(tabId)
                        view?.post {
                            BrowserSessionManager.instance.updateTabMeta(tabId) { it.blockedCount = count }
                        }
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                }
                return null
            }

            override fun shouldOverrideUrlLoading(
                tabId: String,
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val targetUri = request?.url ?: return false
                val scheme = targetUri.scheme?.lowercase().orEmpty()
                if (scheme in setOf("http", "https", "file", "data", "about", "javascript", "blob", "chrome-extension")) {
                    if (scheme == "chrome-extension") {
                        openNewTab(url = targetUri.toString())
                        return true
                    }
                    return false
                }
                if (scheme == "intent") {
                    val parsedIntent = runCatching {
                        Intent.parseUri(targetUri.toString(), Intent.URI_INTENT_SCHEME)
                    }.getOrNull()
                    val fallbackUrl = parsedIntent?.getStringExtra("browser_fallback_url")
                    val canResolve = parsedIntent != null &&
                        parsedIntent.resolveActivity(context.packageManager) != null
                    if (!canResolve && !fallbackUrl.isNullOrBlank()) {
                        view?.loadUrl(fallbackUrl)
                        return true
                    }
                    if (!canResolve) {
                        toastBanner = "No app installed to handle this link"
                        return true
                    }
                }
                if (BrowserDataStore.preferences.value.askBeforeOpeningApp) {
                    pendingExternalIntentUri = targetUri
                } else {
                    runCatching {
                        val intent = if (scheme == "intent") {
                            Intent.parseUri(targetUri.toString(), Intent.URI_INTENT_SCHEME)
                        } else {
                            Intent(Intent.ACTION_VIEW, targetUri)
                        }
                        context.startActivity(intent)
                    }
                }
                return true
            }

            override fun onPageStarted(tabId: String, view: WebView?, url: String?) {
                if (tabId == BrowserSessionManager.instance.activeTabIdState.value) {
                    isLoading = true
                }
                val tabObj = BrowserSessionManager.instance.getTab(tabId)
                val isExt = tabObj?.isExtensionPage == true
                val isPriv = tabObj?.isPrivate == true
                if (!url.isNullOrBlank()) {
                    BrowserExtensionRuntimeBridge.registerOrUpdateTab(
                        tabId = tabId,
                        url = url,
                        title = tabObj?.title.orEmpty(),
                        isActive = tabId == BrowserSessionManager.instance.activeTabIdState.value,
                        isPrivate = isPriv,
                        isExtensionPage = isExt,
                        webView = view,
                        status = "loading"
                    )
                    BrowserExtensionRuntimeBridge.dispatchWebNavigationEvent("onBeforeNavigate", tabId, url)
                    BrowserExtensionRuntimeBridge.dispatchTabUpdatedEvent(
                        tabId,
                        JSONObject().put("status", "loading").put("url", url)
                    )
                }
                if (!isExt && !BrowserIdentity.isGoogleSignInUrl(url)) {
                    ExtensionPageInjector.injectForPage(
                        webView = view,
                        url = url,
                        extensions = BrowserExtensionManager.extensions.value,
                        tabNumericId = BrowserExtensionRuntimeBridge.numericIdFor(tabId),
                        isPrivateTab = isPriv,
                        runAt = "document_start"
                    )
                }
            }

            override fun onPageCommitVisible(tabId: String, view: WebView?, url: String?) {
                val tabObj = BrowserSessionManager.instance.getTab(tabId)
                val isExt = tabObj?.isExtensionPage == true
                val isPriv = tabObj?.isPrivate == true
                if (!url.isNullOrBlank()) {
                    BrowserExtensionRuntimeBridge.dispatchWebNavigationEvent("onCommitted", tabId, url)
                    BrowserExtensionRuntimeBridge.dispatchWebNavigationEvent("onDOMContentLoaded", tabId, url)
                }
                if (!isExt && !BrowserIdentity.isGoogleSignInUrl(url)) {
                    ExtensionPageInjector.injectForPage(
                        webView = view,
                        url = url,
                        extensions = BrowserExtensionManager.extensions.value,
                        tabNumericId = BrowserExtensionRuntimeBridge.numericIdFor(tabId),
                        isPrivateTab = isPriv,
                        runAt = "document_end"
                    )
                }
            }

            override fun onPageFinished(tabId: String, view: WebView?, url: String?) {
                if (tabId == BrowserSessionManager.instance.activeTabIdState.value) {
                    isLoading = false
                    loadProgress = 100
                }
                val tabObj = BrowserSessionManager.instance.getTab(tabId)
                val isExt = tabObj?.isExtensionPage == true
                val isPriv = tabObj?.isPrivate == true
                val pageTitle = view?.title?.takeIf { it.isNotBlank() }
                if (!url.isNullOrBlank() && !isExt) {
                    BrowserDataStore.recordHistory(
                        title = pageTitle ?: BrowserAdBlockEngine.extractHost(url),
                        url = url,
                        isPrivate = isPriv
                    )
                }
                if (!url.isNullOrBlank()) {
                    BrowserExtensionRuntimeBridge.registerOrUpdateTab(
                        tabId = tabId,
                        url = url,
                        title = pageTitle ?: tabObj?.title.orEmpty(),
                        isActive = tabId == BrowserSessionManager.instance.activeTabIdState.value,
                        isPrivate = isPriv,
                        isExtensionPage = isExt,
                        webView = view,
                        status = "complete"
                    )
                    BrowserExtensionRuntimeBridge.dispatchWebNavigationEvent("onCompleted", tabId, url)
                    BrowserExtensionRuntimeBridge.dispatchTabUpdatedEvent(
                        tabId,
                        JSONObject().apply {
                            put("status", "complete")
                            put("url", url)
                            if (!pageTitle.isNullOrBlank()) put("title", pageTitle)
                        }
                    )
                    val cwsId = CrxExtensionInstaller.extractChromeWebStoreId(url)
                    if (cwsId != null && (url.contains("chromewebstore.google.com") || url.contains("chrome.google.com/webstore"))) {
                        view?.evaluateJavascript(ExtensionPageInjector.buildChromeWebStorePageHookJs(cwsId), null)
                    }
                }
                if (!isExt && !BrowserIdentity.isGoogleSignInUrl(url)) {
                    ExtensionPageInjector.injectForPage(
                        webView = view,
                        url = url,
                        extensions = BrowserExtensionManager.extensions.value,
                        tabNumericId = BrowserExtensionRuntimeBridge.numericIdFor(tabId),
                        isPrivateTab = isPriv,
                        runAt = "document_idle"
                    )
                }
                if (tabId == BrowserSessionManager.instance.activeTabIdState.value) {
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
            }

            override fun onProgressChanged(tabId: String, newProgress: Int) {
                if (tabId == BrowserSessionManager.instance.activeTabIdState.value) {
                    loadProgress = newProgress
                    isLoading = newProgress in 1..99
                }
            }

            override fun onConsoleMessage(tabId: String, message: ConsoleMessage) {
                val tabObj = BrowserSessionManager.instance.getTab(tabId)
                val extId = tabObj?.extensionId
                if (tabObj?.isExtensionPage == true && !extId.isNullOrBlank()) {
                    BrowserExtensionManager.recordLog(extId, "[Tab] ${message.message()}")
                }
            }

            override fun onDownloadRequested(
                tabId: String,
                url: String,
                contentDisposition: String?,
                mimeType: String?
            ) {
                val dl = BrowserDataStore.enqueueSystemDownload(context, url, contentDisposition, mimeType)
                toastBanner = "Downloading ${dl.fileName}"
            }

            override fun onFindMatches(tabId: String, activeOrdinal: Int, totalMatches: Int) {
                if (tabId == BrowserSessionManager.instance.activeTabIdState.value) {
                    findMatchSummary = if (totalMatches == 0) "0/0" else "${activeOrdinal + 1}/$totalMatches"
                }
            }

            override fun onShowFileChooser(
                tabId: String,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: WebChromeClient.FileChooserParams?
            ): Boolean {
                pendingFileChooserCallback?.onReceiveValue(null)
                pendingFileChooserCallback = filePathCallback
                val acceptTypes = fileChooserParams?.acceptTypes
                    ?.filter { it.isNotBlank() && it.contains("/") }
                    ?.toTypedArray()
                    ?.takeIf { it.isNotEmpty() }
                    ?: arrayOf("*/*")
                return runCatching {
                    fileChooserLauncher.launch(acceptTypes)
                    true
                }.getOrElse {
                    pendingFileChooserCallback = null
                    filePathCallback.onReceiveValue(null)
                    false
                }
            }
        }
        BrowserSessionManager.instance.attachUiContext(context)
        BrowserSessionManager.instance.attachHostUiCallbacks(uiCallbacks)
        onDispose {
            pendingFileChooserCallback?.onReceiveValue(null)
            pendingFileChooserCallback = null
            BrowserSessionManager.instance.exitFullscreenVideo()
            // Park playing WebView in the Activity's hidden 1x1 window host so YouTube/audio keeps
            // playing uninterrupted when switching to Chat or any other in-app screen!
            val parked = BrowserSessionManager.instance.parkPlayingWebViewInActivity(hostActivity)
            if (!parked && !BrowserDataStore.preferences.value.backgroundMediaPlayback) {
                BrowserSessionManager.instance.pauseInactiveMedia()
            }
            BrowserSessionManager.instance.resetWebViewsToAppContext(forceAll = false)
            BrowserSessionManager.instance.attachUiContext(null)
            BrowserSessionManager.instance.attachHostUiCallbacks(null)
            BrowserExtensionRuntimeBridge.setTabCallbacks(null)
        }
    }

    BackHandler {
        when {
            fullscreenVideo != null -> {
                BrowserSessionManager.instance.exitFullscreenVideo()
            }
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
                BrowserSessionManager.instance.pauseTab(activeTab.id)
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
            .background(if (activeTab.isPrivate) Color(0xFF0E0C14) else JarvisBrowserColors.BgDark)
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
                    JarvisStartPage(
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (!findInPageOpen) Modifier.statusBarsPadding() else Modifier)
                    ) {
                        // Extension Tab Header matching `00:11 - 00:16` in reference video
                        if (activeTab.isExtensionPage) {
                            val extMeta = remember(activeTab.extensionId, extensions) {
                                extensions.firstOrNull { it.id == activeTab.extensionId }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(JarvisBrowserColors.Surface)
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                                    .testTag("extension_tab_header"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (extMeta != null) {
                                    ExtensionBadgeIcon(extMeta, sizeDp = 26)
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = extMeta?.name ?: activeTab.title,
                                        color = JarvisBrowserColors.TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Chrome Extension",
                                        color = JarvisBrowserColors.Coral,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                IconButton(
                                    onClick = { closeTab(activeTab.id) },
                                    modifier = Modifier.size(34.dp).testTag("close_extension_tab_btn")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Extension", tint = JarvisBrowserColors.TextSecondary)
                                }
                            }
                            HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            val currentTabId = activeTab.id
                            val currentTabInitialUrl = activeTab.url

                            key(currentTabId) {
                                AndroidView(
                                    factory = { ctx ->
                                        try {
                                            val sharedWebView = BrowserSessionManager.instance.obtainWebViewForUi(
                                                tabId = currentTabId,
                                                context = ctx
                                            )
                                            if (sharedWebView != null) {
                                                if (sharedWebView.url.isNullOrBlank() && currentTabInitialUrl.isNotBlank()) {
                                                    sharedWebView.loadUrl(currentTabInitialUrl)
                                                }
                                                sharedWebView
                                            } else {
                                                android.view.View(ctx)
                                            }
                                        } catch (e: Throwable) {
                                            webViewInitFailed = true
                                            android.view.View(ctx)
                                        }
                                    },
                                    update = { view ->
                                        if (view is WebView) {
                                            runCatching {
                                                view.settings.javaScriptEnabled =
                                                    prefs.javaScriptEnabled || activeTab.isExtensionPage || (activeSessionTab?.isAgentControlled == true)
                                            }
                                        }
                                    },
                                    onRelease = { view ->
                                        runCatching {
                                            if (!BrowserSessionManager.instance.isParkedInBackgroundHost(view)) {
                                                (view.parent as? ViewGroup)?.removeView(view)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Live Agentra Automation Overlays (Glowing Frame, Arrow Cursor + Tooltip, AI Touch Lock, Non-Blocking JS Dialogs)
                            val lockActiveNow = isTouchLocked || (activeSessionTab?.aiLocked == true)
                            BrowserAgentCursorOverlay(
                                isAgentActive = isAgentActive,
                                cursorPosition = agentCursorPosition,
                                tabId = activeSessionTab?.id,
                                isLocked = lockActiveNow
                            )

                            BrowserAgentLockOverlay(
                                tab = activeSessionTab,
                                isLocked = lockActiveNow,
                                onUnlock = {
                                    BrowserSessionManager.instance.setTouchLocked(false)
                                }
                            )

                            BrowserJsDialogCard(
                                tab = activeSessionTab,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )

                            BrowserAgentIndicator(
                                isAgentActive = isAgentActive,
                                actionSummary = agentActionSummary,
                                modifier = Modifier.align(Alignment.BottomCenter)
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
                    onInstallClick = { startStagedWebStoreInstall(detectedWebStoreId) },
                    onOpenExtensions = { overlayMode = BrowserOverlayMode.EXTENSIONS_DEV }
                )
            }

            // Bottom Jarvis Omnibox Dock (`[ 🏠 | 🛡️ URL | [N] | ⋮ ]`)
            JarvisBottomOmniboxDock(
                activeTab = activeTab,
                tabCount = tabs.count { it.isPrivate == activeTab.isPrivate },
                isLoading = isLoading,
                loadProgress = loadProgress,
                groupTabs = groupTabs,
                pinnedExtensions = pinnedExtensions,
                actionUiStates = actionUiStates,
                onExtensionActionClick = { ext -> launchExtension(ext) },
                onOpenExtensionsMenu = { overlayMode = BrowserOverlayMode.EXTENSIONS_QUICK },
                onHomeClick = {
                    captureThumbnailForActiveTab()
                    BrowserSessionManager.instance.pauseTab(activeTab.id)
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
                    BrowserSessionManager.instance.switchTab(gidTabId)
                },
                onAddTabToGroup = { openNewTab(groupId = activeTab.groupId) },
                onToggleDesktopMode = {
                    val nextDesktop = !activeTab.desktopMode
                    BrowserSessionManager.instance.setDesktopMode(activeTab.id, nextDesktop)
                    toastBanner = if (nextDesktop) "Desktop Site enabled" else "Mobile Site enabled"
                },
                onReloadOrStop = {
                    val wv = activeWebView
                    if (wv != null) {
                        if (isLoading) {
                            wv.stopLoading()
                            isLoading = false
                        } else {
                            wv.reload()
                        }
                    }
                }
            )
        }

        // Focused Omnibox Overlay (Search + Suggestions + Search Engine Switcher)
        if (omniboxFocused) {
            Box(modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
                JarvisFocusedOmniboxOverlay(
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

        // Toast Feedback Pill (with Undo option matching `00:20` when a tab is closed)
        AnimatedVisibility(
            visible = toastBanner != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(JarvisBrowserColors.SurfaceElevated)
                    .border(1.dp, JarvisBrowserColors.Coral.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(toastBanner.orEmpty(), color = JarvisBrowserColors.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                if (lastClosedTab != null) {
                    Text(
                        text = "Undo",
                        color = JarvisBrowserColors.Coral,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clickable {
                                val restore = lastClosedTab ?: return@clickable
                                lastClosedTab = null
                                toastBanner = null
                                BrowserSessionManager.instance.restoreTab(restore)
                            }
                            .testTag("toast_undo_btn")
                    )
                } else if (lastInstalledExt != null) {
                    Text(
                        text = "Open",
                        color = JarvisBrowserColors.Coral,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clickable {
                                val target = lastInstalledExt ?: return@clickable
                                lastInstalledExt = null
                                toastBanner = null
                                launchExtension(target)
                            }
                            .testTag("toast_open_ext_btn")
                    )
                }
            }
        }

        // Jarvis Bottom Menu Sheet
        if (overlayMode == BrowserOverlayMode.MENU) {
            JarvisBottomMenuSheet(
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
                    BrowserSessionManager.instance.setDesktopMode(activeTab.id, nextDesktop)
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
                    BrowserSessionManager.instance.switchTab(id)
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
            JarvisLibraryScreen(
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

        // Extensions Quick Sheet -> Manage -> Developer -> Extension Tab / Popup / Worker / Details
        if (overlayMode == BrowserOverlayMode.EXTENSIONS_QUICK) {
            JarvisExtensionsQuickSheet(
                extensions = extensions,
                onDismiss = { overlayMode = BrowserOverlayMode.NONE },
                onLaunchExtension = { ext -> launchExtension(ext) },
                onOpenManage = { overlayMode = BrowserOverlayMode.EXTENSIONS_MANAGE },
                onOpenDeveloper = { overlayMode = BrowserOverlayMode.EXTENSIONS_DEV },
                onOpenWebStore = {
                    overlayMode = BrowserOverlayMode.NONE
                    navigateInActiveTab("https://chromewebstore.google.com/")
                },
                onOpenInTab = { ext -> launchExtension(ext, preferTab = true) },
                onOpenDetails = { ext ->
                    selectedExtension = ext
                    overlayMode = BrowserOverlayMode.EXTENSION_DETAILS
                },
                onContextMenuClick = { item ->
                    val tabNum = BrowserExtensionRuntimeBridge.numericIdFor(activeTab.id)
                    val infoJson = JSONObject().apply {
                        put("menuItemId", item.itemId)
                        put("pageUrl", activeTab.url)
                    }
                    val tabJson = JSONObject().apply {
                        put("id", tabNum)
                        put("url", activeTab.url)
                        put("title", activeTab.title)
                        put("active", true)
                    }
                    BrowserExtensionManager.getWorkerHost(item.extensionId)?.evalJsFireAndForget(
                        "if (window.__jarvisDispatchContextMenuClick) { window.__jarvisDispatchContextMenuClick(${JSONObject.quote(item.extensionId)}, $infoJson, $tabJson); }"
                    )
                    overlayMode = BrowserOverlayMode.NONE
                }
            )
        }

        if (overlayMode == BrowserOverlayMode.EXTENSIONS_MANAGE) {
            ManageExtensionsScreen(
                extensions = extensions,
                onBack = { overlayMode = BrowserOverlayMode.EXTENSIONS_QUICK },
                onOpenDeveloperOptions = { overlayMode = BrowserOverlayMode.EXTENSIONS_DEV },
                onOpenPopup = { ext -> launchExtension(ext) },
                onShowDetails = { ext ->
                    selectedExtension = ext
                    overlayMode = BrowserOverlayMode.EXTENSION_DETAILS
                },
                onOpenOptions = { ext ->
                    launchExtension(ext, preferTab = true, entryPageOverride = ext.optionsPage)
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
                onOpenPopup = { ext -> launchExtension(ext) }
            )
        }

        if (overlayMode == BrowserOverlayMode.EXTENSION_POPUP && selectedExtension != null) {
            ExtensionPopupSheet(
                extension = selectedExtension!!,
                activeTabId = activeTab.id,
                onDismiss = {
                    selectedPopupEntryOverride = null
                    overlayMode = BrowserOverlayMode.NONE
                },
                onOpenUrlInNewTab = { url -> openNewTab(url = url) },
                onOpenInFullTab = { ext -> launchExtension(ext, preferTab = true, entryPageOverride = selectedPopupEntryOverride) },
                onOpenDetails = { ext ->
                    selectedExtension = ext
                    overlayMode = BrowserOverlayMode.EXTENSION_DETAILS
                },
                entryOverride = selectedPopupEntryOverride
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
                onDismiss = { overlayMode = BrowserOverlayMode.EXTENSIONS_MANAGE },
                onOpenUi = { ext -> launchExtension(ext) },
                onOpenOptions = { ext -> launchExtension(ext, preferTab = true, entryPageOverride = ext.optionsPage) },
                onInspectWorker = { ext ->
                    selectedExtension = ext
                    overlayMode = BrowserOverlayMode.WORKER_INSPECTOR
                }
            )
        }

        pendingWebStorePreview?.let { preview ->
            ExtensionInstallConfirmDialog(
                preview = preview,
                onConfirm = { pinToToolbar ->
                    val finalized = CrxExtensionInstaller.finalizeStagedInstall(context, preview, pinToToolbar)
                    pendingWebStorePreview = null
                    finalized.onSuccess { installed ->
                        lastInstalledExt = installed
                        toastBanner = "Installed '${installed.name}'"
                    }.onFailure { err ->
                        toastBanner = "Install failed: ${err.message}"
                    }
                },
                onCancel = {
                    CrxExtensionInstaller.discardStagedInstall(preview)
                    pendingWebStorePreview = null
                }
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
                    .background(JarvisBrowserColors.Surface)
                    .border(1.dp, JarvisBrowserColors.Coral.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, tint = JarvisBrowserColors.Coral, modifier = Modifier.size(18.dp))
                        Text("Agentra Page Summary", style = MaterialTheme.typography.titleSmall, color = JarvisBrowserColors.TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { isSummaryModalOpen = false }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Close", tint = JarvisBrowserColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                if (isSummarizing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = JarvisBrowserColors.Coral, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Analyzing webpage...", color = JarvisBrowserColors.TextSecondary, fontSize = 12.5.sp)
                    }
                } else {
                    val summary = summaryResult ?: "No summary available."
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(JarvisBrowserColors.BgDark)
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(summary, color = JarvisBrowserColors.TextPrimary, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(summary))
                            toastBanner = "Summary copied"
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = JarvisBrowserColors.SkyBlue, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(JarvisBrowserColors.Coral)
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

        // Fallback Fullscreen HTML5 / YouTube Video Overlay (only used if not already synchronously attached to decorView)
        fullscreenVideo?.takeIf { !it.attachedToDecorView }?.let { fs ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .testTag("browser_fullscreen_video_container")
            ) {
                key(fs.customView) {
                    AndroidView(
                        factory = {
                            (fs.customView.parent as? ViewGroup)?.removeView(fs.customView)
                            fs.customView.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            fs.customView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
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
                runCatching {
                    val launchIntent = if (uri.scheme.equals("intent", ignoreCase = true)) {
                        Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                    } else {
                        Intent(Intent.ACTION_VIEW, uri)
                    }
                    context.startActivity(launchIntent)
                }.onFailure {
                    toastBanner = "Unable to open external app"
                }
            }
        )
    }
}
