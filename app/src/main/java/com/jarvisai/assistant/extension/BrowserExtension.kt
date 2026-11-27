package com.jarvisai.assistant.extension

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class ExtensionSkill(
    val name: String,
    val title: String,
    val description: String,
    val path: String
)

data class ContentScriptSpec(
    val matches: List<String> = emptyList(),
    val excludeMatches: List<String> = emptyList(),
    val js: List<String> = emptyList(),
    val css: List<String> = emptyList(),
    val runAt: String = "document_idle",
    val allFrames: Boolean = false,
    val includeGlobs: List<String> = emptyList(),
    val excludeGlobs: List<String> = emptyList()
)

data class ExtensionActionUiState(
    val title: String = "",
    val badgeText: String = "",
    val badgeColorHex: Long = 0xFFF25F70L
)

data class ExtensionContextMenuItem(
    val extensionId: String,
    val itemId: String,
    val title: String,
    val contexts: List<String> = listOf("page"),
    val documentUrlPatterns: List<String> = emptyList()
)

data class BrowserExtension(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val toolCount: Int = 0,
    val permissions: List<String> = emptyList(),
    var isEnabled: Boolean = true,
    val dirPath: String = "",
    val serviceWorkerFile: String = "service-worker.js",
    val tools: List<String> = emptyList(),
    val skills: List<ExtensionSkill> = emptyList(),
    val manifestVersion: Int = 3,
    val iconPath: String = "",
    val popupFile: String = "",
    val sidePanelPath: String = "",
    val optionsPage: String = "",
    val backgroundScripts: List<String> = emptyList(),
    val backgroundIsModule: Boolean = false,
    val contentScripts: List<ContentScriptSpec> = emptyList(),
    val hostPermissions: List<String> = emptyList(),
    val webAccessibleResources: List<String> = emptyList(),
    val allowInPrivate: Boolean = false,
    val sourceType: String = "bundled",
    val homepageUrl: String = "",
    val updateUrl: String = "",
    val installedAt: Long = 0L,
    val backgroundPage: String = "",
    val newTabOverridePage: String = "",
    val declarativeRuleResources: List<String> = emptyList(),
    val actionTitle: String = "",
    val pinned: Boolean = false,
    val allowUserScripts: Boolean = false,
    val defaultLocale: String = "en"
) {
    val hasCustomUiPage: Boolean
        get() = popupFile.isNotBlank() || sidePanelPath.isNotBlank() || optionsPage.isNotBlank() || newTabOverridePage.isNotBlank()

    val uiEntryPage: String
        get() = popupFile.ifBlank { sidePanelPath }.ifBlank { optionsPage }.ifBlank { newTabOverridePage }.ifBlank { "__jarvis_ext_dashboard.html" }

    fun safeHostId(): String = id.lowercase().replace(Regex("[^a-z0-9-]"), "-").trim('-').ifBlank { "ext" }

    fun extensionOrigin(): String = "https://ext-${safeHostId()}.jarvis.invalid"

    companion object {
        const val META_FILE_NAME = ".jarvis_ext_meta.json"
        private val MSG_TOKEN_REGEX = Regex("__MSG_([a-zA-Z0-9_@]+)__")

        fun fromManifest(dir: File, manifestJson: JSONObject): BrowserExtension {
            val metaFile = File(dir, META_FILE_NAME)
            val metaJson = if (metaFile.exists()) {
                runCatching { JSONObject(metaFile.readText()) }.getOrNull()
            } else null

            val manifestVersion = manifestJson.optInt("manifest_version", 3)
            val defaultLocale = manifestJson.optString("default_locale", "en").ifBlank { "en" }

            val background = manifestJson.optJSONObject("background")
            val worker = background?.optString("service_worker", "")?.trim().orEmpty()
            val bgPage = background?.optString("page", "")?.trim()?.removePrefix("/").orEmpty()
            val bgIsModule = background?.optString("type", "")?.equals("module", ignoreCase = true) == true
            val bgScripts = mutableListOf<String>()
            background?.optJSONArray("scripts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i).trim().removePrefix("/")
                    if (s.isNotBlank()) bgScripts.add(s)
                }
            }
            val resolvedWorker = when {
                worker.isNotBlank() -> worker.removePrefix("/")
                bgScripts.isNotEmpty() -> bgScripts.first()
                bgPage.isNotBlank() -> bgPage
                File(dir, "service-worker.js").exists() -> "service-worker.js"
                File(dir, "background.js").exists() -> "background.js"
                else -> ""
            }

            // Action / Popup / SidePanel / Options / Overrides
            val actionObj = manifestJson.optJSONObject("action")
                ?: manifestJson.optJSONObject("browser_action")
                ?: manifestJson.optJSONObject("page_action")
            var popupFile = actionObj?.optString("default_popup", "")?.trim()?.removePrefix("/").orEmpty()

            val sidePanelObj = manifestJson.optJSONObject("side_panel")
                ?: manifestJson.optJSONObject("sidebar_action")
            var sidePanelPath = (
                sidePanelObj?.optString("default_path", "").takeIf { !it.isNullOrBlank() }
                    ?: sidePanelObj?.optString("default_panel", "")
                )?.trim()?.removePrefix("/").orEmpty()

            val optionsUi = manifestJson.optJSONObject("options_ui")
            val urlOverrides = manifestJson.optJSONObject("chrome_url_overrides")
            var newTabOverride = urlOverrides?.optString("newtab")?.trim()?.removePrefix("/").orEmpty()
            if (newTabOverride.isNotBlank() && !File(dir, newTabOverride.substringBefore("?").substringBefore("#")).exists()) {
                newTabOverride = ""
            }

            var optionsPage = (
                optionsUi?.optString("page")?.takeIf { it.isNotBlank() }
                    ?: manifestJson.optString("options_page").takeIf { it.isNotBlank() }
                    ?: ""
                ).trim().removePrefix("/")

            // Verify declared HTML files exist, and auto-discover HTML entry page if manifest uses dynamic sidePanel.setOptions / tabs.create
            if (popupFile.isNotBlank() && !File(dir, popupFile.substringBefore("?").substringBefore("#")).exists()) {
                popupFile = ""
            }
            if (sidePanelPath.isNotBlank() && !File(dir, sidePanelPath.substringBefore("?").substringBefore("#")).exists()) {
                sidePanelPath = ""
            }
            if (optionsPage.isNotBlank() && !File(dir, optionsPage.substringBefore("?").substringBefore("#")).exists()) {
                optionsPage = ""
            }

            if (popupFile.isBlank() && sidePanelPath.isBlank() && optionsPage.isBlank() && newTabOverride.isBlank()) {
                val discovered = discoverHtmlEntryPage(dir, resolvedWorker, bgScripts, bgPage)
                if (discovered.isNotBlank()) {
                    if (discovered.contains("sidepanel", ignoreCase = true) || discovered.contains("panel", ignoreCase = true)) {
                        sidePanelPath = discovered
                    } else {
                        popupFile = discovered
                    }
                }
            }

            // Icons (pick highest resolution existing icon, or scan directory for icon png/svg/webp)
            val iconsObj = manifestJson.optJSONObject("icons")
                ?: actionObj?.optJSONObject("default_icon")
            val resolvedIconPath = resolveBestIcon(dir, iconsObj)

            val mcpTools = manifestJson.optJSONArray("mcp_tools") ?: JSONArray()
            val toolNames = mutableListOf<String>()
            for (i in 0 until mcpTools.length()) {
                val t = mcpTools.optJSONObject(i) ?: continue
                val tName = t.optString("name").trim()
                if (tName.isNotEmpty()) toolNames.add(tName)
            }

            val permList = jsonArrayToStringList(manifestJson.optJSONArray("permissions"))
            val hostPermList = (
                jsonArrayToStringList(manifestJson.optJSONArray("host_permissions")) +
                    jsonArrayToStringList(manifestJson.optJSONArray("optional_host_permissions")) +
                    permList.filter { it.contains("://") || it == "<all_urls>" }
                ).distinct()

            // Declarative Net Request static rulesets
            val dnrResources = mutableListOf<String>()
            val dnrArr = manifestJson.optJSONObject("declarative_net_request")?.optJSONArray("rule_resources")
            if (dnrArr != null) {
                for (i in 0 until dnrArr.length()) {
                    val item = dnrArr.optJSONObject(i) ?: continue
                    val path = item.optString("path").trim().removePrefix("/")
                    if (path.isNotBlank() && item.optBoolean("enabled", true)) {
                        dnrResources.add(path)
                    }
                }
            }

            // Web accessible resources (MV2 list of strings OR MV3 list of objects)
            val warArr = manifestJson.optJSONArray("web_accessible_resources") ?: JSONArray()
            val warList = mutableListOf<String>()
            for (i in 0 until warArr.length()) {
                val item = warArr.opt(i)
                when (item) {
                    is String -> if (item.isNotBlank()) warList.add(item)
                    is JSONObject -> warList.addAll(jsonArrayToStringList(item.optJSONArray("resources")))
                }
            }

            // Content scripts
            val csArr = manifestJson.optJSONArray("content_scripts") ?: JSONArray()
            val csList = mutableListOf<ContentScriptSpec>()
            for (i in 0 until csArr.length()) {
                val csObj = csArr.optJSONObject(i) ?: continue
                csList.add(
                    ContentScriptSpec(
                        matches = jsonArrayToStringList(csObj.optJSONArray("matches")),
                        excludeMatches = jsonArrayToStringList(csObj.optJSONArray("exclude_matches")),
                        js = jsonArrayToStringList(csObj.optJSONArray("js")),
                        css = jsonArrayToStringList(csObj.optJSONArray("css")),
                        runAt = csObj.optString("run_at", "document_idle").ifBlank { "document_idle" },
                        allFrames = csObj.optBoolean("all_frames", false),
                        includeGlobs = jsonArrayToStringList(csObj.optJSONArray("include_globs")),
                        excludeGlobs = jsonArrayToStringList(csObj.optJSONArray("exclude_globs"))
                    )
                )
            }

            val skillsJson = manifestJson.optJSONArray("skills") ?: JSONArray()
            val skillList = mutableListOf<ExtensionSkill>()
            for (i in 0 until skillsJson.length()) {
                val s = skillsJson.optJSONObject(i) ?: continue
                skillList.add(
                    ExtensionSkill(
                        name = s.optString("name", ""),
                        title = s.optString("title", s.optString("name", "")),
                        description = s.optString("description", ""),
                        path = s.optString("path", "")
                    )
                )
            }

            val rawId = metaJson?.optString("id")?.takeIf { it.isNotBlank() }
                ?: manifestJson.optString("id").takeIf { it.isNotBlank() }
                ?: CrxExtensionInstaller.extractExtensionIdFromManifestKey(manifestJson.optString("key", ""))
                ?: dir.name
            val derivedId = rawId.lowercase().replace(Regex("[^a-z0-9-]"), "-").trim('-').ifBlank { dir.name }

            val localeMap = loadLocaleMessages(dir, defaultLocale)
            val rawName = manifestJson.optString("name", dir.name)
            val cleanName = resolveAllMsgTokens(rawName, localeMap, derivedId).ifBlank { dir.name }
            val rawDesc = manifestJson.optString("description", "")
            val cleanDesc = resolveAllMsgTokens(rawDesc, localeMap, derivedId)
            val rawActionTitle = actionObj?.optString("default_title", "")?.trim().orEmpty()
            val cleanActionTitle = resolveAllMsgTokens(rawActionTitle, localeMap, derivedId).ifBlank { cleanName }

            return BrowserExtension(
                id = derivedId,
                name = cleanName,
                version = manifestJson.optString("version", "1.0.0"),
                description = cleanDesc,
                toolCount = toolNames.size,
                permissions = permList,
                isEnabled = metaJson?.optBoolean("enabled", true) ?: true,
                dirPath = dir.absolutePath,
                serviceWorkerFile = resolvedWorker,
                tools = toolNames,
                skills = skillList,
                manifestVersion = manifestVersion,
                iconPath = resolvedIconPath,
                popupFile = popupFile,
                sidePanelPath = sidePanelPath,
                optionsPage = optionsPage,
                backgroundScripts = bgScripts,
                backgroundIsModule = bgIsModule,
                contentScripts = csList,
                hostPermissions = hostPermList,
                webAccessibleResources = warList,
                allowInPrivate = metaJson?.optBoolean("allowInPrivate", false) ?: false,
                sourceType = metaJson?.optString("sourceType")?.takeIf { it.isNotBlank() } ?: "bundled",
                homepageUrl = manifestJson.optString("homepage_url", ""),
                updateUrl = metaJson?.optString("updateUrl").orEmpty()
                    .ifBlank { manifestJson.optString("update_url", "") },
                installedAt = metaJson?.optLong("installedAt", dir.lastModified()) ?: dir.lastModified(),
                backgroundPage = bgPage,
                newTabOverridePage = newTabOverride,
                declarativeRuleResources = dnrResources,
                actionTitle = cleanActionTitle,
                pinned = metaJson?.optBoolean("pinned", false) ?: false,
                allowUserScripts = metaJson?.optBoolean("allowUserScripts", false) ?: false,
                defaultLocale = defaultLocale
            )
        }

        fun loadLocaleMessages(dir: File, defaultLocale: String = "en"): JSONObject {
            val deviceLocale = Locale.getDefault()
            val fullCode = listOfNotNull(
                deviceLocale.language.takeIf { it.isNotBlank() },
                deviceLocale.country.takeIf { it.isNotBlank() }
            ).joinToString("_")
            val candidates = listOf(
                fullCode.takeIf { it.isNotBlank() }?.let { File(dir, "_locales/$it/messages.json") },
                deviceLocale.language.takeIf { it.isNotBlank() }?.let { File(dir, "_locales/$it/messages.json") },
                File(dir, "_locales/${defaultLocale.ifBlank { "en" }}/messages.json"),
                File(dir, "_locales/en/messages.json"),
                File(dir, "_locales/en_US/messages.json")
            ).filterNotNull().distinctBy { it.absolutePath }

            val merged = JSONObject()
            for (file in candidates.reversed()) {
                if (!file.exists() || !file.isFile) continue
                runCatching {
                    val obj = JSONObject(file.readText())
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val entry = obj.optJSONObject(k) ?: continue
                        merged.put(k.lowercase(Locale.ROOT), entry)
                    }
                }
            }
            return merged
        }

        fun resolveAllMsgTokens(
            input: String,
            localeMap: JSONObject,
            extensionId: String = ""
        ): String {
            if (input.isBlank() || !input.contains("__MSG_")) return input
            return MSG_TOKEN_REGEX.replace(input) { match ->
                val rawKey = match.groupValues[1]
                if (rawKey == "@@extension_id" && extensionId.isNotBlank()) {
                    return@replace extensionId
                }
                val entry = localeMap.optJSONObject(rawKey.lowercase(Locale.ROOT))
                val msg = entry?.optString("message")?.takeIf { it.isNotEmpty() }
                msg ?: match.value
            }
        }

        fun resolveLocalizedTextForExtension(ext: BrowserExtension, rawText: String): String {
            if (rawText.isBlank()) return rawText
            val dir = File(ext.dirPath)
            if (!dir.exists()) return rawText
            val localeMap = loadLocaleMessages(dir, ext.defaultLocale)
            return resolveAllMsgTokens(rawText, localeMap, ext.id)
        }

        private fun discoverHtmlEntryPage(
            dir: File,
            workerFile: String,
            bgScripts: List<String>,
            bgPage: String
        ): String {
            val scriptsToScan = buildList {
                if (workerFile.isNotBlank()) add(workerFile)
                addAll(bgScripts)
            }.distinct()

            val htmlLiteralRegex = Regex("""["']([a-zA-Z0-9_./-]+\.html?)["']""")
            for (relScript in scriptsToScan) {
                val sf = File(dir, relScript.removePrefix("/"))
                if (sf.exists() && sf.isFile && sf.length() < 2_000_000L) {
                    val content = runCatching { sf.readText() }.getOrDefault("")
                    for (m in htmlLiteralRegex.findAll(content)) {
                        val candidate = m.groupValues[1].removePrefix("/")
                        if (candidate.contains("background", ignoreCase = true)) continue
                        val target = File(dir, candidate)
                        if (target.exists() && target.isFile) {
                            return candidate
                        }
                    }
                }
            }

            val allHtmlFiles = runCatching {
                dir.walkTopDown()
                    .maxDepth(3)
                    .filter { f ->
                        f.isFile &&
                            (f.name.endsWith(".html", ignoreCase = true) || f.name.endsWith(".htm", ignoreCase = true)) &&
                            !f.name.startsWith("__jarvis_") &&
                            !f.name.equals(bgPage.substringAfterLast('/'), ignoreCase = true) &&
                            !f.name.contains("background", ignoreCase = true)
                    }
                    .map { it.relativeTo(dir).invariantSeparatorsPath }
                    .toList()
            }.getOrDefault(emptyList())

            if (allHtmlFiles.isEmpty()) return ""

            val priorityTokens = listOf("sidepanel", "side_panel", "popup", "panel", "index", "app", "main", "dashboard", "options")
            for (token in priorityTokens) {
                val match = allHtmlFiles.firstOrNull { it.substringAfterLast('/').contains(token, ignoreCase = true) }
                if (match != null) return match
            }
            return allHtmlFiles.first()
        }

        private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val item = arr.optString(i).trim()
                if (item.isNotEmpty()) out.add(item)
            }
            return out
        }

        private fun resolveBestIcon(dir: File, iconsObj: JSONObject?): String {
            if (iconsObj == null) return ""
            val preferredSizes = listOf("128", "96", "64", "48", "32", "16")
            for (sizeKey in preferredSizes) {
                val rel = iconsObj.optString(sizeKey).trim().removePrefix("/")
                if (rel.isNotEmpty()) {
                    val f = File(dir, rel)
                    if (f.exists() && f.isFile) return f.absolutePath
                }
            }
            val keys = iconsObj.keys()
            while (keys.hasNext()) {
                val rel = iconsObj.optString(keys.next()).trim().removePrefix("/")
                if (rel.isNotEmpty()) {
                    val f = File(dir, rel)
                    if (f.exists() && f.isFile) return f.absolutePath
                }
            }
            return ""
        }
    }
}
