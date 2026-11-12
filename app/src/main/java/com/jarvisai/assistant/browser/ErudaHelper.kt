package com.jarvisai.assistant.browser

import android.os.Handler
import android.os.Looper
import android.webkit.WebView

/**
 * On-device mobile WebView developer console helper (`ErudaHelper`).
 * Injects the Eruda DevTools suite (DOM Elements, Console, Network, Resources, Sources, Info)
 * into the active [WebView] on the Android Main Looper and supports instant toggling.
 * Automatically skips Google Sign-In pages to preserve OAuth security integrity.
 */
object ErudaHelper {

    private const val ERUDA_CDN_URL = "https://cdn.jsdelivr.net/npm/eruda@3.4.1/eruda.min.js"
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Injects and initializes Eruda in [webView]. If [showImmediately] is true,
     * opens the floating DevTools panel as soon as initialization finishes.
     */
    fun inject(webView: WebView?, showImmediately: Boolean = true) {
        if (webView == null) return
        runOnMain {
            val currentUrl = webView.url
            if (BrowserIdentity.isGoogleSignInUrl(currentUrl)) return@runOnMain

            // Try loading bundled local asset first if present, falling back to jsDelivr CDN
            val localAssetJs = runCatching {
                webView.context.assets.open("eruda.min.js").bufferedReader().use { it.readText() }
            }.getOrNull()

            val showCall = if (showImmediately) "eruda.show();" else ""

            val script = if (!localAssetJs.isNullOrBlank()) {
                """
                (function() {
                    try {
                        if (!window.eruda) {
                            $localAssetJs
                        }
                        if (window.eruda && !window.__erudaInitialized) {
                            eruda.init({
                                tool: ['console', 'elements', 'network', 'resources', 'sources', 'info'],
                                useShadowDom: true,
                                autoScale: true
                            });
                            window.__erudaInitialized = true;
                            window.__erudaVisible = false;
                        }
                        if (window.eruda && $showImmediately) {
                            $showCall
                            window.__erudaVisible = true;
                        }
                        return 'eruda_ready_local';
                    } catch (e) {
                        return 'eruda_error:' + e.message;
                    }
                })();
                """.trimIndent()
            } else {
                """
                (function() {
                    try {
                        if (window.eruda) {
                            if (!window.__erudaInitialized) {
                                eruda.init({
                                    tool: ['console', 'elements', 'network', 'resources', 'sources', 'info'],
                                    useShadowDom: true,
                                    autoScale: true
                                });
                                window.__erudaInitialized = true;
                            }
                            if ($showImmediately) {
                                eruda.show();
                                window.__erudaVisible = true;
                            }
                            return 'eruda_already_loaded';
                        }
                        var existing = document.getElementById('__jarvis_eruda_script');
                        if (existing) return 'eruda_loading';
                        var script = document.createElement('script');
                        script.id = '__jarvis_eruda_script';
                        script.src = '$ERUDA_CDN_URL';
                        script.async = true;
                        script.onload = function() {
                            if (window.eruda) {
                                eruda.init({
                                    tool: ['console', 'elements', 'network', 'resources', 'sources', 'info'],
                                    useShadowDom: true,
                                    autoScale: true
                                });
                                window.__erudaInitialized = true;
                                if ($showImmediately) {
                                    eruda.show();
                                    window.__erudaVisible = true;
                                }
                            }
                        };
                        (document.head || document.documentElement).appendChild(script);
                        return 'eruda_injected_cdn';
                    } catch (e) {
                        return 'eruda_error:' + e.message;
                    }
                })();
                """.trimIndent()
            }

            webView.evaluateJavascript(script, null)
        }
    }

    /**
     * Toggles the visibility of the Eruda DevTools panel in [webView], injecting it first if needed.
     */
    fun toggle(webView: WebView?) {
        if (webView == null) return
        runOnMain {
            val currentUrl = webView.url
            if (BrowserIdentity.isGoogleSignInUrl(currentUrl)) return@runOnMain

            val checkAndToggleScript = """
                (function() {
                    if (!window.eruda || !window.__erudaInitialized) {
                        return 'NEED_INJECT';
                    }
                    if (window.__erudaVisible) {
                        eruda.hide();
                        window.__erudaVisible = false;
                        return 'HIDDEN';
                    } else {
                        eruda.show();
                        window.__erudaVisible = true;
                        return 'SHOWN';
                    }
                })();
            """.trimIndent()

            webView.evaluateJavascript(checkAndToggleScript) { result ->
                if (result?.contains("NEED_INJECT") == true) {
                    inject(webView, showImmediately = true)
                }
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
