package com.jarvisai.assistant.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.agent.agy.CloudPreviewServer
import com.jarvisai.assistant.data.remote.JarvisHubConfig
import com.jarvisai.assistant.model.ToolCall
import com.jarvisai.assistant.model.ToolCallStatus
import com.jarvisai.assistant.terminal.WorkspaceSyncManager
import org.json.JSONObject
import java.io.File
import java.util.Locale

@Composable
fun AgentToolCallCard(
    toolCall: ToolCall,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember(toolCall.id) { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val isRunning = toolCall.status == ToolCallStatus.RUNNING
    val isFailed = toolCall.status == ToolCallStatus.FAILED || (toolCall.exitCode != null && toolCall.exitCode != 0)

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            isExpanded = false
        }
    }

    val statusColor = when {
        isRunning -> Color(0xFF38BDF8) // Pulsing Sky Cyan while executing
        isFailed -> Color(0xFFEF4444)  // Red on failure
        else -> Color(0xFF22C55E)      // AntiGem Emerald Green
    }

    val runningAlpha = if (isRunning) {
        val infiniteTransition = rememberInfiniteTransition(label = "tool_pulse")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "tool_pulse_alpha"
        )
        animated
    } else {
        1f
    }

    val normalizedTool = toolCall.name.lowercase().trim()
    val isFileWriteOrEdit = normalizedTool in setOf(
        "write_to_file", "write_file", "edit_file", "replace_file_content",
        "multi_replace_file_content", "code_action", "open_file_in_ide"
    )
    val isReadTool = normalizedTool in setOf(
        "read_file", "view_file", "view_file_outline", "view_code_item", "ast_outline"
    )

    val extractedFilePath = remember(toolCall.command, toolCall.args, toolCall.output) {
        extractTargetFilePath(toolCall)
    }
    val cleanDisplayCommand = remember(toolCall.name, toolCall.command, toolCall.args) {
        extractCleanCommandSummary(toolCall)
    }
    val readLineRangeSuffix = remember(isReadTool, toolCall.args, toolCall.command) {
        if (isReadTool) extractLineRangeSuffix(toolCall) else null
    }

    val actionLabel = when {
        normalizedTool in setOf("bash", "run_command", "shell", "terminal_exec", "shell_exec") -> if (isRunning) "Running bash" else "Ran bash"
        normalizedTool == "command_status" -> if (isRunning) "Checking status" else "Command status"
        normalizedTool in setOf("read_file", "view_file", "view_file_outline", "view_code_item", "ast_outline") ->
            if (isRunning) "Reading file" else "Read file"
        normalizedTool in setOf("write_to_file", "write_file") -> if (isRunning) "Writing file" else "Wrote file"
        normalizedTool in setOf("edit_file", "replace_file_content", "multi_replace_file_content", "code_action", "file_change") ->
            if (isRunning) "Editing file" else "Edited file"
        normalizedTool in setOf("list_dir", "list_directory", "find_by_name", "find") -> if (isRunning) "Listing" else "Listed"
        normalizedTool in setOf("grep_search", "search", "code_search") -> if (isRunning) "Searching" else "Searched"
        normalizedTool in setOf("web_search", "search_web", "read_url_content", "read_url") -> if (isRunning) "Fetching web" else "Fetched web"
        normalizedTool in setOf("define_subagent", "invoke_subagent", "subagent") -> if (isRunning) "Running subagent" else "Subagent"
        normalizedTool in setOf("system", "notify_user", "task_boundary", "checkpoint") -> "System:"
        normalizedTool.startsWith("mcp_") -> normalizedTool.removePrefix("mcp_").replace('_', ' ')
        else -> normalizedTool.removePrefix("cactus:").replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    val actionIcon: ImageVector = when {
        normalizedTool in setOf("bash", "run_command", "shell", "terminal_exec", "shell_exec", "command_status") -> Icons.Outlined.Terminal
        normalizedTool in setOf("read_file", "view_file", "view_file_outline", "view_code_item", "ast_outline") -> Icons.Outlined.Description
        normalizedTool in setOf("write_to_file", "write_file", "edit_file", "replace_file_content", "multi_replace_file_content", "code_action", "file_change", "open_file_in_ide") -> Icons.Outlined.Edit
        normalizedTool in setOf("list_dir", "list_directory", "find_by_name", "find") -> Icons.Outlined.FolderOpen
        normalizedTool in setOf("grep_search", "search", "code_search") -> Icons.Outlined.Search
        normalizedTool in setOf("web_search", "search_web", "read_url_content", "read_url") -> Icons.Outlined.Public
        normalizedTool in setOf("system", "notify_user", "task_boundary", "checkpoint", "subagent", "define_subagent", "invoke_subagent") -> Icons.Outlined.Notifications
        else -> Icons.Outlined.Build
    }

    val summaryPreview = remember(extractedFilePath, cleanDisplayCommand, readLineRangeSuffix, normalizedTool) {
        val base = when {
            normalizedTool in setOf("bash", "run_command", "shell", "terminal_exec", "system", "notify_user") ->
                cleanDisplayCommand
            extractedFilePath != null -> File(extractedFilePath).name.ifBlank { extractedFilePath }
            else -> cleanDisplayCommand
        }.replace('\n', ' ').trim()
        if (!readLineRangeSuffix.isNullOrBlank() && !base.contains("(lines ")) {
            "$base $readLineRangeSuffix"
        } else {
            base
        }
    }

    val formattedDuration = remember(toolCall.durationMs) {
        formatAntiGemToolDuration(toolCall.durationMs)
    }

    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(160),
        label = "tool_chevron"
    )

    // AntiGem-style completely borderless & boxless compact timeline row
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 2.dp, vertical = 5.dp)
                .testTag("tool_call_row_${toolCall.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse step" else "Expand step",
                tint = Color(0xFF6B7280),
                modifier = Modifier
                    .size(14.dp)
                    .rotate(rotation)
            )

            Spacer(modifier = Modifier.width(5.dp))

            Icon(
                imageVector = actionIcon,
                contentDescription = actionLabel,
                tint = statusColor.copy(alpha = runningAlpha),
                modifier = Modifier.size(14.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = actionLabel,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = if (isRunning) Color(0xFF38BDF8) else Color(0xFFE5E7EB),
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = summaryPreview,
                fontSize = 12.sp,
                color = Color(0xFF7D8590),
                fontFamily = if (normalizedTool in setOf("system", "notify_user")) {
                    FontFamily.SansSerif
                } else {
                    FontFamily.Monospace
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (formattedDuration != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formattedDuration,
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1
                )
            }

            if (toolCall.exitCode != null) {
                Spacer(modifier = Modifier.width(6.dp))
                val isZero = toolCall.exitCode == 0
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isZero) Color(0xFF072B17) else Color(0xFF3B1114)
                ) {
                    Text(
                        text = "exit ${toolCall.exitCode}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isZero) Color(0xFF22C55E) else Color(0xFFF87171),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(start = 20.dp, top = 3.dp, bottom = 6.dp)) {
                if ((isFileWriteOrEdit || extractedFilePath != null) && !extractedFilePath.isNullOrBlank()) {
                    val targetFile = File(extractedFilePath)
                    val isWebPreviewable = targetFile.extension.lowercase() in setOf("html", "htm")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = {
                                WorkspaceSyncManager.requestOpenFileInIde(targetFile.absolutePath)
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF141B26),
                            border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
                            modifier = Modifier.testTag("tool_card_open_in_ide_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "Open in Sora IDE",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Open in Sora IDE",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (isWebPreviewable) {
                            Surface(
                                onClick = {
                                    val rootDir = targetFile.parentFile ?: targetFile
                                    val url = CloudPreviewServer.start(
                                        rootDir = rootDir,
                                        port = JarvisHubConfig.ISOLATED_WEB_PREVIEW_PORT
                                    ) + "/" + targetFile.name
                                    com.jarvisai.assistant.browser.BrowserSessionManager.instance.init(context)
                                    com.jarvisai.assistant.browser.BrowserSessionManager.instance.openUrlFromUi(
                                        rawUrl = url,
                                        switchToBrowser = true
                                    )
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF141B26),
                                border = BorderStroke(0.8.dp, Color(0xFF22C55E).copy(alpha = 0.45f)),
                                modifier = Modifier.testTag("tool_card_live_preview_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = "Live Web Preview",
                                        tint = Color(0xFF22C55E),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Live Preview (:2239)",
                                        color = Color(0xFF22C55E),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0D1117),
                    border = BorderStroke(0.6.dp, Color(0xFF1F2937)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isRunning) "RUNNING..." else if (isFileWriteOrEdit) "FILE DIFF & OUTPUT" else "OUTPUT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isRunning) Color(0xFF38BDF8) else Color(0xFF6B7280),
                                fontFamily = FontFamily.Monospace
                            )
                            if (toolCall.output.isNotBlank() || toolCall.command.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        val clipContent = buildString {
                                            if (toolCall.command.isNotBlank()) appendLine("$ ${toolCall.command}")
                                            if (toolCall.output.isNotBlank()) append(toolCall.output)
                                        }.trim()
                                        clipboardManager.setText(AnnotatedString(clipContent))
                                        Toast.makeText(context, "Copied step output", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy step output",
                                        tint = Color(0xFF9CA3AF),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        if (cleanDisplayCommand.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "$ $cleanDisplayCommand",
                                color = Color(0xFFE5E7EB),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val rawOutput = toolCall.output.ifBlank {
                            if (isRunning) "Executing live on device..." else "(no output)"
                        }
                        val hasDiffLines = rawOutput.lines().any {
                            it.startsWith("+") || it.startsWith("-") || it.startsWith("@@")
                        }

                        if (hasDiffLines) {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                rawOutput.lines().take(60).forEach { line ->
                                    val bg = when {
                                        line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF22C55E).copy(alpha = 0.14f)
                                        line.startsWith("-") && !line.startsWith("---") -> Color(0xFFEF4444).copy(alpha = 0.14f)
                                        else -> Color.Transparent
                                    }
                                    val fg = when {
                                        line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF86EFAC)
                                        line.startsWith("-") && !line.startsWith("---") -> Color(0xFFFCA5A5)
                                        line.startsWith("@@") -> Color(0xFF60A5FA)
                                        else -> Color(0xFFD1D5DB)
                                    }
                                    Text(
                                        text = line,
                                        color = fg,
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(bg)
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = rawOutput,
                                color = if (isFailed) Color(0xFFFCA5A5) else Color(0xFF9CA3AF),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 50,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatAntiGemToolDuration(durationMs: Long): String? {
    if (durationMs <= 0L) return null
    return when {
        durationMs < 1000L -> "${durationMs}ms"
        durationMs < 60_000L -> String.format(Locale.US, "%.1fs", durationMs / 1000.0)
        else -> {
            val totalSec = durationMs / 1000L
            val mins = totalSec / 60L
            val secs = totalSec % 60L
            if (secs > 0L) "${mins}m ${secs}s" else "${mins}m"
        }
    }
}

private fun extractLineRangeSuffix(toolCall: ToolCall): String? {
    val sources = listOf(toolCall.args.trim(), toolCall.command.trim())
    for (src in sources) {
        if (src.startsWith("{")) {
            val suffix = runCatching {
                val obj = JSONObject(src)
                val start = when {
                    obj.has("StartLine") -> obj.optInt("StartLine", -1)
                    obj.has("startLine") -> obj.optInt("startLine", -1)
                    obj.has("start_line") -> obj.optInt("start_line", -1)
                    else -> -1
                }
                val end = when {
                    obj.has("EndLine") -> obj.optInt("EndLine", -1)
                    obj.has("endLine") -> obj.optInt("endLine", -1)
                    obj.has("end_line") -> obj.optInt("end_line", -1)
                    else -> -1
                }
                if (start >= 0 && end >= 0) "(lines $start-$end)" else null
            }.getOrNull()
            if (suffix != null) return suffix
        }
    }
    return null
}

private fun extractCleanCommandSummary(toolCall: ToolCall): String {
    val rawCmd = toolCall.command.trim()
    val rawArgs = toolCall.args.trim()
    val jsonSource = when {
        rawCmd.startsWith("{") -> rawCmd
        rawArgs.startsWith("{") -> rawArgs
        else -> null
    }
    if (jsonSource != null) {
        val parsed = runCatching {
            val obj = JSONObject(jsonSource)
            val keys = listOf(
                "CommandLine", "commandLine", "command", "cmd",
                "TargetFile", "targetFile", "AbsolutePath", "absolutePath",
                "absolutePathUri", "uri", "filePath", "path",
                "DirectoryPath", "directoryPath", "directoryPathUri",
                "Query", "query", "SearchTerm", "Pattern", "pattern",
                "Url", "url", "taskName", "name", "toolSummary"
            )
            keys.firstNotNullOfOrNull { k ->
                obj.optString(k, "").trim().removePrefix("file://").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
        if (!parsed.isNullOrBlank()) return parsed
    }
    if (rawCmd.isNotBlank() && !rawCmd.startsWith("{")) return rawCmd.removePrefix("file://")
    if (rawArgs.isNotBlank() && !rawArgs.startsWith("{")) return rawArgs.removePrefix("file://")
    return toolCall.name.replace('_', ' ')
}

private fun extractTargetFilePath(toolCall: ToolCall): String? {
    val candidates = listOf(toolCall.args, toolCall.command, toolCall.output)
    for (raw in candidates) {
        val trimmed = raw.trim().removePrefix("file://")
        if (trimmed.startsWith("{")) {
            runCatching {
                val obj = JSONObject(trimmed)
                val keys = listOf("TargetFile", "targetFile", "absolutePathUri", "uri", "path", "file_path", "filePath", "AbsolutePath", "file")
                for (k in keys) {
                    val v = obj.optString(k, "").trim().removePrefix("file://")
                    if (v.isNotBlank() && (v.contains("/") || v.contains("."))) return v
                }
            }
        }
        if (trimmed.startsWith("/") && !trimmed.contains(" ") && trimmed.length < 260) {
            return trimmed
        }
    }
    return null
}
