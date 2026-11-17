package com.jarvisai.assistant.ui.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

data class AppReleaseInfo(
    val currentVersion: String,
    val newVersion: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isNewer: Boolean,
    val changelogItems: List<String> = emptyList(),
    val compareUrl: String? = null,
    val releasePageUrl: String = "https://github.com/sagarku03/MyAI/releases/latest",
    val assetApiUrl: String = ""
) {
    val tagName: String
        get() = newVersion
}

sealed interface ApkUpdateResult {
    data class LaunchedInstaller(val apkFile: File) : ApkUpdateResult
    data class NeedsUnknownSourcesPermission(val apkFile: File) : ApkUpdateResult
    data class SignatureConflict(
        val apkFile: File,
        val message: String,
        val browserUrl: String
    ) : ApkUpdateResult
}

data class DownloadProgress(
    val percent: Int,
    val downloadedBytes: Long,
    val totalBytes: Long
)

/**
 * Resilient DNS resolver for GitHub & CDN endpoints (github.com, api.github.com,
 * objects.githubusercontent.com, release-assets.githubusercontent.com).
 * Falls back to DNS-over-HTTPS (Cloudflare 1.1.1.1 & Google 8.8.8.8 via direct numeric IP,
 * requiring zero carrier DNS) whenever Android's system DNS fails with UnknownHostException.
 */
internal class ResilientGitHubDns(
    private val systemDns: Dns = Dns.SYSTEM,
    private val dohHttpClient: OkHttpClient = defaultDohClient,
    private val extraDohUrls: List<String> = DEFAULT_DOH_ENDPOINTS
) : Dns {

    private val addressCache = ConcurrentHashMap<String, List<InetAddress>>()

    override fun lookup(hostname: String): List<InetAddress> {
        val cleanHost = hostname.trim()
        if (cleanHost.isEmpty()) {
            throw UnknownHostException("Empty hostname")
        }

        // Fast-path for literal IP strings
        if (isValidIpv4Literal(cleanHost) || isValidIpv6Literal(cleanHost)) {
            return listOf(InetAddress.getByName(cleanHost))
        }

        // 1. Primary: System DNS (ordered IPv4 first to avoid broken mobile carrier IPv6 paths)
        val systemResult = runCatching { systemDns.lookup(cleanHost) }.getOrNull()
        if (!systemResult.isNullOrEmpty()) {
            val ordered = orderIpv4First(systemResult)
            addressCache[cleanHost] = ordered
            return ordered
        }

        // 2. Fallback: DNS-over-HTTPS via numeric IP endpoints (bypasses broken carrier DNS)
        val dohResult = resolveViaDoh(cleanHost)
        if (dohResult.isNotEmpty()) {
            val ordered = orderIpv4First(dohResult)
            addressCache[cleanHost] = ordered
            return ordered
        }

        // 3. Last-resort session cache fallback
        val cached = addressCache[cleanHost]
        if (!cached.isNullOrEmpty()) {
            return cached
        }

        throw UnknownHostException("Unable to resolve host \"$cleanHost\" via system DNS or DoH")
    }

    private fun resolveViaDoh(hostname: String): List<InetAddress> {
        val recordTypes = listOf("A", "AAAA")
        for (recordType in recordTypes) {
            for (endpoint in extraDohUrls) {
                val url = "$endpoint?name=$hostname&type=$recordType"
                val parsed = runCatching {
                    val req = Request.Builder()
                        .url(url)
                        .header("Accept", "application/dns-json")
                        .header("User-Agent", "JARVIS-Android-Updater")
                        .get()
                        .build()
                    dohHttpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use emptyList()
                        val body = resp.body?.string().orEmpty()
                        parseDohAddresses(hostname, body)
                    }
                }.getOrDefault(emptyList())
                if (parsed.isNotEmpty()) {
                    return parsed
                }
            }
        }
        return emptyList()
    }

    companion object {
        private val DEFAULT_DOH_ENDPOINTS = listOf(
            "https://1.1.1.1/dns-query",
            "https://8.8.8.8/resolve",
            "https://1.0.0.1/dns-query"
        )

        private val defaultDohClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .dns(Dns.SYSTEM)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .build()
        }

        private fun orderIpv4First(addresses: List<InetAddress>): List<InetAddress> {
            val ipv4 = addresses.filterIsInstance<Inet4Address>()
            val others = addresses.filter { it !is Inet4Address }
            return (ipv4 + others).distinctBy { it.hostAddress }
        }

        internal fun isValidIpv4Literal(ip: String): Boolean {
            val parts = ip.split(".")
            if (parts.size != 4) return false
            return parts.all { part ->
                part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() } &&
                    (part.toIntOrNull()?.let { it in 0..255 } == true)
            }
        }

        internal fun isValidIpv6Literal(ip: String): Boolean {
            if (!ip.contains(":")) return false
            return ip.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }
        }

        internal fun parseDohAddresses(hostname: String, jsonBody: String): List<InetAddress> {
            if (jsonBody.isBlank()) return emptyList()
            return runCatching {
                val json = JSONObject(jsonBody)
                val status = json.optInt("Status", 0)
                if (status != 0) return emptyList()
                val answers = json.optJSONArray("Answer") ?: return emptyList()
                val results = mutableListOf<InetAddress>()
                for (i in 0 until answers.length()) {
                    val answer = answers.optJSONObject(i) ?: continue
                    val recordType = answer.optInt("type", -1)
                    val data = answer.optString("data", "").trim()
                    val isValidIp = when (recordType) {
                        1 -> isValidIpv4Literal(data)
                        28 -> isValidIpv6Literal(data)
                        else -> false
                    }
                    if (isValidIp) {
                        runCatching {
                            val rawBytes = InetAddress.getByName(data).address
                            results.add(InetAddress.getByAddress(hostname, rawBytes))
                        }
                    }
                }
                orderIpv4First(results)
            }.getOrDefault(emptyList())
        }
    }
}

class AppUpdateManager(
    private val client: OkHttpClient = buildDefaultClient()
) {

    private val downloadClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    companion object {
        private const val REPO_OWNER = "sagarku03"
        private const val REPO_NAME = "MyAI"
        private const val GITHUB_API_LATEST_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
        private const val GITHUB_API_LIST_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases?per_page=5"
        private const val GITHUB_WEB_LATEST_URL = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"
        private const val RAW_VERSION_JSON_URL = "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/main/version.json"
        val CURRENT_VERSION_TAG: String
            get() = "v${BuildConfig.VERSION_NAME.removePrefix("v").trim()}"

        internal val sharedResilientDns: Dns by lazy { ResilientGitHubDns() }

        fun buildDefaultClient(): OkHttpClient = OkHttpClient.Builder()
            .dns(sharedResilientDns)
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun checkForUpdates(): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val currentVer = BuildConfig.VERSION_NAME.removePrefix("v").trim()

            // 1. Primary: GitHub REST API (/releases/latest or /releases list)
            val apiRelease = runCatching { fetchFromGitHubApi() }.getOrNull()
            if (apiRelease != null && apiRelease.downloadUrl.isNotBlank()) {
                return@runCatching apiRelease
            }

            // 2. Fallback: GitHub Web Redirect + Expanded Assets + raw version.json (zero API rate-limit)
            val webRelease = runCatching { fetchFromGitHubWebFallback(currentVer) }.getOrNull()
            if (webRelease != null) {
                return@runCatching webRelease
            }

            // 3. If API succeeded without APK asset yet, return it so UI can tell user APK is still building
            if (apiRelease != null) {
                return@runCatching apiRelease
            }

            // Force primary error message if both API & web fallback failed
            fetchFromGitHubApi()
        }
    }

    private fun fetchFromGitHubApi(): AppReleaseInfo {
        val req = Request.Builder()
            .url(GITHUB_API_LATEST_URL)
            .header("User-Agent", "JARVIS-Android-Updater")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                return parseReleaseJson(JSONObject(body))
            }
            // If /releases/latest returned 404 (e.g. prerelease only), inspect /releases list
            if (resp.code == 404) {
                val listReq = Request.Builder()
                    .url(GITHUB_API_LIST_URL)
                    .header("User-Agent", "JARVIS-Android-Updater")
                    .header("Accept", "application/vnd.github+json")
                    .get()
                    .build()
                client.newCall(listReq).execute().use { listResp ->
                    val listBody = listResp.body?.string().orEmpty()
                    if (listResp.isSuccessful) {
                        val arr = JSONArray(listBody)
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            if (!item.optBoolean("draft", false)) {
                                return parseReleaseJson(item)
                            }
                        }
                    }
                }
            }
            throw Exception("GitHub API HTTP ${resp.code}")
        }
    }

    internal fun parseReleaseJson(
        json: JSONObject,
        localVersion: String = BuildConfig.VERSION_NAME.removePrefix("v").trim()
    ): AppReleaseInfo {
        val rawTag = json.getString("tag_name").trim()
        val tagVer = rawTag.removePrefix("v").trim()
        val normalizedTag = if (rawTag.startsWith("v", ignoreCase = true)) rawTag else "v$tagVer"
        val rawBodyNotes = json.optString("body", "")
        val htmlUrl = json.optString("html_url", "").ifBlank {
            "https://github.com/$REPO_OWNER/$REPO_NAME/releases/tag/$normalizedTag"
        }
        val assets = json.optJSONArray("assets")
        var apkUrl = ""
        var apkAssetApiUrl = ""
        var versionJsonUrl = ""

        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                val dlUrl = asset.optString("browser_download_url", "")
                val apiUrl = asset.optString("url", "")
                if (name.endsWith(".apk", ignoreCase = true) && apkUrl.isBlank()) {
                    apkUrl = dlUrl
                    apkAssetApiUrl = apiUrl
                } else if (name.equals("version.json", ignoreCase = true) && versionJsonUrl.isBlank()) {
                    versionJsonUrl = dlUrl
                }
            }
        }

        val compareRegex = Regex("https://github\\.com/[^\\s)]+/compare/[^\\s)]+")
        val compareUrl = compareRegex.find(rawBodyNotes)?.value
        val bodyItems = rawBodyNotes.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                    !line.contains("Full Changelog", ignoreCase = true) &&
                    !line.startsWith("### What's New", ignoreCase = true) &&
                    !line.startsWith("## What's New", ignoreCase = true) &&
                    !compareRegex.matches(line)
            }
            .map { it.removePrefix("- ").removePrefix("* ").removePrefix("• ").trim() }
            .filter { it.isNotBlank() && !isCommitNoise(it) }

        val versionJsonItems = fetchVersionJsonChangelog(
            expectedVersion = tagVer,
            assetUrl = versionJsonUrl
        )

        val mergedItems = (versionJsonItems + bodyItems)
            .map { it.trim() }
            .filter { it.isNotBlank() && !isCommitNoise(it) }
            .distinct()
            .take(12)

        val formattedNotes = if (mergedItems.isNotEmpty()) {
            mergedItems.joinToString("\n") { "• $it" }
        } else {
            "• Stability improvements and Antigravity engine updates"
        }

        val currentVer = localVersion.removePrefix("v").trim()
        val isNewer = isVersionGreater(tagVer, currentVer)

        return AppReleaseInfo(
            currentVersion = "v$currentVer",
            newVersion = "v$tagVer",
            releaseNotes = formattedNotes,
            downloadUrl = apkUrl,
            isNewer = isNewer,
            changelogItems = if (mergedItems.isNotEmpty()) mergedItems else listOf("Stability improvements and Antigravity engine updates"),
            compareUrl = compareUrl,
            releasePageUrl = htmlUrl,
            assetApiUrl = apkAssetApiUrl
        )
    }

    private fun fetchFromGitHubWebFallback(currentVer: String): AppReleaseInfo? {
        // Follow GitHub web redirect on /releases/latest without hitting rate-limited REST API
        val webReq = Request.Builder()
            .url(GITHUB_WEB_LATEST_URL)
            .header("User-Agent", "Mozilla/5.0 (Android) JARVIS-Updater")
            .get()
            .build()

        var resolvedTag = ""
        var releaseHtml = ""
        client.newCall(webReq).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            releaseHtml = resp.body?.string().orEmpty()
            val tagMarker = "/releases/tag/"
            val idx = finalUrl.indexOf(tagMarker)
            if (idx >= 0) {
                resolvedTag = finalUrl.substring(idx + tagMarker.length).substringBefore("?").substringBefore("#").trim()
            } else {
                val tagMatch = Regex("/$REPO_OWNER/$REPO_NAME/releases/tag/([vV]?[0-9]+\\.[0-9]+\\.[0-9]+[^\"'\\s<]*)").find(releaseHtml)
                resolvedTag = tagMatch?.groupValues?.getOrNull(1).orEmpty()
            }
        }

        // Also inspect raw version.json as secondary signal
        val rawInfo = fetchRawVersionJsonObject()
        val rawVer = rawInfo?.optString("version_name", "")?.removePrefix("v")?.trim().orEmpty()

        val effectiveTag = resolvedTag.ifBlank {
            if (rawVer.isNotBlank()) "v$rawVer" else ""
        }
        if (effectiveTag.isBlank()) return null

        val tagVer = effectiveTag.removePrefix("v").trim()
        val normalizedTag = if (effectiveTag.startsWith("v", ignoreCase = true)) effectiveTag else "v$tagVer"
        val isNewer = isVersionGreater(tagVer, currentVer)

        var apkUrl = ""
        val assetsPageUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/expanded_assets/$normalizedTag"
        runCatching {
            val assetsReq = Request.Builder()
                .url(assetsPageUrl)
                .header("User-Agent", "Mozilla/5.0 (Android) JARVIS-Updater")
                .get()
                .build()
            client.newCall(assetsReq).execute().use { aResp ->
                if (aResp.isSuccessful) {
                    val aHtml = aResp.body?.string().orEmpty()
                    val apkHrefMatch = Regex("href=\"(/$REPO_OWNER/$REPO_NAME/releases/download/[^\"]+\\.apk)\"").find(aHtml)
                    val relHref = apkHrefMatch?.groupValues?.getOrNull(1).orEmpty()
                    if (relHref.isNotBlank()) {
                        apkUrl = "https://github.com$relHref"
                    }
                }
            }
        }

        // Fallback check on conventional CI filename
        if (apkUrl.isBlank()) {
            val conventionalUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$normalizedTag/JARVIS-AI-$normalizedTag.apk"
            if (verifyRemoteApkUrlExists(conventionalUrl)) {
                apkUrl = conventionalUrl
            }
        }

        val items = mutableListOf<String>()
        if (rawInfo != null) {
            val arr = rawInfo.optJSONArray("changelog")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val line = arr.optString(i, "").trim()
                    if (line.isNotBlank() && !isCommitNoise(line)) items.add(line)
                }
            }
        }
        val cleanItems = items.distinct().take(12).ifEmpty {
            listOf("Stability improvements and Antigravity engine updates")
        }

        return AppReleaseInfo(
            currentVersion = "v$currentVer",
            newVersion = "v$tagVer",
            releaseNotes = cleanItems.joinToString("\n") { "• $it" },
            downloadUrl = apkUrl,
            isNewer = isNewer,
            changelogItems = cleanItems,
            compareUrl = null,
            releasePageUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/tag/$normalizedTag"
        )
    }

    private fun verifyRemoteApkUrlExists(url: String): Boolean {
        return runCatching {
            val headReq = Request.Builder()
                .url(url)
                .header("User-Agent", "JARVIS-Android-Updater")
                .head()
                .build()
            client.newCall(headReq).execute().use { resp ->
                resp.isSuccessful && !(resp.header("Content-Type")?.contains("text/html", ignoreCase = true) == true)
            }
        }.getOrDefault(false)
    }

    private fun fetchRawVersionJsonObject(): JSONObject? {
        return runCatching {
            val req = Request.Builder()
                .url("$RAW_VERSION_JSON_URL?ts=${System.currentTimeMillis()}")
                .header("User-Agent", "JARVIS-Android-Updater")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val txt = resp.body?.string().orEmpty()
                if (txt.isBlank()) null else JSONObject(txt)
            }
        }.getOrNull()
    }

    private fun isCommitNoise(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("auto-bump version.json") ||
            lower.contains("[skip ci]") ||
            lower.startsWith("chore(release):") ||
            lower.startsWith("merge pull request") ||
            lower.startsWith("merge branch")
    }

    private fun fetchVersionJsonChangelog(expectedVersion: String, assetUrl: String): List<String> {
        val candidateUrls = listOfNotNull(
            assetUrl.takeIf { it.isNotBlank() },
            "$RAW_VERSION_JSON_URL?ts=${System.currentTimeMillis()}"
        )
        for (url in candidateUrls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "JARVIS-Android-Updater")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val txt = resp.body?.string().orEmpty()
                    if (txt.isBlank()) return@use
                    val vj = JSONObject(txt)
                    val vjVer = vj.optString("version_name", "").removePrefix("v").trim()
                    if (url.startsWith(RAW_VERSION_JSON_URL) && vjVer.isNotBlank() && vjVer != expectedVersion && !isVersionGreater(vjVer, BuildConfig.VERSION_NAME.removePrefix("v").trim())) {
                        return@use
                    }
                    val arr = vj.optJSONArray("changelog") ?: return@use
                    val parsed = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val item = arr.optString(i, "").trim()
                        if (item.isNotBlank() && !isCommitNoise(item)) {
                            parsed.add(item)
                        }
                    }
                    if (parsed.isNotEmpty()) return parsed
                }
            } catch (_: Exception) {
                // Try next source
            }
        }
        return emptyList()
    }

    private fun isVersionGreater(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private data class DownloadSource(
        val url: String,
        val acceptHeader: String
    )

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        releasePageUrl: String = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest",
        fallbackAssetApiUrl: String = "",
        onProgress: (DownloadProgress) -> Unit
    ): Result<ApkUpdateResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = downloadUrl.trim()
            val cleanFallbackApiUrl = fallbackAssetApiUrl.trim()
            val sources = buildList {
                if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                    add(
                        DownloadSource(
                            url = cleanUrl,
                            acceptHeader = "application/vnd.android.package-archive, application/octet-stream, */*"
                        )
                    )
                }
                if ((cleanFallbackApiUrl.startsWith("http://") || cleanFallbackApiUrl.startsWith("https://")) &&
                    cleanFallbackApiUrl != cleanUrl
                ) {
                    add(
                        DownloadSource(
                            url = cleanFallbackApiUrl,
                            acceptHeader = "application/octet-stream"
                        )
                    )
                }
            }
            if (sources.isEmpty()) {
                throw IllegalArgumentException("APK download link abhi ready nahi hai. Thodi der baad try karein.")
            }

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            // Clean previous partial/stale downloads
            updatesDir.listFiles()?.forEach { oldFile ->
                runCatching { oldFile.delete() }
            }

            val tempFile = File(updatesDir, "update.apk.part")
            val targetFile = File(updatesDir, "update.apk")

            var downloadSucceeded = false
            var lastError: Throwable? = null

            outer@ for (source in sources) {
                for (attempt in 1..2) {
                    if (tempFile.exists()) tempFile.delete()
                    try {
                        val req = Request.Builder()
                            .url(source.url)
                            .header("User-Agent", "JARVIS-Android-Updater")
                            .header("Accept", source.acceptHeader)
                            .get()
                            .build()

                        downloadClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                throw IOException("Download failed (HTTP ${resp.code})")
                            }
                            val contentType = resp.header("Content-Type").orEmpty().lowercase()
                            if (contentType.contains("text/html")) {
                                throw IOException("Server returned web page instead of APK file. Browser se download karein.")
                            }

                            val body = resp.body ?: throw IOException("Empty APK response from server")
                            val totalLength = body.contentLength()

                            body.byteStream().use { input ->
                                FileOutputStream(tempFile).use { output ->
                                    val buffer = ByteArray(16 * 1024)
                                    var bytesRead: Int
                                    var totalRead = 0L
                                    var lastUiEmitMs = 0L

                                    while (input.read(buffer).also { bytesRead = it } != -1) {
                                        output.write(buffer, 0, bytesRead)
                                        totalRead += bytesRead
                                        val now = System.currentTimeMillis()
                                        if (now - lastUiEmitMs >= 150L || (totalLength > 0 && totalRead >= totalLength)) {
                                            lastUiEmitMs = now
                                            val pct = if (totalLength > 0) ((totalRead * 100) / totalLength).toInt().coerceIn(0, 100) else -1
                                            onProgress(DownloadProgress(pct, totalRead, totalLength))
                                        }
                                    }
                                    output.flush()
                                }
                            }
                        }

                        if (!tempFile.exists() || tempFile.length() < 500_000L) {
                            val actualLen = tempFile.length()
                            tempFile.delete()
                            throw IOException("Downloaded APK incomplete ($actualLen bytes). Network check karke dubara try karein.")
                        }

                        // Validate ZIP/APK magic header ("PK\x03\x04")
                        val headerBytes = ByteArray(4)
                        FileInputStream(tempFile).use { it.read(headerBytes) }
                        val isZipApk = headerBytes[0] == 0x50.toByte() &&
                            headerBytes[1] == 0x4B.toByte() &&
                            headerBytes[2] == 0x03.toByte() &&
                            headerBytes[3] == 0x04.toByte()
                        if (!isZipApk) {
                            tempFile.delete()
                            throw IOException("Invalid APK archive downloaded. Browser se download try karein.")
                        }

                        downloadSucceeded = true
                        break@outer
                    } catch (err: Throwable) {
                        lastError = err
                        val retryable = err is UnknownHostException ||
                            err is SocketTimeoutException ||
                            err is ConnectException ||
                            err is SSLException ||
                            err is IOException
                        if (retryable && attempt < 2) {
                            delay(650L)
                            continue
                        }
                        break
                    }
                }
            }

            if (!downloadSucceeded) {
                throw Exception(formatFriendlyNetworkError(context, lastError))
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            // Pre-flight PackageManager validation & signing key compatibility check
            val preflightError = validateApkPackageAndSignature(context, targetFile)
            if (preflightError != null) {
                if (preflightError.isSignatureMismatch) {
                    return@runCatching ApkUpdateResult.SignatureConflict(
                        apkFile = targetFile,
                        message = preflightError.message,
                        browserUrl = releasePageUrl
                    )
                } else {
                    targetFile.delete()
                    throw Exception(preflightError.message)
                }
            }

            // Permission check for Unknown App Sources (Android 8+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                withContext(Dispatchers.Main) {
                    openUnknownSourcesSettings(context)
                }
                return@runCatching ApkUpdateResult.NeedsUnknownSourcesPermission(targetFile)
            }

            withContext(Dispatchers.Main) {
                installApk(context, targetFile)
            }
            ApkUpdateResult.LaunchedInstaller(targetFile)
        }
    }

    internal fun formatFriendlyNetworkError(context: Context, err: Throwable?): String {
        val online = isNetworkAvailable(context)
        if (!online) {
            return "Internet connection active nahi hai. Mobile Data ya Wi-Fi ON karke dubara try karein."
        }
        return when {
            err is UnknownHostException || err?.cause is UnknownHostException ->
                "Mobile carrier DNS GitHub download server ko resolve nahi kar pa raha. Dubara 'Update Now' tap karein ya niche 'Open Download in Browser' se download karein."
            err is SocketTimeoutException || err?.cause is SocketTimeoutException ||
                err is ConnectException || err?.cause is ConnectException ->
                "GitHub server se connection timeout ho gaya. Network check karke retry karein ya Browser se download karein."
            else -> err?.message ?: "APK download failed. Dubara try karein ya Browser se download karein."
        }
    }

    @Suppress("DEPRECATION")
    private fun isNetworkAvailable(context: Context): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val active = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(active) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                cm.activeNetworkInfo?.isConnected == true
            }
        }.getOrDefault(true)
    }

    private data class PreflightValidationError(
        val message: String,
        val isSignatureMismatch: Boolean = false
    )

    @Suppress("DEPRECATION")
    private fun validateApkPackageAndSignature(context: Context, apkFile: File): PreflightValidationError? {
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
                ?: return PreflightValidationError("Android package parser could not read this APK (corrupted download).")

            if (!archiveInfo.packageName.equals(context.packageName, ignoreCase = false)) {
                return PreflightValidationError("Package mismatch: expected ${context.packageName}, got ${archiveInfo.packageName}.")
            }

            val installedInfo = pm.getPackageInfo(context.packageName, flags)
            val installedSigs = extractSignatureHexSet(installedInfo)
            val archiveSigs = extractSignatureHexSet(archiveInfo)

            if (installedSigs.isNotEmpty() && archiveSigs.isNotEmpty() && installedSigs.intersect(archiveSigs).isEmpty()) {
                return PreflightValidationError(
                    message = "Signing key mismatch: current app aur GitHub update APK alag key se signed hain. Direct update fail hoga — pehle purana app uninstall karein ya niche Browser button se APK install karein.",
                    isSignatureMismatch = true
                )
            }
            null
        } catch (e: Exception) {
            null // Do not block install on OEM parser reflection quirks
        }
    }

    @Suppress("DEPRECATION")
    private fun extractSignatureHexSet(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners ?: info.signingInfo?.signingCertificateHistory
        } else {
            info.signatures
        } ?: return emptySet()
        return sigs.map { it.toCharsString() }.toSet()
    }

    fun openUnknownSourcesSettings(context: Context) {
        val settingsIntent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching {
            context.startActivity(settingsIntent)
        }.onFailure {
            val appDetails = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(appDetails)
        }
    }

    fun installApk(context: Context, file: File) {
        if (!file.exists()) {
            throw IllegalStateException("Update APK file not found on device.")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            openUnknownSourcesSettings(context)
            return
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        runCatching {
            context.startActivity(viewIntent)
        }.onFailure { primaryErr ->
            @Suppress("DEPRECATION")
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            runCatching {
                context.startActivity(installIntent)
            }.onFailure {
                throw Exception("Could not open Android Package Installer: ${primaryErr.message}")
            }
        }
    }
}
