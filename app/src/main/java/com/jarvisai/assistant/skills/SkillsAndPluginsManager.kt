package com.jarvisai.assistant.skills

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.browser.ExtensionSkills
import com.jarvisai.assistant.data.remote.JarvisHubConfig
import com.jarvisai.assistant.extension.BrowserExtensionManager
import com.jarvisai.assistant.ide.ActiveCodeEditorRegistry
import com.jarvisai.assistant.mcp.CascadeCatalogPlugin
import com.jarvisai.assistant.mcp.McpServerSpec
import com.jarvisai.assistant.mcp.McpServersManager
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
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

data class SkillDefinitionDto(
    val identifier: String,
    val name: String,
    val displayName: String,
    val description: String,
    val content: String,
    val scope: String, // "GLOBAL" or "WORKSPACE"
    val path: String,
    val sourceRepo: String? = null
)

data class InstalledPluginDto(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val pluginUri: String,
    val isEnabled: Boolean = true,
    val isWebExtension: Boolean = false,
    val skills: List<SkillDefinitionDto> = emptyList()
)

data class BuildWithGooglePluginItemDto(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val trustLevel: String = "TRUSTED",
    val version: String = "1.0.0",
    val iconColorHex: Long = 0xFF38BDF8L,
    val bundledSkills: List<Pair<String, String>> = emptyList(), // skillSlug to SKILL.md markdown
    val bundledMcpSpec: McpServerSpec? = null
)

/**
 * Manages real on-device Agent Skills (`SKILL.md`), installed Antigravity/WebView plugins,
 * the Official "Build with Google" Store, and live GitHub repository skill/plugin installation.
 */
object SkillsAndPluginsManager {

    private const val TAG = "SkillsAndPluginsMgr"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _skills = MutableStateFlow<List<SkillDefinitionDto>>(emptyList())
    val skills: StateFlow<List<SkillDefinitionDto>> = _skills.asStateFlow()

    private val _installedPlugins = MutableStateFlow<List<InstalledPluginDto>>(emptyList())
    val installedPlugins: StateFlow<List<InstalledPluginDto>> = _installedPlugins.asStateFlow()

    private val _googlePlugins = MutableStateFlow<List<BuildWithGooglePluginItemDto>>(buildStoreCatalog())
    val googlePlugins: StateFlow<List<BuildWithGooglePluginItemDto>> = _googlePlugins.asStateFlow()

    private val _skillsFilterScope = MutableStateFlow("ALL") // ALL, GLOBAL, WORKSPACE
    val skillsFilterScope: StateFlow<String> = _skillsFilterScope.asStateFlow()

    private val _isSkillsLoading = MutableStateFlow(false)
    val isSkillsLoading: StateFlow<Boolean> = _isSkillsLoading.asStateFlow()

    private val _isPluginsLoading = MutableStateFlow(false)
    val isPluginsLoading: StateFlow<Boolean> = _isPluginsLoading.asStateFlow()

    private val _isStoreLoading = MutableStateFlow(false)
    val isStoreLoading: StateFlow<Boolean> = _isStoreLoading.asStateFlow()

    private val _installingPluginId = MutableStateFlow<String?>(null)
    val installingPluginId: StateFlow<String?> = _installingPluginId.asStateFlow()

    private val _deletingPluginId = MutableStateFlow<String?>(null)
    val deletingPluginId: StateFlow<String?> = _deletingPluginId.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    @Volatile
    private var cachedContext: Context? = null

    fun setSkillsFilterScope(scope: String) {
        _skillsFilterScope.value = scope
    }

    fun clearStatus() {
        _statusMessage.value = null
        _errorMessage.value = null
    }

    fun getGlobalSkillsRoot(context: Context): File {
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        return File(homeDir, ".gemini/${JarvisHubConfig.ISOLATED_APP_DATA_DIR}/skills").apply { mkdirs() }
    }

    fun getPluginsRoot(context: Context): File {
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        return File(homeDir, ".gemini/${JarvisHubConfig.ISOLATED_APP_DATA_DIR}/plugins").apply { mkdirs() }
    }

    private fun ensureStarterSkills(context: Context) {
        val root = getGlobalSkillsRoot(context)
        val marker = File(root, ".seeded")
        if (marker.exists()) return
        val existingDirs = root.listFiles()?.filter { it.isDirectory }.orEmpty()
        if (existingDirs.isEmpty()) {
            val composeSkillDir = File(root, "android-compose-architect").apply { mkdirs() }
            File(composeSkillDir, "SKILL.md").writeText(
                """
                |---
                |name: android-compose-architect
                |display_name: Android Jetpack Compose Architect
                |description: Guidelines for clean Material 3 Compose UIs, StateFlow ViewModels, WindowInsets edge-to-edge compliance, and single-file modularity.
                |---
                |# Android Jetpack Compose Architect
                |1. Use Material 3 color tokens (`MaterialTheme.colorScheme`), `enableEdgeToEdge()`, and proper `statusBarsPadding()` / `navigationBarsPadding()`.
                |2. Keep Composables stateless where possible and hoist state into `ViewModel` `StateFlow`.
                |3. Assign `Modifier.testTag(...)` to primary buttons and controls and maintain 48.dp minimum touch targets.
                """.trimMargin()
            )
            val mcpAuthorSkillDir = File(root, "mcp-workflow-builder").apply { mkdirs() }
            File(mcpAuthorSkillDir, "SKILL.md").writeText(
                """
                |---
                |name: mcp-workflow-builder
                |display_name: MCP Tool & Workflow Builder
                |description: Patterns for composing MCP servers, structured JSON-RPC 2.0 handlers, and safe command permissions in Agentra.
                |---
                |# MCP Workflow Builder
                |- Prefer `tools/list` schema validation before invoking remote MCP actions.
                |- Check `CommandsPermissionManager` policy before running destructive shell commands.
                |- Mirror skill & MCP specs under `~/.gemini/agentra-antigravity/`.
                """.trimMargin()
            )
        }
        runCatching { marker.writeText("1") }
    }

    suspend fun refreshSkills(
        context: Context,
        workspacePath: String? = ActiveCodeEditorRegistry.currentProjectPath
    ): List<SkillDefinitionDto> = withContext(Dispatchers.IO) {
        cachedContext = context.applicationContext
        _isSkillsLoading.value = true
        ensureStarterSkills(context)

        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val discovered = linkedMapOf<String, SkillDefinitionDto>()

        val globalScanRoots = listOf(
            File(homeDir, ".gemini/${JarvisHubConfig.ISOLATED_APP_DATA_DIR}/skills"),
            File(homeDir, ".gemini/skills"),
            File(homeDir, ".agents/skills"),
            File(homeDir, ".claude/skills")
        )
        globalScanRoots.forEach { dir ->
            scanSkillsDirectory(dir, scope = "GLOBAL", out = discovered)
        }

        // Plugin bundled skills
        getPluginsRoot(context).listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
            val pluginSkillsDir = File(pluginDir, "skills")
            if (pluginSkillsDir.exists()) {
                scanSkillsDirectory(pluginSkillsDir, scope = "GLOBAL", out = discovered)
            }
            val topSkill = File(pluginDir, "SKILL.md")
            if (topSkill.exists() && topSkill.isFile) {
                parseSkillFile(topSkill, scope = "GLOBAL", idFallback = pluginDir.name)?.let {
                    discovered[it.identifier] = it
                }
            }
        }

        // Workspace skills
        if (!workspacePath.isNullOrBlank()) {
            val wsRoot = File(workspacePath)
            listOf(
                File(wsRoot, ".gemini/skills"),
                File(wsRoot, ".agent/skills"),
                File(wsRoot, "skills")
            ).forEach { dir ->
                scanSkillsDirectory(dir, scope = "WORKSPACE", out = discovered)
            }
        }

        // WebView Extension skills
        ExtensionSkills.loadEnabledSkills(context).forEach { extSkill ->
            val key = "ext:${extSkill.extensionId}:${extSkill.skillName}"
            if (!discovered.containsKey(key)) {
                discovered[key] = SkillDefinitionDto(
                    identifier = key,
                    name = extSkill.skillName,
                    displayName = extSkill.title.ifBlank { extSkill.skillName },
                    description = extSkill.description.ifBlank { "Bundled skill from ${extSkill.extensionName}" },
                    content = extSkill.markdownContent.ifBlank { extSkill.description },
                    scope = "GLOBAL",
                    path = "extension://${extSkill.extensionId}",
                    sourceRepo = extSkill.extensionName
                )
            }
        }

        val sorted = discovered.values.sortedWith(compareBy<SkillDefinitionDto> { it.scope != "WORKSPACE" }.thenBy { it.displayName.lowercase() })
        _skills.value = sorted
        _isSkillsLoading.value = false
        sorted
    }

    private fun scanSkillsDirectory(
        dir: File,
        scope: String,
        out: MutableMap<String, SkillDefinitionDto>
    ) {
        if (!dir.exists() || !dir.isDirectory) return
        val topSkill = File(dir, "SKILL.md")
        if (topSkill.exists() && topSkill.isFile) {
            parseSkillFile(topSkill, scope, dir.name)?.let { out[it.identifier] = it }
        }
        dir.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
            val f = File(sub, "SKILL.md")
            if (f.exists() && f.isFile) {
                parseSkillFile(f, scope, sub.name)?.let { out[it.identifier] = it }
            }
        }
    }

    fun parseSkillFile(file: File, scope: String, idFallback: String): SkillDefinitionDto? {
        val raw = runCatching { file.readText() }.getOrNull()?.trim() ?: return null
        if (raw.isEmpty()) return null

        var name = idFallback
        var displayName = idFallback.replace('-', ' ').replace('_', ' ')
            .split(' ').filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercaseChar() } }
        var description = ""
        var body = raw

        if (raw.startsWith("---")) {
            val endIdx = raw.indexOf("\n---", startIndex = 3)
            if (endIdx > 0) {
                val frontmatter = raw.substring(3, endIdx).trim()
                body = raw.substring(endIdx + 4).trim()
                frontmatter.lines().forEach { line ->
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val k = line.substring(0, colon).trim().lowercase()
                        val v = line.substring(colon + 1).trim().trim('"', '\'')
                        when (k) {
                            "name" -> if (v.isNotBlank()) name = v
                            "display_name", "title" -> if (v.isNotBlank()) displayName = v
                            "description" -> if (v.isNotBlank()) description = v
                        }
                    }
                }
            }
        }

        if (description.isBlank()) {
            description = body.lines()
                .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                ?.take(180)
                ?: "Agent workflow skill ($name)"
        }
        if (displayName.isBlank()) displayName = name

        val sourceMetaFile = File(file.parentFile, ".agentra-source.json").takeIf { it.exists() }
            ?: File(file.parentFile, ".jarvis-source.json").takeIf { it.exists() }
        val sourceMeta = sourceMetaFile?.let { metaFile ->
            runCatching { JSONObject(metaFile.readText()).optString("repo").takeIf(String::isNotBlank) }.getOrNull()
        }

        val id = "${scope.lowercase()}:${name.lowercase().replace(Regex("[^a-z0-9_.-]"), "-")}"
        return SkillDefinitionDto(
            identifier = id,
            name = name,
            displayName = displayName,
            description = description,
            content = raw,
            scope = scope,
            path = file.absolutePath,
            sourceRepo = sourceMeta
        )
    }

    suspend fun deleteSkill(context: Context, skill: SkillDefinitionDto): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(skill.path)
            if (file.exists()) {
                val parent = file.parentFile
                if (parent != null && parent.name == skill.name || File(parent, "SKILL.md").absolutePath == file.absolutePath) {
                    parent.deleteRecursively()
                } else {
                    file.delete()
                }
            }
            refreshSkills(context)
            _statusMessage.value = "Removed skill '${skill.displayName}'"
        }
    }

    suspend fun refreshInstalledPlugins(context: Context): List<InstalledPluginDto> = withContext(Dispatchers.IO) {
        cachedContext = context.applicationContext
        _isPluginsLoading.value = true
        BrowserExtensionManager.init(context)

        val pluginsList = mutableListOf<InstalledPluginDto>()
        val root = getPluginsRoot(context)

        root.listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
            val manifestFile = File(pluginDir, "plugin.json")
            val json = runCatching { if (manifestFile.exists()) JSONObject(manifestFile.readText()) else JSONObject() }.getOrDefault(JSONObject())
            val id = json.optString("id", pluginDir.name)
            val name = json.optString("name", pluginDir.name)
            val version = json.optString("version", "1.0.0")
            val desc = json.optString("description", "")
            val uri = json.optString("pluginUri", pluginDir.absolutePath)

            val bundledSkills = linkedMapOf<String, SkillDefinitionDto>()
            val skillsDir = File(pluginDir, "skills")
            if (skillsDir.exists()) scanSkillsDirectory(skillsDir, "GLOBAL", bundledSkills)

            pluginsList.add(
                InstalledPluginDto(
                    id = id,
                    name = name,
                    version = version,
                    description = desc,
                    pluginUri = uri,
                    isEnabled = json.optBoolean("enabled", true),
                    isWebExtension = false,
                    skills = bundledSkills.values.toList()
                )
            )
        }

        // Also include installed Browser WebView extensions
        BrowserExtensionManager.extensions.value.forEach { ext ->
            if (pluginsList.none { it.id == ext.id }) {
                val extSkills = ext.skills.map { s ->
                    SkillDefinitionDto(
                        identifier = "ext:${ext.id}:${s.name}",
                        name = s.name,
                        displayName = s.title.ifBlank { s.name },
                        description = s.description,
                        content = s.description,
                        scope = "GLOBAL",
                        path = "${ext.dirPath}/${s.path}"
                    )
                }
                pluginsList.add(
                    InstalledPluginDto(
                        id = ext.id,
                        name = ext.name,
                        version = ext.version,
                        description = ext.description,
                        pluginUri = ext.dirPath.ifBlank { "builtin://${ext.id}" },
                        isEnabled = ext.isEnabled,
                        isWebExtension = true,
                        skills = extSkills
                    )
                )
            }
        }

        val sorted = pluginsList.sortedBy { it.name.lowercase() }
        _installedPlugins.value = sorted
        _isPluginsLoading.value = false
        sorted
    }

    suspend fun refreshGooglePlugins(context: Context): List<BuildWithGooglePluginItemDto> = withContext(Dispatchers.IO) {
        _isStoreLoading.value = true
        val catalog = buildStoreCatalog()
        _googlePlugins.value = catalog
        _isStoreLoading.value = false
        catalog
    }

    suspend fun installGooglePlugin(
        context: Context,
        pluginId: String,
        scope: String = "global"
    ): Result<InstalledPluginDto> = withContext(Dispatchers.IO) {
        _installingPluginId.value = pluginId
        try {
            val item = _googlePlugins.value.firstOrNull { it.id == pluginId }
                ?: return@withContext Result.failure(IllegalArgumentException("Plugin '$pluginId' not found in Store"))

            val pluginRoot = if (scope.equals("project", ignoreCase = true) && !ActiveCodeEditorRegistry.currentProjectPath.isNullOrBlank()) {
                File(ActiveCodeEditorRegistry.currentProjectPath!!, ".gemini/plugins/${item.id}")
            } else {
                File(getPluginsRoot(context), item.id)
            }
            pluginRoot.mkdirs()

            val manifest = JSONObject()
                .put("id", item.id)
                .put("name", item.name)
                .put("version", item.version)
                .put("description", item.description)
                .put("category", item.category)
                .put("pluginUri", "google-store://${item.id}")
                .put("enabled", true)
            File(pluginRoot, "plugin.json").writeText(manifest.toString(2))

            val skillsDir = File(pluginRoot, "skills").apply { mkdirs() }
            item.bundledSkills.forEach { (slug, md) ->
                val sDir = File(skillsDir, slug).apply { mkdirs() }
                File(sDir, "SKILL.md").writeText(md)
            }

            if (item.bundledMcpSpec != null) {
                McpServersManager.upsertServer(context, item.bundledMcpSpec)
            }

            refreshSkills(context)
            val plugins = refreshInstalledPlugins(context)
            val installed = plugins.firstOrNull { it.id == item.id }
                ?: InstalledPluginDto(item.id, item.name, item.version, item.description, pluginRoot.absolutePath)

            _statusMessage.value = "Installed '${item.name}' (${item.bundledSkills.size} skill(s))"
            Result.success(installed)
        } catch (e: Exception) {
            _errorMessage.value = "Install failed: ${e.message}"
            Result.failure(e)
        } finally {
            _installingPluginId.value = null
        }
    }

    suspend fun deletePlugin(context: Context, idOrUri: String, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        _deletingPluginId.value = idOrUri
        try {
            val pluginDir = File(getPluginsRoot(context), idOrUri)
            if (pluginDir.exists()) {
                pluginDir.deleteRecursively()
            } else {
                val extDir = File(context.filesDir, "extensions/$idOrUri")
                if (extDir.exists()) {
                    extDir.deleteRecursively()
                    BrowserExtensionManager.init(context)
                    BrowserExtensionManager.reloadExtensions()
                } else {
                    val uriFile = File(idOrUri)
                    if (uriFile.exists() && uriFile.isDirectory) {
                        uriFile.deleteRecursively()
                    }
                }
            }
            refreshSkills(context)
            refreshInstalledPlugins(context)
            _statusMessage.value = "Uninstalled '$name'"
            Result.success(Unit)
        } catch (e: Exception) {
            _errorMessage.value = "Uninstall failed: ${e.message}"
            Result.failure(e)
        } finally {
            _deletingPluginId.value = null
        }
    }

    /**
     * Installs skills or a plugin directly from a GitHub repository (`owner/repo` or full GitHub URL)
     * using the public GitHub Contents API & raw file downloads.
     */
    suspend fun installFromGitHub(
        context: Context,
        repoInput: String,
        asPlugin: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = parseGitHubRepoInput(repoInput)
                ?: error("Enter a valid GitHub repo like 'owner/repo' or 'https://github.com/owner/repo'")
            val (owner, repo, branchHint, subPath) = parsed

            val apiBase = "https://api.github.com/repos/$owner/$repo/contents"
            val candidatePaths = listOfNotNull(
                subPath.takeIf { it.isNotBlank() },
                "skills",
                ".gemini/skills",
                ""
            ).distinct()

            val downloadedSkills = mutableListOf<Pair<String, String>>()

            for (path in candidatePaths) {
                val url = buildString {
                    append(apiBase)
                    if (path.isNotBlank()) append("/").append(path.trim('/'))
                    if (branchHint.isNotBlank()) append("?ref=").append(branchHint)
                }
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Agentra-Android")
                    .get()
                    .build()

                val body = httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()
                } ?: continue

                val trimmed = body.trim()
                if (trimmed.startsWith("[")) {
                    val items = JSONArray(trimmed)
                    for (i in 0 until items.length()) {
                        val entry = items.optJSONObject(i) ?: continue
                        val entryName = entry.optString("name")
                        val entryType = entry.optString("type")
                        val downloadUrl = entry.optString("download_url")
                        if (entryType == "file" && entryName.equals("SKILL.md", ignoreCase = true) && downloadUrl.isNotBlank()) {
                            fetchRawText(downloadUrl)?.let { content ->
                                val slug = path.substringAfterLast('/').ifBlank { repo }
                                downloadedSkills.add(slug to content)
                            }
                        } else if (entryType == "dir") {
                            val subSkillUrl = "https://raw.githubusercontent.com/$owner/$repo/${branchHint.ifBlank { "HEAD" }}/${entry.optString("path")}/SKILL.md"
                            fetchRawText(subSkillUrl)?.let { content ->
                                downloadedSkills.add(entryName to content)
                            }
                        }
                    }
                } else if (trimmed.startsWith("{")) {
                    val obj = JSONObject(trimmed)
                    val dlUrl = obj.optString("download_url")
                    if (dlUrl.isNotBlank()) {
                        fetchRawText(dlUrl)?.let { content ->
                            downloadedSkills.add(repo to content)
                        }
                    }
                }
                if (downloadedSkills.isNotEmpty()) break
            }

            if (downloadedSkills.isEmpty()) {
                // Direct raw fallback for main/master branches
                for (br in listOfNotNull(branchHint.takeIf { it.isNotBlank() }, "main", "master")) {
                    val rawUrl = if (subPath.isNotBlank()) {
                        "https://raw.githubusercontent.com/$owner/$repo/$br/${subPath.trim('/')}/SKILL.md"
                    } else {
                        "https://raw.githubusercontent.com/$owner/$repo/$br/SKILL.md"
                    }
                    val content = fetchRawText(rawUrl)
                    if (!content.isNullOrBlank()) {
                        downloadedSkills.add(repo to content)
                        break
                    }
                }
            }

            require(downloadedSkills.isNotEmpty()) {
                "No SKILL.md found in $owner/$repo. Verify repo is public and contains SKILL.md or skills/*/SKILL.md."
            }

            val sourceMeta = JSONObject()
                .put("repo", "$owner/$repo")
                .put("ref", branchHint.ifBlank { "HEAD" })
                .put("installed_at", System.currentTimeMillis().toString())
                .toString(2)

            if (asPlugin) {
                val pluginId = "$owner-$repo".lowercase().replace(Regex("[^a-z0-9_-]"), "-")
                val pluginDir = File(getPluginsRoot(context), pluginId).apply { mkdirs() }
                File(pluginDir, "plugin.json").writeText(
                    JSONObject()
                        .put("id", pluginId)
                        .put("name", "$owner/$repo")
                        .put("version", "git")
                        .put("description", "Installed from GitHub ($owner/$repo) with ${downloadedSkills.size} skill(s)")
                        .put("pluginUri", "https://github.com/$owner/$repo")
                        .put("enabled", true)
                        .toString(2)
                )
                val skillsDir = File(pluginDir, "skills").apply { mkdirs() }
                downloadedSkills.forEach { (slug, content) ->
                    val target = File(skillsDir, slug.lowercase().replace(Regex("[^a-z0-9_-]"), "-")).apply { mkdirs() }
                    File(target, "SKILL.md").writeText(content)
                    File(target, ".agentra-source.json").writeText(sourceMeta)
                }
            } else {
                val root = getGlobalSkillsRoot(context)
                downloadedSkills.forEach { (slug, content) ->
                    val target = File(root, slug.lowercase().replace(Regex("[^a-z0-9_-]"), "-")).apply { mkdirs() }
                    File(target, "SKILL.md").writeText(content)
                    File(target, ".agentra-source.json").writeText(sourceMeta)
                }
            }

            refreshSkills(context)
            refreshInstalledPlugins(context)
            val summary = "Installed ${downloadedSkills.size} skill(s) from $owner/$repo"
            _statusMessage.value = summary
            summary
        }.onFailure { err ->
            Log.w(TAG, "GitHub skill install error: ${err.message}")
            _errorMessage.value = err.message ?: "GitHub install failed"
        }
    }

    private fun fetchRawText(url: String): String? {
        return runCatching {
            val req = Request.Builder().url(url).header("User-Agent", "Agentra-Android").get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string()?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }

    data class GitHubRepoTarget(val owner: String, val repo: String, val branch: String, val subPath: String)

    fun parseGitHubRepoInput(raw: String): GitHubRepoTarget? {
        val cleaned = raw.trim().removeSuffix(".git").trim('/')
        if (cleaned.isBlank()) return null
        val treeMatch = Regex("""https?://github\.com/([^/]+)/([^/]+)/tree/([^/]+)(?:/(.*))?""").find(cleaned)
        if (treeMatch != null) {
            return GitHubRepoTarget(
                owner = treeMatch.groupValues[1],
                repo = treeMatch.groupValues[2],
                branch = treeMatch.groupValues[3],
                subPath = treeMatch.groupValues[4]
            )
        }
        val urlMatch = Regex("""https?://github\.com/([^/]+)/([^/]+)(?:/(.*))?""").find(cleaned)
        if (urlMatch != null) {
            return GitHubRepoTarget(
                owner = urlMatch.groupValues[1],
                repo = urlMatch.groupValues[2],
                branch = "",
                subPath = urlMatch.groupValues[3]
            )
        }
        val shortMatch = Regex("""^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$""").find(cleaned)
        if (shortMatch != null) {
            return GitHubRepoTarget(shortMatch.groupValues[1], shortMatch.groupValues[2], "", "")
        }
        return null
    }

    /**
     * Generates a concise prompt injection listing active agent skills & SKILL.md paths for chat turns.
     */
    fun buildActiveSkillsPromptSection(context: Context): String {
        val currentSkills = _skills.value.ifEmpty {
            ensureStarterSkills(context)
            val tmp = linkedMapOf<String, SkillDefinitionDto>()
            scanSkillsDirectory(getGlobalSkillsRoot(context), "GLOBAL", tmp)
            tmp.values.toList()
        }
        if (currentSkills.isEmpty()) return ""
        return buildString {
            appendLine("## Active Agent Skills")
            currentSkills.take(12).forEach { s ->
                appendLine("- **${s.displayName}** (`${s.name}`): ${s.description.take(140)}")
            }
        }.trim()
    }

    private fun buildStoreCatalog(): List<BuildWithGooglePluginItemDto> = listOf(
        BuildWithGooglePluginItemDto(
            id = "android-cli-plugin",
            name = "Android CLI & Jetpack Compose",
            description = "Official Android developer guidance, Material 3 adaptive layouts, Navigation 3, and Play policy patterns.",
            category = "Android",
            trustLevel = "TRUSTED",
            version = "1.4.0",
            iconColorHex = 0xFF34D399L,
            bundledSkills = listOf(
                "android-m3-patterns" to """
                    |---
                    |name: android-m3-patterns
                    |display_name: Android M3 & Adaptive UI
                    |description: Adaptive WindowSizeClass, edge-to-edge WindowInsets, Navigation 3, and Robolectric verification rules.
                    |---
                    |# Android M3 & Adaptive UI
                    |- Use `Scaffold` with proper `WindowInsets` handling and `enableEdgeToEdge()`.
                    |- Keep touch targets >= 48.dp and add explicit `contentDescription` and `testTag`.
                    |- Use Kotlin Coroutines, `StateFlow`, and Robolectric JVM tests for fast verification.
                """.trimMargin(),
                "google-play-compliance" to """
                    |---
                    |name: google-play-compliance
                    |display_name: Google Play Policy Guard
                    |description: Enforces Android Photo Picker, least-privilege permissions, and Play Store safe patterns.
                    |---
                    |# Play Compliance
                    |Avoid broad storage permissions when `ActivityResultContracts.PickVisualMedia` satisfies media picking.
                """.trimMargin()
            )
        ),
        BuildWithGooglePluginItemDto(
            id = "firebase-studio-plugin",
            name = "Firebase Studio & Firestore",
            description = "Firebase AI Logic (`Firebase.ai`), Cloud Firestore zero-trust security rules, and Credential Manager Google Sign-In.",
            category = "Cloud & DB",
            trustLevel = "TRUSTED",
            version = "2.1.0",
            iconColorHex = 0xFFF59E0BL,
            bundledSkills = listOf(
                "firebase-firestore-rules" to """
                    |---
                    |name: firebase-firestore-rules
                    |display_name: Firestore Schema & Security Rules
                    |description: Zero-trust Firestore rules, relational collection patterns, and offline persistence checks.
                    |---
                    |# Firestore Best Practices
                    |1. Validate `request.auth != null` and strict ownership (`resource.data.uid == request.auth.uid`).
                    |2. Never deploy wildcard `allow read, write: if true` rules.
                """.trimMargin()
            ),
            bundledMcpSpec = McpServerSpec(
                serverName = "firebase",
                command = "npx",
                args = listOf("-y", "firebase-tools@latest", "mcp")
            )
        ),
        BuildWithGooglePluginItemDto(
            id = "gemini-api-plugin",
            name = "Gemini Multimodal & Live API",
            description = "Best practices for Gemini 3.8/2.5 structured JSON outputs, function calling, thinking budgets, and Live audio.",
            category = "AI & Gemini",
            trustLevel = "TRUSTED",
            version = "1.2.0",
            iconColorHex = 0xFF38BDF8L,
            bundledSkills = listOf(
                "gemini-structured-tools" to """
                    |---
                    |name: gemini-structured-tools
                    |display_name: Gemini Tool & Schema Authoring
                    |description: Clean tool schema design, function declarations, and thinking budget tuning.
                    |---
                    |# Gemini Function Calling
                    |- Declare crisp parameter schemas with clear `required` arrays and `description` hints.
                """.trimMargin()
            )
        ),
        BuildWithGooglePluginItemDto(
            id = "google-stitch-plugin",
            name = "Google Stitch UI Design",
            description = "Connect Google Stitch MCP to synthesize cohesive mobile & web UI systems and interactive design tokens.",
            category = "Design",
            trustLevel = "TRUSTED",
            version = "1.0.2",
            iconColorHex = 0xFFEC4899L,
            bundledSkills = listOf(
                "stitch-design-tokens" to """
                    |---
                    |name: stitch-design-tokens
                    |display_name: Stitch UI Synthesis
                    |description: Prompt engineering for cohesive typography, spacing grids, and accessible dark/light token palettes.
                    |---
                    |# Stitch Design System
                    |Maintain an 8dp spatial grid, crisp contrast ratios (>=4.5:1), and cohesive accent pairings.
                """.trimMargin()
            ),
            bundledMcpSpec = McpServerSpec(
                serverName = "stitch",
                serverUrl = "https://stitch.googleapis.com/mcp",
                headers = mapOf("X-Goog-Api-Key" to "")
            )
        ),
        BuildWithGooglePluginItemDto(
            id = "modern-web-plugin",
            name = "Modern Web & TypeScript",
            description = "React 19, Vite, Tailwind CSS design patterns, Playwright MCP verification, and Core Web Vitals checks.",
            category = "Web",
            trustLevel = "TRUSTED",
            version = "1.3.0",
            iconColorHex = 0xFF6366F1L,
            bundledSkills = listOf(
                "modern-web-architecture" to """
                    |---
                    |name: modern-web-architecture
                    |display_name: Modern Web & Vite Guidance
                    |description: Clean component composition, responsive CSS grids, and automated verification flows.
                    |---
                    |# Modern Web Architecture
                    |Prefer semantic HTML, CSS variables, accessible focus states, and clean state derivation.
                """.trimMargin()
            )
        ),
        BuildWithGooglePluginItemDto(
            id = "flutter-dart-plugin",
            name = "Flutter & Dart Builder",
            description = "Riverpod state management, Material 3 widget composition, and multi-platform Flutter build workflows.",
            category = "Cross-Platform",
            trustLevel = "TRUSTED",
            version = "1.1.0",
            iconColorHex = 0xFF0EA5E9L,
            bundledSkills = listOf(
                "flutter-clean-ui" to """
                    |---
                    |name: flutter-clean-ui
                    |display_name: Flutter Widget Craft
                    |description: Sliver layouts, Riverpod providers, and smooth implicit animations in Flutter.
                    |---
                    |# Flutter Craft
                    |Keep build methods shallow and extract reusable widgets cleanly.
                """.trimMargin()
            )
        )
    )
}
