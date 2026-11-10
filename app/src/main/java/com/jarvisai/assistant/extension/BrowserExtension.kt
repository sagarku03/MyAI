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
    val skills: List<ExtensionSkill> = emptyList()
) {
    companion object {
        fun fromManifest(dir: File, manifestJson: JSONObject): BrowserExtension {
            val background = manifestJson.optJSONObject("background")
            val worker = background?.optString("service_worker", "service-worker.js") ?: "service-worker.js"

            val mcpTools = manifestJson.optJSONArray("mcp_tools") ?: JSONArray()
            val toolNames = mutableListOf<String>()
            for (i in 0 until mcpTools.length()) {
                val t = mcpTools.getJSONObject(i)
                toolNames.add(t.getString("name"))
            }

            val perms = manifestJson.optJSONArray("permissions") ?: JSONArray()
            val permList = mutableListOf<String>()
            for (i in 0 until perms.length()) {
                permList.add(perms.getString(i))
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

            return BrowserExtension(
                id = manifestJson.optString("id", dir.name),
                name = manifestJson.optString("name", dir.name),
                version = manifestJson.optString("version", "1.0.0"),
                description = manifestJson.optString("description", ""),
                toolCount = toolNames.size,
                permissions = permList,
                dirPath = dir.absolutePath,
                serviceWorkerFile = worker,
                tools = toolNames,
                skills = skillList
            )
        }
    }
}
