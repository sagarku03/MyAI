package com.jarvisai.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jarvisai.assistant.browser.BrowserAdBlockEngine
import com.jarvisai.assistant.browser.BrowserDataStore
import com.jarvisai.assistant.browser.BrowserSearchEngine
import com.jarvisai.assistant.extension.BrowserExtensionManager
import com.jarvisai.assistant.extension.ContentScriptSpec
import com.jarvisai.assistant.extension.CrxExtensionInstaller
import com.jarvisai.assistant.extension.ExtensionPageInjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JarvisBrowserExtensionsTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        BrowserDataStore.init(context)
        BrowserExtensionManager.init(context)
    }

    @Test
    fun crx3HeaderStrippingAndArchiveInstallWorks() {
        val manifest = """
            {
              "manifest_version": 3,
              "name": "Jarvis Dark Reader Test",
              "version": "2.4.1",
              "description": "Applies dark theme dynamically",
              "permissions": ["storage", "activeTab"],
              "action": { "default_popup": "popup.html" },
              "background": { "service_worker": "bg.js" },
              "content_scripts": [
                {
                  "matches": ["https://*.wikipedia.org/*"],
                  "js": ["content.js"],
                  "css": ["style.css"],
                  "run_at": "document_idle"
                }
              ]
            }
        """.trimIndent()

        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("popup.html"))
            zos.write("<html><body><h1>Popup</h1></body></html>".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("content.js"))
            zos.write("console.log('content loaded');".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("style.css"))
            zos.write("body { background: #111; }".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("bg.js"))
            zos.write("console.log('worker started');".toByteArray())
            zos.closeEntry()
        }
        val zipBytes = zipOut.toByteArray()

        // Wrap ZIP inside a real CRX3 binary header (`Cr24` + version 3 + protobuf header length)
        val fakeProtobufHeader = ByteArray(16) { 0x42 }
        val crxOut = ByteArrayOutputStream()
        crxOut.write("Cr24".toByteArray(Charsets.US_ASCII))
        crxOut.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(3).array())
        crxOut.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(fakeProtobufHeader.size).array())
        crxOut.write(fakeProtobufHeader)
        crxOut.write(zipBytes)

        val stripped = CrxExtensionInstaller.stripCrxHeaderToZipBytes(crxOut.toByteArray())
        assertEquals(zipBytes.size, stripped.size)
        assertEquals(0x50.toByte(), stripped[0]) // 'P'
        assertEquals(0x4B.toByte(), stripped[1]) // 'K'

        val result = CrxExtensionInstaller.installFromArchiveBytes(
            context = context,
            rawBytes = crxOut.toByteArray(),
            explicitId = "test-crx3-dark",
            sourceType = "crx"
        )
        assertTrue(result.isSuccess)
        val installed = BrowserExtensionManager.extensions.value.firstOrNull { it.id == "test-crx3-dark" }
        assertNotNull(installed)
        assertEquals("Jarvis Dark Reader Test", installed!!.name)
        assertEquals("2.4.1", installed.version)
        assertEquals("popup.html", installed.popupFile)
        assertEquals("bg.js", installed.serviceWorkerFile)
        assertEquals(1, installed.contentScripts.size)
        assertTrue(BrowserExtensionManager.removeExtension("test-crx3-dark"))
    }

    @Test
    fun chromeWebStoreUrlExtractionAndUserscriptInstallWork() {
        val storeId = CrxExtensionInstaller.extractChromeWebStoreId(
            "https://chromewebstore.google.com/detail/ublock-origin/cjpalhdlnbpafiamejdnhcphjbkeiagm?hl=en"
        )
        assertEquals("cjpalhdlnbpafiamejdnhcphjbkeiagm", storeId)
        assertNull(CrxExtensionInstaller.extractChromeWebStoreId("https://example.com/not-a-store"))

        val userScript = """
            // ==UserScript==
            // @name         YouTube Clean Enhancer
            // @version      1.5.0
            // @description  Enhances YouTube playback controls
            // @match        https://*.youtube.com/*
            // @run-at       document-idle
            // ==/UserScript==
            console.log('YouTube Clean Enhancer active');
        """.trimIndent()

        val installRes = CrxExtensionInstaller.installFromUserScript(
            context = context,
            scriptSource = userScript,
            suggestedId = "yt-enhancer"
        )
        assertTrue(installRes.isSuccess)
        val ext = installRes.getOrNull()!!
        assertEquals("YouTube Clean Enhancer", ext.name)
        assertEquals("1.5.0", ext.version)
        assertTrue(ext.contentScripts.any { it.matches.contains("https://*.youtube.com/*") })
        BrowserExtensionManager.removeExtension(ext.id)
    }

    @Test
    fun extensionPatternMatcherAndAdBlockerWorkAccurately() {
        val ytSpec = ContentScriptSpec(
            matches = listOf("https://*.youtube.com/*"),
            excludeMatches = listOf("https://studio.youtube.com/*")
        )
        assertTrue(ExtensionPageInjector.matchesContentScript("https://m.youtube.com/watch?v=123", ytSpec))
        assertFalse(ExtensionPageInjector.matchesContentScript("https://studio.youtube.com/channel", ytSpec))
        assertFalse(ExtensionPageInjector.matchesContentScript("https://github.com", ytSpec))
        assertTrue(ExtensionPageInjector.matchesPattern("https://example.org/test", "<all_urls>"))

        assertTrue(BrowserAdBlockEngine.shouldBlockRequest("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js", true))
        assertTrue(BrowserAdBlockEngine.shouldBlockRequest("https://google-analytics.com/analytics.js", true))
        assertFalse(BrowserAdBlockEngine.shouldBlockRequest("https://github.com/trending", true))
        assertFalse(BrowserAdBlockEngine.shouldBlockRequest("https://google-analytics.com/analytics.js", false))
    }

    @Test
    fun browserDataStoreHandlesOmniboxPrivacyBookmarksAndGroups() {
        BrowserDataStore.updatePreferences { it.copy(searchEngine = BrowserSearchEngine.DUCKDUCKGO) }
        assertEquals(
            "https://duckduckgo.com/?q=kotlin%20compose",
            BrowserDataStore.resolveOmniboxInput("kotlin compose")
        )
        assertEquals(
            "https://github.com",
            BrowserDataStore.resolveOmniboxInput("github.com")
        )

        // Private mode must not save into history
        BrowserDataStore.clearHistory()
        BrowserDataStore.recordHistory("Secret Page", "https://secret.example.com", isPrivate = true)
        assertTrue(BrowserDataStore.history.value.isEmpty())

        BrowserDataStore.recordHistory("Public Page", "https://example.org", isPrivate = false)
        assertEquals(1, BrowserDataStore.history.value.size)

        // Bookmarks & Tab Groups
        assertTrue(BrowserDataStore.toggleBookmark("Kotlin Docs", "https://kotlinlang.org"))
        assertTrue(BrowserDataStore.isBookmarked("https://kotlinlang.org"))
        val grp = BrowserDataStore.createOrUpdateTabGroup("Research")
        assertTrue(BrowserDataStore.tabGroups.value.any { it.id == grp.id && it.title == "Research" })
    }

    @Test
    fun runtimeBridgeServesVirtualHttpsOriginInjectsChromeHeadPolyfillAndResolvesActiveWebTab() {
        val manifest = """
            {
              "manifest_version": 3,
              "name": "Flow Automation",
              "version": "1.2.0",
              "description": "Batch prompts on Google Flow",
              "permissions": ["storage", "tabs", "scripting", "sidePanel"],
              "side_panel": { "default_path": "sidepanel.html" },
              "background": { "service_worker": "worker.js", "type": "module" }
            }
        """.trimIndent()

        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("sidepanel.html"))
            zos.write("<!DOCTYPE html><html><head><title>Flow</title><script type=\"module\" src=\"app.js\"></script></head><body>OK</body></html>".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("app.js"))
            zos.write("console.log(chrome.runtime.id);".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("worker.js"))
            zos.write("chrome.runtime.onMessage.addListener(() => true);".toByteArray())
            zos.closeEntry()
        }

        val res = CrxExtensionInstaller.installFromArchiveBytes(
            context = context,
            rawBytes = zipOut.toByteArray(),
            explicitId = "flowautohjklmnop",
            sourceType = "zip"
        )
        assertTrue(res.isSuccess)
        val ext = res.getOrNull()!!
        assertEquals("sidepanel.html", ext.sidePanelPath)
        assertEquals("sidepanel.html", ext.uiEntryPage)
        assertTrue(ext.backgroundIsModule)

        // Private mode access toggle persists
        BrowserExtensionManager.togglePrivateAccess(ext.id, true)
        assertTrue(BrowserExtensionManager.extensions.value.first { it.id == ext.id }.allowInPrivate)

        // Parse both virtual HTTPS and chrome-extension:// URLs
        val parsedHttps = com.jarvisai.assistant.extension.BrowserExtensionRuntimeBridge.parseExtensionResourceUrl(
            "https://ext-flowautohjklmnop.jarvis.invalid/sidepanel.html"
        )
        assertEquals("flowautohjklmnop" to "sidepanel.html", parsedHttps)

        val parsedChrome = com.jarvisai.assistant.extension.BrowserExtensionRuntimeBridge.parseExtensionResourceUrl(
            "chrome-extension://flowautohjklmnop/app.js"
        )
        assertEquals("flowautohjklmnop" to "app.js", parsedChrome)

        // Test WebResourceRequest interception for sidepanel.html -> <head> polyfill injected BEFORE module script
        val htmlReq = object : android.webkit.WebResourceRequest {
            override fun getUrl(): android.net.Uri = android.net.Uri.parse("https://ext-flowautohjklmnop.jarvis.invalid/sidepanel.html")
            override fun isForMainFrame(): Boolean = true
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        }
        val interceptedHtml = com.jarvisai.assistant.extension.BrowserExtensionRuntimeBridge.interceptExtensionRequest(context, htmlReq, 1)
        assertNotNull(interceptedHtml)
        assertEquals("text/html", interceptedHtml!!.mimeType)
        assertEquals("*", interceptedHtml.responseHeaders["Access-Control-Allow-Origin"])
        val htmlText = interceptedHtml.data.bufferedReader().readText()
        val polyIdx = htmlText.indexOf("window.__jarvisMakeChromeApi")
        val modIdx = htmlText.indexOf("<script type=\"module\"")
        assertTrue("chrome polyfill must appear in <head> before <script type=\"module\">", polyIdx in 0 until modIdx)

        // Test virtual worker host interception loads module service worker
        val workerReq = object : android.webkit.WebResourceRequest {
            override fun getUrl(): android.net.Uri = android.net.Uri.parse("https://ext-flowautohjklmnop.jarvis.invalid/__jarvis_worker_host.html")
            override fun isForMainFrame(): Boolean = true
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        }
        val workerResp = com.jarvisai.assistant.extension.BrowserExtensionRuntimeBridge.interceptExtensionRequest(context, workerReq, 1)
        assertNotNull(workerResp)
        val workerHtml = workerResp!!.data.bufferedReader().readText()
        assertTrue(workerHtml.contains("<script type=\"module\" src=\"/worker.js\"></script>"))

        // Test numeric tab tracking & lastActiveWebTabId resolution when extension tab is foreground
        val bridge = com.jarvisai.assistant.extension.BrowserExtensionRuntimeBridge.ChromeJsBridge(context, { null })
        com.jarvisai.assistant.extension.BrowserExtensionRuntimeBridge.registerOrUpdateTab(
            tabId = "tab-web-1",
            url = "https://labs.google/fx/tools/flow",
            title = "Google Flow",
            isActive = true,
            isPrivate = false,
            isExtensionPage = false,
            webView = null
        )
        com.jarvisai.assistant.extension.BrowserExtensionRuntimeBridge.registerOrUpdateTab(
            tabId = "tab-ext-1",
            url = "https://ext-flowautohjklmnop.jarvis.invalid/sidepanel.html",
            title = "Flow Automation",
            isActive = true,
            isPrivate = false,
            isExtensionPage = true,
            webView = null
        )
        val queried = org.json.JSONArray(bridge.tabsQuery("ui", "{\"active\":true}"))
        assertTrue(queried.length() >= 1)
        val firstTab = queried.getJSONObject(0)
        assertEquals("https://labs.google/fx/tools/flow", firstTab.getString("url"))
        assertTrue(firstTab.getInt("id") > 0)

        com.jarvisai.assistant.extension.BrowserExtensionRuntimeBridge.unregisterTab("tab-web-1")
        com.jarvisai.assistant.extension.BrowserExtensionRuntimeBridge.unregisterTab("tab-ext-1")
        BrowserExtensionManager.removeExtension(ext.id)
    }
}
