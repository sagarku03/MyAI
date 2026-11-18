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

    private val workerHosts = ConcurrentHashMap<String, ExtensionWorkerHost>()
    private val workerLogsMap = ConcurrentHashMap<String, MutableList<String>>()
    private val _logsTick = MutableStateFlow(0L)
    val logsTick: StateFlow<Long> = _logsTick.asStateFlow()

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
        val list = mutableListOf<BrowserExtension>()

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
                    val ext = parsed.copy(
                        isEnabled = savedEnabled,
                        allowInPrivate = savedPrivate
                    )
                    list.add(ext)

                    if (ext.isEnabled) {
                        val host = workerHosts.getOrPut(ext.id) { ExtensionWorkerHost(context, ext) }
                        if (ext.serviceWorkerFile.isNotBlank()) {
                            host.initialize {}
                        }
                    } else {
                        workerHosts.remove(ext.id)?.destroy()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing manifest for ${extDir.name}", e)
                }
            }
        }
        _extensions.value = list.sortedBy { it.name.lowercase() }
    }

    private fun startWorker(context: Context, ext: BrowserExtension): ExtensionWorkerHost {
        val existing = workerHosts[ext.id]
        if (existing != null) return existing
        val host = ExtensionWorkerHost(context, ext).apply { initialize {} }
        workerHosts[ext.id] = host
        return host
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
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
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
                    allowInPrivate = updated.allowInPrivate
                )
            }

            if (enable) {
                startWorker(ctx, updated)
                recordLog(extId, "Extension enabled")
            } else {
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
                    allowInPrivate = allow
                )
            }
        }
    }

    fun removeExtension(extId: String): Boolean {
        val ctx = appContext ?: return false
        val target = _extensions.value.firstOrNull { it.id == extId } ?: return false
        workerHosts.remove(extId)?.destroy()
        workerLogsMap.remove(extId)
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("enabled_$extId")
            .remove("private_$extId")
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
