package com.jarvisai.assistant.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

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
    val timestamp: Long = System.currentTimeMillis()
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

    @Volatile
    private var appContext: Context? = null

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
                            timestamp = o.optLong("timestamp")
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
        if (!trimmed.contains(" ") && trimmed.contains(".")) {
            return "https://$trimmed"
        }
        if (lower.startsWith("localhost") || Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$").matches(trimmed)) {
            return "http://$trimmed"
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

    fun enqueueSystemDownload(
        context: Context,
        url: String,
        contentDisposition: String?,
        mimeType: String?
    ): DownloadEntry {
        val guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            .ifBlank { "download_${System.currentTimeMillis()}" }
        runCatching {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(guessedName)
                setDescription("Jarvis Browser Download")
                setMimeType(mimeType)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, guessedName)
            }
            dm.enqueue(req)
        }
        val entry = DownloadEntry(
            fileName = guessedName,
            url = url,
            localPath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), guessedName).absolutePath,
            mimeType = mimeType ?: "application/octet-stream",
            sizeLabel = "Queued",
            isOfflinePage = false
        )
        addDownloadEntry(entry)
        return entry
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
        _downloads.value = (listOf(entry) + _downloads.value).take(100)
        persistDownloads()
    }

    fun removeDownloadEntry(id: String) {
        val target = _downloads.value.firstOrNull { it.id == id }
        if (target != null && target.isOfflinePage && target.localPath.isNotBlank()) {
            runCatching { File(target.localPath).delete() }
        }
        _downloads.value = _downloads.value.filterNot { it.id == id }
        persistDownloads()
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
        val cal = Calendar.getInstance()
        val todayDay = cal.get(Calendar.DAY_OF_YEAR)
        val todayYear = cal.get(Calendar.YEAR)

        val target = Calendar.getInstance().apply { timeInMillis = timestamp }
        val tDay = target.get(Calendar.DAY_OF_YEAR)
        val tYear = target.get(Calendar.YEAR)

        return when {
            tYear == todayYear && tDay == todayDay -> "Today"
            tYear == todayYear && tDay == todayDay - 1 -> "Yesterday"
            else -> SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).format(Date(timestamp))
        }
    }

    fun formatRelativeTime(timestamp: Long): String {
        val diffSec = ((System.currentTimeMillis() - timestamp) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 60 -> "Updated just now"
            diffSec < 3600 -> "${diffSec / 60}m ago"
            diffSec < 86400 -> "${diffSec / 3600}h ago"
            else -> SimpleDateFormat("MMM d", Locale.US).format(Date(timestamp))
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
            url = "jarvis://extensions",
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
