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

    fun globToRegex(glob: String): Regex {
        val sb = StringBuilder("^")
        for (ch in glob) {
            when (ch) {
                '*' -> sb.append(".*")
                '?' -> sb.append('.')
                else -> sb.append(Regex.escape(ch.toString()))
            }
        }
        sb.append('$')
        return Regex(sb.toString())
    }

    fun matchesContentScript(url: String, spec: ContentScriptSpec): Boolean {
        if (spec.excludeMatches.any { matchesPattern(url, it) }) return false
        if (spec.excludeGlobs.any { runCatching { globToRegex(it).matches(url) }.getOrDefault(false) }) return false
        val matched = spec.matches.any { matchesPattern(url, it) }
        if (!matched) return false
        if (spec.includeGlobs.isNotEmpty() &&
            spec.includeGlobs.none { runCatching { globToRegex(it).matches(url) }.getOrDefault(false) }
        ) {
            return false
        }
        return true
    }

    /**
     * Builds the JavaScript snippet injected on `chromewebstore.google.com` pages so tapping
     * Google's in-page "Add to Chrome" button opens Agentra's native install confirmation dialog.
     */
    fun buildChromeWebStorePageHookJs(extensionId: String): String {
        val quotedId = JSONObject.quote(extensionId)
        return """
        (function() {
            if (window.__jarvisCwsHookedId === $quotedId) return;
            window.__jarvisCwsHookedId = $quotedId;
            if (!window.__jarvisCwsClickListenerAttached) {
                window.__jarvisCwsClickListenerAttached = true;
                document.addEventListener('click', function(e) {
                    var el = e.target;
                    while (el && el !== document.body) {
                        var tag = (el.tagName || '').toLowerCase();
                        var role = (el.getAttribute && el.getAttribute('role')) || '';
                        if (tag === 'button' || role === 'button') {
                            var txt = ((el.innerText || el.textContent || '') + ' ' + (el.getAttribute('aria-label') || '')).toLowerCase();
                            if (txt.indexOf('add to chrome') !== -1 ||
                                txt.indexOf('add to brave') !== -1 ||
                                txt.indexOf('add to quetta') !== -1 ||
                                txt.indexOf('remove from chrome') !== -1 ||
                                txt.indexOf('install') !== -1) {
                                e.preventDefault();
                                e.stopPropagation();
                                var curId = window.__jarvisCwsHookedId;
                                var h1 = document.querySelector('h1');
                                var title = h1 ? (h1.innerText || '').trim() : document.title;
                                if (window.__jarvis_chrome_bridge && window.__jarvis_chrome_bridge.requestWebStoreInstall) {
                                    window.__jarvis_chrome_bridge.requestWebStoreInstall(curId, title || '');
                                }
                                return;
                            }
                        }
                        el = el.parentElement;
                    }
                }, true);
            }
        })();
        """.trimIndent()
    }

    /**
     * Injects all matching enabled extension CSS and JS scripts for [url] at [runAt] phase
     * (`document_start`, `document_end`, or `document_idle`). Respects private browsing permissions ([isPrivateTab]).
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
                        "document_end" -> spec.runAt == "document_end"
                        else -> spec.runAt != "document_start" && spec.runAt != "document_end"
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
                    val rawCss = runCatching { cssFile.readText() }.getOrNull().orEmpty()
                    val resolvedCss = BrowserExtensionRuntimeBridge.rewriteExtensionAssetReferences(ext, rawCss)
                    if (resolvedCss.isNotBlank()) {
                        scriptBuilder.append(
                            """
                            (function(){
                                try {
                                    var st = document.createElement('style');
                                    st.setAttribute('data-jarvis-ext', ${JSONObject.quote(ext.id)});
                                    st.textContent = ${JSONObject.quote(resolvedCss)};
                                    (document.head || document.documentElement).appendChild(st);
                                } catch(e) {}
                            })();
                            """.trimIndent()
                        ).append('\n')
                    }
                }
            }

            // Combine all JS files in a single content_scripts entry so they share one isolated scope,
            // matching Chromium's ContentScript isolated-world execution semantics.
            if (spec.js.isNotEmpty()) {
                val manifestRaw = runCatching { File(dir, "manifest.json").readText() }.getOrDefault("{}")
                val safeManifest = try { JSONObject(manifestRaw).toString() } catch (_: Exception) { "{}" }
                val combinedJs = StringBuilder()
                for (jsRel in spec.js) {
                    val jsFile = File(dir, jsRel.removePrefix("/"))
                    if (jsFile.exists() && jsFile.isFile) {
                        val jsContent = runCatching { jsFile.readText() }.getOrNull().orEmpty()
                        if (jsContent.isNotBlank()) {
                            combinedJs.append("\n// --- ").append(jsRel).append(" ---\n")
                            combinedJs.append(jsContent).append("\n;\n")
                        }
                    }
                }
                if (combinedJs.isNotEmpty()) {
                    scriptBuilder.append(
                        """
                        (function() {
                            var chrome = window.__jarvisMakeChromeApi(${JSONObject.quote(ext.id)}, ${JSONObject.quote(ext.extensionOrigin())}, $safeManifest);
                            var browser = chrome;
                            try {
                                $combinedJs
                            } catch(err) {
                                console.warn('[JarvisExt:' + ${JSONObject.quote(ext.id)} + ']', err);
                            }
                        })();
                        """.trimIndent()
                    ).append('\n')
                }
            }
        }

        val finalScript = scriptBuilder.toString()
        if (finalScript.isNotBlank()) {
            webView.evaluateJavascript(finalScript, null)
        }
    }
}
