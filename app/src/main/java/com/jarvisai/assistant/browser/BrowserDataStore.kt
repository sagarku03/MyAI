package com.jarvisai.assistant.browser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class BrowserSearchEngine(
    val id: String,
    val displayName: String,
    val shortBadge: String,
    val searchUrlTemplate: String,
    val badgeColorHex: Long
) {
    GOOGLE("google", "Google", "G", "https://www.google.com/search?q=%s", 0xFF4285F4),
    BING("bing", "Microsoft Bing", "B", "https://www.bing.com/search?q=%s", 0xFF008373),
    YAHOO("yahoo", "Yahoo!", "Y!", "https://search.yahoo.com/search?p=%s", 0xFF6001D2),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "D", "https://duckduckgo.com/?q=%s", 0xFFDE5833),
    YANDEX("yandex", "Yandex", "Ya", "https://yandex.com/search/?text=%s", 0xFFFC3F1D);

    fun buildQueryUrl(query: String): String {
        val encoded = Uri.encode(query.trim())
        return searchUrlTemplate.replace("%s", encoded)
    }

    companion object {
        fun fromId(id: String): BrowserSearchEngine {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GOOGLE
        }
    }
}

data class SpeedDialItem(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    val title: String,
    val url: String,
    val badgeLetter: String = title.take(1).uppercase(),
    val accentHex: Long = 0xFFF25F70,
    val specialAction: String = "" // "", "EXTENSIONS", "WEBSTORE"
)

data class BookmarkEntry(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    val title: String,
    val url: String,
    val folder: String = "Mobile bookmarks",
    val createdAt: Long = System.currentTimeMillis()
)

data class HistoryEntry(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    val title: String,
    val url: String,
    val domain: String,
    val visitedAt: Long = System.currentTimeMillis()
)

data class DownloadEntry(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    val fileName: String,
    val url: String,
    val localPath: String = "",
    val mimeType: String = "application/octet-stream",
    val sizeLabel: String = "",
    val isOfflinePage: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val contentUri: String = ""
)

data class PlaylistMediaItem(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    val title: String,
    val url: String,
    val pageUrl: String,
    val domain: String,
    val isVideo: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)

data class TabGroupRecord(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    val title: String,
    val colorHex: Long = 0xFFF25F70,
    val updatedAt: Long = System.currentTimeMillis()
)

data class BrowserPreferencesState(
    val searchEngine: BrowserSearchEngine = BrowserSearchEngine.GOOGLE,
    val adBlockEnabled: Boolean = true,
    val askBeforeOpeningApp: Boolean = true,
    val developerModeExtensions: Boolean = false,
    val forceDarkPages: Boolean = false,
    val javaScriptEnabled: Boolean = true,
    val desktopModeDefault: Boolean = false,
    val backgroundMediaPlayback: Boolean = true,
    val emailMaskAlias: String = "jarvis.shield@privacymask.me"
)

/**
 * Persists Jarvis browser data locally in JSON/SharedPreferences:
 * Speed dials, search engine, favorites/folders, history, downloads & offline archives,
 * watch-later media playlist, tab groups, and privacy/extension preferences.
 */
object BrowserDataStore {

    private const val PREFS_NAME = "jarvis_browser_store"
    private const val KEY_SPEED_DIALS = "speed_dials"
    private const val KEY_BOOKMARKS = "bookmarks"
    private const val KEY_FOLDERS = "bookmark_folders"
    private const val KEY_HISTORY = "history"
    private const val KEY_DOWNLOADS = "downloads"
    private const val KEY_PLAYLIST = "playlist"
    private const val KEY_TAB_GROUPS = "tab_groups"
    private const val KEY_PREFS = "browser_prefs"
    const val AGENTRA_DOWNLOADS_FOLDER = "Agentra Downloads"
    private const val DOWNLOAD_CHANNEL_ID = "agentra_browser_downloads"

    @Volatile
    private var appContext: Context? = null
    private val downloadsLock = Any()
    private val activeDownloadIds = ConcurrentHashMap.newKeySet<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val downloadExecutor = Executors.newCachedThreadPool()
    private val notifIdSeq = AtomicInteger(9200)
    private val downloadHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val _speedDials = MutableStateFlow(defaultSpeedDials())
    val speedDials: StateFlow<List<SpeedDialItem>> = _speedDials.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<BookmarkEntry>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkEntry>> = _bookmarks.asStateFlow()

    private val _folders = MutableStateFlow(listOf("Mobile bookmarks", "Bookmarks bar", "Other bookmarks"))
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val downloads: StateFlow<List<DownloadEntry>> = _downloads.asStateFlow()

    private val _playlist = MutableStateFlow<List<PlaylistMediaItem>>(emptyList())
    val playlist: StateFlow<List<PlaylistMediaItem>> = _playlist.asStateFlow()

    private val _tabGroups = MutableStateFlow<List<TabGroupRecord>>(emptyList())
    val tabGroups: StateFlow<List<TabGroupRecord>> = _tabGroups.asStateFlow()

    private val _preferences = MutableStateFlow(BrowserPreferencesState())
    val preferences: StateFlow<BrowserPreferencesState> = _preferences.asStateFlow()

    fun init(context: Context) {
        val appCtx = context.applicationContext ?: context
        if (appContext?.filesDir == appCtx.filesDir) return
        appContext = appCtx
        loadAll(appCtx)
    }

    private fun prefs(context: Context? = appContext) =
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadAll(context: Context) {
        val sp = prefs(context) ?: return

        sp.getString(KEY_SPEED_DIALS, null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                val list = mutableListOf<SpeedDialItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        SpeedDialItem(
                            id = o.optString("id"),
                            title = o.optString("title"),
                            url = o.optString("url"),
                            badgeLetter = o.optString("badgeLetter", o.optString("title").take(1)),
                            accentHex = o.optLong("accentHex", 0xFFF25F70),
                            specialAction = o.optString("specialAction", "")
                        )
                    )
                }
                if (list.isNotEmpty()) _speedDials.value = list
            }
        }

        sp.getString(KEY_FOLDERS, null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val f = arr.optString(i).trim()
                    if (f.isNotEmpty()) list.add(f)
                }
                if (list.isNotEmpty()) _folders.value = list
            }
        }

        sp.getString(KEY_BOOKMARKS, null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                val list = mutableListOf<BookmarkEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        BookmarkEntry(
                            id = o.optString("id"),
                            title = o.optString("title"),
                            url = o.optString("url"),
                            folder = o.optString("folder", "Mobile bookmarks"),
                            createdAt = o.optLong("createdAt")
                        )
                    )
                }
                _bookmarks.value = list
            }
        }

        sp.getString(KEY_HISTORY, null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                val list = mutableListOf<HistoryEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        HistoryEntry(
                            id = o.optString("id"),
                            title = o.optString("title"),
                            url = o.optString("url"),
                            domain = o.optString("domain"),
                            visitedAt = o.optLong("visitedAt")
                        )
                    )
                }
                _history.value = list
            }
        }

        sp.getString(KEY_DOWNLOADS, null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                val list = mutableListOf<DownloadEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        DownloadEntry(
                            id = o.optString("id"),
                            fileName = o.optString("fileName"),
                            url = o.optString("url"),
                            localPath = o.optString("localPath"),
                            mimeType = o.optString("mimeType"),
                            sizeLabel = o.optString("sizeLabel"),
                            isOfflinePage = o.optBoolean("isOfflinePage"),
                            timestamp = o.optLong("timestamp"),
                            contentUri = o.optString("contentUri", "")
                        )
                    )
                }
                _downloads.value = list
            }
        }

        sp.getString(KEY_PLAYLIST, null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                val list = mutableListOf<PlaylistMediaItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        PlaylistMediaItem(
                            id = o.optString("id"),
                            title = o.optString("title"),
                            url = o.optString("url"),
                            pageUrl = o.optString("pageUrl"),
                            domain = o.optString("domain"),
                            isVideo = o.optBoolean("isVideo", true),
                            addedAt = o.optLong("addedAt")
                        )
                    )
                }
                _playlist.value = list
            }
        }

        sp.getString(KEY_TAB_GROUPS, null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                val list = mutableListOf<TabGroupRecord>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        TabGroupRecord(
                            id = o.optString("id"),
                            title = o.optString("title"),
                            colorHex = o.optLong("colorHex", 0xFFF25F70),
                            updatedAt = o.optLong("updatedAt")
                        )
                    )
                }
                _tabGroups.value = list
            }
        }

        sp.getString(KEY_PREFS, null)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                _preferences.value = BrowserPreferencesState(
                    searchEngine = BrowserSearchEngine.fromId(o.optString("searchEngine", "google")),
                    adBlockEnabled = o.optBoolean("adBlockEnabled", true),
                    askBeforeOpeningApp = o.optBoolean("askBeforeOpeningApp", true),
                    developerModeExtensions = o.optBoolean("developerModeExtensions", false),
                    forceDarkPages = o.optBoolean("forceDarkPages", false),
                    javaScriptEnabled = o.optBoolean("javaScriptEnabled", true),
                    desktopModeDefault = o.optBoolean("desktopModeDefault", false),
                    backgroundMediaPlayback = o.optBoolean("backgroundMediaPlayback", true),
                    emailMaskAlias = o.optString("emailMaskAlias", "jarvis.shield@privacymask.me")
                )
            }
        }
    }

    fun updatePreferences(transform: (BrowserPreferencesState) -> BrowserPreferencesState) {
        val next = transform(_preferences.value)
        _preferences.value = next
        val obj = JSONObject().apply {
            put("searchEngine", next.searchEngine.id)
            put("adBlockEnabled", next.adBlockEnabled)
            put("askBeforeOpeningApp", next.askBeforeOpeningApp)
            put("developerModeExtensions", next.developerModeExtensions)
            put("forceDarkPages", next.forceDarkPages)
            put("javaScriptEnabled", next.javaScriptEnabled)
            put("desktopModeDefault", next.desktopModeDefault)
            put("backgroundMediaPlayback", next.backgroundMediaPlayback)
            put("emailMaskAlias", next.emailMaskAlias)
        }
        prefs()?.edit()?.putString(KEY_PREFS, obj.toString())?.apply()
    }

    fun resolveOmniboxInput(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"
        val lower = trimmed.lowercase()
        if (lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("file://") ||
            lower.startsWith("about:") ||
            lower.startsWith("javascript:")
        ) {
            return trimmed
        }
        if (lower.startsWith("localhost") || Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$").matches(trimmed)) {
            return "http://$trimmed"
        }
        if (!trimmed.contains(" ") && trimmed.contains(".")) {
            return "https://$trimmed"
        }
        return _preferences.value.searchEngine.buildQueryUrl(trimmed)
    }

    fun addSpeedDial(title: String, url: String, accentHex: Long = 0xFFF25F70) {
        val cleanUrl = resolveOmniboxInput(url)
        val item = SpeedDialItem(
            title = title.ifBlank { BrowserAdBlockEngine.extractHost(cleanUrl).ifBlank { "Site" } },
            url = cleanUrl,
            badgeLetter = title.trim().take(1).uppercase().ifBlank { "W" },
            accentHex = accentHex
        )
        _speedDials.value = _speedDials.value + item
        persistSpeedDials()
    }

    fun removeSpeedDial(id: String) {
        _speedDials.value = _speedDials.value.filterNot { it.id == id }
        persistSpeedDials()
    }

    private fun persistSpeedDials() {
        val arr = JSONArray()
        _speedDials.value.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("title", s.title)
                put("url", s.url)
                put("badgeLetter", s.badgeLetter)
                put("accentHex", s.accentHex)
                put("specialAction", s.specialAction)
            })
        }
        prefs()?.edit()?.putString(KEY_SPEED_DIALS, arr.toString())?.apply()
    }

    fun isBookmarked(url: String): Boolean {
        val clean = url.trimEnd('/')
        return _bookmarks.value.any { it.url.trimEnd('/') == clean }
    }

    fun toggleBookmark(title: String, url: String, folder: String = "Mobile bookmarks"): Boolean {
        val clean = url.trimEnd('/')
        val existing = _bookmarks.value.firstOrNull { it.url.trimEnd('/') == clean }
        return if (existing != null) {
            removeBookmark(existing.id)
            false
        } else {
            addBookmark(title, url, folder)
            true
        }
    }

    fun addBookmark(title: String, url: String, folder: String = "Mobile bookmarks") {
        val item = BookmarkEntry(
            title = title.ifBlank { BrowserAdBlockEngine.extractHost(url).ifBlank { url } },
            url = url,
            folder = folder
        )
        _bookmarks.value = listOf(item) + _bookmarks.value.filterNot { it.url.trimEnd('/') == url.trimEnd('/') }
        if (!_folders.value.contains(folder)) {
            _folders.value = _folders.value + folder
            prefs()?.edit()?.putString(KEY_FOLDERS, JSONArray(_folders.value).toString())?.apply()
        }
        persistBookmarks()
    }

    fun removeBookmark(id: String) {
        _bookmarks.value = _bookmarks.value.filterNot { it.id == id }
        persistBookmarks()
    }

    private fun persistBookmarks() {
        val arr = JSONArray()
        _bookmarks.value.forEach { b ->
            arr.put(JSONObject().apply {
                put("id", b.id)
                put("title", b.title)
                put("url", b.url)
                put("folder", b.folder)
                put("createdAt", b.createdAt)
            })
        }
        prefs()?.edit()?.putString(KEY_BOOKMARKS, arr.toString())?.apply()
    }

    fun recordHistory(title: String, url: String, isPrivate: Boolean) {
        if (isPrivate) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val host = BrowserAdBlockEngine.extractHost(url)
        val cleanTitle = title.ifBlank { host.ifBlank { url } }
        val deduped = _history.value.filterNot {
            it.url == url && (System.currentTimeMillis() - it.visitedAt) < 120_000L
        }
        val entry = HistoryEntry(title = cleanTitle, url = url, domain = host)
        _history.value = (listOf(entry) + deduped).take(250)
        persistHistory()
    }

    fun removeHistoryItem(id: String) {
        _history.value = _history.value.filterNot { it.id == id }
        persistHistory()
    }

    fun clearHistory() {
        _history.value = emptyList()
        persistHistory()
    }

    private fun persistHistory() {
        val arr = JSONArray()
        _history.value.forEach { h ->
            arr.put(JSONObject().apply {
                put("id", h.id)
                put("title", h.title)
                put("url", h.url)
                put("domain", h.domain)
                put("visitedAt", h.visitedAt)
            })
        }
        prefs()?.edit()?.putString(KEY_HISTORY, arr.toString())?.apply()
    }

    fun resolvePublicAgentraDownloadsDir(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            AGENTRA_DOWNLOADS_FOLDER
        )
    }

    internal fun resolveDownloadFileName(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        suggestedFilename: String? = null
    ): String {
        val rawFromCd = extractFileNameFromContentDisposition(contentDisposition)
        val candidate = suggestedFilename?.trim()?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() }
            ?: rawFromCd?.takeIf { it.isNotBlank() }
            ?: runCatching { URLUtil.guessFileName(url, contentDisposition, mimeType) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
            ?: "download_${System.currentTimeMillis()}"

        val cleaned = candidate
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
            .trim()
            .trimStart('.')
            .ifBlank { "download_${System.currentTimeMillis()}" }

        val cleanMime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        val mimeExt = if (cleanMime.isNotBlank() && cleanMime != "application/octet-stream") {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(cleanMime)?.lowercase()
        } else null

        var finalName = cleaned
        if (!mimeExt.isNullOrBlank()) {
            val dotIdx = finalName.lastIndexOf('.')
            val curExt = if (dotIdx > 0) finalName.substring(dotIdx + 1).lowercase() else ""
            if (curExt.isBlank() || curExt == "bin" || curExt == "php" || curExt == "ashx") {
                val stem = if (dotIdx > 0) finalName.substring(0, dotIdx) else finalName
                finalName = "$stem.$mimeExt"
            }
        }

        if (finalName.length > 160) {
            val dotIdx = finalName.lastIndexOf('.')
            finalName = if (dotIdx > 0 && finalName.length - dotIdx <= 12) {
                val ext = finalName.substring(dotIdx)
                finalName.substring(0, 160 - ext.length) + ext
            } else {
                finalName.take(160)
            }
        }
        return finalName
    }

    private fun extractFileNameFromContentDisposition(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) return null
        val rfc5987 = Regex("""filename\*\s*=\s*(?:UTF-8''|[^']*'[^']*')([^;]+)""", RegexOption.IGNORE_CASE)
            .find(contentDisposition)?.groupValues?.getOrNull(1)?.trim()?.trim('"', '\'')
        if (!rfc5987.isNullOrBlank()) {
            return runCatching { URLDecoder.decode(rfc5987, "UTF-8") }.getOrDefault(rfc5987)
        }
        val quoted = Regex("""filename\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(contentDisposition)?.groupValues?.getOrNull(1)?.trim()
        if (!quoted.isNullOrBlank()) return quoted
        val bare = Regex("""filename\s*=\s*([^;\s]+)""", RegexOption.IGNORE_CASE)
            .find(contentDisposition)?.groupValues?.getOrNull(1)?.trim()?.trim('"', '\'')
        return bare?.takeIf { it.isNotBlank() }
    }

    private data class SavedDownloadFile(
        val fileName: String,
        val localPath: String,
        val contentUri: String,
        val byteCount: Long
    )

    private fun ensureDownloadsDirCreated() {
        runCatching {
            val dir = resolvePublicAgentraDownloadsDir()
            if (!dir.exists()) dir.mkdirs()
        }
    }

    private fun deduplicateFile(dir: File, desiredName: String): File {
        var candidate = File(dir, desiredName)
        if (!candidate.exists()) return candidate
        val dotIdx = desiredName.lastIndexOf('.')
        val stem = if (dotIdx > 0) desiredName.substring(0, dotIdx) else desiredName
        val ext = if (dotIdx > 0) desiredName.substring(dotIdx) else ""
        var counter = 1
        while (candidate.exists() && counter < 500) {
            candidate = File(dir, "$stem ($counter)$ext")
            counter++
        }
        return candidate
    }

    private fun writeStreamToAgentraDownloads(
        context: Context,
        desiredName: String,
        mimeType: String,
        writer: (OutputStream) -> Long
    ): SavedDownloadFile {
        ensureDownloadsDirCreated()
        val effectiveMime = mimeType.substringBefore(';').trim().ifBlank { "application/octet-stream" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mediaResult = runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, desiredName)
                    put(MediaStore.Downloads.MIME_TYPE, effectiveMime)
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/$AGENTRA_DOWNLOADS_FOLDER"
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore insert returned null")
                var bytesWritten = 0L
                try {
                    val os = resolver.openOutputStream(uri, "w")
                        ?: error("Failed opening MediaStore output stream")
                    os.use { out ->
                        bytesWritten = writer(out)
                        out.flush()
                    }
                    val finishValues = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(uri, finishValues, null, null)
                } catch (e: Throwable) {
                    runCatching { resolver.delete(uri, null, null) }
                    throw e
                }

                var finalDisplayName = desiredName
                var resolvedPath = File(resolvePublicAgentraDownloadsDir(), desiredName).absolutePath
                runCatching {
                    resolver.query(
                        uri,
                        arrayOf(MediaStore.Downloads.DISPLAY_NAME, MediaStore.MediaColumns.DATA),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                            if (nameIdx >= 0) {
                                cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let {
                                    finalDisplayName = it
                                }
                            }
                            val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            if (dataIdx >= 0) {
                                cursor.getString(dataIdx)?.takeIf { it.isNotBlank() }?.let {
                                    resolvedPath = it
                                }
                            } else {
                                resolvedPath = File(resolvePublicAgentraDownloadsDir(), finalDisplayName).absolutePath
                            }
                        }
                    }
                }
                SavedDownloadFile(
                    fileName = finalDisplayName,
                    localPath = resolvedPath,
                    contentUri = uri.toString(),
                    byteCount = bytesWritten
                )
            }
            if (mediaResult.isSuccess) return mediaResult.getOrThrow()
        }

        // Pre-Android 10 (or MediaStore fallback) writes directly to Download/Agentra Downloads
        val candidateDirs = listOfNotNull(
            resolvePublicAgentraDownloadsDir(),
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, AGENTRA_DOWNLOADS_FOLDER) },
            File(context.filesDir, AGENTRA_DOWNLOADS_FOLDER)
        )
        var lastError: Throwable? = null
        for (dir in candidateDirs) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val targetFile = deduplicateFile(dir, desiredName)
                var written = 0L
                FileOutputStream(targetFile).use { fos ->
                    written = writer(fos)
                    fos.flush()
                }
                runCatching {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath),
                        arrayOf(effectiveMime),
                        null
                    )
                }
                val providerUri = runCatching {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile).toString()
                }.getOrDefault("")
                return SavedDownloadFile(
                    fileName = targetFile.name,
                    localPath = targetFile.absolutePath,
                    contentUri = providerUri,
                    byteCount = written
                )
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw IllegalStateException(lastError?.message ?: "Unable to write download file")
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    fun enqueueSystemDownload(
        context: Context,
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        sourceWebView: WebView? = null,
        suggestedFilename: String? = null
    ): DownloadEntry {
        val appCtx = context.applicationContext ?: context
        if (appContext == null) init(appCtx)
        ensureDownloadsDirCreated()

        val initialMime = mimeType?.substringBefore(';')?.trim()?.ifBlank { "application/octet-stream" }
            ?: "application/octet-stream"
        val guessedName = resolveDownloadFileName(url, contentDisposition, initialMime, suggestedFilename)
        val entryId = UUID.randomUUID().toString().substring(0, 8)
        val expectedPath = File(resolvePublicAgentraDownloadsDir(), guessedName).absolutePath

        val entry = DownloadEntry(
            id = entryId,
            fileName = guessedName,
            url = if (url.length > 2048) url.take(2048) else url,
            localPath = expectedPath,
            mimeType = initialMime,
            sizeLabel = "Downloading...",
            isOfflinePage = false
        )
        activeDownloadIds.add(entryId)
        addDownloadEntry(entry)

        val trimmedUrl = url.trim()
        when {
            trimmedUrl.startsWith("data:", ignoreCase = true) -> {
                downloadExecutor.execute {
                    saveDataUrlIntoEntry(appCtx, entryId, trimmedUrl, initialMime, guessedName)
                }
            }
            trimmedUrl.startsWith("blob:", ignoreCase = true) -> {
                if (sourceWebView != null) {
                    mainHandler.post {
                        val js = """
                            (function(){
                              try {
                                fetch(${JSONObject.quote(trimmedUrl)}).then(function(r){
                                  var m = (r.headers && r.headers.get('content-type')) || '';
                                  return r.blob().then(function(b){ return { b: b, m: b.type || m }; });
                                }).then(function(info){
                                  var fr = new FileReader();
                                  fr.onloadend = function(){
                                    if (window.__jarvis_download_bridge) {
                                      window.__jarvis_download_bridge.onBlobDataReady(${JSONObject.quote(entryId)}, String(fr.result || ''), info.m || '');
                                    }
                                  };
                                  fr.onerror = function(){
                                    if (window.__jarvis_download_bridge) {
                                      window.__jarvis_download_bridge.onBlobFailed(${JSONObject.quote(entryId)}, 'Blob read failed');
                                    }
                                  };
                                  fr.readAsDataURL(info.b);
                                }).catch(function(err){
                                  if (window.__jarvis_download_bridge) {
                                    window.__jarvis_download_bridge.onBlobFailed(${JSONObject.quote(entryId)}, String(err && err.message ? err.message : err));
                                  }
                                });
                              } catch(e) {
                                if (window.__jarvis_download_bridge) {
                                  window.__jarvis_download_bridge.onBlobFailed(${JSONObject.quote(entryId)}, String(e && e.message ? e.message : e));
                                }
                              }
                            })();
                        """.trimIndent()
                        sourceWebView.evaluateJavascript(js, null)
                    }
                } else {
                    activeDownloadIds.remove(entryId)
                    updateDownloadEntry(entryId) { it.copy(sizeLabel = "Failed: Blob source unavailable") }
                }
            }
            trimmedUrl.startsWith("http://", ignoreCase = true) || trimmedUrl.startsWith("https://", ignoreCase = true) -> {
                val ua = runCatching {
                    BrowserIdentity.buildUserAgent(WebSettings.getDefaultUserAgent(appCtx), false)
                }.getOrDefault("Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/134.0.0.0 Mobile Safari/537.36")
                downloadExecutor.execute {
                    performHttpDownload(appCtx, entryId, trimmedUrl, contentDisposition, initialMime, suggestedFilename, ua)
                }
            }
            else -> {
                activeDownloadIds.remove(entryId)
                updateDownloadEntry(entryId) { it.copy(sizeLabel = "Unsupported URL scheme") }
            }
        }
        return entry
    }

    private fun performHttpDownload(
        context: Context,
        entryId: String,
        url: String,
        initialCd: String?,
        initialMime: String,
        suggestedFilename: String?,
        userAgent: String
    ) {
        try {
            val reqBuilder = Request.Builder().url(url).header("User-Agent", userAgent)
            val cookieHeader = runCatching { CookieManager.getInstance()?.getCookie(url) }.getOrNull().orEmpty()
            if (cookieHeader.isNotBlank()) {
                reqBuilder.header("Cookie", cookieHeader)
            }
            downloadHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("HTTP ${resp.code}")
                }
                val body = resp.body ?: throw IllegalStateException("Empty response")
                val respCd = resp.header("Content-Disposition") ?: initialCd
                val respMime = resp.header("Content-Type")?.substringBefore(';')?.trim()
                    ?.takeIf { it.isNotBlank() } ?: initialMime
                val resolvedName = resolveDownloadFileName(
                    url = resp.request.url.toString(),
                    contentDisposition = respCd,
                    mimeType = respMime,
                    suggestedFilename = suggestedFilename
                )
                val totalBytes = body.contentLength()
                val saved = writeStreamToAgentraDownloads(context, resolvedName, respMime) { out ->
                    val input = body.byteStream()
                    val buf = ByteArray(16 * 1024)
                    var copied = 0L
                    var lastReportAt = 0L
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        copied += read
                        val now = System.currentTimeMillis()
                        if (now - lastReportAt >= 350L) {
                            lastReportAt = now
                            val pctText = if (totalBytes > 0L) {
                                val pct = ((copied * 100L) / totalBytes).toInt().coerceIn(0, 99)
                                "Downloading $pct% (${formatByteSize(copied)})"
                            } else {
                                "Downloading ${formatByteSize(copied)}"
                            }
                            updateDownloadEntry(entryId) { it.copy(sizeLabel = pctText, fileName = resolvedName, mimeType = respMime) }
                        }
                    }
                    copied
                }
                val completedEntry = updateDownloadEntry(entryId) {
                    it.copy(
                        fileName = saved.fileName,
                        localPath = saved.localPath,
                        contentUri = saved.contentUri,
                        mimeType = respMime,
                        sizeLabel = "${formatByteSize(saved.byteCount)} • Download/$AGENTRA_DOWNLOADS_FOLDER"
                    )
                }
                if (completedEntry != null) {
                    postDownloadCompleteNotification(context, completedEntry)
                }
            }
        } catch (t: Throwable) {
            updateDownloadEntry(entryId) {
                it.copy(sizeLabel = "Failed: ${t.message ?: "Network error"}")
            }
        } finally {
            activeDownloadIds.remove(entryId)
        }
    }

    private fun saveDataUrlIntoEntry(
        context: Context,
        entryId: String,
        dataUrl: String,
        fallbackMime: String,
        fallbackName: String
    ) {
        try {
            val commaIdx = dataUrl.indexOf(',')
            if (commaIdx <= 5) throw IllegalArgumentException("Invalid data URL")
            val header = dataUrl.substring(5, commaIdx)
            val dataPart = dataUrl.substring(commaIdx + 1)
            val isBase64 = header.split(';').any { it.equals("base64", ignoreCase = true) }
            val parsedMime = header.substringBefore(';').trim().takeIf { it.contains('/') } ?: fallbackMime
            val bytes = if (isBase64) {
                Base64.decode(dataPart, Base64.DEFAULT)
            } else {
                URLDecoder.decode(dataPart, "UTF-8").toByteArray(Charsets.UTF_8)
            }
            val finalName = resolveDownloadFileName(
                url = "https://download.local/$fallbackName",
                contentDisposition = null,
                mimeType = parsedMime,
                suggestedFilename = fallbackName
            )
            val saved = writeStreamToAgentraDownloads(context, finalName, parsedMime) { out ->
                out.write(bytes)
                bytes.size.toLong()
            }
            val completed = updateDownloadEntry(entryId) {
                it.copy(
                    fileName = saved.fileName,
                    localPath = saved.localPath,
                    contentUri = saved.contentUri,
                    mimeType = parsedMime,
                    sizeLabel = "${formatByteSize(saved.byteCount)} • Download/$AGENTRA_DOWNLOADS_FOLDER"
                )
            }
            if (completed != null) {
                postDownloadCompleteNotification(context, completed)
            }
        } catch (t: Throwable) {
            updateDownloadEntry(entryId) {
                it.copy(sizeLabel = "Failed: ${t.message ?: "Save error"}")
            }
        } finally {
            activeDownloadIds.remove(entryId)
        }
    }

    private fun postDownloadCompleteNotification(context: Context, entry: DownloadEntry) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (nm.getNotificationChannel(DOWNLOAD_CHANNEL_ID) == null) {
                    val ch = NotificationChannel(
                        DOWNLOAD_CHANNEL_ID,
                        "Agentra Downloads",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Browser downloads saved to Download/$AGENTRA_DOWNLOADS_FOLDER"
                    }
                    nm.createNotificationChannel(ch)
                }
            }
            val viewIntent = buildViewDownloadIntent(context, entry)
            val pendingIntent = viewIntent?.let {
                PendingIntent.getActivity(
                    context,
                    entry.id.hashCode(),
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            val notif = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(entry.fileName)
                .setContentText("Saved to Download/$AGENTRA_DOWNLOADS_FOLDER")
                .setAutoCancel(true)
                .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
                .build()
            nm.notify(notifIdSeq.getAndIncrement(), notif)
        }
    }

    fun buildViewDownloadIntent(context: Context, entry: DownloadEntry): Intent? {
        val uri: Uri? = when {
            entry.contentUri.isNotBlank() -> runCatching { Uri.parse(entry.contentUri) }.getOrNull()
            entry.localPath.isNotBlank() && File(entry.localPath).exists() -> runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(entry.localPath))
            }.getOrNull()
            else -> null
        }
        if (uri == null) return null
        val mime = entry.mimeType.takeIf { it.contains('/') } ?: "*/*"
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun openDownloadEntry(context: Context, entry: DownloadEntry): Boolean {
        val intent = buildViewDownloadIntent(context, entry) ?: return false
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun refreshDownloadsFromStorage(context: Context) {
        val appCtx = context.applicationContext ?: context
        if (appContext == null) init(appCtx)
        downloadExecutor.execute {
            runCatching {
                val diskEntries = mutableListOf<DownloadEntry>()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val cols = arrayOf(
                        MediaStore.Downloads._ID,
                        MediaStore.Downloads.DISPLAY_NAME,
                        MediaStore.Downloads.MIME_TYPE,
                        MediaStore.Downloads.SIZE,
                        MediaStore.Downloads.DATE_MODIFIED,
                        MediaStore.MediaColumns.DATA
                    )
                    val sel = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
                    val args = arrayOf("%$AGENTRA_DOWNLOADS_FOLDER%")
                    appCtx.contentResolver.query(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        cols,
                        sel,
                        args,
                        "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                    )?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(MediaStore.Downloads._ID)
                        val nameIdx = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                        val mimeIdx = cursor.getColumnIndex(MediaStore.Downloads.MIME_TYPE)
                        val sizeIdx = cursor.getColumnIndex(MediaStore.Downloads.SIZE)
                        val dateIdx = cursor.getColumnIndex(MediaStore.Downloads.DATE_MODIFIED)
                        val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        while (cursor.moveToNext()) {
                            val rowId = if (idIdx >= 0) cursor.getLong(idIdx) else continue
                            val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                            if (name.isBlank() || name.startsWith(".pending")) continue
                            val mime = (if (mimeIdx >= 0) cursor.getString(mimeIdx) else null)
                                ?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
                            val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                            val modMs = if (dateIdx >= 0) cursor.getLong(dateIdx) * 1000L else System.currentTimeMillis()
                            val path = (if (dataIdx >= 0) cursor.getString(dataIdx) else null)
                                ?.takeIf { it.isNotBlank() }
                                ?: File(resolvePublicAgentraDownloadsDir(), name).absolutePath
                            val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, rowId.toString())
                            diskEntries.add(
                                DownloadEntry(
                                    fileName = name,
                                    url = "",
                                    localPath = path,
                                    mimeType = mime,
                                    sizeLabel = "${formatByteSize(size.coerceAtLeast(0L))} • Download/$AGENTRA_DOWNLOADS_FOLDER",
                                    isOfflinePage = false,
                                    timestamp = if (modMs > 0L) modMs else System.currentTimeMillis(),
                                    contentUri = uri.toString()
                                )
                            )
                        }
                    }
                }

                // Also scan public Download/Agentra Downloads directly when filesystem readable
                val folder = resolvePublicAgentraDownloadsDir()
                if (folder.exists() && folder.isDirectory) {
                    folder.listFiles()?.forEach { f ->
                        if (f.isFile && !f.name.startsWith(".")) {
                            val alreadySeen = diskEntries.any { it.localPath == f.absolutePath || it.fileName == f.name }
                            if (!alreadySeen) {
                                val ext = f.extension.lowercase()
                                val guessedMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                                    ?: "application/octet-stream"
                                val providerUri = runCatching {
                                    FileProvider.getUriForFile(appCtx, "${appCtx.packageName}.fileprovider", f).toString()
                                }.getOrDefault("")
                                diskEntries.add(
                                    DownloadEntry(
                                        fileName = f.name,
                                        url = "",
                                        localPath = f.absolutePath,
                                        mimeType = guessedMime,
                                        sizeLabel = "${formatByteSize(f.length())} • Download/$AGENTRA_DOWNLOADS_FOLDER",
                                        isOfflinePage = false,
                                        timestamp = f.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
                                        contentUri = providerUri
                                    )
                                )
                            }
                        }
                    }
                }

                if (diskEntries.isNotEmpty()) {
                    synchronized(downloadsLock) {
                        val current = _downloads.value.toMutableList()
                        for (disk in diskEntries) {
                            val matchIdx = current.indexOfFirst {
                                (disk.contentUri.isNotBlank() && it.contentUri == disk.contentUri) ||
                                    (disk.localPath.isNotBlank() && it.localPath == disk.localPath) ||
                                    (it.fileName == disk.fileName && !it.isOfflinePage && it.id !in activeDownloadIds)
                            }
                            if (matchIdx >= 0) {
                                val existing = current[matchIdx]
                                current[matchIdx] = existing.copy(
                                    fileName = disk.fileName,
                                    localPath = disk.localPath,
                                    contentUri = disk.contentUri.ifBlank { existing.contentUri },
                                    mimeType = disk.mimeType.ifBlank { existing.mimeType },
                                    sizeLabel = disk.sizeLabel
                                )
                            } else {
                                current.add(disk)
                            }
                        }
                        _downloads.value = current.sortedByDescending { it.timestamp }.take(100)
                        persistDownloads()
                    }
                }
            }
        }
    }

    class WebViewDownloadBridge(private val context: Context) {
        @JavascriptInterface
        fun onBlobDataReady(entryId: String, dataUrl: String, detectedMime: String) {
            val appCtx = context.applicationContext ?: context
            val existing = synchronized(downloadsLock) {
                _downloads.value.firstOrNull { it.id == entryId }
            }
            val fallbackName = existing?.fileName ?: "download_${System.currentTimeMillis()}"
            val fallbackMime = detectedMime.takeIf { it.isNotBlank() } ?: (existing?.mimeType ?: "application/octet-stream")
            downloadExecutor.execute {
                saveDataUrlIntoEntry(appCtx, entryId, dataUrl, fallbackMime, fallbackName)
            }
        }

        @JavascriptInterface
        fun onBlobFailed(entryId: String, reason: String) {
            activeDownloadIds.remove(entryId)
            updateDownloadEntry(entryId) {
                it.copy(sizeLabel = "Failed: ${reason.ifBlank { "Blob download error" }}")
            }
        }
    }

    fun recordOfflinePage(title: String, url: String, archiveFile: File): DownloadEntry {
        val sizeKb = (archiveFile.length() / 1024L).coerceAtLeast(1L)
        val entry = DownloadEntry(
            fileName = title.ifBlank { archiveFile.name },
            url = url,
            localPath = archiveFile.absolutePath,
            mimeType = "multipart/related",
            sizeLabel = "${sizeKb} KB • Offline",
            isOfflinePage = true
        )
        addDownloadEntry(entry)
        return entry
    }

    fun addDownloadEntry(entry: DownloadEntry) {
        synchronized(downloadsLock) {
            _downloads.value = (listOf(entry) + _downloads.value).take(100)
            persistDownloads()
        }
    }

    private fun updateDownloadEntry(id: String, transform: (DownloadEntry) -> DownloadEntry): DownloadEntry? {
        synchronized(downloadsLock) {
            var updatedItem: DownloadEntry? = null
            _downloads.value = _downloads.value.map { item ->
                if (item.id == id) {
                    transform(item).also { updatedItem = it }
                } else {
                    item
                }
            }
            persistDownloads()
            return updatedItem
        }
    }

    fun removeDownloadEntry(id: String) {
        val target = synchronized(downloadsLock) {
            val hit = _downloads.value.firstOrNull { it.id == id }
            _downloads.value = _downloads.value.filterNot { it.id == id }
            persistDownloads()
            hit
        }
        if (target != null) {
            if (target.contentUri.startsWith("content://") && !target.isOfflinePage) {
                runCatching { appContext?.contentResolver?.delete(Uri.parse(target.contentUri), null, null) }
            }
            if (target.localPath.isNotBlank()) {
                runCatching { File(target.localPath).delete() }
            }
        }
    }

    private fun persistDownloads() {
        val arr = JSONArray()
        _downloads.value.forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.id)
                put("fileName", d.fileName)
                put("url", d.url)
                put("localPath", d.localPath)
                put("mimeType", d.mimeType)
                put("sizeLabel", d.sizeLabel)
                put("isOfflinePage", d.isOfflinePage)
                put("timestamp", d.timestamp)
                put("contentUri", d.contentUri)
            })
        }
        prefs()?.edit()?.putString(KEY_DOWNLOADS, arr.toString())?.apply()
    }

    fun addToPlaylist(title: String, mediaUrl: String, pageUrl: String, isVideo: Boolean = true) {
        val host = BrowserAdBlockEngine.extractHost(pageUrl.ifBlank { mediaUrl })
        val item = PlaylistMediaItem(
            title = title.ifBlank { "Watch Later ($host)" },
            url = mediaUrl,
            pageUrl = pageUrl.ifBlank { mediaUrl },
            domain = host.ifBlank { "media" },
            isVideo = isVideo
        )
        _playlist.value = (listOf(item) + _playlist.value.filterNot { it.url == mediaUrl }).take(100)
        persistPlaylist()
    }

    fun removeFromPlaylist(id: String) {
        _playlist.value = _playlist.value.filterNot { it.id == id }
        persistPlaylist()
    }

    private fun persistPlaylist() {
        val arr = JSONArray()
        _playlist.value.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("title", p.title)
                put("url", p.url)
                put("pageUrl", p.pageUrl)
                put("domain", p.domain)
                put("isVideo", p.isVideo)
                put("addedAt", p.addedAt)
            })
        }
        prefs()?.edit()?.putString(KEY_PLAYLIST, arr.toString())?.apply()
    }

    fun createOrUpdateTabGroup(title: String, colorHex: Long = 0xFFF25F70, id: String? = null): TabGroupRecord {
        val existing = id?.let { gid -> _tabGroups.value.firstOrNull { it.id == gid } }
        val group = existing?.copy(title = title, colorHex = colorHex, updatedAt = System.currentTimeMillis())
            ?: TabGroupRecord(title = title, colorHex = colorHex)
        _tabGroups.value = _tabGroups.value.filterNot { it.id == group.id } + group
        persistTabGroups()
        return group
    }

    fun removeTabGroup(id: String) {
        _tabGroups.value = _tabGroups.value.filterNot { it.id == id }
        persistTabGroups()
    }

    private fun persistTabGroups() {
        val arr = JSONArray()
        _tabGroups.value.forEach { g ->
            arr.put(JSONObject().apply {
                put("id", g.id)
                put("title", g.title)
                put("colorHex", g.colorHex)
                put("updatedAt", g.updatedAt)
            })
        }
        prefs()?.edit()?.putString(KEY_TAB_GROUPS, arr.toString())?.apply()
    }

    fun formatHistoryDayLabel(timestamp: Long): String {
        val today = com.jarvisai.assistant.util.IndiaTimeUtils.calendarNow()
        val todayDay = today.get(Calendar.DAY_OF_YEAR)
        val todayYear = today.get(Calendar.YEAR)

        val target = com.jarvisai.assistant.util.IndiaTimeUtils.calendarAt(timestamp)
        val tDay = target.get(Calendar.DAY_OF_YEAR)
        val tYear = target.get(Calendar.YEAR)

        return when {
            tYear == todayYear && tDay == todayDay -> "Today"
            tYear == todayYear && tDay == todayDay - 1 -> "Yesterday"
            else -> com.jarvisai.assistant.util.IndiaTimeUtils.format("EEE, MMM d, yyyy", timestamp)
        }
    }

    fun formatRelativeTime(timestamp: Long): String {
        val diffSec = ((System.currentTimeMillis() - timestamp) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 60 -> "Updated just now"
            diffSec < 3600 -> "${diffSec / 60}m ago"
            diffSec < 86400 -> "${diffSec / 3600}h ago"
            else -> com.jarvisai.assistant.util.IndiaTimeUtils.format("MMM d", timestamp)
        }
    }

    private fun defaultSpeedDials(): List<SpeedDialItem> = listOf(
        SpeedDialItem(
            id = "dial_google",
            title = "Google",
            url = "https://www.google.com",
            badgeLetter = "G",
            accentHex = 0xFF4285F4
        ),
        SpeedDialItem(
            id = "dial_youtube",
            title = "YouTube",
            url = "https://m.youtube.com",
            badgeLetter = "▶",
            accentHex = 0xFFFF0000
        ),
        SpeedDialItem(
            id = "dial_facebook",
            title = "Facebook",
            url = "https://m.facebook.com",
            badgeLetter = "f",
            accentHex = 0xFF1877F2
        ),
        SpeedDialItem(
            id = "dial_extensions",
            title = "Extensions",
            url = "agentra://extensions",
            badgeLetter = "🧩",
            accentHex = 0xFFF25F70,
            specialAction = "EXTENSIONS"
        ),
        SpeedDialItem(
            id = "dial_webstore",
            title = "Web Store",
            url = "https://chromewebstore.google.com/",
            badgeLetter = "◈",
            accentHex = 0xFF34A853,
            specialAction = "WEBSTORE"
        )
    )
}
