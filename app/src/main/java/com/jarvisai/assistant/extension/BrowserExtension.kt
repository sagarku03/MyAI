package com.jarvisai.assistant.extension

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
    val allFrames: Boolean = false
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
    val installedAt: Long = 0L
) {
    val hasCustomUiPage: Boolean
        get() = popupFile.isNotBlank() || sidePanelPath.isNotBlank() || optionsPage.isNotBlank()

    val uiEntryPage: String
        get() = popupFile.ifBlank { sidePanelPath }.ifBlank { optionsPage }.ifBlank { "__jarvis_ext_dashboard.html" }

    fun safeHostId(): String = id.lowercase().replace(Regex("[^a-z0-9-]"), "-").trim('-').ifBlank { "ext" }

    fun extensionOrigin(): String = "https://ext-${safeHostId()}.jarvis.invalid"

    companion object {
        const val META_FILE_NAME = ".jarvis_ext_meta.json"

        fun fromManifest(dir: File, manifestJson: JSONObject): BrowserExtension {
            val metaFile = File(dir, META_FILE_NAME)
            val metaJson = if (metaFile.exists()) {
                runCatching { JSONObject(metaFile.readText()) }.getOrNull()
            } else null

            val manifestVersion = manifestJson.optInt("manifest_version", 3)

            val background = manifestJson.optJSONObject("background")
            val worker = background?.optString("service_worker", "")?.trim().orEmpty()
            val bgPage = background?.optString("page", "")?.trim().orEmpty()
            val bgIsModule = background?.optString("type", "")?.equals("module", ignoreCase = true) == true
            val bgScripts = mutableListOf<String>()
            background?.optJSONArray("scripts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i).trim()
                    if (s.isNotBlank()) bgScripts.add(s)
                }
            }
            val resolvedWorker = when {
                worker.isNotBlank() -> worker
                bgScripts.isNotEmpty() -> bgScripts.first()
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
            var optionsPage = (
                optionsUi?.optString("page")?.takeIf { it.isNotBlank() }
                    ?: manifestJson.optString("options_page").takeIf { it.isNotBlank() }
                    ?: urlOverrides?.optString("newtab")?.takeIf { it.isNotBlank() }
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

            if (popupFile.isBlank() && sidePanelPath.isBlank() && optionsPage.isBlank()) {
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
            val hostPermList = jsonArrayToStringList(manifestJson.optJSONArray("host_permissions"))

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
                        allFrames = csObj.optBoolean("all_frames", false)
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
                ?: dir.name
            val derivedId = rawId.lowercase().replace(Regex("[^a-z0-9-]"), "-").trim('-').ifBlank { dir.name }

            val rawName = manifestJson.optString("name", dir.name)
            val cleanName = if (rawName.startsWith("__MSG_")) {
                resolveLocalizedMessage(dir, manifestJson, rawName) ?: dir.name
            } else {
                rawName
            }

            val rawDesc = manifestJson.optString("description", "")
            val cleanDesc = if (rawDesc.startsWith("__MSG_")) {
                resolveLocalizedMessage(dir, manifestJson, rawDesc) ?: rawDesc
            } else {
                rawDesc
            }

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
                installedAt = metaJson?.optLong("installedAt", dir.lastModified()) ?: dir.lastModified()
            )
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

        private fun resolveLocalizedMessage(dir: File, manifest: JSONObject, token: String): String? {
            val key = token.removePrefix("__MSG_").removeSuffix("__")
            val locale = manifest.optString("default_locale", "en").ifBlank { "en" }
            val candidates = listOf(
                File(dir, "_locales/$locale/messages.json"),
                File(dir, "_locales/en/messages.json"),
                File(dir, "_locales/en_US/messages.json")
            )
            for (file in candidates) {
                if (!file.exists()) continue
                val msg = runCatching {
                    val obj = JSONObject(file.readText())
                    obj.optJSONObject(key)?.optString("message")
                        ?: run {
                            var found: String? = null
                            val it = obj.keys()
                            while (it.hasNext()) {
                                val k = it.next()
                                if (k.equals(key, ignoreCase = true)) {
                                    found = obj.optJSONObject(k)?.optString("message")
                                    break
                                }
                            }
                            found
                        }
                }.getOrNull()
                if (!msg.isNullOrBlank()) return msg
            }
            return null
        }
    }
}
