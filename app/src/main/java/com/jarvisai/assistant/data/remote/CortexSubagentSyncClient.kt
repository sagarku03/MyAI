package com.jarvisai.assistant.data.remote

import android.util.Log
import com.jarvisai.assistant.agent.core.AgyOkHttpClient
import com.jarvisai.assistant.model.ToolCall
import com.jarvisai.assistant.model.ToolCallStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class CortexSubagentRef(
    val childCascadeId: String,
    val parentCascadeId: String,
    val role: String,
    val typeName: String,
    val prompt: String,
    val hubBaseUrl: String
)

data class CortexSummaryEntry(
    val cascadeId: String,
    val parentCascadeId: String?,
    val title: String,
    val subagentRole: String,
    val subagentTypeName: String,
    val isRunning: Boolean,
    val notFullyIdle: Boolean,
    val hasActiveChildren: Boolean,
    val hasRunningSubagents: Boolean,
    val hasActivity: Boolean,
    val stepCount: Int
)

data class CortexTrajectorySnapshot(
    val cascadeId: String,
    val status: String,
    val isRunning: Boolean,
    val isIdle: Boolean,
    val title: String,
    val stepCount: Int,
    val initialPrompt: String,
    val steps: JSONArray?,
    val discoveredSubagents: List<CortexSubagentRef>
)

data class CortexTurnSlice(
    val responseText: String,
    val fullThought: String,
    val toolCalls: List<ToolCall>,
    val hasActiveStep: Boolean,
    val errorMessage: String,
    val discoveredSubagents: List<CortexSubagentRef>
)

/**
 * Mirrors AntiGem's live Cortex / `agy --hub` Cascade trajectory & subagent synchronization:
 * - Discovers child subagent cascades from `CortexStepInvokeSubagent` steps and `GetAllCascadeTrajectories`
 * - Polls child subagent trajectories so they appear live in the Drawer and can be opened to inspect
 *   thoughts, tool calls, and `System: Message from Parent Agent`
 * - Polls parent trajectories after `invoke_subagent` so asynchronous subagent completion reports and
 *   parent follow-up responses stream and persist cleanly in the parent chat.
 */
object CortexSubagentSyncClient {

    private const val TAG = "CortexSubagentSync"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private fun isRunningStatus(status: String): Boolean {
        val u = status.uppercase()
        return u.contains("RUNNING") || u.contains("BUSY") || u.contains("GENERATING") || u.contains("IN_PROGRESS")
    }

    private fun isIdleStatus(status: String): Boolean {
        val u = status.uppercase()
        return u.contains("IDLE") || u.contains("DONE") || u.contains("COMPLETED") ||
            u.contains("FINISHED") || u.contains("CANCEL") || u.contains("ERROR")
    }

    private fun isStepDone(status: String): Boolean {
        val u = status.uppercase()
        return u.contains("DONE") || u.contains("COMPLETED") || u.contains("ERROR") ||
            u.contains("FAILED") || u.contains("CANCEL") || u.contains("CLEARED") ||
            u.contains("HALTED") || u.contains("INTERRUPTED")
    }

    private fun isStepActive(status: String): Boolean {
        val u = status.uppercase()
        return u.contains("RUNNING") || u.contains("GENERATING") || u.contains("PENDING") ||
            u.contains("QUEUED") || u.contains("WAITING")
    }

    /**
     * Extracts all child subagent references from a single Cortex trajectory step JSON object.
     */
    fun extractSubagentsFromStep(
        step: JSONObject,
        parentCascadeId: String,
        hubBaseUrl: String
    ): List<CortexSubagentRef> {
        val found = linkedMapOf<String, CortexSubagentRef>()

        fun register(cid: String, role: String, typeName: String, prompt: String) {
            val cleanCid = cid.trim()
            if (cleanCid.isBlank() || cleanCid == parentCascadeId) return
            val existing = found[cleanCid]
            found[cleanCid] = CortexSubagentRef(
                childCascadeId = cleanCid,
                parentCascadeId = parentCascadeId,
                role = role.trim().ifBlank { existing?.role.orEmpty() },
                typeName = typeName.trim().ifBlank { existing?.typeName.orEmpty() },
                prompt = prompt.trim().ifBlank { existing?.prompt.orEmpty() },
                hubBaseUrl = hubBaseUrl
            )
        }

        fun inspectInvokeObj(inv: JSONObject?, fallbackArgs: JSONObject?) {
            if (inv == null) return
            val defaultName = inv.optString("subagentName", "")
                .ifBlank { inv.optString("role", "") }
                .ifBlank { fallbackArgs?.optString("role", "").orEmpty() }
                .ifBlank { fallbackArgs?.optString("subagent_name", "").orEmpty() }
                .ifBlank { fallbackArgs?.optString("name", "").orEmpty() }
            val defaultType = inv.optString("typeName", "")
                .ifBlank { inv.optString("type_name", "") }
                .ifBlank { fallbackArgs?.optString("type_name", "").orEmpty() }
                .ifBlank { fallbackArgs?.optString("typeName", "").orEmpty() }
                .ifBlank { fallbackArgs?.optString("subagent_type", "").orEmpty() }
            val defaultPrompt = inv.optString("prompt", "")
                .ifBlank { inv.optString("initialPrompt", "") }
                .ifBlank { fallbackArgs?.optString("prompt", "").orEmpty() }
                .ifBlank { fallbackArgs?.optString("initial_prompt", "").orEmpty() }

            val directCid = inv.optString("conversationId", "")
                .ifBlank { inv.optString("conversation_id", "") }
                .ifBlank { inv.optString("subagentConversationId", "") }
                .ifBlank { inv.optString("subagent_conversation_id", "") }
                .ifBlank { inv.optString("cascadeId", "") }
                .ifBlank { inv.optString("cascade_id", "") }
            if (directCid.isNotBlank()) {
                register(directCid, defaultName, defaultType, defaultPrompt)
            }

            val specs = inv.optJSONArray("subagents")
            val results = inv.optJSONArray("results")
            if (results != null) {
                for (idx in 0 until results.length()) {
                    val res = results.optJSONObject(idx) ?: continue
                    val rCid = res.optString("conversationId", "")
                        .ifBlank { res.optString("conversation_id", "") }
                        .ifBlank { res.optString("cascadeId", "") }
                    val spec = specs?.optJSONObject(idx)
                    val role = spec?.optString("role", "").orEmpty()
                        .ifBlank { spec?.optString("typeName", "").orEmpty() }
                        .ifBlank { spec?.optString("type_name", "").orEmpty() }
                        .ifBlank { defaultName }
                    val typeName = spec?.optString("typeName", "").orEmpty()
                        .ifBlank { spec?.optString("type_name", "").orEmpty() }
                        .ifBlank { defaultType }
                    val prompt = spec?.optString("initialPrompt", "").orEmpty()
                        .ifBlank { spec?.optString("initial_prompt", "").orEmpty() }
                        .ifBlank { defaultPrompt }
                    if (rCid.isNotBlank()) {
                        register(rCid, role, typeName, prompt)
                    }
                }
            }
        }

        val gen = step.optJSONObject("generic")
        val metaArgs = step.optJSONObject("metadata")
            ?.optJSONObject("toolCall")
            ?.optString("argumentsJson", "")
            ?.takeIf { it.trim().startsWith("{") }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        val genArgs = gen?.optJSONObject("args") ?: metaArgs

        inspectInvokeObj(step.optJSONObject("invokeSubagent"), genArgs)
        inspectInvokeObj(step.optJSONObject("invoke_subagent"), genArgs)
        inspectInvokeObj(step.optJSONObject("subagent"), genArgs)
        inspectInvokeObj(step.optJSONObject("browserSubagent"), genArgs)
        val genPayload = gen?.optJSONObject("result")?.optJSONObject("payload")
        inspectInvokeObj(genPayload?.optJSONObject("invokeSubagent"), genArgs)
        inspectInvokeObj(genPayload?.optJSONObject("invoke_subagent"), genArgs)
        if (genPayload != null && (genPayload.has("subagentConversationId") || genPayload.has("conversationId"))) {
            inspectInvokeObj(genPayload, genArgs)
        }

        return found.values.toList()
    }

    /**
     * Queries `GetAllCascadeTrajectories` on the Local AGY Hub to list all known parent/child
     * trajectories along with `parentConversationId`, `subagentSpec`, and running/idle flags.
     */
    suspend fun fetchAllCascadeSummaries(hubBaseUrl: String): List<CortexSummaryEntry> = withContext(Dispatchers.IO) {
        val cleanUrl = hubBaseUrl.trimEnd('/')
        if (cleanUrl.isBlank()) return@withContext emptyList()
        runCatching {
            val req = Request.Builder()
                .url("$cleanUrl/GetAllCascadeTrajectories")
                .post("{}".toRequestBody(JSON_MEDIA))
                .build()
            AgyOkHttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use emptyList()
                val root = JSONObject(body)
                val mapObj = root.optJSONObject("trajectorySummaries")
                    ?: root.optJSONObject("trajectory_summaries")
                    ?: root.optJSONObject("summaries")
                    ?: return@use emptyList()

                val out = mutableListOf<CortexSummaryEntry>()
                val keys = mapObj.keys()
                while (keys.hasNext()) {
                    val cid = keys.next()
                    val obj = mapObj.optJSONObject(cid) ?: continue
                    val meta = obj.optJSONObject("trajectoryMetadata")
                        ?: obj.optJSONObject("trajectory_metadata")
                    val toolSource = meta?.optJSONObject("sourceMetadata")?.optJSONObject("tool")
                        ?: meta?.optJSONObject("source_metadata")?.optJSONObject("tool")
                    val subSpec = meta?.optJSONObject("subagentSpec")
                        ?: meta?.optJSONObject("subagent_spec")
                    val ann = obj.optJSONObject("annotations")

                    val parentCid = listOfNotNull(
                        meta?.optString("parentConversationId")?.takeIf { it.isNotBlank() && it != cid },
                        meta?.optString("parent_conversation_id")?.takeIf { it.isNotBlank() && it != cid },
                        toolSource?.optString("conversationId")?.takeIf { it.isNotBlank() && it != cid },
                        toolSource?.optString("conversation_id")?.takeIf { it.isNotBlank() && it != cid },
                        obj.optString("forkParentConversationId").takeIf { it.isNotBlank() && it != cid },
                        obj.optString("fork_parent_conversation_id").takeIf { it.isNotBlank() && it != cid },
                        meta?.optString("rootConversationId")?.takeIf { it.isNotBlank() && it != cid },
                        meta?.optString("root_conversation_id")?.takeIf { it.isNotBlank() && it != cid }
                    ).firstOrNull()

                    val status = obj.optString("status", "")
                    val title = listOfNotNull(
                        ann?.optString("title")?.takeIf { it.isNotBlank() },
                        obj.optString("summary").takeIf { it.isNotBlank() },
                        subSpec?.optString("role")?.takeIf { it.isNotBlank() },
                        toolSource?.optString("label")?.takeIf { it.isNotBlank() }
                    ).firstOrNull().orEmpty()

                    val role = listOfNotNull(
                        subSpec?.optString("role")?.takeIf { it.isNotBlank() },
                        toolSource?.optString("label")?.takeIf { it.isNotBlank() }
                    ).firstOrNull().orEmpty()

                    val typeName = listOfNotNull(
                        subSpec?.optString("typeName")?.takeIf { it.isNotBlank() },
                        subSpec?.optString("type_name")?.takeIf { it.isNotBlank() },
                        toolSource?.optJSONObject("toolCall")?.optString("name")?.takeIf { it.isNotBlank() },
                        toolSource?.optJSONObject("tool_call")?.optString("name")?.takeIf { it.isNotBlank() }
                    ).firstOrNull().orEmpty()

                    out.add(
                        CortexSummaryEntry(
                            cascadeId = cid,
                            parentCascadeId = parentCid,
                            title = title,
                            subagentRole = role,
                            subagentTypeName = typeName,
                            isRunning = isRunningStatus(status),
                            notFullyIdle = obj.optBoolean("notFullyIdle", obj.optBoolean("not_fully_idle", false)),
                            hasActiveChildren = obj.optBoolean("hasActiveChildren", obj.optBoolean("has_active_children", false)),
                            hasRunningSubagents = obj.optBoolean("hasRunningSubagents", obj.optBoolean("has_running_subagents", false)),
                            hasActivity = obj.optBoolean("hasActivity", obj.optBoolean("has_activity", false)),
                            stepCount = obj.optInt("stepCount", obj.optInt("step_count", 0))
                        )
                    )
                }
                out
            }
        }.onFailure {
            Log.d(TAG, "fetchAllCascadeSummaries failed: ${it.message}")
        }.getOrDefault(emptyList())
    }

    /**
     * Fetches the full trajectory snapshot for a given `cascadeId` via `GetCascadeTrajectory`.
     */
    suspend fun fetchCascadeSnapshot(
        hubBaseUrl: String,
        cascadeId: String
    ): CortexTrajectorySnapshot? = withContext(Dispatchers.IO) {
        val cleanUrl = hubBaseUrl.trimEnd('/')
        if (cleanUrl.isBlank() || cascadeId.isBlank()) return@withContext null
        runCatching {
            val payload = JSONObject().put("cascadeId", cascadeId).toString()
            val req = Request.Builder()
                .url("$cleanUrl/GetCascadeTrajectory")
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()
            AgyOkHttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                val root = JSONObject(body)
                val trajectory = root.optJSONObject("trajectory")
                val status = listOfNotNull(
                    trajectory?.optString("status")?.takeIf { it.isNotBlank() },
                    root.optString("status").takeIf { it.isNotBlank() },
                    root.optString("cascadeStatus").takeIf { it.isNotBlank() },
                    trajectory?.optString("cascadeStatus")?.takeIf { it.isNotBlank() },
                    root.optString("runStatus").takeIf { it.isNotBlank() },
                    trajectory?.optString("runStatus")?.takeIf { it.isNotBlank() }
                ).firstOrNull().orEmpty()

                val steps = trajectory?.optJSONArray("steps")
                val stepCount = steps?.length() ?: 0
                val title = listOfNotNull(
                    trajectory?.optString("summary")?.takeIf { it.isNotBlank() },
                    trajectory?.optString("title")?.takeIf { it.isNotBlank() },
                    root.optString("summary").takeIf { it.isNotBlank() },
                    root.optString("title").takeIf { it.isNotBlank() },
                    trajectory?.optJSONObject("metadata")?.optString("summary")?.takeIf { it.isNotBlank() }
                ).firstOrNull()?.trim().orEmpty()

                var initialPrompt = ""
                val discovered = linkedMapOf<String, CortexSubagentRef>()
                if (steps != null) {
                    for (i in 0 until stepCount) {
                        val step = steps.optJSONObject(i) ?: continue
                        if (initialPrompt.isBlank()) {
                            val sType = step.optString("type", "")
                            if (sType == "CORTEX_STEP_TYPE_USER_INPUT" || sType == "TRAJECTORY_STEP_TYPE_USER_INPUT") {
                                initialPrompt = extractUserInputText(step)
                            } else if (sType == "CORTEX_STEP_TYPE_SYSTEM_MESSAGE") {
                                val sys = step.optJSONObject("systemMessage") ?: step.optJSONObject("system_message")
                                val msg = sys?.optString("message", "").orEmpty().trim()
                                if (msg.isNotBlank() && !isIgnoredSystemNotice(msg)) {
                                    initialPrompt = msg
                                }
                            }
                        }
                        extractSubagentsFromStep(step, cascadeId, cleanUrl).forEach { ref ->
                            discovered[ref.childCascadeId] = ref
                        }
                    }
                }

                CortexTrajectorySnapshot(
                    cascadeId = cascadeId,
                    status = status,
                    isRunning = isRunningStatus(status),
                    isIdle = isIdleStatus(status),
                    title = title,
                    stepCount = stepCount,
                    initialPrompt = initialPrompt,
                    steps = steps,
                    discoveredSubagents = discovered.values.toList()
                )
            }
        }.onFailure {
            Log.d(TAG, "fetchCascadeSnapshot($cascadeId) failed: ${it.message}")
        }.getOrNull()
    }

    /**
     * Parses a slice of steps `[fromStepIndex until stepCount)` into chronological ToolCalls
     * (`__thought__`, `__assistant_text__`, tool steps, system notices) plus the final response text.
     */
    fun parseTurnSlice(
        snapshot: CortexTrajectorySnapshot,
        fromStepIndex: Int = 0,
        hubBaseUrl: String = ""
    ): CortexTurnSlice {
        val steps = snapshot.steps ?: return CortexTurnSlice("", "", emptyList(), false, "", emptyList())
        val total = steps.length()
        val start = fromStepIndex.coerceIn(0, total)

        val tools = linkedMapOf<String, ToolCall>()
        val thoughts = mutableListOf<String>()
        val responseParts = linkedMapOf<Int, String>()
        val discovered = linkedMapOf<String, CortexSubagentRef>()
        var hasActive = false
        var errorMsg = ""

        for (i in start until total) {
            val step = steps.optJSONObject(i) ?: continue
            val stepType = step.optString("type", "")
            val stepStatus = step.optString("status", "")
            val done = isStepDone(stepStatus)
            val active = isStepActive(stepStatus) && !done
            if (active) hasActive = true

            val hasLaterNonUserStep = (i + 1 until total).any { idx ->
                val t = steps.optJSONObject(idx)?.optString("type", "").orEmpty()
                t != "CORTEX_STEP_TYPE_USER_INPUT" && t != "TRAJECTORY_STEP_TYPE_USER_INPUT"
            }

            extractSubagentsFromStep(step, snapshot.cascadeId, hubBaseUrl).forEach { ref ->
                discovered[ref.childCascadeId] = ref
            }

            when {
                stepType == "CORTEX_STEP_TYPE_USER_INPUT" || stepType == "TRAJECTORY_STEP_TYPE_USER_INPUT" -> {
                    continue
                }

                stepType == "CORTEX_STEP_TYPE_SYSTEM_MESSAGE" -> {
                    val sys = step.optJSONObject("systemMessage") ?: step.optJSONObject("system_message")
                    val msg = sys?.optString("message", "").orEmpty().trim()
                    val renderInfo = sys?.optJSONObject("renderInfo") ?: sys?.optJSONObject("render_info")
                    val agentMsg = sys?.optJSONObject("agentMessage") ?: sys?.optJSONObject("agent_message")
                    val hidden = renderInfo?.optBoolean("hidden", false) == true ||
                        agentMsg?.optBoolean("hideFromUser", agentMsg.optBoolean("hide_from_user", false)) == true
                    if (!hidden && msg.isNotBlank() && !isIgnoredSystemNotice(msg)) {
                        val noticeTitle = renderInfo?.optString("title", "").orEmpty()
                            .takeIf { it.isNotBlank() && !it.equals("System", ignoreCase = true) }
                            ?: agentMsg?.optJSONObject("renderDetails")?.optString("messageTitle", "").orEmpty()
                                .ifBlank { agentMsg?.optJSONObject("render_details")?.optString("message_title", "").orEmpty() }
                            .ifBlank { "System update" }
                        val id = "${snapshot.cascadeId}_sys_$i"
                        tools[id] = ToolCall(
                            id = id,
                            stepIndex = i * 10 + 1,
                            name = "system",
                            command = noticeTitle,
                            output = msg.take(6000),
                            status = ToolCallStatus.SUCCESS,
                            exitCode = 0
                        )
                    }
                }

                stepType == "CORTEX_STEP_TYPE_PLANNER_RESPONSE" ||
                    stepType == "TRAJECTORY_STEP_TYPE_MODEL_THINKING" ||
                    stepType == "TRAJECTORY_STEP_TYPE_MODEL_RESPONSE" ||
                    stepType == "CORTEX_STEP_TYPE_FINAL_RESPONSE" -> {
                    val planner = step.optJSONObject("plannerResponse") ?: step.optJSONObject("finalResponse")
                    val thinking = planner?.optString("thinking", "")
                        ?.ifBlank { step.optString("thinkingTrace", "") }
                        .orEmpty().trim()
                    val response = planner?.optString("response", "")
                        ?.ifBlank { planner.optString("modifiedResponse", "") }
                        ?.ifBlank { planner.optString("text", "") }
                        ?.ifBlank { planner.optString("message", "") }
                        ?.ifBlank { step.optString("contentMarkdown", "") }
                        ?.ifBlank { step.optString("text", "") }
                        .orEmpty().trim()

                    if (thinking.isNotBlank()) {
                        thoughts.add(thinking)
                        val durMs = parseIsoDurationMs(planner?.optString("thinkingDuration", "").orEmpty())
                        val id = "${snapshot.cascadeId}_thought_$i"
                        tools[id] = ToolCall(
                            id = id,
                            stepIndex = i * 10,
                            name = "__thought__",
                            output = thinking,
                            status = if (done || hasLaterNonUserStep || response.isNotBlank()) ToolCallStatus.SUCCESS else ToolCallStatus.RUNNING,
                            durationMs = durMs
                        )
                    }

                    if (response.isNotBlank()) {
                        if (hasLaterNonUserStep) {
                            val id = "${snapshot.cascadeId}_text_$i"
                            tools[id] = ToolCall(
                                id = id,
                                stepIndex = i * 10 + 2,
                                name = "__assistant_text__",
                                output = response,
                                status = ToolCallStatus.SUCCESS
                            )
                        } else {
                            responseParts[i] = response
                        }
                    }
                }

                stepType == "CORTEX_STEP_TYPE_NOTIFY_USER" || step.has("notifyUser") -> {
                    val notify = step.optJSONObject("notifyUser")
                    val msg = notify?.optString("message", "")
                        ?.ifBlank { notify.optString("Message", "") }
                        ?.ifBlank { notify.optString("notificationContent", "") }
                        ?.ifBlank { step.optString("message", "") }
                        .orEmpty().trim()
                    if (msg.isNotBlank()) {
                        if (hasLaterNonUserStep) {
                            val id = "${snapshot.cascadeId}_notify_$i"
                            tools[id] = ToolCall(
                                id = id,
                                stepIndex = i * 10 + 3,
                                name = "__assistant_text__",
                                output = msg,
                                status = ToolCallStatus.SUCCESS
                            )
                        } else {
                            responseParts[i] = msg
                        }
                    }
                }

                stepType == "CORTEX_STEP_TYPE_ERROR_MESSAGE" -> {
                    val err = step.optJSONObject("errorMessage")?.optJSONObject("error")
                    errorMsg = err?.optString("userErrorMessage", "")
                        ?.ifBlank { err.optString("shortError", "") }
                        ?.ifBlank { err.optString("message", "") }
                        .orEmpty()
                        .ifBlank { "Execution error" }
                }

                else -> {
                    val parsedTool = parseGenericToolStep(snapshot.cascadeId, i, step, stepType, stepStatus, done, active, hasLaterNonUserStep)
                    if (parsedTool != null) {
                        tools[parsedTool.id] = parsedTool
                    }
                }
            }
        }

        return CortexTurnSlice(
            responseText = responseParts.values.joinToString("\n\n").trim(),
            fullThought = thoughts.joinToString("\n\n").trim(),
            toolCalls = tools.values.sortedBy { it.stepIndex },
            hasActiveStep = hasActive,
            errorMessage = errorMsg,
            discoveredSubagents = discovered.values.toList()
        )
    }

    private fun parseGenericToolStep(
        cascadeId: String,
        index: Int,
        step: JSONObject,
        stepType: String,
        stepStatus: String,
        done: Boolean,
        active: Boolean,
        hasLaterStep: Boolean
    ): ToolCall? {
        val rc = step.optJSONObject("runCommand") ?: step.optJSONObject("shellExec")
        val viewFile = step.optJSONObject("viewFile") ?: step.optJSONObject("readFile")
        val writeFile = step.optJSONObject("writeToFile") ?: step.optJSONObject("writeFile")
        val editFile = step.optJSONObject("replaceFileContent")
            ?: step.optJSONObject("multiReplaceFileContent")
            ?: step.optJSONObject("codeAction")
            ?: step.optJSONObject("editFile")
        val listDir = step.optJSONObject("listDirectory") ?: step.optJSONObject("findByName") ?: step.optJSONObject("find")
        val grep = step.optJSONObject("grepSearch")
        val web = step.optJSONObject("searchWeb") ?: step.optJSONObject("readUrlContent")
        val sub = step.optJSONObject("invokeSubagent") ?: step.optJSONObject("subagent") ?: step.optJSONObject("browserSubagent")
        val mcp = step.optJSONObject("mcpTool") ?: step.optJSONObject("callMcpTool")
        val gen = step.optJSONObject("generic")
        val meta = step.optJSONObject("metadata")

        val isKnownTool = rc != null || viewFile != null || writeFile != null || editFile != null ||
            listDir != null || grep != null || web != null || sub != null || mcp != null || gen != null ||
            stepType.contains("TOOL", ignoreCase = true) || stepType.contains("SUBAGENT", ignoreCase = true) ||
            stepType.contains("SEARCH", ignoreCase = true) || stepType.contains("COMMAND", ignoreCase = true) ||
            stepType.contains("FILE", ignoreCase = true) || stepType.contains("MCP", ignoreCase = true)
        if (!isKnownTool) return null

        val name = when {
            rc != null -> "run_command"
            viewFile != null -> "read_file"
            writeFile != null -> "write_to_file"
            editFile != null -> "edit_file"
            listDir != null -> "list_dir"
            grep != null -> "grep_search"
            web != null -> "web_search"
            sub != null || stepType.contains("SUBAGENT", ignoreCase = true) -> "subagent"
            mcp != null -> mcp.optString("toolName", "mcp_tool").ifBlank { "mcp_tool" }
            !gen?.optString("name", "").isNullOrBlank() -> gen?.optString("name", "tool") ?: "tool"
            else -> stepType.removePrefix("CORTEX_STEP_TYPE_").lowercase().ifBlank { "tool" }
        }

        val cmd = when {
            rc != null -> rc.optString("commandLine", rc.optString("command", rc.optString("proposedCommandLine", "")))
            viewFile != null -> viewFile.optString("absolutePath", viewFile.optString("fileUri", ""))
            writeFile != null -> writeFile.optString("targetFile", writeFile.optString("path", ""))
            editFile != null -> editFile.optString("uri", editFile.optString("absolutePathUri", editFile.optString("targetFile", "")))
            listDir != null -> listDir.optString("directoryPath", listDir.optString("searchDirectory", listDir.optString("pattern", "")))
            grep != null -> grep.optString("query", grep.optString("commandRun", ""))
            web != null -> web.optString("query", web.optString("url", ""))
            sub != null -> sub.optString("subagentName", "").ifBlank {
                sub.optJSONArray("subagents")?.optJSONObject(0)?.optString("role", "").orEmpty()
            }.ifBlank { "invoke subagent" }
            mcp != null -> mcp.optString("toolName", "").ifBlank { meta?.optString("toolSummary", "").orEmpty() }
            gen != null -> meta?.optString("toolSummary", "").orEmpty().ifBlank {
                gen.optJSONObject("args")?.toString()?.take(120).orEmpty()
            }
            else -> meta?.optString("toolSummary", "").orEmpty()
        }.trim().take(160)

        val out = when {
            rc != null -> rc.optJSONObject("combinedOutput")?.optString("full", "").orEmpty()
                .ifBlank { rc.optString("output", "") }
            viewFile != null -> viewFile.optString("content", "")
            editFile != null -> editFile.optString("diff", editFile.optString("patch", ""))
            grep != null -> grep.optString("output", "")
            web != null -> web.optString("summary", "").ifBlank {
                web.optString("markdown", web.optString("content", web.optString("output", "")))
            }
            sub != null -> sub.optString("finalResponse", "").ifBlank { sub.optString("prompt", "") }
            mcp != null -> mcp.optString("resultString", "").ifBlank {
                mcp.optString("error", "").ifBlank { mcp.opt("result")?.toString().orEmpty() }
            }
            gen != null -> gen.optJSONObject("result")?.optJSONObject("payload")?.toString().orEmpty()
            else -> ""
        }.trim().take(6000)

        val exitCode = if (rc?.has("exitCode") == true) rc.optInt("exitCode") else null
        val status = when {
            stepStatus.contains("ERROR", true) || stepStatus.contains("FAIL", true) || (exitCode != null && exitCode != 0) -> ToolCallStatus.FAILED
            done || hasLaterStep || out.isNotBlank() -> ToolCallStatus.SUCCESS
            active -> ToolCallStatus.RUNNING
            else -> ToolCallStatus.RUNNING
        }

        return ToolCall(
            id = "${cascadeId}_step_$index",
            stepIndex = index * 10 + 4,
            name = name,
            command = cmd.ifBlank { name.replace('_', ' ') },
            output = out,
            status = status,
            exitCode = exitCode
        )
    }

    private fun extractUserInputText(step: JSONObject): String {
        val ui = step.optJSONObject("userInput") ?: step.optJSONObject("user_input") ?: return ""
        val items = ui.optJSONArray("items")
        if (items != null && items.length() > 0) {
            val sb = StringBuilder()
            for (k in 0 until items.length()) {
                val t = items.optJSONObject(k)?.optString("text", "").orEmpty()
                if (t.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(t)
                }
            }
            if (sb.isNotEmpty()) return sb.toString().trim()
        }
        return ui.optString("userResponse", "")
            .ifBlank { ui.optString("content", "") }
            .trim()
    }

    private fun isIgnoredSystemNotice(msg: String): Boolean {
        return msg.contains("subagents and background tasks have been stopped", ignoreCase = true) ||
            msg.contains("stopped due to server restart", ignoreCase = true)
    }

    private fun parseIsoDurationMs(raw: String): Long {
        if (raw.isBlank()) return 0L
        val clean = raw.trim().removeSuffix("s")
        val secs = clean.toDoubleOrNull() ?: return 0L
        return (secs * 1000.0).toLong().coerceAtLeast(0L)
    }
}
