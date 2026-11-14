package com.jarvisai.assistant.ui.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.ui.components.bouncyOverscroll
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class McpCatalogEntry(
    val id: String,
    val name: String,
    val command: String,
    val args: List<String>,
    val description: String,
    val envKeyName: String? = null
)

val BUILTIN_MCP_CATALOG = listOf(
    McpCatalogEntry(
        id = "filesystem",
        name = "Filesystem MCP (@modelcontextprotocol/server-filesystem)",
        command = "npx",
        args = listOf("-y", "@modelcontextprotocol/server-filesystem", "/data/data/com.termux/files/home"),
        description = "Read, write, and search files across Termux home and coding workspaces."
    ),
    McpCatalogEntry(
        id = "github",
        name = "GitHub MCP (@modelcontextprotocol/server-github)",
        command = "npx",
        args = listOf("-y", "@modelcontextprotocol/server-github"),
        description = "Manage repositories, issues, PRs, and code search via GitHub REST/GraphQL.",
        envKeyName = "GITHUB_PERSONAL_ACCESS_TOKEN"
    ),
    McpCatalogEntry(
        id = "postgres",
        name = "PostgreSQL Inspector (@modelcontextprotocol/server-postgres)",
        command = "npx",
        args = listOf("-y", "@modelcontextprotocol/server-postgres", "postgresql://localhost/mydb"),
        description = "Inspect schemas and run read-only SQL queries against PostgreSQL databases."
    ),
    McpCatalogEntry(
        id = "sqlite",
        name = "SQLite Analytics (@modelcontextprotocol/server-sqlite)",
        command = "npx",
        args = listOf("-y", "@modelcontextprotocol/server-sqlite", "--db-path", "workspace.db"),
        description = "Local SQLite database query execution and schema introspection."
    ),
    McpCatalogEntry(
        id = "brave-search",
        name = "Brave Web Search (@modelcontextprotocol/server-brave-search)",
        command = "npx",
        args = listOf("-y", "@modelcontextprotocol/server-brave-search"),
        description = "Live web & local search integration for autonomous research agents.",
        envKeyName = "BRAVE_API_KEY"
    ),
    McpCatalogEntry(
        id = "memory",
        name = "Knowledge Graph Memory (@modelcontextprotocol/server-memory)",
        command = "npx",
        args = listOf("-y", "@modelcontextprotocol/server-memory"),
        description = "Persistent entity-relation graph memory across agent sessions."
    ),
    McpCatalogEntry(
        id = "jarvis-android-mcp",
        name = "Jarvis Android & Device MCP Bridge",
        command = "http",
        args = listOf("http://127.0.0.1:8765/mcp"),
        description = "Unified Android OS, Terminal PTY, Accessibility UI, Memory Blackboard & Flow MCP server."
    ),
    McpCatalogEntry(
        id = "browser-cdp",
        name = "Jarvis Native CDP Browser Bridge",
        command = "http",
        args = listOf("http://127.0.0.1:8091/mcp/browser"),
        description = "Built-in Android WebView CDP automation socket with live browser MCP tools."
    )
)

object McpConfigSynchronizer {
    fun syncMcpServers(
        context: Context,
        enabledIds: Set<String>,
        envValues: Map<String, String>
    ) {
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val serversObj = JSONObject()
        BUILTIN_MCP_CATALOG.filter { it.id in enabledIds }.forEach { entry ->
            val spec = JSONObject().apply {
                if (entry.command == "http") {
                    put("serverUrl", entry.args.firstOrNull().orEmpty())
                    put("url", entry.args.firstOrNull().orEmpty())
                    put("transport", "http")
                } else {
                    put("command", entry.command)
                    put("args", JSONArray(entry.args))
                }
                if (entry.envKeyName != null) {
                    val token = envValues[entry.envKeyName].orEmpty()
                    if (token.isNotBlank()) {
                        put("env", JSONObject().put(entry.envKeyName, token))
                    }
                }
            }
            serversObj.put(entry.id, spec)
        }
        val rootJson = JSONObject().put("mcpServers", serversObj).toString(2)
        val targetDirs = listOf(
            File(homeDir, ".gemini/jarvis-antigravity"),
            File(homeDir, ".gemini"),
            File(homeDir, ".claude"),
            File("/data/data/com.termux/files/home/.gemini/jarvis-antigravity"),
            File("/data/data/com.termux/files/home/.gemini")
        )
        targetDirs.forEach { dir ->
            runCatching {
                dir.mkdirs()
                val fileName = if (dir.name == ".claude") "mcp_servers.json" else "mcp_config.json"
                File(dir, fileName).writeText(rootJson)
            }
        }
    }
}

@Composable
fun CascadeMcpCatalogDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("jarvis_mcp_catalog", Context.MODE_PRIVATE) }
    val enabledMap = remember { mutableStateMapOf<String, Boolean>() }
    val envMap = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(Unit) {
        BUILTIN_MCP_CATALOG.forEach { item ->
            val defaultEnabled = item.id == "jarvis-android-mcp" || item.id == "filesystem" || item.id == "browser-cdp"
            enabledMap[item.id] = prefs.getBoolean("enabled_${item.id}", defaultEnabled)
            if (item.envKeyName != null) {
                envMap[item.envKeyName] = prefs.getString("env_${item.envKeyName}", "") ?: ""
            }
        }
    }

    fun persistAndSync() {
        val editor = prefs.edit()
        val activeSet = mutableSetOf<String>()
        enabledMap.forEach { (id, on) ->
            editor.putBoolean("enabled_$id", on)
            if (on) activeSet.add(id)
        }
        envMap.forEach { (k, v) ->
            editor.putString("env_$k", v)
        }
        editor.apply()
        McpConfigSynchronizer.syncMcpServers(context, activeSet, envMap)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0B0F19),
            border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Cascade & Claude MCP Server Catalog",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color(0xFFF8FAFC),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "One-tap provisioning to ~/.gemini/jarvis-antigravity/mcp_config.json",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_mcp_catalog_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .bouncyOverscroll(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(BUILTIN_MCP_CATALOG, key = { it.id }) { item ->
                        val isEnabled = enabledMap[item.id] == true
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF111827),
                            border = BorderStroke(
                                1.dp,
                                if (isEnabled) Color(0xFF38BDF8).copy(alpha = 0.5f) else Color(0xFF1E293B)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color(0xFFF8FAFC),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                        Text(
                                            text = item.description,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${item.command} ${item.args.joinToString(" ")}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFF10B981),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }

                                    Switch(
                                        checked = isEnabled,
                                        onCheckedChange = { checked ->
                                            enabledMap[item.id] = checked
                                            persistAndSync()
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF6366F1)
                                        )
                                    )
                                }

                                if (item.envKeyName != null && isEnabled) {
                                    OutlinedTextField(
                                        value = envMap[item.envKeyName] ?: "",
                                        onValueChange = { value ->
                                            envMap[item.envKeyName] = value
                                            persistAndSync()
                                        },
                                        label = { Text(item.envKeyName) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF38BDF8),
                                            unfocusedBorderColor = Color(0xFF334155),
                                            focusedTextColor = Color(0xFFF8FAFC),
                                            unfocusedTextColor = Color(0xFFF8FAFC)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
