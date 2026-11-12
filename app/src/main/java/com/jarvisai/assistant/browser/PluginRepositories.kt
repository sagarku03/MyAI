package com.jarvisai.assistant.browser

import android.content.Context
import com.jarvisai.assistant.extension.BrowserExtension
import com.jarvisai.assistant.extension.BrowserExtensionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages remote and local plugin/extension repositories, validates extension `manifest.json`
 * schemas, and installs or updates web automation extensions into `BrowserExtensionManager`.
 */
object PluginRepositories {

    data class PluginCatalogEntry(
        val id: String,
        val name: String,
        val version: String,
        val description: String,
        val author: String,
        val repositoryUrl: String,
        val manifestJson: JSONObject,
        val serviceWorkerScript: String,
        val skillMarkdown: String = ""
    )

    data class ManifestValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList(),
        val parsedId: String = "",
        val parsedName: String = "",
        val parsedVersion: String = "1.0.0"
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val _repositories = MutableStateFlow(
        listOf(
            "https://raw.githubusercontent.com/sagarku03/MyAI/main/extensions/catalog.json"
        )
    )
    val repositories: StateFlow<List<String>> = _repositories.asStateFlow()

    private val _catalog = MutableStateFlow<List<PluginCatalogEntry>>(builtInCatalog())
    val catalog: StateFlow<List<PluginCatalogEntry>> = _catalog.asStateFlow()

    /**
     * Validates an extension `manifest.json` object before installation.
     */
    fun validateManifest(manifest: JSONObject): ManifestValidationResult {
        val errors = mutableListOf<String>()
        val name = manifest.optString("name", "").trim()
        val version = manifest.optString("version", "").trim()
        val rawId = manifest.optString("id", name.lowercase().replace(Regex("[^a-z0-9_-]"), "-")).trim()

        if (name.isEmpty()) {
            errors.add("Missing required field: 'name'")
        }
        if (version.isEmpty()) {
            errors.add("Missing required field: 'version'")
        }
        if (rawId.isEmpty()) {
            errors.add("Unable to derive valid extension 'id'")
        }

        val mcpTools = manifest.optJSONArray("mcp_tools")
        if (mcpTools != null) {
            for (i in 0 until mcpTools.length()) {
                val toolObj = mcpTools.optJSONObject(i)
                if (toolObj == null || toolObj.optString("name").isBlank()) {
                    errors.add("Invalid mcp_tools entry at index $i: missing 'name'")
                }
            }
        }

        return ManifestValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            parsedId = rawId,
            parsedName = name,
            parsedVersion = version.ifBlank { "1.0.0" }
        )
    }

    /**
     * Adds a custom plugin repository catalog URL and refreshes the catalog.
     */
    suspend fun addRepository(url: String): Result<List<PluginCatalogEntry>> {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("Repository URL must start with http:// or https://"))
        }
        if (!_repositories.value.contains(trimmed)) {
            _repositories.value = _repositories.value + trimmed
        }
        return refreshCatalog()
    }

    /**
     * Fetches remote plugin catalog entries from configured repositories and merges them
     * with the built-in curated catalog.
     */
    suspend fun refreshCatalog(): Result<List<PluginCatalogEntry>> = withContext(Dispatchers.IO) {
        val merged = LinkedHashMap<String, PluginCatalogEntry>()
        for (item in builtInCatalog()) {
            merged[item.id] = item
        }

        for (repoUrl in _repositories.value) {
            runCatching {
                val req = Request.Builder().url(repoUrl).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val manifest = obj.optJSONObject("manifest") ?: obj
                            val validation = validateManifest(manifest)
                            if (validation.isValid) {
                                merged[validation.parsedId] = PluginCatalogEntry(
                                    id = validation.parsedId,
                                    name = validation.parsedName,
                                    version = validation.parsedVersion,
                                    description = manifest.optString("description", ""),
                                    author = obj.optString("author", "Antigravity Community"),
                                    repositoryUrl = repoUrl,
                                    manifestJson = manifest,
                                    serviceWorkerScript = obj.optString(
                                        "service_worker_js",
                                        "function onToolCall(name, args) { return { status: 'ok', tool: name, args: args }; }"
                                    ),
                                    skillMarkdown = obj.optString("skill_md", "")
                                )
                            }
                        }
                    }
                }
            }
        }

        val list = merged.values.toList()
        _catalog.value = list
        Result.success(list)
    }

    /**
     * Installs a [PluginCatalogEntry] into the local extensions directory and reloads [BrowserExtensionManager].
     */
    fun installCatalogPlugin(context: Context, entry: PluginCatalogEntry): Result<BrowserExtension> {
        val validation = validateManifest(entry.manifestJson)
        if (!validation.isValid) {
            return Result.failure(IllegalArgumentException(validation.errors.joinToString("; ")))
        }

        return runCatching {
            val extRoot = File(context.filesDir, "extensions/${entry.id}").apply { mkdirs() }
            File(extRoot, "manifest.json").writeText(entry.manifestJson.toString(2))
            File(extRoot, "service-worker.js").writeText(entry.serviceWorkerScript)
            if (entry.skillMarkdown.isNotBlank()) {
                File(extRoot, "SKILL.md").writeText(entry.skillMarkdown)
            }
            val manager = BrowserExtensionManager.getInstance(context)
            manager.reloadExtensions()
            BrowserExtension.fromManifest(extRoot, entry.manifestJson)
        }
    }

    private fun builtInCatalog(): List<PluginCatalogEntry> {
        val githubManifest = JSONObject().apply {
            put("id", "github-pr-inspector")
            put("name", "GitHub PR & Diff Inspector")
            put("version", "1.2.0")
            put("description", "Extracts structured pull request diffs, review comments, and CI check statuses from GitHub pages.")
            put("permissions", JSONArray(listOf("activeTab", "scripting")))
            put("background", JSONObject().put("service_worker", "service-worker.js"))
            put(
                "mcp_tools",
                JSONArray().put(
                    JSONObject()
                        .put("name", "ext_github_pr_summary")
                        .put("description", "Summarizes open PR metadata and changed files on the active GitHub tab")
                )
            )
            put(
                "skills",
                JSONArray().put(
                    JSONObject()
                        .put("name", "github-review")
                        .put("title", "GitHub Pull Request Review")
                        .put("description", "Inspect PR files, inline review threads, and CI status checks.")
                        .put("path", "SKILL.md")
                )
            )
        }

        val scraperManifest = JSONObject().apply {
            put("id", "structured-table-extractor")
            put("name", "Structured Table & JSON Extractor")
            put("version", "1.1.0")
            put("description", "Converts HTML tables, pricing grids, and specification lists into clean JSON arrays.")
            put("permissions", JSONArray(listOf("activeTab")))
            put("background", JSONObject().put("service_worker", "service-worker.js"))
            put(
                "mcp_tools",
                JSONArray().put(
                    JSONObject()
                        .put("name", "ext_extract_tables")
                        .put("description", "Extracts all visible HTML tables from the current page as JSON rows")
                )
            )
        }

        return listOf(
            PluginCatalogEntry(
                id = "github-pr-inspector",
                name = "GitHub PR & Diff Inspector",
                version = "1.2.0",
                description = "Extracts structured pull request diffs, review comments, and CI check statuses from GitHub pages.",
                author = "Jarvis Autonomous Web Team",
                repositoryUrl = "builtin://extensions/github-pr-inspector",
                manifestJson = githubManifest,
                serviceWorkerScript = """
                    function onToolCall(name, args) {
                        return {
                            status: "success",
                            extension: "github-pr-inspector",
                            tool: name,
                            timestamp: Date.now()
                        };
                    }
                """.trimIndent(),
                skillMarkdown = "# GitHub PR Review Skill\nUse `browser_read_page` and `ext_github_pr_summary` to inspect PR diffs."
            ),
            PluginCatalogEntry(
                id = "structured-table-extractor",
                name = "Structured Table & JSON Extractor",
                version = "1.1.0",
                description = "Converts HTML tables, pricing grids, and specification lists into clean JSON arrays.",
                author = "Jarvis Autonomous Web Team",
                repositoryUrl = "builtin://extensions/structured-table-extractor",
                manifestJson = scraperManifest,
                serviceWorkerScript = """
                    function onToolCall(name, args) {
                        return {
                            status: "success",
                            extension: "structured-table-extractor",
                            tool: name,
                            timestamp: Date.now()
                        };
                    }
                """.trimIndent(),
                skillMarkdown = "# Table Extractor Skill\nExtracts semantic rows and columns from HTML `<table>` elements."
            )
        )
    }
}
