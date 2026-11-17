package com.jarvisai.assistant.extension

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object BrowserExtensionManager {

    private const val TAG = "BrowserExtensionManager"
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _extensions = MutableStateFlow<List<BrowserExtension>>(emptyList())
    val extensions: StateFlow<List<BrowserExtension>> = _extensions.asStateFlow()

    private val workerHosts = ConcurrentHashMap<String, ExtensionWorkerHost>()

    fun getInstance(context: Context): BrowserExtensionManager {
        init(context)
        return this
    }

    fun reloadExtensions() {
        val ctx = appContext ?: return
        loadInstalledExtensions(ctx)
    }

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        unpackBundledExtensions(context)
        loadInstalledExtensions(context)
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
                if (!extTargetDir.exists() || !File(extTargetDir, "manifest.json").exists()) {
                    copyAssetFolder(context, "extensions/$extName", extTargetDir)
                }
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
        val dir = getExtensionsDir(context)
        val list = mutableListOf<BrowserExtension>()

        dir.listFiles()?.filter { it.isDirectory }?.forEach { extDir ->
            val manifestFile = File(extDir, "manifest.json")
            if (manifestFile.exists()) {
                try {
                    val json = JSONObject(manifestFile.readText())
                    val ext = BrowserExtension.fromManifest(extDir, json)
                    list.add(ext)

                    if (ext.isEnabled) {
                        workerHosts.putIfAbsent(ext.id, ExtensionWorkerHost(context, ext))
                    } else {
                        workerHosts.remove(ext.id)?.destroy()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing manifest for ${extDir.name}", e)
                }
            }
        }
        _extensions.value = list
    }

    private fun startWorker(context: Context, ext: BrowserExtension): ExtensionWorkerHost {
        val existing = workerHosts[ext.id]
        if (existing != null) return existing
        val host = ExtensionWorkerHost(context, ext)
        workerHosts[ext.id] = host
        return host
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
        val current = _extensions.value.toMutableList()
        val index = current.indexOfFirst { it.id == extId }
        if (index != -1) {
            val updated = current[index].copy(isEnabled = enable)
            current[index] = updated
            _extensions.value = current

            if (enable) {
                startWorker(ctx, updated)
            } else {
                workerHosts.remove(extId)?.destroy()
            }
        }
    }
}
