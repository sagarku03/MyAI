package com.jarvisai.assistant.browser

import android.content.Context
import android.net.Uri
import android.os.Build
import android.webkit.WebSettings
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Configures Android WebView identity, viewport/desktop layout settings, URL rewriting,
 * and Client Hints to match standard Google Chrome (Mobile or Desktop), preventing
 * Google OAuth (`Error 403: disallowed_useragent`) blocks and ensuring true Desktop Site
 * rendering across YouTube, Facebook, Wikipedia, GitHub, and responsive web apps.
 */
object BrowserIdentity {

    private const val CHROME_FULL_VERSION = "134.0.6998.135"
    private const val CHROME_MAJOR_VERSION = 134

    private val GOOGLE_AUTH_HOSTS = setOf(
        "accounts.google.com",
        "accounts.youtube.com",
        "myaccount.google.com",
        "oauth2.googleapis.com",
        "gds.google.com"
    )

    /**
     * Returns true if the given URL belongs to a Google OAuth or account sign-in flow.
     * Script injection (PageAgent, Eruda, input locks, extension content scripts, desktop viewport)
     * must be skipped on these origins to avoid triggering anti-bot/anti-tamper protections.
     */
    fun isGoogleSignInUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        return GOOGLE_AUTH_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /**
     * Builds a clean Chrome User-Agent string without the `; wv` Android WebView token
     * or `Version/4.0` marker that triggers OAuth blocks. Uses standard Windows Chrome Desktop UA
     * when [desktop] is true for maximum desktop-site compatibility.
     */
    fun buildUserAgent(defaultUserAgent: String?, desktop: Boolean): String {
        if (desktop) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME_MAJOR_VERSION.0.0.0 Safari/537.36"
        }
        val cleaned = defaultUserAgent
            ?.replace("; wv)", ")")
            ?.replace("; wv;", ";")
            ?.replace(Regex("Version/\\d+\\.\\d+\\s+"), "")
            ?.trim()
        if (!cleaned.isNullOrBlank() && cleaned.contains("Chrome/")) {
            return cleaned
        }
        val model = Build.MODEL.takeIf { it.isNotBlank() } ?: "Pixel 9 Pro"
        val release = Build.VERSION.RELEASE.takeIf { it.isNotBlank() } ?: "15"
        return "Mozilla/5.0 (Linux; Android $release; $model) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME_MAJOR_VERSION.0.0.0 Mobile Safari/537.36"
    }

    /**
     * Applies Chrome identity spoofing, strips the `X-Requested-With` package header,
     * configures `Sec-CH-UA` Client Hints metadata, and applies matching viewport settings
     * on the target [WebSettings].
     */
    fun applyTo(settings: WebSettings, context: Context, desktop: Boolean) {
        val rawDefaultUa = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
        settings.userAgentString = buildUserAgent(rawDefaultUa, desktop)

        // 1. Strip X-Requested-With header so origins cannot detect embedded APK package ID
        stripRequestedWithHeader(settings)

        // 2. Override Sec-CH-UA Client Hints to replace "Android WebView" with "Google Chrome"
        applyClientHintsMetadata(settings, desktop)

        // 3. Configure WebView viewport and layout algorithm for Desktop vs Mobile mode
        applyViewportSettings(settings, desktop)
    }

    /**
     * Configures zoom, overview scaling, and layout algorithm for Desktop vs Mobile rendering.
     * Desktop mode uses `LayoutAlgorithm.NORMAL` so text autosizing does not distort desktop grids.
     */
    fun applyViewportSettings(settings: WebSettings, desktop: Boolean) {
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.layoutAlgorithm = if (desktop) {
            WebSettings.LayoutAlgorithm.NORMAL
        } else {
            WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        }
        settings.textZoom = 100
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
    }

    /**
     * Rewrites mobile-specific subdomains (such as `m.youtube.com`, `m.facebook.com`,
     * `en.m.wikipedia.org`, `mobile.twitter.com`) to their desktop equivalents when enabling
     * Desktop Mode, and cleanly reverts YouTube's forced desktop query/host when returning to Mobile.
     */
    fun rewriteUrlForDesktopMode(rawUrl: String, desktop: Boolean): String {
        if (rawUrl.isBlank()) return rawUrl
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return rawUrl
        val scheme = uri.scheme?.lowercase() ?: return rawUrl
        if (scheme != "http" && scheme != "https") return rawUrl
        val host = uri.host?.lowercase() ?: return rawUrl

        if (desktop) {
            if (host == "m.youtube.com" || host == "youtube.com" || host == "www.youtube.com") {
                val builder = uri.buildUpon().authority("www.youtube.com").clearQuery()
                for (name in uri.queryParameterNames) {
                    if (name.equals("app", ignoreCase = true) || name.equals("persist_app", ignoreCase = true)) {
                        continue
                    }
                    uri.getQueryParameters(name).forEach { value ->
                        builder.appendQueryParameter(name, value)
                    }
                }
                builder.appendQueryParameter("app", "desktop")
                return builder.build().toString()
            }
            if (host == "m.facebook.com" || host == "mbasic.facebook.com") {
                return uri.buildUpon().authority("www.facebook.com").build().toString()
            }
            if (host == "mobile.twitter.com" || host == "mobile.x.com") {
                return uri.buildUpon().authority("x.com").build().toString()
            }
            if (host.endsWith(".m.wikipedia.org")) {
                val desktopWikiHost = host.replace(".m.wikipedia.org", ".wikipedia.org")
                return uri.buildUpon().authority(desktopWikiHost).build().toString()
            }
            if (host.startsWith("m.") && host.count { it == '.' } >= 2) {
                val desktopHost = "www." + host.removePrefix("m.")
                return uri.buildUpon().authority(desktopHost).build().toString()
            }
            if (host.startsWith("mobile.") && host.count { it == '.' } >= 2) {
                val desktopHost = "www." + host.removePrefix("mobile.")
                return uri.buildUpon().authority(desktopHost).build().toString()
            }
            return rawUrl
        } else {
            if (host == "www.youtube.com" || host == "youtube.com" || host == "m.youtube.com") {
                val builder = uri.buildUpon().authority("m.youtube.com").clearQuery()
                for (name in uri.queryParameterNames) {
                    if (name.equals("app", ignoreCase = true) || name.equals("persist_app", ignoreCase = true)) {
                        continue
                    }
                    uri.getQueryParameters(name).forEach { value ->
                        builder.appendQueryParameter(name, value)
                    }
                }
                return builder.build().toString()
            }
            if (uri.getQueryParameter("app") == "desktop") {
                val builder = uri.buildUpon().clearQuery()
                for (name in uri.queryParameterNames) {
                    if (name.equals("app", ignoreCase = true) || name.equals("persist_app", ignoreCase = true)) {
                        continue
                    }
                    uri.getQueryParameters(name).forEach { value ->
                        builder.appendQueryParameter(name, value)
                    }
                }
                return builder.build().toString()
            }
            return rawUrl
        }
    }

    /**
     * JavaScript snippet injected on Desktop-mode pages to enforce a 1280px desktop layout
     * viewport scaled cleanly to the device width with pinch-to-zoom enabled.
     */
    fun buildDesktopViewportScript(): String = """
        (function() {
          try {
            var desktopWidth = 1280;
            var screenW = Math.min(window.screen.width || 390, window.screen.height || 844);
            if (window.innerWidth > window.innerHeight) {
              screenW = Math.max(window.screen.width || 844, window.screen.height || 390);
            }
            var scale = Math.max(0.25, Math.min(1.0, screenW / desktopWidth));
            var content = 'width=' + desktopWidth + ', initial-scale=' + scale.toFixed(3) + ', minimum-scale=0.2, maximum-scale=5.0, user-scalable=yes';
            var head = document.head || document.documentElement;
            if (!head) return;
            var meta = document.querySelector('meta[name="viewport"]');
            if (!meta) {
              meta = document.createElement('meta');
              meta.setAttribute('name', 'viewport');
              head.appendChild(meta);
            }
            if (meta.getAttribute('content') !== content) {
              meta.setAttribute('content', content);
            }
            window.dispatchEvent(new Event('resize'));
          } catch (e) {}
        })();
    """.trimIndent()

    private fun stripRequestedWithHeader(settings: WebSettings) {
        runCatching {
            if (WebViewFeature.isFeatureSupported("REQUESTED_WITH_HEADER_ALLOW_LIST")) {
                val method = WebSettingsCompat::class.java.getMethod(
                    "setRequestedWithHeaderOriginAllowList",
                    WebSettings::class.java,
                    Set::class.java
                )
                method.invoke(null, settings, emptySet<String>())
            }
        }
    }

    private fun applyClientHintsMetadata(settings: WebSettings, desktop: Boolean) {
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
                val existing = runCatching { WebSettingsCompat.getUserAgentMetadata(settings) }.getOrNull()
                val existingBrands = existing?.brandVersionList.orEmpty()

                val updatedBrands = if (existingBrands.isNotEmpty()) {
                    existingBrands.map { brand ->
                        if (brand.brand.contains("WebView", ignoreCase = true)) {
                            UserAgentMetadata.BrandVersion.Builder()
                                .setBrand("Google Chrome")
                                .setMajorVersion(brand.majorVersion.ifBlank { CHROME_MAJOR_VERSION.toString() })
                                .setFullVersion(brand.fullVersion.ifBlank { CHROME_FULL_VERSION })
                                .build()
                        } else {
                            brand
                        }
                    }
                } else {
                    listOf(
                        UserAgentMetadata.BrandVersion.Builder()
                            .setBrand("Chromium")
                            .setMajorVersion(CHROME_MAJOR_VERSION.toString())
                            .setFullVersion(CHROME_FULL_VERSION)
                            .build(),
                        UserAgentMetadata.BrandVersion.Builder()
                            .setBrand("Google Chrome")
                            .setMajorVersion(CHROME_MAJOR_VERSION.toString())
                            .setFullVersion(CHROME_FULL_VERSION)
                            .build(),
                        UserAgentMetadata.BrandVersion.Builder()
                            .setBrand("Not-A.Brand")
                            .setMajorVersion("99")
                            .setFullVersion("99.0.0.0")
                            .build()
                    )
                }

                val metadata = UserAgentMetadata.Builder()
                    .setBrandVersionList(updatedBrands)
                    .setFullVersion(existing?.fullVersion ?: CHROME_FULL_VERSION)
                    .setPlatform(if (desktop) "Windows" else (existing?.platform ?: "Android"))
                    .setPlatformVersion(if (desktop) "15.0.0" else (existing?.platformVersion ?: (Build.VERSION.RELEASE ?: "15.0.0")))
                    .setArchitecture(if (desktop) "x86" else (existing?.architecture ?: "arm"))
                    .setBitness(if (desktop) 64 else (existing?.bitness ?: 64))
                    .setModel(if (desktop) "" else (existing?.model ?: Build.MODEL.orEmpty()))
                    .setMobile(!desktop)
                    .setWow64(false)
                    .build()

                WebSettingsCompat.setUserAgentMetadata(settings, metadata)
            }
        }
    }
}
