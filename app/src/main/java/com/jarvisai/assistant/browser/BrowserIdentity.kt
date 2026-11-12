package com.jarvisai.assistant.browser

import android.content.Context
import android.net.Uri
import android.os.Build
import android.webkit.WebSettings
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Configures Android WebView identity and Client Hints to match standard Google Chrome,
 * preventing Google OAuth (`Error 403: disallowed_useragent`) blocks and detecting
 * sensitive authentication origins where automation script injection must be skipped.
 */
object BrowserIdentity {

    private const val CHROME_FULL_VERSION = "131.0.6778.200"
    private const val CHROME_MAJOR_VERSION = 131

    private val GOOGLE_AUTH_HOSTS = setOf(
        "accounts.google.com",
        "accounts.youtube.com",
        "myaccount.google.com",
        "oauth2.googleapis.com",
        "gds.google.com"
    )

    /**
     * Returns true if the given URL belongs to a Google OAuth or account sign-in flow.
     * Script injection (PageAgent, Eruda, input locks, extension content scripts) must
     * be skipped on these origins to avoid triggering anti-bot/anti-tamper protections.
     */
    fun isGoogleSignInUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        return GOOGLE_AUTH_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /**
     * Builds a clean Chrome User-Agent string without the `; wv` Android WebView token
     * or `Version/4.0` marker that triggers OAuth blocks.
     */
    fun buildUserAgent(defaultUserAgent: String?, desktop: Boolean): String {
        if (desktop) {
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME_FULL_VERSION Safari/537.36"
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
        return "Mozilla/5.0 (Linux; Android $release; $model) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME_FULL_VERSION Mobile Safari/537.36"
    }

    /**
     * Applies Chrome identity spoofing, strips the `X-Requested-With` package header,
     * and configures `Sec-CH-UA` Client Hints metadata on the target [WebSettings].
     */
    fun applyTo(settings: WebSettings, context: Context, desktop: Boolean) {
        val rawDefaultUa = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
        settings.userAgentString = buildUserAgent(rawDefaultUa, desktop)

        // 1. Strip X-Requested-With header so origins cannot detect embedded APK package ID
        stripRequestedWithHeader(settings)

        // 2. Override Sec-CH-UA Client Hints to replace "Android WebView" with "Google Chrome"
        applyClientHintsMetadata(settings, desktop)
    }

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
                    .setPlatform(if (desktop) "Linux" else (existing?.platform ?: "Android"))
                    .setPlatformVersion(existing?.platformVersion ?: (Build.VERSION.RELEASE ?: "15.0.0"))
                    .setArchitecture(if (desktop) "x86" else (existing?.architecture ?: "arm"))
                    .setBitness(existing?.bitness ?: 64)
                    .setModel(if (desktop) "" else (existing?.model ?: Build.MODEL.orEmpty()))
                    .setMobile(!desktop)
                    .setWow64(false)
                    .build()

                WebSettingsCompat.setUserAgentMetadata(settings, metadata)
            }
        }
    }
}
