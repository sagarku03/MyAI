package com.jarvisai.assistant.browser

import android.content.Context
import com.jarvisai.assistant.extension.BrowserExtension
import com.jarvisai.assistant.extension.BrowserExtensionManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Discovers, validates, and loads agent skills (`SKILL.md` / `skills` manifest entries)
 * and content scripts bundled inside installed browser extensions so AI agents can invoke
 * domain-specific web automation workflows.
 */
object ExtensionSkills {

    data class ResolvedExtensionSkill(
        val extensionId: String,
        val extensionName: String,
        val skillName: String,
        val title: String,
        val description: String,
        val markdownContent: String,
        val matchPatterns: List<String> = emptyList()
    )

    data class ContentScriptRule(
        val extensionId: String,
        val matches: List<String>,
        val jsFiles: List<String>,
        val runAt: String = "document_idle"
    )

    /**
     * Collects all enabled extension skills across installed extensions.
     */
    fun loadEnabledSkills(context: Context): List<ResolvedExtensionSkill> {
        val manager = BrowserExtensionManager.getInstance(context)
        val results = mutableListOf<ResolvedExtensionSkill>()

        for (ext in manager.extensions.value) {
            if (!ext.isEnabled || ext.dirPath.isBlank()) continue
            val extDir = File(ext.dirPath)
            if (!extDir.exists()) continue

            for (skill in ext.skills) {
                val skillFile = File(extDir, skill.path.ifBlank { "SKILL.md" })
                val content = if (skillFile.exists() && skillFile.isFile) {
                    runCatching { skillFile.readText() }.getOrDefault("")
                } else {
                    skill.description
                }
                results.add(
                    ResolvedExtensionSkill(
                        extensionId = ext.id,
                        extensionName = ext.name,
                        skillName = skill.name.ifBlank { ext.id },
                        title = skill.title.ifBlank { ext.name },
                        description = skill.description,
                        markdownContent = content
                    )
                )
            }

            // Also auto-discover top-level SKILL.md if not explicitly listed in manifest.json
            if (ext.skills.isEmpty()) {
                val fallbackSkillMd = File(extDir, "SKILL.md")
                if (fallbackSkillMd.exists() && fallbackSkillMd.isFile) {
                    val content = runCatching { fallbackSkillMd.readText() }.getOrDefault("")
                    results.add(
                        ResolvedExtensionSkill(
                            extensionId = ext.id,
                            extensionName = ext.name,
                            skillName = ext.id,
                            title = ext.name,
                            description = ext.description,
                            markdownContent = content
                        )
                    )
                }
            }
        }
        return results
    }

    /**
     * Parses `content_scripts` declarations from an extension's `manifest.json`.
     */
    fun loadContentScripts(ext: BrowserExtension): List<ContentScriptRule> {
        if (!ext.isEnabled || ext.dirPath.isBlank()) return emptyList()
        val manifestFile = File(ext.dirPath, "manifest.json")
        if (!manifestFile.exists()) return emptyList()

        return runCatching {
            val json = JSONObject(manifestFile.readText())
            val arr = json.optJSONArray("content_scripts") ?: JSONArray()
            val rules = mutableListOf<ContentScriptRule>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val matchesArr = item.optJSONArray("matches") ?: JSONArray()
                val jsArr = item.optJSONArray("js") ?: JSONArray()
                val matches = (0 until matchesArr.length()).mapNotNull { matchesArr.optString(it).takeIf(String::isNotBlank) }
                val jsFiles = (0 until jsArr.length()).mapNotNull { jsArr.optString(it).takeIf(String::isNotBlank) }
                if (matches.isNotEmpty() && jsFiles.isNotEmpty()) {
                    rules.add(
                        ContentScriptRule(
                            extensionId = ext.id,
                            matches = matches,
                            jsFiles = jsFiles,
                            runAt = item.optString("run_at", "document_idle")
                        )
                    )
                }
            }
            rules
        }.getOrDefault(emptyList())
    }

    // Checks whether url matches a Chrome extension match pattern (e.g. <all_urls>).
    fun matchesUrlPattern(url: String, pattern: String): Boolean {
        if (url.isBlank() || BrowserIdentity.isGoogleSignInUrl(url)) return false
        if (pattern == "<all_urls>" || pattern.equals("*://*/*", ignoreCase = true)) return true
        val regexPattern = buildString {
            append("^")
            for (ch in pattern) {
                when (ch) {
                    '*' -> append(".*")
                    '.', '?', '+', '^', '$', '(', ')', '[', ']', '{', '}', '|', '\\' -> {
                        append('\\').append(ch)
                    }
                    else -> append(ch)
                }
            }
            append("$")
        }
        return runCatching { Regex(regexPattern, RegexOption.IGNORE_CASE).matches(url) }.getOrDefault(false)
    }

    /**
     * Builds a system prompt appendix summarizing all active extension skills for the agent.
     */
    fun buildAgentSkillsPromptSection(context: Context): String {
        val skills = loadEnabledSkills(context)
        if (skills.isEmpty()) return ""
        return buildString {
            appendLine("## Active Browser Extension Skills")
            for (skill in skills) {
                appendLine("### ${skill.title} (`${skill.skillName}` from ${skill.extensionName})")
                if (skill.description.isNotBlank()) {
                    appendLine(skill.description)
                }
                if (skill.markdownContent.isNotBlank() && skill.markdownContent != skill.description) {
                    appendLine(skill.markdownContent.take(1500))
                }
                appendLine()
            }
        }
    }
}
