package com.jarvisai.assistant.extension

import android.webkit.WebView
import org.json.JSONObject
import java.io.File

/**
 * Matches Chrome extension `content_scripts` patterns against active URLs and injects
 * CSS/JS + full `chrome.*` polyfills backed by [BrowserExtensionRuntimeBridge].
 */
object ExtensionPageInjector {

    fun matchesPattern(pageUrl: String, pattern: String): Boolean {
        val trimmedUrl = pageUrl.trim()
        val trimmedPattern = pattern.trim()
        if (trimmedUrl.isEmpty() || trimmedPattern.isEmpty()) return false
        if (trimmedPattern == "<all_urls>") {
            return trimmedUrl.startsWith("http://") ||
                trimmedUrl.startsWith("https://") ||
                trimmedUrl.startsWith("file://")
        }
        if (trimmedPattern == "*" || trimmedPattern == "*://*/*") {
            return trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")
        }

        val schemeSep = trimmedPattern.indexOf("://")
        if (schemeSep <= 0) return false
        val patScheme = trimmedPattern.substring(0, schemeSep).lowercase()
        val rest = trimmedPattern.substring(schemeSep + 3)
        val pathSep = rest.indexOf('/')
        val patHost = if (pathSep >= 0) rest.substring(0, pathSep).lowercase() else rest.lowercase()
        val patPath = if (pathSep >= 0) rest.substring(pathSep) else "/*"

        val urlSchemeSep = trimmedUrl.indexOf("://")
        if (urlSchemeSep <= 0) return false
        val urlScheme = trimmedUrl.substring(0, urlSchemeSep).lowercase()
        val urlRest = trimmedUrl.substring(urlSchemeSep + 3)
        val urlPathSep = urlRest.indexOf('/')
        val urlHostWithPort = if (urlPathSep >= 0) urlRest.substring(0, urlPathSep).lowercase() else urlRest.lowercase()
        val urlHost = urlHostWithPort.substringBefore(':')
        val urlPath = if (urlPathSep >= 0) urlRest.substring(urlPathSep) else "/"

        if (patScheme != "*" && patScheme != urlScheme) return false
        if (patScheme == "*" && urlScheme != "http" && urlScheme != "https") return false

        val hostMatch = when {
            patHost == "*" -> true
            patHost.startsWith("*.") -> {
                val baseDomain = patHost.removePrefix("*.")
                urlHost == baseDomain || urlHost.endsWith(".$baseDomain")
            }
            else -> patHost.substringBefore(':') == urlHost
        }
        if (!hostMatch) return false

        val regexStr = buildString {
            append('^')
            for (ch in patPath) {
                if (ch == '*') append(".*") else append(Regex.escape(ch.toString()))
            }
            append('$')
        }
        val urlPathClean = urlPath.substringBefore('#').substringBefore('?')
        val fullUrlPathClean = urlPath.substringBefore('#')
        return runCatching {
            val regex = Regex(regexStr)
            regex.matches(fullUrlPathClean) || regex.matches(urlPathClean)
        }.getOrDefault(false)
    }

    fun matchesContentScript(url: String, spec: ContentScriptSpec): Boolean {
        if (spec.excludeMatches.any { matchesPattern(url, it) }) return false
        return spec.matches.any { matchesPattern(url, it) }
    }

    /**
     * Injects all matching enabled extension CSS and JS scripts for [url] at [runAt] phase
     * (`document_start` or `document_idle`). Respects private browsing permissions ([isPrivateTab]).
     */
    fun injectForPage(
        webView: WebView?,
        url: String?,
        extensions: List<BrowserExtension>,
        tabNumericId: Int = 1,
        isPrivateTab: Boolean = false,
        runAt: String = "document_idle"
    ) {
        if (webView == null || url.isNullOrBlank()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) return

        val enabled = extensions.filter {
            it.isEnabled &&
                it.contentScripts.isNotEmpty() &&
                (!isPrivateTab || it.allowInPrivate)
        }
        if (enabled.isEmpty()) return

        val matchingPairs = enabled.flatMap { ext ->
            ext.contentScripts
                .filter { spec ->
                    val phaseMatches = when (runAt) {
                        "document_start" -> spec.runAt == "document_start"
                        else -> spec.runAt != "document_start"
                    }
                    phaseMatches && matchesContentScript(url, spec)
                }
                .map { ext to it }
        }

        if (matchingPairs.isEmpty()) return

        val scriptBuilder = StringBuilder()
        var injectedAnyBootstrap = false

        for ((ext, spec) in matchingPairs) {
            val dir = File(ext.dirPath)
            if (!dir.exists()) continue

            if (!injectedAnyBootstrap) {
                scriptBuilder.append(
                    BrowserExtensionRuntimeBridge.buildUnifiedChromePolyfillJs(
                        ext = ext,
                        contextType = "content",
                        tabNumericId = tabNumericId
                    )
                ).append('\n')
                injectedAnyBootstrap = true
            }

            for (cssRel in spec.css) {
                val cssFile = File(dir, cssRel.removePrefix("/"))
                if (cssFile.exists() && cssFile.isFile) {
                    val cssContent = runCatching { cssFile.readText() }.getOrNull().orEmpty()
                    if (cssContent.isNotBlank()) {
                        scriptBuilder.append(
                            """
                            (function(){
                                try {
                                    var st = document.createElement('style');
                                    st.setAttribute('data-jarvis-ext', ${JSONObject.quote(ext.id)});
                                    st.textContent = ${JSONObject.quote(cssContent)};
                                    (document.head || document.documentElement).appendChild(st);
                                } catch(e) {}
                            })();
                            """.trimIndent()
                        ).append('\n')
                    }
                }
            }

            if (spec.js.isNotEmpty()) {
                val manifestRaw = runCatching { File(dir, "manifest.json").readText() }.getOrDefault("{}")
                val safeManifest = try { JSONObject(manifestRaw).toString() } catch (_: Exception) { "{}" }
                for (jsRel in spec.js) {
                    val jsFile = File(dir, jsRel.removePrefix("/"))
                    if (jsFile.exists() && jsFile.isFile) {
                        val jsContent = runCatching { jsFile.readText() }.getOrNull().orEmpty()
                        if (jsContent.isNotBlank()) {
                            scriptBuilder.append(
                                """
                                (function() {
                                    var chrome = window.__jarvisMakeChromeApi(${JSONObject.quote(ext.id)}, ${JSONObject.quote(ext.extensionOrigin())}, $safeManifest);
                                    try {
                                        $jsContent
                                    } catch(err) {
                                        console.warn('[JarvisExt:' + ${JSONObject.quote(ext.id)} + ']', err);
                                    }
                                })();
                                """.trimIndent()
                            ).append('\n')
                        }
                    }
                }
            }
        }

        val finalScript = scriptBuilder.toString()
        if (finalScript.isNotBlank()) {
            webView.evaluateJavascript(finalScript, null)
        }
    }
}
