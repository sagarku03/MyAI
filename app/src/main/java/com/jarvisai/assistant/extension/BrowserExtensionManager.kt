package com.jarvisai.assistant.extension

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object BrowserExtensionManager {

    private const val TAG = "BrowserExtensionManager"
    private const val PREFS_NAME = "jarvis_extension_prefs"
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _extensions = MutableStateFlow<List<BrowserExtension>>(emptyList())
    val extensions: StateFlow<List<BrowserExtension>> = _extensions.asStateFlow()

    private val _actionUiStates = MutableStateFlow<Map<String, ExtensionActionUiState>>(emptyMap())
    val actionUiStates: StateFlow<Map<String, ExtensionActionUiState>> = _actionUiStates.asStateFlow()

    private val _contextMenuItems = MutableStateFlow<List<ExtensionContextMenuItem>>(emptyList())
    val contextMenuItems: StateFlow<List<ExtensionContextMenuItem>> = _contextMenuItems.asStateFlow()

    private val _isQuickSheetGrid = MutableStateFlow(false)
    val isQuickSheetGrid: StateFlow<Boolean> = _isQuickSheetGrid.asStateFlow()

    private val workerHosts = ConcurrentHashMap<String, ExtensionWorkerHost>()
    private val workerLogsMap = ConcurrentHashMap<String, MutableList<String>>()
    private val dnrStaticRulesMap = ConcurrentHashMap<String, List<DeclarativeRule>>()
    private val dnrDynamicRulesMap = ConcurrentHashMap<String, MutableList<DeclarativeRule>>()
    private val dnrSessionRulesMap = ConcurrentHashMap<String, MutableList<DeclarativeRule>>()
    private val _logsTick = MutableStateFlow(0L)
    val logsTick: StateFlow<Long> = _logsTick.asStateFlow()

    data class DeclarativeRule(
        val id: Int,
        val priority: Int = 1,
        val actionType: String, // "block", "redirect", "allow", "allowAllRequests"
        val redirectUrl: String = "",
        val urlFilter: String = "",
        val regexFilter: Regex? = null,
        val initiatorDomains: Set<String> = emptySet(),
        val excludedInitiatorDomains: Set<String> = emptySet(),
        val requestDomains: Set<String> = emptySet(),
        val excludedRequestDomains: Set<String> = emptySet(),
        val isUrlFilterCaseSensitive: Boolean = false
    )

    data class DnrEvaluationResult(
        val actionType: String = "none", // "none" | "block" | "redirect" | "allow"
        val redirectUrl: String = "",
        val matchedExtensionId: String = "",
        val ruleId: Int = -1
    )

    fun getInstance(context: Context): BrowserExtensionManager {
        init(context)
        return this
    }

    fun reloadExtensions() {
        val ctx = appContext ?: return
        loadInstalledExtensions(ctx)
    }

    fun init(context: Context) {
        val appCtx = context.applicationContext ?: context
        if (appContext?.filesDir == appCtx.filesDir) return
        appContext = appCtx
        unpackBundledExtensions(appCtx)
        loadInstalledExtensions(appCtx)
    }

    private fun getExtensionsDir(context: Context): File {
        return File(context.filesDir, "extensions").apply { mkdirs() }
    }

    private fun unpackBundledExtensions(context: Context) {
        try {
            val assetList = context.assets.list("extensions") ?: return
            val targetDir = getExtensionsDir(context)

            for (extName in assetList) {
                val extTargetDir = File(targetDir, extName)
                // Always sync bundled extension files so upgraded popup.html / manifest.json are present
                copyAssetFolder(context, "extensions/$extName", extTargetDir)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed unpacking bundled extensions: ${e.message}")
        }
    }

    private fun copyAssetFolder(context: Context, assetPath: String, targetDir: File) {
        val files = context.assets.list(assetPath) ?: return
        targetDir.mkdirs()
        for (file in files) {
            val subPath = "$assetPath/$file"
            val subFiles = context.assets.list(subPath)
            if (subFiles != null && subFiles.isNotEmpty()) {
                copyAssetFolder(context, subPath, File(targetDir, file))
            } else {
                context.assets.open(subPath).use { input ->
                    File(targetDir, file).outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    fun loadInstalledExtensions(context: Context) {
        appContext = context.applicationContext ?: context
        val dir = getExtensionsDir(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isQuickSheetGrid.value = prefs.getBoolean("quick_sheet_grid", false)
        val list = mutableListOf<BrowserExtension>()
        val actionDefaults = mutableMapOf<String, ExtensionActionUiState>()

        dir.listFiles()?.filter { it.isDirectory }?.forEach { extDir ->
            val manifestFile = File(extDir, "manifest.json")
            if (manifestFile.exists()) {
                try {
                    val json = JSONObject(manifestFile.readText())
                    val parsed = BrowserExtension.fromManifest(extDir, json)
                    val savedEnabled = if (prefs.contains("enabled_${parsed.id}")) {
                        prefs.getBoolean("enabled_${parsed.id}", parsed.isEnabled)
                    } else {
                        parsed.isEnabled
                    }
                    val savedPrivate = if (prefs.contains("private_${parsed.id}")) {
                        prefs.getBoolean("private_${parsed.id}", parsed.allowInPrivate)
                    } else {
                        parsed.allowInPrivate
                    }
                    val savedPinned = if (prefs.contains("pinned_${parsed.id}")) {
                        prefs.getBoolean("pinned_${parsed.id}", parsed.pinned)
                    } else {
                        parsed.pinned
                    }
                    val savedUserScripts = if (prefs.contains("userscripts_${parsed.id}")) {
                        prefs.getBoolean("userscripts_${parsed.id}", parsed.allowUserScripts)
                    } else {
                        parsed.allowUserScripts
                    }
                    val ext = parsed.copy(
                        isEnabled = savedEnabled,
                        allowInPrivate = savedPrivate,
                        pinned = savedPinned,
                        allowUserScripts = savedUserScripts
                    )
                    list.add(ext)

                    val prevAction = _actionUiStates.value[ext.id]
                    actionDefaults[ext.id] = prevAction ?: ExtensionActionUiState(
                        title = ext.actionTitle.ifBlank { ext.name }
                    )

                    if (ext.isEnabled) {
                        loadStaticDnrRulesForExtension(ext)
                        val host = workerHosts.getOrPut(ext.id) { ExtensionWorkerHost(context, ext) }
                        if (ext.serviceWorkerFile.isNotBlank() || ext.backgroundScripts.isNotEmpty() || ext.backgroundPage.isNotBlank()) {
                            host.initialize {}
                        }
                    } else {
                        dnrStaticRulesMap.remove(ext.id)
                        workerHosts.remove(ext.id)?.destroy()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing manifest for ${extDir.name}", e)
                }
            }
        }
        _actionUiStates.value = actionDefaults
        _extensions.value = list.sortedBy { it.name.lowercase() }
    }

    private fun startWorker(context: Context, ext: BrowserExtension): ExtensionWorkerHost {
        val existing = workerHosts[ext.id]
        if (existing != null) return existing
        val host = ExtensionWorkerHost(context, ext).apply { initialize {} }
        workerHosts[ext.id] = host
        return host
    }

    fun getWorkerHost(extId: String): ExtensionWorkerHost? = workerHosts[extId]

    fun reloadSingleExtension(extId: String) {
        val ctx = appContext ?: return
        workerHosts.remove(extId)?.destroy()
        _contextMenuItems.value = _contextMenuItems.value.filterNot { it.extensionId == extId }
        dnrSessionRulesMap.remove(extId)
        loadInstalledExtensions(ctx)
        recordLog(extId, "Extension reloaded")
    }

    fun isWorkerRunning(extId: String): Boolean {
        val ext = _extensions.value.firstOrNull { it.id == extId } ?: return false
        if (!ext.isEnabled) return false
        return workerHosts[extId] != null
    }

    fun updateDynamicUiPath(extId: String, rawPath: String) {
        val clean = rawPath.trim().removePrefix("/")
        if (clean.isBlank()) return
        val current = _extensions.value.toMutableList()
        val idx = current.indexOfFirst { it.id.equals(extId, ignoreCase = true) }
        if (idx >= 0) {
            val existing = current[idx]
            val f = File(existing.dirPath, clean.substringBefore("?").substringBefore("#"))
            if (f.exists() && f.isFile) {
                current[idx] = existing.copy(
                    sidePanelPath = clean,
                    popupFile = existing.popupFile.ifBlank { clean }
                )
                _extensions.value = current
            }
        }
    }

    fun dispatchActionClickedToWorker(extId: String, tabJson: String = "{\"id\":1,\"url\":\"https://www.google.com\",\"active\":true}") {
        val ctx = appContext ?: return
        val ext = _extensions.value.firstOrNull { it.id == extId && it.isEnabled } ?: return
        val host = workerHosts[extId] ?: startWorker(ctx, ext)
        host.dispatchActionClicked(tabJson)
        recordLog(extId, "Action clicked dispatched to worker")
    }

    fun dispatchRuntimeMessageToWorker(
        extId: String,
        msgJson: String,
        senderJson: String,
        replyKey: String
    ): Boolean {
        val ctx = appContext ?: return false
        val ext = _extensions.value.firstOrNull { it.id == extId && it.isEnabled } ?: return false
        val host = workerHosts[extId] ?: startWorker(ctx, ext)
        host.dispatchRuntimeMessage(msgJson, senderJson, replyKey)
        return true
    }

    fun recordLog(extId: String, message: String) {
        val list = workerLogsMap.getOrPut(extId) { mutableListOf() }
        val ts = com.jarvisai.assistant.util.IndiaTimeUtils.nowTime24h()
        synchronized(list) {
            list.add("[$ts] $message")
            if (list.size > 150) {
                list.removeAt(0)
            }
        }
        _logsTick.value = _logsTick.value + 1L
    }

    fun getWorkerLogs(extId: String): List<String> {
        val list = workerLogsMap[extId] ?: return emptyList()
        synchronized(list) {
            return list.toList()
        }
    }

    suspend fun evaluateInWorker(extId: String, js: String): Result<String> {
        val ctx = appContext ?: return Result.failure(IllegalStateException("Extension manager not initialized"))
        val ext = _extensions.value.firstOrNull { it.id == extId }
            ?: return Result.failure(IllegalArgumentException("Extension not found"))
        val host = workerHosts[extId] ?: startWorker(ctx, ext)
        recordLog(extId, "> $js")
        val res = host.evalRawJs(js)
        res.onSuccess { recordLog(extId, "< $it") }
            .onFailure { recordLog(extId, "! ${it.message}") }
        return res
    }

    suspend fun executeTool(extensionId: String, toolName: String, args: JSONObject): Result<String> {
        val ctx = appContext
        val host = workerHosts[extensionId] ?: run {
            val ext = _extensions.value.firstOrNull { it.id == extensionId && it.isEnabled }
            if (ctx != null && ext != null) {
                startWorker(ctx, ext)
            } else {
                return Result.failure(IllegalStateException("Extension $extensionId worker is not enabled"))
            }
        }
        return host.executeTool(toolName, args)
    }

    fun toggleExtension(extId: String, enable: Boolean) {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("enabled_$extId", enable)
            .apply()

        val current = _extensions.value.toMutableList()
        val index = current.indexOfFirst { it.id == extId }
        if (index != -1) {
            val updated = current[index].copy(isEnabled = enable)
            current[index] = updated
            _extensions.value = current

            val dir = File(updated.dirPath)
            if (dir.exists()) {
                CrxExtensionInstaller.writeMetadataFile(
                    dir = dir,
                    id = updated.id,
                    sourceType = updated.sourceType,
                    updateUrl = updated.updateUrl,
                    enabled = enable,
                    allowInPrivate = updated.allowInPrivate,
                    pinned = updated.pinned,
                    allowUserScripts = updated.allowUserScripts
                )
            }

            if (enable) {
                loadStaticDnrRulesForExtension(updated)
                startWorker(ctx, updated)
                recordLog(extId, "Extension enabled")
            } else {
                dnrStaticRulesMap.remove(extId)
                dnrSessionRulesMap.remove(extId)
                _contextMenuItems.value = _contextMenuItems.value.filterNot { it.extensionId == extId }
                workerHosts.remove(extId)?.destroy()
                recordLog(extId, "Extension disabled")
            }
        }
    }

    fun togglePrivateAccess(extId: String, allow: Boolean) {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("private_$extId", allow)
            .apply()

        val current = _extensions.value.toMutableList()
        val index = current.indexOfFirst { it.id == extId }
        if (index != -1) {
            val updated = current[index].copy(allowInPrivate = allow)
            current[index] = updated
            _extensions.value = current
            val dir = File(updated.dirPath)
            if (dir.exists()) {
                CrxExtensionInstaller.writeMetadataFile(
                    dir = dir,
                    id = updated.id,
                    sourceType = updated.sourceType,
                    updateUrl = updated.updateUrl,
                    enabled = updated.isEnabled,
                    allowInPrivate = allow,
                    pinned = updated.pinned,
                    allowUserScripts = updated.allowUserScripts
                )
            }
        }
    }

    fun togglePinned(extId: String, pinned: Boolean) {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("pinned_$extId", pinned)
            .apply()

        val current = _extensions.value.toMutableList()
        val index = current.indexOfFirst { it.id == extId }
        if (index != -1) {
            val updated = current[index].copy(pinned = pinned)
            current[index] = updated
            _extensions.value = current
            val dir = File(updated.dirPath)
            if (dir.exists()) {
                CrxExtensionInstaller.writeMetadataFile(
                    dir = dir,
                    id = updated.id,
                    sourceType = updated.sourceType,
                    updateUrl = updated.updateUrl,
                    enabled = updated.isEnabled,
                    allowInPrivate = updated.allowInPrivate,
                    pinned = pinned,
                    allowUserScripts = updated.allowUserScripts
                )
            }
        }
    }

    fun toggleAllowUserScripts(extId: String, allow: Boolean) {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("userscripts_$extId", allow)
            .apply()

        val current = _extensions.value.toMutableList()
        val index = current.indexOfFirst { it.id == extId }
        if (index != -1) {
            val updated = current[index].copy(allowUserScripts = allow)
            current[index] = updated
            _extensions.value = current
            val dir = File(updated.dirPath)
            if (dir.exists()) {
                CrxExtensionInstaller.writeMetadataFile(
                    dir = dir,
                    id = updated.id,
                    sourceType = updated.sourceType,
                    updateUrl = updated.updateUrl,
                    enabled = updated.isEnabled,
                    allowInPrivate = updated.allowInPrivate,
                    pinned = updated.pinned,
                    allowUserScripts = allow
                )
            }
        }
    }

    fun setQuickSheetGrid(isGrid: Boolean) {
        _isQuickSheetGrid.value = isGrid
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean("quick_sheet_grid", isGrid)
            ?.apply()
    }

    fun getActiveNewTabOverride(): BrowserExtension? {
        return _extensions.value.firstOrNull {
            it.isEnabled && it.newTabOverridePage.isNotBlank() &&
                File(it.dirPath, it.newTabOverridePage.substringBefore("?").substringBefore("#")).exists()
        }
    }

    fun updateActionBadge(extId: String, text: String) {
        val cur = _actionUiStates.value.toMutableMap()
        val prev = cur[extId] ?: ExtensionActionUiState()
        cur[extId] = prev.copy(badgeText = text.take(4))
        _actionUiStates.value = cur
    }

    fun updateActionBadgeColor(extId: String, colorHex: Long) {
        val cur = _actionUiStates.value.toMutableMap()
        val prev = cur[extId] ?: ExtensionActionUiState()
        cur[extId] = prev.copy(badgeColorHex = colorHex)
        _actionUiStates.value = cur
    }

    fun updateActionTitle(extId: String, title: String) {
        val cur = _actionUiStates.value.toMutableMap()
        val prev = cur[extId] ?: ExtensionActionUiState()
        cur[extId] = prev.copy(title = title)
        _actionUiStates.value = cur
    }

    fun upsertContextMenuItem(item: ExtensionContextMenuItem) {
        val next = _contextMenuItems.value.toMutableList()
        next.removeAll { it.extensionId == item.extensionId && it.itemId == item.itemId }
        next.add(item)
        _contextMenuItems.value = next
    }

    fun removeContextMenuItem(extId: String, itemId: String) {
        _contextMenuItems.value = _contextMenuItems.value.filterNot {
            it.extensionId == extId && it.itemId == itemId
        }
    }

    fun clearContextMenuItems(extId: String) {
        _contextMenuItems.value = _contextMenuItems.value.filterNot { it.extensionId == extId }
    }

    private fun loadStaticDnrRulesForExtension(ext: BrowserExtension) {
        if (ext.declarativeRuleResources.isEmpty() || ext.dirPath.isBlank()) {
            dnrStaticRulesMap.remove(ext.id)
            return
        }
        val parsed = mutableListOf<DeclarativeRule>()
        for (resRel in ext.declarativeRuleResources) {
            val f = File(ext.dirPath, resRel.removePrefix("/"))
            if (f.exists() && f.isFile) {
                runCatching {
                    val arr = JSONArray(f.readText())
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { parseDnrRule(it)?.let(parsed::add) }
                    }
                }
            }
        }
        dnrStaticRulesMap[ext.id] = parsed
    }

    private fun parseDnrRule(obj: JSONObject): DeclarativeRule? {
        val id = obj.optInt("id", -1)
        val priority = obj.optInt("priority", 1)
        val actionObj = obj.optJSONObject("action") ?: return null
        val actionType = actionObj.optString("type", "").lowercase()
        if (actionType.isBlank()) return null
        val redirectUrl = actionObj.optJSONObject("redirect")?.optString("url", "").orEmpty()
        val cond = obj.optJSONObject("condition") ?: JSONObject()
        val urlFilter = cond.optString("urlFilter", "")
        val regexFilterRaw = cond.optString("regexFilter", "")
        val caseSensitive = cond.optBoolean("isUrlFilterCaseSensitive", false)
        val compiledRegex = if (regexFilterRaw.isNotBlank()) {
            runCatching {
                Regex(regexFilterRaw, if (caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE))
            }.getOrNull()
        } else null

        fun readDomainSet(key: String): Set<String> {
            val a = cond.optJSONArray(key) ?: return emptySet()
            val set = mutableSetOf<String>()
            for (i in 0 until a.length()) {
                val s = a.optString(i).trim().lowercase()
                if (s.isNotEmpty()) set.add(s)
            }
            return set
        }

        return DeclarativeRule(
            id = id,
            priority = priority,
            actionType = actionType,
            redirectUrl = redirectUrl,
            urlFilter = urlFilter,
            regexFilter = compiledRegex,
            initiatorDomains = readDomainSet("initiatorDomains"),
            excludedInitiatorDomains = readDomainSet("excludedInitiatorDomains"),
            requestDomains = readDomainSet("requestDomains"),
            excludedRequestDomains = readDomainSet("excludedRequestDomains"),
            isUrlFilterCaseSensitive = caseSensitive
        )
    }

    fun updateDeclarativeRules(
        extId: String,
        isSession: Boolean,
        removeIds: List<Int>,
        addRulesArray: JSONArray?
    ) {
        val store = if (isSession) dnrSessionRulesMap else dnrDynamicRulesMap
        val list = store.getOrPut(extId) { mutableListOf() }
        synchronized(list) {
            if (removeIds.isNotEmpty()) {
                val rm = removeIds.toSet()
                list.removeAll { it.id in rm }
            }
            if (addRulesArray != null) {
                for (i in 0 until addRulesArray.length()) {
                    addRulesArray.optJSONObject(i)?.let { parseDnrRule(it)?.let(list::add) }
                }
            }
        }
    }

    fun getDeclarativeRulesJson(extId: String, isSession: Boolean): String {
        val list = if (isSession) dnrSessionRulesMap[extId] else dnrDynamicRulesMap[extId]
        val arr = JSONArray()
        list?.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("priority", r.priority)
                    put("action", JSONObject().apply {
                        put("type", r.actionType)
                        if (r.redirectUrl.isNotBlank()) {
                            put("redirect", JSONObject().put("url", r.redirectUrl))
                        }
                    })
                    put("condition", JSONObject().apply {
                        if (r.urlFilter.isNotBlank()) put("urlFilter", r.urlFilter)
                        if (r.regexFilter != null) put("regexFilter", r.regexFilter.pattern)
                    })
                }
            )
        }
        return arr.toString()
    }

    fun evaluateDeclarativeNetRequest(url: String?, initiatorUrl: String? = null): DnrEvaluationResult {
        if (url.isNullOrBlank()) return DnrEvaluationResult()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return DnrEvaluationResult()
        val reqHost = runCatching { Uri.parse(url).host?.lowercase().orEmpty() }.getOrDefault("")
        val initHost = initiatorUrl?.let { runCatching { Uri.parse(it).host?.lowercase().orEmpty() }.getOrNull() }.orEmpty()

        var bestAllowPriority = Int.MIN_VALUE
        var bestRule: Pair<String, DeclarativeRule>? = null

        for (ext in _extensions.value) {
            if (!ext.isEnabled) continue
            val allRules = buildList {
                dnrStaticRulesMap[ext.id]?.let(::addAll)
                dnrDynamicRulesMap[ext.id]?.let(::addAll)
                dnrSessionRulesMap[ext.id]?.let(::addAll)
            }
            for (rule in allRules) {
                if (!matchesDnrCondition(rule, url, reqHost, initHost)) continue
                if (rule.actionType == "allow" || rule.actionType == "allowallrequests") {
                    if (rule.priority >= bestAllowPriority) bestAllowPriority = rule.priority
                } else if (rule.actionType == "block" || rule.actionType == "redirect") {
                    if (bestRule == null || rule.priority > bestRule.second.priority) {
                        bestRule = ext.id to rule
                    }
                }
            }
        }
        val winner = bestRule ?: return DnrEvaluationResult()
        if (bestAllowPriority > winner.second.priority) {
            return DnrEvaluationResult(actionType = "allow", matchedExtensionId = winner.first, ruleId = winner.second.id)
        }
        return DnrEvaluationResult(
            actionType = winner.second.actionType,
            redirectUrl = winner.second.redirectUrl,
            matchedExtensionId = winner.first,
            ruleId = winner.second.id
        )
    }

    private fun matchesDnrCondition(rule: DeclarativeRule, url: String, reqHost: String, initHost: String): Boolean {
        if (rule.excludedRequestDomains.any { reqHost == it || reqHost.endsWith(".$it") }) return false
        if (rule.requestDomains.isNotEmpty() && rule.requestDomains.none { reqHost == it || reqHost.endsWith(".$it") }) return false
        if (initHost.isNotBlank()) {
            if (rule.excludedInitiatorDomains.any { initHost == it || initHost.endsWith(".$it") }) return false
            if (rule.initiatorDomains.isNotEmpty() && rule.initiatorDomains.none { initHost == it || initHost.endsWith(".$it") }) return false
        }
        if (rule.regexFilter != null) {
            if (!rule.regexFilter.containsMatchIn(url)) return false
        }
        val filter = rule.urlFilter
        if (filter.isNotBlank() && filter != "*") {
            return matchesDnrUrlFilter(url, filter, rule.isUrlFilterCaseSensitive)
        }
        return true
    }

    private fun matchesDnrUrlFilter(url: String, rawFilter: String, caseSensitive: Boolean): Boolean {
        val cmpUrl = if (caseSensitive) url else url.lowercase()
        var filter = if (caseSensitive) rawFilter else rawFilter.lowercase()
        val anchoredHost = filter.startsWith("||")
        val anchoredStart = !anchoredHost && filter.startsWith("|")
        val anchoredEnd = filter.endsWith("|")
        if (anchoredHost) filter = filter.removePrefix("||")
        else if (anchoredStart) filter = filter.removePrefix("|")
        if (anchoredEnd) filter = filter.dropLast(1)

        val patternBody = filter.split('*').joinToString(".*") { seg ->
            seg.split('^').joinToString("(?:[^a-zA-Z0-9_.%-]|$)") { Regex.escape(it) }
        }
        val fullPattern = when {
            anchoredHost -> "^https?://(?:[^/?#]+\\.)?$patternBody${if (anchoredEnd) "$" else ""}"
            anchoredStart -> "^$patternBody${if (anchoredEnd) "$" else ""}"
            anchoredEnd -> "$patternBody$"
            else -> patternBody
        }
        return runCatching { Regex(fullPattern).containsMatchIn(cmpUrl) }.getOrDefault(false)
    }

    fun removeExtension(extId: String): Boolean {
        val ctx = appContext ?: return false
        val target = _extensions.value.firstOrNull { it.id == extId } ?: return false
        workerHosts.remove(extId)?.destroy()
        workerLogsMap.remove(extId)
        dnrStaticRulesMap.remove(extId)
        dnrDynamicRulesMap.remove(extId)
        dnrSessionRulesMap.remove(extId)
        _contextMenuItems.value = _contextMenuItems.value.filterNot { it.extensionId == extId }
        val nextActions = _actionUiStates.value.toMutableMap()
        nextActions.remove(extId)
        _actionUiStates.value = nextActions
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("enabled_$extId")
            .remove("private_$extId")
            .remove("pinned_$extId")
            .remove("userscripts_$extId")
            .apply()
        val dir = File(target.dirPath)
        val deleted = if (dir.exists()) dir.deleteRecursively() else true
        loadInstalledExtensions(ctx)
        return deleted
    }

    suspend fun installFromUri(context: Context, uri: Uri): Result<BrowserExtension> {
        init(context)
        return CrxExtensionInstaller.installFromUri(context, uri).also { res ->
            res.onSuccess { recordLog(it.id, "Installed ${it.name} v${it.version} (${it.sourceType})") }
        }
    }

    suspend fun installFromChromeWebStore(context: Context, urlOrId: String): Result<BrowserExtension> {
        init(context)
        return CrxExtensionInstaller.installFromChromeWebStore(context, urlOrId).also { res ->
            res.onSuccess { recordLog(it.id, "Installed from Chrome Web Store v${it.version}") }
        }
    }

    suspend fun updateWebStoreExtensions(context: Context): Int {
        init(context)
        var updatedCount = 0
        val webStoreExts = _extensions.value.filter {
            it.sourceType == "webstore" || it.id.matches(Regex("^[a-p]{32}$"))
        }
        for (ext in webStoreExts) {
            val res = CrxExtensionInstaller.installFromChromeWebStore(context, ext.id)
            if (res.isSuccess) {
                updatedCount++
                recordLog(ext.id, "Updated from Chrome Web Store")
            }
        }
        return updatedCount
    }
}
