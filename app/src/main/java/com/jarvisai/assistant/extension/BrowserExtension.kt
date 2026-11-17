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
    val backgroundScripts: List<String> = emptyList(),
    val contentScripts: List<ContentScriptSpec> = emptyList(),
    val hostPermissions: List<String> = emptyList(),
    val sourceType: String = "bundled",
    val homepageUrl: String = "",
    val updateUrl: String = "",
    val installedAt: Long = 0L
) {
    companion object {
        const val META_FILE_NAME = ".jarvis_ext_meta.json"

        fun fromManifest(dir: File, manifestJson: JSONObject): BrowserExtension {
            val metaFile = File(dir, META_FILE_NAME)
            val metaJson = if (metaFile.exists()) {
                runCatching { JSONObject(metaFile.readText()) }.getOrNull()
            } else null

            val manifestVersion = manifestJson.optInt("manifest_version", 3)

            val background = manifestJson.optJSONObject("background")
            val worker = background?.optString("service_worker", "")?.takeIf { it.isNotBlank() }
                ?: "service-worker.js"
            val bgScripts = mutableListOf<String>()
            background?.optJSONArray("scripts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i).trim()
                    if (s.isNotBlank()) bgScripts.add(s)
                }
            }

            // Action / Popup (MV3 action or MV2 browser_action / page_action)
            val actionObj = manifestJson.optJSONObject("action")
                ?: manifestJson.optJSONObject("browser_action")
                ?: manifestJson.optJSONObject("page_action")
            val popupFile = actionObj?.optString("default_popup", "")?.trim().orEmpty()

            // Icons (pick highest resolution existing icon)
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

            val perms = manifestJson.optJSONArray("permissions") ?: JSONArray()
            val permList = mutableListOf<String>()
            for (i in 0 until perms.length()) {
                val p = perms.optString(i).trim()
                if (p.isNotEmpty()) permList.add(p)
            }

            val hostPerms = manifestJson.optJSONArray("host_permissions") ?: JSONArray()
            val hostPermList = mutableListOf<String>()
            for (i in 0 until hostPerms.length()) {
                val p = hostPerms.optString(i).trim()
                if (p.isNotEmpty()) hostPermList.add(p)
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

            val derivedId = metaJson?.optString("id")?.takeIf { it.isNotBlank() }
                ?: manifestJson.optString("id").takeIf { it.isNotBlank() }
                ?: dir.name

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
                serviceWorkerFile = if (worker == "service-worker.js" && bgScripts.isNotEmpty() && !File(dir, worker).exists()) {
                    bgScripts.first()
                } else {
                    worker
                },
                tools = toolNames,
                skills = skillList,
                manifestVersion = manifestVersion,
                iconPath = resolvedIconPath,
                popupFile = popupFile,
                backgroundScripts = bgScripts,
                contentScripts = csList,
                hostPermissions = hostPermList,
                sourceType = metaJson?.optString("sourceType")?.takeIf { it.isNotBlank() } ?: "bundled",
                homepageUrl = manifestJson.optString("homepage_url", ""),
                updateUrl = metaJson?.optString("updateUrl").orEmpty()
                    .ifBlank { manifestJson.optString("update_url", "") },
                installedAt = metaJson?.optLong("installedAt", dir.lastModified()) ?: dir.lastModified()
            )
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
