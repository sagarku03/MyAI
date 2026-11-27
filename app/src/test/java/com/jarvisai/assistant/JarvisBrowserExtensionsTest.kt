package com.jarvisai.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jarvisai.assistant.browser.BrowserAdBlockEngine
import com.jarvisai.assistant.browser.BrowserDataStore
import com.jarvisai.assistant.browser.BrowserSearchEngine
import com.jarvisai.assistant.extension.BrowserExtensionManager
import com.jarvisai.assistant.extension.BrowserExtensionRuntimeBridge
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

    @Test
    fun autoHtmlDiscoveryUnderscoreIdSanitizationCspStrippingAndVirtualDashboardWork() {
        // 1. Archive with underscores in explicitId, no default_popup in manifest, and dist/sidepanel.html with strict CSP meta tag
        val manifestNoPopup = """
            {
              "manifest_version": 3,
              "name": "TryAIToday Flow Automator",
              "version": "1.4.0",
              "permissions": ["storage", "tabs", "scripting", "sidePanel"],
              "background": { "service_worker": "background.js" }
            }
        """.trimIndent()

        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestNoPopup.toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("background.js"))
            zos.write("chrome.sidePanel.setOptions({ path: 'dist/sidepanel.html' });".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("dist/sidepanel.html"))
            zos.write(
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self'">
                    <title>Flow Automator</title>
                </head>
                <body><h1>Flow UI</h1></body>
                </html>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()
        }

        val res = CrxExtensionInstaller.installFromArchiveBytes(
            context = context,
            rawBytes = zipOut.toByteArray(),
            explicitId = "TryAIToday_Flow_Automator_1.4",
            sourceType = "zip"
        )
        assertTrue(res.isSuccess)
        val ext = res.getOrNull()!!
        // Underscores and dots must be sanitized to hyphens so Uri.parse(...).host never fails
        assertEquals("tryaitoday-flow-automator-1-4", ext.id)
        assertFalse(ext.extensionOrigin().contains("_"))
        // Auto-discovered dist/sidepanel.html even though manifest.json had no default_popup
        assertEquals("dist/sidepanel.html", ext.uiEntryPage)

        val htmlReq = object : android.webkit.WebResourceRequest {
            override fun getUrl(): android.net.Uri = android.net.Uri.parse("${ext.extensionOrigin()}/${ext.uiEntryPage}")
            override fun isForMainFrame(): Boolean = true
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        }
        val htmlResp = com.jarvisai.assistant.extension.BrowserExtensionRuntimeBridge.interceptExtensionRequest(context, htmlReq, 1)
        assertNotNull(htmlResp)
        val servedHtml = htmlResp!!.data.bufferedReader().readText()
        assertFalse("Restrictive CSP meta tag must be stripped", servedHtml.contains("Content-Security-Policy"))
        assertTrue("External same-origin polyfill script must be injected", servedHtml.contains("/__jarvis_chrome_polyfill.js"))

        // Also verify external polyfill JS route serves 200 OK
        val polyReq = object : android.webkit.WebResourceRequest {
            override fun getUrl(): android.net.Uri = android.net.Uri.parse("${ext.extensionOrigin()}/__jarvis_chrome_polyfill.js")
            override fun isForMainFrame(): Boolean = false
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        }
        val polyResp = com.jarvisai.assistant.extension.BrowserExtensionRuntimeBridge.interceptExtensionRequest(context, polyReq, 1)
        assertNotNull(polyResp)
        assertEquals("application/javascript", polyResp!!.mimeType)

        BrowserExtensionManager.removeExtension(ext.id)
    }

    @Test
    fun crx3ProtobufHeaderExtractsAuthenticExtensionIdAndStagesInstall() {
        val manifest = """
            {
              "manifest_version": 3,
              "name": "Privacy Filter",
              "version": "1.0.0",
              "permissions": ["storage", "declarativeNetRequest", "tabs"],
              "host_permissions": ["<all_urls>"]
            }
        """.trimIndent()
        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toByteArray())
            zos.closeEntry()
        }
        val zipBytes = zipOut.toByteArray()

        // Construct valid CRX3 protobuf header: field 10000 (tag (10000 shl 3) or 2 = 80002 -> varint 92 f1 04)
        // containing SignedData { field 1 crx_id = 16 bytes [0x01, 0x23, ...] }
        val crxId16 = byteArrayOf(
            0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(),
            0x10, 0x32, 0x54, 0x76, 0x98.toByte(), 0xBA.toByte(), 0xDC.toByte(), 0xFE.toByte()
        )
        val expectedId = CrxExtensionInstaller.bytes16ToChromeId(crxId16)!!
        val signedData = ByteArrayOutputStream().apply {
            write(0x0A) // field 1, wire type 2
            write(16)
            write(crxId16)
        }.toByteArray()
        val protoHeader = ByteArrayOutputStream().apply {
            // varint for (10000 << 3) | 2 = 80002 (0x13882): 0x82, 0xF1, 0x04
            write(0x82)
            write(0xF1)
            write(0x04)
            write(signedData.size)
            write(signedData)
        }.toByteArray()

        val crxOut = ByteArrayOutputStream().apply {
            write("Cr24".toByteArray(Charsets.US_ASCII))
            write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(3).array())
            write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(protoHeader.size).array())
            write(protoHeader)
            write(zipBytes)
        }.toByteArray()

        assertEquals(expectedId, CrxExtensionInstaller.extractCrxExtensionId(crxOut))

        val stagedRes = CrxExtensionInstaller.stageFromArchiveBytes(
            context = context,
            rawBytes = crxOut,
            sourceType = "crx"
        )
        assertTrue(stagedRes.isSuccess)
        val staged = stagedRes.getOrNull()!!
        assertEquals(expectedId, staged.extension.id)

        val finalized = CrxExtensionInstaller.finalizeStagedInstall(context, staged, pinOnInstall = true)
        assertTrue(finalized.isSuccess)
        val installed = BrowserExtensionManager.extensions.value.firstOrNull { it.id == expectedId }
        assertNotNull(installed)
        assertTrue(installed!!.pinned)

        BrowserExtensionManager.toggleAllowUserScripts(expectedId, true)
        assertTrue(BrowserExtensionManager.extensions.value.first { it.id == expectedId }.allowUserScripts)
        assertTrue(BrowserExtensionManager.removeExtension(expectedId))
    }

    @Test
    fun tabsQueryReturnsOnlySingleActiveWebTabWhenExtensionPageIsForeground() {
        val bridge = BrowserExtensionRuntimeBridge.ChromeJsBridge(context, { null })
        BrowserExtensionRuntimeBridge.registerOrUpdateTab(
            tabId = "web-a",
            url = "https://example.com/dashboard",
            title = "Dashboard",
            isActive = true,
            isPrivate = false,
            isExtensionPage = false,
            webView = null
        )
        BrowserExtensionRuntimeBridge.registerOrUpdateTab(
            tabId = "ext-b",
            url = "https://ext-demo.jarvis.invalid/popup.html",
            title = "Demo Ext",
            isActive = true,
            isPrivate = false,
            isExtensionPage = true,
            webView = null
        )

        val activeArr = org.json.JSONArray(bridge.tabsQuery("ui", "{\"active\":true}"))
        assertEquals(1, activeArr.length())
        assertEquals("https://example.com/dashboard", activeArr.getJSONObject(0).getString("url"))

        val patternArr = org.json.JSONArray(bridge.tabsQuery("worker", "{\"url\":[\"https://*.example.com/*\"]}"))
        assertEquals(1, patternArr.length())

        BrowserExtensionRuntimeBridge.unregisterTab("web-a")
        BrowserExtensionRuntimeBridge.unregisterTab("ext-b")
    }

    @Test
    fun mv2BackgroundScriptsNewTabOverrideLocaleAndCssRewritingWork() {
        val manifest = """
            {
              "manifest_version": 2,
              "name": "__MSG_extName__",
              "version": "2.0.0",
              "description": "__MSG_extDesc__",
              "default_locale": "en",
              "browser_action": {
                "default_title": "__MSG_extName__"
              },
              "chrome_url_overrides": {
                "newtab": "newtab.html"
              },
              "background": {
                "scripts": ["helper.js", "background.js"]
              },
              "content_scripts": [
                {
                  "matches": ["https://example.org/*"],
                  "css": ["inject.css"],
                  "js": ["cs1.js", "cs2.js"],
                  "include_globs": ["*example.org/app*"],
                  "exclude_globs": ["*logout*"]
                }
              ]
            }
        """.trimIndent()
        val messagesEn = """
            {
              "extName": { "message": "Localized Helper" },
              "extDesc": { "message": "Hello ${'$'}USER${'$'}!", "placeholders": { "user": { "content": "${'$'}1" } } }
            }
        """.trimIndent()

        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json")); zos.write(manifest.toByteArray()); zos.closeEntry()
            zos.putNextEntry(ZipEntry("_locales/en/messages.json")); zos.write(messagesEn.toByteArray()); zos.closeEntry()
            zos.putNextEntry(ZipEntry("newtab.html")); zos.write("<html><body>NewTab</body></html>".toByteArray()); zos.closeEntry()
            zos.putNextEntry(ZipEntry("helper.js")); zos.write("var SHARED = 42;".toByteArray()); zos.closeEntry()
            zos.putNextEntry(ZipEntry("background.js")); zos.write("importScripts('helper.js');".toByteArray()); zos.closeEntry()
            zos.putNextEntry(ZipEntry("inject.css")); zos.write("body { background-image: url('chrome-extension://__MSG_@@extension_id__/bg.png'); }".toByteArray()); zos.closeEntry()
            zos.putNextEntry(ZipEntry("cs1.js")); zos.write("var x = 1;".toByteArray()); zos.closeEntry()
            zos.putNextEntry(ZipEntry("cs2.js")); zos.write("console.log(x);".toByteArray()); zos.closeEntry()
        }

        val res = CrxExtensionInstaller.installFromArchiveBytes(
            context = context,
            rawBytes = zipOut.toByteArray(),
            explicitId = "localized-mv2-ext",
            sourceType = "zip"
        )
        assertTrue(res.isSuccess)
        val ext = res.getOrNull()!!
        assertEquals("Localized Helper", ext.name)
        assertEquals(listOf("helper.js", "background.js"), ext.backgroundScripts)
        assertEquals("newtab.html", ext.newTabOverridePage)
        assertEquals(ext.id, BrowserExtensionManager.getActiveNewTabOverride()?.id)

        val workerReq = object : android.webkit.WebResourceRequest {
            override fun getUrl(): android.net.Uri = android.net.Uri.parse("${ext.extensionOrigin()}/__jarvis_worker_host.html")
            override fun isForMainFrame(): Boolean = true
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        }
        val workerHtml = BrowserExtensionRuntimeBridge.interceptExtensionRequest(context, workerReq, 1)!!
            .data.bufferedReader().readText()
        assertTrue(workerHtml.contains("<script src=\"/helper.js\"></script>"))
        assertTrue(workerHtml.contains("<script src=\"/background.js\"></script>"))
        assertTrue(workerHtml.contains("window.importScripts"))

        val cssReq = object : android.webkit.WebResourceRequest {
            override fun getUrl(): android.net.Uri = android.net.Uri.parse("${ext.extensionOrigin()}/inject.css")
            override fun isForMainFrame(): Boolean = false
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        }
        val cssServed = BrowserExtensionRuntimeBridge.interceptExtensionRequest(context, cssReq, 1)!!
            .data.bufferedReader().readText()
        assertTrue(cssServed.contains("${ext.extensionOrigin()}/bg.png"))

        // Glob matching check
        val spec = ext.contentScripts.first()
        assertTrue(ExtensionPageInjector.matchesContentScript("https://example.org/app/home", spec))
        assertFalse(ExtensionPageInjector.matchesContentScript("https://example.org/app/logout", spec))
        assertFalse(ExtensionPageInjector.matchesContentScript("https://example.org/other", spec))

        BrowserExtensionManager.removeExtension(ext.id)
    }

    @Test
    fun declarativeNetRequestAndContextMenusAndCwsHookWork() {
        val manifest = """
            {
              "manifest_version": 3,
              "name": "DNR Guard",
              "version": "1.0.0",
              "permissions": ["declarativeNetRequest", "contextMenus", "storage"],
              "declarative_net_request": {
                "rule_resources": [
                  { "id": "ruleset_1", "enabled": true, "path": "rules.json" }
                ]
              }
            }
        """.trimIndent()
        val staticRules = """
            [
              {
                "id": 1,
                "priority": 1,
                "action": { "type": "block" },
                "condition": { "urlFilter": "||tracker.example^" }
              }
            ]
        """.trimIndent()

        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json")); zos.write(manifest.toByteArray()); zos.closeEntry()
            zos.putNextEntry(ZipEntry("rules.json")); zos.write(staticRules.toByteArray()); zos.closeEntry()
        }
        val ext = CrxExtensionInstaller.installFromArchiveBytes(
            context = context,
            rawBytes = zipOut.toByteArray(),
            explicitId = "dnr-guard-test",
            sourceType = "zip"
        ).getOrThrow()

        val blocked = BrowserExtensionManager.evaluateDeclarativeNetRequest("https://ads.tracker.example/pixel.js")
        assertEquals("block", blocked.actionType)

        val bridge = BrowserExtensionRuntimeBridge.ChromeJsBridge(context, { null })
        bridge.dnrUpdateRules(
            ext.id,
            true,
            """
            {
              "addRules": [
                {
                  "id": 10,
                  "priority": 5,
                  "action": { "type": "allow" },
                  "condition": { "urlFilter": "safe.tracker.example" }
                },
                {
                  "id": 11,
                  "priority": 3,
                  "action": { "type": "redirect", "redirect": { "url": "https://clean.example/" } },
                  "condition": { "urlFilter": "||old.example/" }
                }
              ]
            }
            """.trimIndent()
        )
        assertEquals("allow", BrowserExtensionManager.evaluateDeclarativeNetRequest("https://safe.tracker.example/ok").actionType)
        val redir = BrowserExtensionManager.evaluateDeclarativeNetRequest("https://old.example/page")
        assertEquals("redirect", redir.actionType)
        assertEquals("https://clean.example/", redir.redirectUrl)

        bridge.actionSetBadgeText(ext.id, "9+")
        assertEquals("9+", BrowserExtensionManager.actionUiStates.value[ext.id]?.badgeText)

        bridge.contextMenusCreate(ext.id, """{"id":"m1","title":"Inspect Element"}""")
        assertTrue(BrowserExtensionManager.contextMenuItems.value.any { it.extensionId == ext.id && it.itemId == "m1" })

        val cwsHook = ExtensionPageInjector.buildChromeWebStorePageHookJs("cjpalhdlnbpafiamejdnhcphjbkeiagm")
        assertTrue(cwsHook.contains("requestWebStoreInstall"))

        BrowserExtensionManager.removeExtension(ext.id)
    }

    @Test
    fun htmlViewportMetaInjectedOnlyWhenMissingForExtensionPopupSheetParity() {
        val manifest = """
            {
              "manifest_version": 3,
              "name": "Viewport Parity",
              "version": "1.0.0",
              "action": { "default_popup": "no_vp.html" }
            }
        """.trimIndent()
        val noVpHtml = "<!DOCTYPE html><html><head><title>No VP</title></head><body><main>Hi</main></body></html>"
        val withVpHtml = "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=420, initial-scale=1\"></head><body><main>Ok</main></body></html>"

        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json")); zos.write(manifest.toByteArray()); zos.closeEntry()
            zos.putNextEntry(ZipEntry("no_vp.html")); zos.write(noVpHtml.toByteArray()); zos.closeEntry()
            zos.putNextEntry(ZipEntry("with_vp.html")); zos.write(withVpHtml.toByteArray()); zos.closeEntry()
        }
        val ext = CrxExtensionInstaller.installFromArchiveBytes(
            context = context,
            rawBytes = zipOut.toByteArray(),
            explicitId = "viewport-parity-test",
            sourceType = "zip"
        ).getOrThrow()

        fun reqFor(file: String) = object : android.webkit.WebResourceRequest {
            override fun getUrl(): android.net.Uri = android.net.Uri.parse("${ext.extensionOrigin()}/$file")
            override fun isForMainFrame(): Boolean = true
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        }

        val noVpServed = BrowserExtensionRuntimeBridge.interceptExtensionRequest(context, reqFor("no_vp.html"), 1)!!
            .data.bufferedReader().use { it.readText() }
        assertTrue(noVpServed.contains("""<meta name="viewport" content="width=device-width, initial-scale=1.0">"""))

        val withVpServed = BrowserExtensionRuntimeBridge.interceptExtensionRequest(context, reqFor("with_vp.html"), 1)!!
            .data.bufferedReader().use { it.readText() }
        assertTrue(withVpServed.contains("width=420"))
        assertFalse(withVpServed.contains("width=device-width, initial-scale=1.0"))

        BrowserExtensionManager.removeExtension(ext.id)
    }
}
