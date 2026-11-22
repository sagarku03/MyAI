package com.jarvisai.assistant.data.miniapps

import android.content.Context
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class MiniAppInfo(
    val id: String,
    val slug: String,
    val name: String,
    val version: String,
    val description: String,
    val entry: String,
    val iconEmoji: String,
    val permissions: List<String>,
    val rootDir: File,
    val warnings: List<String> = emptyList()
)

data class MiniAppValidation(
    val valid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

/**
 * Manages Agentra Mini-Apps stored under `$HOME/mini-apps/<slug>/` with isolated per-app runtime
 * data inside `$HOME/.agentra/mini-apps/<id>/` (`kv.json` and `files/`).
 */
object MiniAppsManager {

    private val _apps = MutableStateFlow<List<MiniAppInfo>>(emptyList())
    val apps: StateFlow<List<MiniAppInfo>> = _apps.asStateFlow()

    fun getMiniAppsRoot(context: Context): File {
        val home = LocalEnvironmentManager.getHomeDir(context)
        return File(home, "mini-apps").apply { mkdirs() }
    }

    fun getAppDataDir(context: Context, appId: String): File {
        val safeId = appId.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "default" }
        val home = LocalEnvironmentManager.getHomeDir(context)
        return File(File(File(home, ".agentra"), "mini-apps"), safeId).apply { mkdirs() }
    }

    fun refresh(context: Context): List<MiniAppInfo> {
        val root = getMiniAppsRoot(context)
        val dirs = root.listFiles { f -> f.isDirectory }?.sortedBy { it.name.lowercase() }.orEmpty()
        val loaded = dirs.mapNotNull { dir -> parseMiniAppDir(dir) }
        _apps.value = loaded
        return loaded
    }

    fun getById(context: Context, idOrSlug: String): MiniAppInfo? {
        val current = _apps.value.ifEmpty { refresh(context) }
        return current.firstOrNull { it.id == idOrSlug || it.slug == idOrSlug }
    }

    fun parseMiniAppDir(dir: File): MiniAppInfo? {
        val manifestFile = File(dir, "manifest.json")
        if (!manifestFile.isFile) return null
        val obj = runCatching { JSONObject(manifestFile.readText()) }.getOrNull() ?: return null
        val warnings = mutableListOf<String>()
        val slug = dir.name
        val id = obj.optString("id").trim().ifBlank { slug }
        val name = obj.optString("name").trim().ifBlank { slug }
        val version = obj.optString("version").trim().ifBlank { "1.0.0" }
        val description = obj.optString("description").trim()
        val entry = obj.optString("entry").trim().ifBlank { "index.html" }
        val iconEmoji = obj.optString("icon").trim().ifBlank { "⚡" }

        val perms = mutableListOf<String>()
        val rawPerms = obj.optJSONArray("permissions")
        if (rawPerms != null) {
            for (i in 0 until rawPerms.length()) {
                val p = rawPerms.optString(i).trim()
                if (p.isNotBlank()) perms.add(p)
            }
        }
        val entryFile = File(dir, entry)
        if (!entryFile.isFile) {
            warnings.add("Entry file '$entry' is missing")
        }

        return MiniAppInfo(
            id = id,
            slug = slug,
            name = name,
            version = version,
            description = description,
            entry = entry,
            iconEmoji = iconEmoji,
            permissions = perms,
            rootDir = dir,
            warnings = warnings
        )
    }

    fun validateManifest(rawJson: String): MiniAppValidation {
        val obj = runCatching { JSONObject(rawJson) }.getOrNull()
            ?: return MiniAppValidation(false, listOf("Invalid JSON syntax"), emptyList())
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (obj.optString("name").isBlank()) errors.add("Missing required field: 'name'")
        if (obj.optString("id").isBlank()) warnings.add("No 'id' specified; folder name will be used")
        if (obj.optString("version").isBlank()) warnings.add("No 'version' specified; defaulting to 1.0.0")
        val entry = obj.optString("entry", "index.html")
        if (entry.contains("..")) errors.add("Entry path cannot traverse directories")
        return MiniAppValidation(errors.isEmpty(), errors, warnings)
    }

    fun createStarterApp(
        context: Context,
        rawName: String,
        description: String = "Built with Agentra Mini-Apps SDK"
    ): Result<MiniAppInfo> {
        val cleanName = rawName.trim().ifBlank { "My Mini App" }
        val slug = cleanName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "mini-app-${System.currentTimeMillis() % 10000}" }

        val dir = File(getMiniAppsRoot(context), slug)
        if (dir.exists()) {
            return Result.failure(IllegalStateException("Mini-app '$slug' already exists"))
        }
        dir.mkdirs()

        val manifest = JSONObject().apply {
            put("id", "ai.agentra.mini.$slug")
            put("name", cleanName)
            put("version", "1.0.0")
            put("description", description)
            put("entry", "index.html")
            put("icon", "⚡")
            put("permissions", JSONArray(listOf("kv", "fs")))
        }
        File(dir, "manifest.json").writeText(manifest.toString(2))

        val html = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width,initial-scale=1" />
              <title>$cleanName</title>
              <link rel="stylesheet" href="/_agentra/ui.css" />
              <style>
                body { font-family: system-ui, sans-serif; margin: 0; padding: 20px; background: #0a0f1a; color: #e2e8f0; }
                .card { background: #111827; border: 1px solid #1f2937; border-radius: 16px; padding: 18px; max-width: 560px; margin: 0 auto; }
                button { background: #38bdf8; color: #04101d; border: 0; border-radius: 10px; padding: 10px 16px; font-weight: 700; cursor: pointer; }
                .count { font-size: 32px; font-weight: 800; color: #38bdf8; margin: 12px 0; }
              </style>
            </head>
            <body>
              <div class="card">
                <h2>⚡ $cleanName</h2>
                <p style="color:#94a3b8;font-size:13px;">$description</p>
                <div class="count" id="val">0</div>
                <button id="incBtn">+ Increment &amp; Persist (KV)</button>
              </div>
              <script src="/_agentra/sdk.js"></script>
              <script>
                const el = document.getElementById('val');
                let n = parseInt((window.AgentraBridge && window.AgentraBridge.kvGet('counter')) || '0', 10) || 0;
                el.textContent = String(n);
                document.getElementById('incBtn').onclick = () => {
                  n += 1;
                  el.textContent = String(n);
                  if (window.AgentraBridge) {
                    window.AgentraBridge.kvSet('counter', String(n));
                    window.AgentraBridge.toast('Saved counter = ' + n);
                  }
                };
              </script>
            </body>
            </html>
        """.trimIndent()
        File(dir, "index.html").writeText(html)

        val info = parseMiniAppDir(dir)
            ?: return Result.failure(IllegalStateException("Failed to initialize manifest.json"))
        refresh(context)
        return Result.success(info)
    }

    fun resolveSafeAppFile(app: MiniAppInfo, relativePath: String): File? {
        val clean = relativePath.trimStart('/')
        val target = File(app.rootDir, clean).canonicalFile
        val rootCanonical = app.rootDir.canonicalFile
        return if (target.path == rootCanonical.path || target.path.startsWith(rootCanonical.path + File.separator)) {
            target
        } else {
            null
        }
    }

    fun readAppFile(context: Context, slugOrId: String, relativePath: String): Result<String> {
        val app = getById(context, slugOrId) ?: return Result.failure(IllegalArgumentException("Mini-app '$slugOrId' not found"))
        val file = resolveSafeAppFile(app, relativePath) ?: return Result.failure(SecurityException("Path outside mini-app root"))
        if (!file.isFile) return Result.failure(IllegalArgumentException("File not found: $relativePath"))
        return runCatching { file.readText() }
    }

    fun writeAppFile(context: Context, slugOrId: String, relativePath: String, content: String): Result<File> {
        val app = getById(context, slugOrId) ?: return Result.failure(IllegalArgumentException("Mini-app '$slugOrId' not found"))
        val file = resolveSafeAppFile(app, relativePath) ?: return Result.failure(SecurityException("Path outside mini-app root"))
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeText(content)
            refresh(context)
            file
        }
    }

    fun clearAppData(context: Context, app: MiniAppInfo) {
        runCatching { getAppDataDir(context, app.id).deleteRecursively() }
    }

    fun deleteApp(context: Context, app: MiniAppInfo): Boolean {
        clearAppData(context, app)
        val ok = runCatching { app.rootDir.deleteRecursively() }.getOrDefault(false)
        refresh(context)
        return ok
    }

    // ---- Per-app KV & sandboxed file storage ----

    @Synchronized
    fun kvGet(context: Context, appId: String, key: String): String {
        val file = File(getAppDataDir(context, appId), "kv.json")
        if (!file.isFile) return ""
        val obj = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return ""
        return obj.optString(key, "")
    }

    @Synchronized
    fun kvSet(context: Context, appId: String, key: String, value: String) {
        val file = File(getAppDataDir(context, appId), "kv.json")
        val obj = if (file.isFile) runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() } else JSONObject()
        obj.put(key, value)
        file.parentFile?.mkdirs()
        file.writeText(obj.toString(2))
    }

    @Synchronized
    fun kvRemove(context: Context, appId: String, key: String) {
        val file = File(getAppDataDir(context, appId), "kv.json")
        if (!file.isFile) return
        val obj = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        obj.remove(key)
        file.writeText(obj.toString(2))
    }

    @Synchronized
    fun kvKeysJson(context: Context, appId: String): String {
        val file = File(getAppDataDir(context, appId), "kv.json")
        if (!file.isFile) return "[]"
        val obj = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return "[]"
        val arr = JSONArray()
        obj.keys().forEach { arr.put(it) }
        return arr.toString()
    }
}
