package com.jarvisai.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jarvisai.assistant.agent.ArtifactReviewMode
import com.jarvisai.assistant.agent.CommandExecutionPolicy
import com.jarvisai.assistant.agent.CommandsPermissionManager
import com.jarvisai.assistant.agent.FileAccessPolicy
import com.jarvisai.assistant.agent.SecurityPreset
import com.jarvisai.assistant.mcp.McpServerSpec
import com.jarvisai.assistant.mcp.McpServersManager
import com.jarvisai.assistant.mcp.server.AndroidLocalBridgeServer
import com.jarvisai.assistant.skills.SkillsAndPluginsManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AntiGemSettingsFeaturesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AndroidLocalBridgeServer.instance.attachContextForTest(context)
    }

    // -------------------------------------------------------------------------
    // 1. Commands & Permissions Policy Engine
    // -------------------------------------------------------------------------

    @Test
    fun commandsPermission_presetsAndWildcardRulesEvaluateAccurately() {
        CommandsPermissionManager.applyPreset(context, SecurityPreset.DEFAULT)
        CommandsPermissionManager.setArtifactReviewMode(context, ArtifactReviewMode.ALWAYS)
        var state = CommandsPermissionManager.state.value
        assertEquals(ArtifactReviewMode.ALWAYS, state.artifactReviewMode)
        assertEquals(CommandExecutionPolicy.OFF, state.commandExecutionPolicy)
        assertEquals(FileAccessPolicy.ASK, state.nonWorkspaceFileAccess)
        assertEquals(SecurityPreset.DEFAULT, state.activePreset)

        // OFF asks on unknown commands, and default denyRules blocks rm -rf /*
        assertEquals("ASK", CommandsPermissionManager.evaluateShellCommand(context, "echo hello"))
        assertEquals("DENY", CommandsPermissionManager.evaluateShellCommand(context, "rm -rf /tmp/foo"))

        // AUTO asks on high-risk commands like curl/git reset --hard, allows safe echo
        CommandsPermissionManager.setCommandExecutionPolicy(context, CommandExecutionPolicy.AUTO)
        assertEquals(SecurityPreset.CUSTOM, CommandsPermissionManager.state.value.activePreset)
        assertEquals("ALLOW", CommandsPermissionManager.evaluateShellCommand(context, "echo hello"))
        assertEquals("ASK", CommandsPermissionManager.evaluateShellCommand(context, "git reset --hard"))

        // TURBO auto-allows normal commands EXCEPT explicit deny rules
        CommandsPermissionManager.applyPreset(context, SecurityPreset.TURBO)
        CommandsPermissionManager.setTerminalSandbox(context, false)
        state = CommandsPermissionManager.state.value
        assertEquals(ArtifactReviewMode.TURBO, state.artifactReviewMode)
        assertEquals(CommandExecutionPolicy.EAGER, state.commandExecutionPolicy)
        assertEquals(FileAccessPolicy.ALLOW, state.nonWorkspaceFileAccess)
        assertFalse(state.enableTerminalSandbox)
        assertEquals(SecurityPreset.TURBO, state.activePreset)

        assertEquals("ALLOW", CommandsPermissionManager.evaluateShellCommand(context, "ls -la"))
        assertEquals("DENY", CommandsPermissionManager.evaluateShellCommand(context, "rm -rf /tmp/danger"))

        // Granular allow + deny rules override default OFF behavior
        CommandsPermissionManager.applyPreset(context, SecurityPreset.DEFAULT)
        CommandsPermissionManager.addOrUpdateRule(context, "bash", "git status*", "ALLOW")
        CommandsPermissionManager.addOrUpdateRule(context, "bash", "curl *", "DENY")

        assertEquals("ALLOW", CommandsPermissionManager.evaluateShellCommand(context, "git status -sb"))
        assertEquals("DENY", CommandsPermissionManager.evaluateShellCommand(context, "curl https://example.com"))

        // Reload from persisted SharedPreferences & security_settings.json
        val reloaded = CommandsPermissionManager.load(context)
        assertEquals(SecurityPreset.DEFAULT, reloaded.activePreset)
        assertTrue(reloaded.allRules.any { it.rawRule == "bash(git status*)" && it.decision == "ALLOW" })

        CommandsPermissionManager.removeRule(context, "bash(curl *)")
        assertEquals("ASK", CommandsPermissionManager.evaluateShellCommand(context, "curl https://example.com"))
    }

    // -------------------------------------------------------------------------
    // 2. MCP Servers Config Persistence, Toggle & Dynamic Tool Resolution
    // -------------------------------------------------------------------------

    @Test
    fun mcpServersManager_configRoundTripAndToggleWork() = runBlocking {
        val sampleSpecs = linkedMapOf(
            "custom_stdio" to McpServerSpec(
                serverName = "custom_stdio",
                command = "echo",
                args = listOf("hello"),
                env = mapOf("NODE_ENV" to "production"),
                disabled = false
            ),
            "notes_http" to McpServerSpec(
                serverName = "notes_http",
                serverUrl = "http://127.0.0.1:2236/notes",
                headers = mapOf("X-Test" to "1"),
                disabled = false
            )
        )

        McpServersManager.writeSpecsToDisk(context, sampleSpecs)
        val loaded = McpServersManager.loadConfigSpecs(context)
        assertTrue(loaded.containsKey("custom_stdio"))
        assertEquals("echo", loaded["custom_stdio"]?.command)
        assertEquals("production", loaded["custom_stdio"]?.env?.get("NODE_ENV"))
        assertTrue(loaded.containsKey("notes_http"))
        assertEquals("1", loaded["notes_http"]?.headers?.get("X-Test"))

        // Toggle disabled updates disk JSON immediately
        val primaryFile = McpServersManager.getPrimaryConfigFile(context)
        assertTrue(primaryFile.exists())
        val updatedSpecs = McpServersManager.parseMcpConfigJson(primaryFile.readText())
        assertFalse(updatedSpecs["notes_http"]!!.disabled)

        val toggled = updatedSpecs.toMutableMap()
        toggled["notes_http"] = toggled["notes_http"]!!.copy(disabled = true)
        McpServersManager.writeSpecsToDisk(context, toggled)

        val afterToggle = McpServersManager.loadConfigSpecs(context)
        assertTrue(afterToggle["notes_http"]!!.disabled)
    }

    @Test
    fun mcpServersManager_dynamicToolNamingResolvesBackToServerAndTool() {
        val target = McpServersManager.resolveDynamicToolTarget("mcp__jarvis_mcp__list_notes")
        assertEquals("jarvis_mcp" to "list_notes", target)
        assertNull(McpServersManager.resolveDynamicToolTarget("invalid_tool_name"))

        // Parsing raw JSON preserves both serverUrl and url formats
        val parsed = McpServersManager.parseMcpConfigJson(
            """
            {
              "mcpServers": {
                "firebase": { "command": "npx", "args": ["-y", "firebase-tools@latest", "mcp"] },
                "stitch": { "url": "https://stitch.googleapis.com/mcp", "disabled": true }
              }
            }
            """.trimIndent()
        )
        assertEquals(2, parsed.size)
        assertEquals("npx", parsed["firebase"]?.command)
        assertEquals(listOf("-y", "firebase-tools@latest", "mcp"), parsed["firebase"]?.args)
        assertTrue(parsed["stitch"]?.isRemote == true)
        assertTrue(parsed["stitch"]?.disabled == true)
    }

    // -------------------------------------------------------------------------
    // 3. Embedded Local Bridge JSON-RPC: Initialize, Notes CRUD, Theme Switching
    // -------------------------------------------------------------------------

    @Test
    fun localBridgeServer_handlesInitializeNotificationsNotesAndThemeTools() {
        // Initialize
        val initResp = AndroidLocalBridgeServer.instance.processRpcJson(
            "/notes",
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}"""
        )
        assertNotNull(initResp)
        val initJson = JSONObject(initResp!!)
        assertEquals("2024-11-05", initJson.getJSONObject("result").getString("protocolVersion"))

        // Notification returns null -> HTTP 202 Accepted
        val notifResp = AndroidLocalBridgeServer.instance.processRpcJson(
            "/notes",
            """{"jsonrpc":"2.0","method":"notifications/initialized"}"""
        )
        assertNull(notifResp)

        // Notes CRUD
        val toolsResp = JSONObject(
            AndroidLocalBridgeServer.instance.processRpcJson(
                "/notes",
                """{"jsonrpc":"2.0","id":2,"method":"tools/list"}"""
            )!!
        )
        val toolNames = (0 until toolsResp.getJSONObject("result").getJSONArray("tools").length()).map { idx ->
            toolsResp.getJSONObject("result").getJSONArray("tools").getJSONObject(idx).getString("name")
        }
        assertTrue(toolNames.containsAll(listOf("notes_list", "notes_create", "notes_update", "notes_delete")))

        val createResp = JSONObject(
            AndroidLocalBridgeServer.instance.processRpcJson(
                "/notes",
                """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"notes_create","arguments":{"title":"AntiGem Sprint","content":"MCP + Skills complete","tags":["todo"]}}}"""
            )!!
        )
        val createdText = createResp.getJSONObject("result").getJSONArray("content").getJSONObject(0).getString("text")
        val createdPayload = JSONObject(createdText)
        assertTrue(createdPayload.getBoolean("created"))
        val noteId = createdPayload.getJSONObject("note").getString("id")

        val listResp = JSONObject(
            AndroidLocalBridgeServer.instance.processRpcJson(
                "/notes",
                """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"notes_list","arguments":{}}}"""
            )!!
        )
        val listPayload = JSONObject(listResp.getJSONObject("result").getJSONArray("content").getJSONObject(0).getString("text"))
        assertTrue(listPayload.getInt("count") >= 1)

        val delResp = JSONObject(
            AndroidLocalBridgeServer.instance.processRpcJson(
                "/notes",
                """{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"notes_delete","arguments":{"id":"$noteId"}}}"""
            )!!
        )
        val delPayload = JSONObject(delResp.getJSONObject("result").getJSONArray("content").getJSONObject(0).getString("text"))
        assertTrue(delPayload.getBoolean("deleted"))

        // Theme list + apply
        val themeListResp = JSONObject(
            AndroidLocalBridgeServer.instance.processRpcJson(
                "/theme",
                """{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"theme_list","arguments":{}}}"""
            )!!
        )
        val themeListJson = JSONObject(themeListResp.getJSONObject("result").getJSONArray("content").getJSONObject(0).getString("text"))
        assertTrue(themeListJson.getJSONArray("palettes").length() > 0)

        val applyThemeResp = JSONObject(
            AndroidLocalBridgeServer.instance.processRpcJson(
                "/theme",
                """{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"theme_apply","arguments":{"palette":"Dracula"}}}"""
            )!!
        )
        val applyThemeJson = JSONObject(applyThemeResp.getJSONObject("result").getJSONArray("content").getJSONObject(0).getString("text"))
        assertTrue(applyThemeJson.getBoolean("applied"))
    }

    // -------------------------------------------------------------------------
    // 4. Skills, Plugins & Build-with-Google Store Engine
    // -------------------------------------------------------------------------

    @Test
    fun skillsAndPluginsManager_parsesSkillMdStoreInstallAndUninstallCleanly() = runBlocking {
        // Parse GitHub shorthand & tree URLs
        val shorthand = SkillsAndPluginsManager.parseGitHubRepoInput("anthropics/skills")
        assertNotNull(shorthand)
        assertEquals("anthropics", shorthand!!.owner)
        assertEquals("skills", shorthand.repo)

        val fullUrl = SkillsAndPluginsManager.parseGitHubRepoInput("https://github.com/Santoshkurmi/antigem/tree/main/skills")
        assertNotNull(fullUrl)
        assertEquals("Santoshkurmi", fullUrl!!.owner)
        assertEquals("antigem", fullUrl.repo)
        assertEquals("main", fullUrl.branch)
        assertEquals("skills", fullUrl.subPath)

        // Parse SKILL.md YAML frontmatter
        val tempSkillDir = File(context.cacheDir, "test_skill_dir").apply { mkdirs() }
        val skillFile = File(tempSkillDir, "SKILL.md").apply {
            writeText(
                """
                ---
                name: compose-m3-pro
                description: Build adaptive Material 3 Jetpack Compose screens cleanly.
                ---
                Use Scaffold with statusBarsPadding() and LazyColumn items keys.
                """.trimIndent()
            )
        }
        val parsedSkill = SkillsAndPluginsManager.parseSkillFile(skillFile, "workspace", "fallback")
        assertNotNull(parsedSkill)
        assertEquals("compose-m3-pro", parsedSkill!!.name)
        assertEquals("Build adaptive Material 3 Jetpack Compose screens cleanly.", parsedSkill.description)
        assertEquals("workspace", parsedSkill.scope)

        // Install a Store plugin -> verify plugin + skill appear -> prompt injection -> uninstall cleans up
        val storeItems = SkillsAndPluginsManager.refreshGooglePlugins(context)
        assertTrue(storeItems.isNotEmpty())
        val firstPlugin = storeItems.first()

        val installRes = SkillsAndPluginsManager.installGooglePlugin(context, firstPlugin.id)
        assertTrue(installRes.isSuccess)

        val plugins = SkillsAndPluginsManager.refreshInstalledPlugins(context)
        assertTrue(plugins.any { it.name == firstPlugin.name || it.id == firstPlugin.id })

        val skills = SkillsAndPluginsManager.refreshSkills(context)
        assertTrue(skills.isNotEmpty())

        val promptSection = SkillsAndPluginsManager.buildActiveSkillsPromptSection(context)
        assertTrue(promptSection.contains("Active Agent Skills"))

        val deleteRes = SkillsAndPluginsManager.deletePlugin(context, firstPlugin.id, firstPlugin.name)
        assertTrue(deleteRes.isSuccess)
    }

    // -------------------------------------------------------------------------
    // 5. MCP Ecosystem Self-Authoring (Filesystem, Custom Server, Skill, Plugin)
    // -------------------------------------------------------------------------

    @Test
    fun mcpEcosystemBridge_selfAuthoringAndFilesystemAndLiveStatusWork() = runBlocking {
        // 1. Ensure default servers are provisioned and probed
        McpServersManager.getPrimaryConfigFile(context).delete()
        val states = McpServersManager.ensureServersLoadedAndProbed(context)
        assertTrue(states.size >= 3)
        assertTrue(states.any { it.spec.serverName == "agentra-android-mcp" && it.tools.isNotEmpty() })
        assertTrue(states.any { it.spec.serverName == "browser_mcp" && it.tools.isNotEmpty() })
        assertTrue(states.any { it.spec.serverName == "filesystem" && it.tools.isNotEmpty() })

        // 2. Test /filesystem native MCP endpoint (write_file + read_file)
        val testFile = File(context.cacheDir, "mcp_fs_test.txt")
        val writeRpc = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 10)
            .put("method", "tools/call")
            .put(
                "params",
                JSONObject()
                    .put("name", "write_file")
                    .put(
                        "arguments",
                        JSONObject()
                            .put("path", testFile.absolutePath)
                            .put("content", "Hello from Jarvis Filesystem MCP!")
                    )
            ).toString()
        val writeResp = JSONObject(AndroidLocalBridgeServer.instance.processRpcJson("/filesystem", writeRpc)!!)
        assertFalse(writeResp.getJSONObject("result").optBoolean("isError", false))
        assertEquals("Hello from Jarvis Filesystem MCP!", testFile.readText())

        // 3. Test mcp_create_server to dynamically register a custom MCP server & tool
        val createServerRpc = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 11)
            .put("method", "tools/call")
            .put(
                "params",
                JSONObject()
                    .put("name", "mcp_create_server")
                    .put(
                        "arguments",
                        JSONObject()
                            .put("server_name", "jarvis-devops-helper")
                            .put("description", "Custom DevOps MCP Server created by Antigravity")
                            .put("tool_name", "check_env")
                            .put("tool_description", "Returns environment status")
                            .put("command_template", "echo DEVOPS_OK")
                    )
            ).toString()
        val createServerResp = JSONObject(AndroidLocalBridgeServer.instance.processRpcJson("/mcp", createServerRpc)!!)
        val createServerJson = JSONObject(
            createServerResp.getJSONObject("result").getJSONArray("content").getJSONObject(0).getString("text")
        )
        assertTrue(createServerJson.getBoolean("success"))
        assertEquals("jarvis-devops-helper", createServerJson.getString("server_name"))

        // Verify the custom server endpoint /custom/jarvis-devops-helper lists the new tool
        val customToolsResp = JSONObject(
            AndroidLocalBridgeServer.instance.processRpcJson(
                "/custom/jarvis-devops-helper",
                """{"jsonrpc":"2.0","id":12,"method":"tools/list"}"""
            )!!
        )
        val customToolsArr = customToolsResp.getJSONObject("result").getJSONArray("tools")
        assertTrue(customToolsArr.length() >= 1)
        assertEquals("check_env", customToolsArr.getJSONObject(0).getString("name"))

        // 4. Test skill_create_or_update via MCP
        val createSkillRpc = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 13)
            .put("method", "tools/call")
            .put(
                "params",
                JSONObject()
                    .put("name", "skill_create_or_update")
                    .put(
                        "arguments",
                        JSONObject()
                            .put("name", "jarvis-auto-skill")
                            .put("description", "Auto-created skill by Antigravity")
                            .put("instructions", "Always verify MCP servers and execute tools directly.")
                    )
            ).toString()
        val createSkillResp = JSONObject(AndroidLocalBridgeServer.instance.processRpcJson("/skills", createSkillRpc)!!)
        val createSkillJson = JSONObject(
            createSkillResp.getJSONObject("result").getJSONArray("content").getJSONObject(0).getString("text")
        )
        assertTrue(createSkillJson.getBoolean("success"))

        // 5. Test plugin_create_or_update via MCP
        val createPluginRpc = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 14)
            .put("method", "tools/call")
            .put(
                "params",
                JSONObject()
                    .put("name", "plugin_create_or_update")
                    .put(
                        "arguments",
                        JSONObject()
                            .put("name", "Jarvis Smart Highlighter")
                            .put("description", "Highlights headings on any page")
                            .put("content_js", "document.querySelectorAll('h1').forEach(el => el.style.border='2px solid cyan');")
                            .put("popup_html", "<!DOCTYPE html><html><body><h3>Smart Highlighter</h3></body></html>")
                    )
            ).toString()
        val createPluginResp = JSONObject(AndroidLocalBridgeServer.instance.processRpcJson("/plugins", createPluginRpc)!!)
        val createPluginJson = JSONObject(
            createPluginResp.getJSONObject("result").getJSONArray("content").getJSONObject(0).getString("text")
        )
        assertTrue(createPluginJson.getBoolean("success"))

        // 6. Verify live MCP status prompt section reports active connected servers > 0 and includes custom server
        val livePrompt = McpServersManager.buildLiveMcpStatusPromptSection(context)
        assertTrue(livePrompt.contains("<mcp_servers>"))
        assertTrue(livePrompt.contains("agentra-android-mcp"))
        assertTrue(livePrompt.contains("browser_mcp"))
        assertTrue(livePrompt.contains("filesystem"))
        assertTrue(livePrompt.contains("jarvis-devops-helper"))
    }

    // -------------------------------------------------------------------------
    // 6. Antigravity CLI (agy) Version Detection, Log Cleaning & Status Snapshot
    // -------------------------------------------------------------------------

    @Test
    fun agyCliManager_cleansAndroidGlogOutputAndDetectsVersionCleanly() = runBlocking {
        val noisyOutput = """
            ERROR: logging before google.Init: E0407 12:00:00.000000 12345 time_zone_lookup.go:100] Initialized Android local timezone to 'Asia/Kolkata'
            1.5.10
        """.trimIndent()

        val cleaned = com.jarvisai.assistant.terminal.AgyCliManager.cleanAgyLog(noisyOutput)
        assertEquals("1.5.10", cleaned)

        val parsedVer = com.jarvisai.assistant.terminal.AgyCliManager.parseVersionFromOutput(noisyOutput)
        assertEquals("1.5.10", parsedVer)

        assertTrue(com.jarvisai.assistant.terminal.AgyCliManager.compareSemanticVersions("1.5.8", "1.5.10") < 0)
        assertEquals(0, com.jarvisai.assistant.terminal.AgyCliManager.compareSemanticVersions("1.5.10", "1.5.10"))
        assertTrue(com.jarvisai.assistant.terminal.AgyCliManager.compareSemanticVersions("1.6.0", "1.5.10") > 0)

        // Provision a local ~/.local/bin/agy binary (>1 KB) with sidecar .agy_version for deterministic test verification
        val localBinDir = com.jarvisai.assistant.terminal.AgyCliManager.getHomeLocalBinDir(context)
        val agyBin = File(localBinDir, "agy").apply {
            writeText("#!/system/bin/sh\necho 1.5.10\n# " + "x".repeat(1100) + "\n")
            setExecutable(true, false)
        }
        File(localBinDir, ".agy_version").writeText("1.5.10")
        com.jarvisai.assistant.terminal.AgyCliManager.invalidateVersionCache()

        val info = com.jarvisai.assistant.terminal.AgyCliManager.refreshCliInfo(context, checkRemoteManifest = false)
        assertTrue(info.installed)
        assertEquals("1.5.10", info.version)
        assertEquals(agyBin.absolutePath, info.path)

        val snapshot = com.jarvisai.assistant.terminal.AgyCliManager.getCliStatusSnapshotJson(context)
        assertTrue(snapshot.getBoolean("installed"))
        assertEquals("1.5.10", snapshot.getString("version"))
        assertEquals(agyBin.absolutePath, snapshot.getString("path"))
    }

    // -------------------------------------------------------------------------
    // 7. LocalServerManager In-Place 20-Byte Framed Security Header Patching & Logs
    // -------------------------------------------------------------------------

    @Test
    fun localServerManager_patchesAgyBinaryFramedHeaderAndLogsTranscriptCleanly() {
        val token = "nfan7m2r00wy"
        val framedHeader = com.jarvisai.assistant.terminal.LocalServerManager.buildFramedHeader(token)
        assertEquals(20, framedHeader.length)
        assertEquals("x-agnfan7m2r00wy_9qx", framedHeader)

        val dummyAgyFile = File(context.cacheDir, "dummy_agy_bin").apply {
            writeText("PREFIX_BYTES_x-codeium-csrf-token_MIDDLE_BYTES_x-codeium-csrf-token_SUFFIX_BYTES")
        }

        val (patchedHeader, firstElapsedMs) = com.jarvisai.assistant.terminal.LocalServerManager.patchAgyBinaryHeader(dummyAgyFile, token, context)
        assertEquals(framedHeader, patchedHeader)
        assertTrue(firstElapsedMs >= 0.0)
        val afterFirstPatch = dummyAgyFile.readText()
        assertFalse(afterFirstPatch.contains("x-codeium-csrf-token"))
        assertTrue(afterFirstPatch.contains("x-agnfan7m2r00wy_9qx"))

        // Second patch with a rotated token should also succeed on an already-patched binary
        val secondToken = "abcd1234efgh"
        val (patchedSecondHeader, secondElapsedMs) = com.jarvisai.assistant.terminal.LocalServerManager.patchAgyBinaryHeader(dummyAgyFile, secondToken, context)
        assertEquals("x-agabcd1234efgh_9qx", patchedSecondHeader)
        assertTrue(secondElapsedMs >= 0.0)
        val afterSecondPatch = dummyAgyFile.readText()
        assertTrue(afterSecondPatch.contains("x-agabcd1234efgh_9qx"))

        // Verify supervisor log buffer & transcript
        com.jarvisai.assistant.terminal.LocalServerManager.clearServerLogs()
        com.jarvisai.assistant.terminal.LocalServerManager.appendServerLog("[Hub Probe] Port 51123 TCP Connected in 676µs")
        com.jarvisai.assistant.terminal.LocalServerManager.appendServerLog("[✓] AGY Hub is online and ready on port 51123!")
        val transcript = com.jarvisai.assistant.terminal.LocalServerManager.getServerLogTranscript()
        assertTrue(transcript.contains("[Hub Probe] Port 51123 TCP Connected in 676µs"))
        assertTrue(transcript.contains("[✓] AGY Hub is online and ready on port 51123!"))
    }
}



