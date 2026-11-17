package com.jarvisai.assistant.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvisai.assistant.mcp.CascadeCatalogPlugin
import com.jarvisai.assistant.mcp.McpServersManager
import com.jarvisai.assistant.ui.components.bouncyOverscroll
import kotlinx.coroutines.launch

@Composable
fun CascadeMcpCatalogDialog(
    onDismiss: () -> Unit,
    onInstalled: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers by McpServersManager.servers.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedReadme by remember { mutableStateOf<CascadeCatalogPlugin?>(null) }
    var feedbackMsg by remember { mutableStateOf<String?>(null) }

    val configuredIds = remember(servers) { servers.map { it.serverName }.toSet() }
    val filteredCatalog = remember(searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) McpServersManager.CASCADE_CATALOG
        else McpServersManager.CASCADE_CATALOG.filter {
            it.title.lowercase().contains(q) ||
                it.id.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 700.dp),
            shape = RoundedCornerShape(22.dp),
            color = AgySettingsColors.BgCanvas,
            border = BorderStroke(1.dp, AgySettingsColors.Terracotta.copy(alpha = 0.4f))
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = AgySettingsColors.Terracotta,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Cascade MCP Catalog",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgySettingsColors.TextPrimary
                            )
                            Text(
                                text = "1-click provisioning to ~/.gemini/jarvis-antigravity/mcp_config.json",
                                fontSize = 11.sp,
                                color = AgySettingsColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_mcp_catalog_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AgySettingsColors.TextSecondary)
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search MCP catalog (firebase, github, sqlite, stitch...)", fontSize = 12.5.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgySettingsColors.Terracotta,
                        unfocusedBorderColor = AgySettingsColors.CardBorder,
                        focusedTextColor = AgySettingsColors.TextPrimary,
                        unfocusedTextColor = AgySettingsColors.TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mcp_catalog_search_input")
                )

                if (!feedbackMsg.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AgySettingsColors.QuotaGreen.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, AgySettingsColors.QuotaGreen.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = feedbackMsg.orEmpty(),
                            color = AgySettingsColors.QuotaGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
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
                    items(filteredCatalog, key = { it.id }) { plugin ->
                        val isInstalled = plugin.id in configuredIds
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
                            border = BorderStroke(
                                1.dp,
                                if (isInstalled) AgySettingsColors.QuotaGreen.copy(alpha = 0.45f)
                                else AgySettingsColors.CardBorder
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = plugin.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AgySettingsColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (plugin.trustLevel == "TRUSTED") {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = AgySettingsColors.QuotaGreen.copy(alpha = 0.16f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Verified,
                                                            contentDescription = null,
                                                            tint = AgySettingsColors.QuotaGreen,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Text(
                                                            "TRUSTED",
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = AgySettingsColors.QuotaGreen
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${plugin.category} · ${if (plugin.isRemote) "Remote SSE/HTTP" else "Stdio"}",
                                            fontSize = 11.sp,
                                            color = AgySettingsColors.Info
                                        )
                                    }
                                }

                                Text(
                                    text = plugin.description,
                                    fontSize = 12.sp,
                                    color = AgySettingsColors.TextSecondary,
                                    lineHeight = 16.sp
                                )

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        text = if (plugin.isRemote) plugin.serverUrl
                                        else "${plugin.command} ${plugin.args.joinToString(" ")}",
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = AgySettingsColors.QuotaGreen,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { selectedReadme = plugin },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, AgySettingsColors.CardBorder),
                                        modifier = Modifier.heightIn(min = 42.dp)
                                    ) {
                                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("README", fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val res = McpServersManager.installCatalogPlugin(context, plugin)
                                                feedbackMsg = res.fold(
                                                    onSuccess = {
                                                        onInstalled?.invoke(plugin.id)
                                                        "Installed '${plugin.id}' (${it.tools.size} tools)"
                                                    },
                                                    onFailure = { "Error: ${it.message}" }
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isInstalled) AgySettingsColors.QuotaGreen else AgySettingsColors.Terracotta
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 42.dp)
                                            .testTag("catalog_install_${plugin.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (isInstalled) Icons.Default.Check else Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = if (isInstalled) "Installed (Re-sync)" else "1-Click Install",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
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

    selectedReadme?.let { readmePlugin ->
        AlertDialog(
            onDismissRequest = { selectedReadme = null },
            containerColor = AgySettingsColors.CardBg,
            title = { Text(readmePlugin.title, color = AgySettingsColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = readmePlugin.readme.ifBlank { readmePlugin.description },
                    color = AgySettingsColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { selectedReadme = null }) {
                    Text("Close", color = AgySettingsColors.Terracotta)
                }
            }
        )
    }
}
