package com.jarvisai.assistant.browser

import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/**
 * Fast host & tracker blocking engine for Jarvis Browser's Privacy Shield.
 */
object BrowserAdBlockEngine {

    private val blockedHosts: Set<String> = hashSetOf(
        "doubleclick.net",
        "adservice.google.com",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "taboola.com",
        "outbrain.com",
        "scorecardresearch.com",
        "quantserve.com",
        "ads.yahoo.com",
        "adnxs.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "criteo.com",
        "criteo.net",
        "moatads.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "amplitude.com",
        "ads-twitter.com",
        "analytics.twitter.com",
        "pixel.facebook.com",
        "an.facebook.com",
        "Amazon-adsystem.com".lowercase(),
        "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com",
        "adserver.adtechus.com",
        "mgid.com",
        "revcontent.com",
        "propellerads.com",
        "popads.net",
        "exoclick.com",
        "juicyads.com",
        "zedo.com",
        "chartbeat.com",
        "newrelic.com"
    )

    private val trackingQueryParams: Set<String> = hashSetOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "mc_cid", "mc_eid", "igshid"
    )

    private val tabBlockedCounts = ConcurrentHashMap<String, Int>()

    fun shouldBlockRequest(url: String?, enabled: Boolean): Boolean {
        if (!enabled || url.isNullOrBlank()) return false
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        val host = extractHost(lower)
        if (host.isBlank()) return false

        if (blockedHosts.contains(host)) return true
        for (blocked in blockedHosts) {
            if (host.endsWith(".$blocked")) return true
        }

        // Common tracker beacons
        if (host.startsWith("ads.") || host.startsWith("ad.") || host.startsWith("pixel.")) {
            return true
        }
        return false
    }

    fun recordBlocked(tabId: String): Int {
        val next = (tabBlockedCounts[tabId] ?: 0) + 1
        tabBlockedCounts[tabId] = next
        return next
    }

    fun getBlockedCount(tabId: String): Int = tabBlockedCounts[tabId] ?: 0

    fun resetTabCount(tabId: String) {
        tabBlockedCounts.remove(tabId)
    }

    fun extractHost(url: String): String {
        return runCatching {
            Uri.parse(url).host?.lowercase()?.removePrefix("www.").orEmpty()
        }.getOrDefault("")
    }

    fun stripTrackingParams(url: String): String {
        return runCatching {
            val uri = Uri.parse(url)
            if (uri.queryParameterNames.isEmpty()) return url
            val builder = uri.buildUpon().clearQuery()
            var changed = false
            for (name in uri.queryParameterNames) {
                if (trackingQueryParams.contains(name.lowercase())) {
                    changed = true
                } else {
                    uri.getQueryParameters(name).forEach { value ->
                        builder.appendQueryParameter(name, value)
                    }
                }
            }
            if (changed) builder.build().toString() else url
        }.getOrDefault(url)
    }
}
