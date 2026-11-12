package com.jarvisai.assistant.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.browser.PluginRepositories
import com.jarvisai.assistant.ui.components.bouncyOverscroll
import kotlinx.coroutines.launch

@Composable
fun PluginRepositoriesTab(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repoUrls by PluginRepositories.repositories.collectAsState()
    val catalogEntries by PluginRepositories.catalog.collectAsState()
    var newRepoUrl by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .bouncyOverscroll(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF111827),
                border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Add GitHub Skill / Extension Catalog URL",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color(0xFFF8FAFC),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    OutlinedTextField(
                        value = newRepoUrl,
                        onValueChange = { newRepoUrl = it },
                        label = { Text("https://raw.githubusercontent.com/.../catalog.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color(0xFFF8FAFC),
                            unfocusedTextColor = Color(0xFFF8FAFC)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val url = newRepoUrl.trim()
                                if (url.isNotEmpty()) {
                                    scope.launch {
                                        val res = PluginRepositories.addRepository(url)
                                        statusText = res.fold(
                                            onSuccess = { "Synced ${it.size} plugins from $url" },
                                            onFailure = { "Error: ${it.message}" }
                                        )
                                        newRepoUrl = ""
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("add_plugin_repo_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Register URL", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    statusText = "Refreshing remote catalogs..."
                                    val res = PluginRepositories.refreshCatalog()
                                    statusText = res.fold(
                                        onSuccess = { "Catalog updated (${it.size} entries)" },
                                        onFailure = { "Refresh failed: ${it.message}" }
                                    )
                                }
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("refresh_plugin_catalog_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Catalog", modifier = Modifier.size(18.dp))
                        }
                    }

                    Text(
                        text = "Active Sources (${repoUrls.size}): ${repoUrls.joinToString(" • ")}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }

        if (statusText != null) {
            item {
                Text(
                    text = statusText.orEmpty(),
                    style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF10B981)),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        items(catalogEntries, key = { it.id }) { entry ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF111827),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${entry.name} v${entry.version}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFFF8FAFC),
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                        Text(
                            text = entry.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "by ${entry.author} • ${entry.repositoryUrl}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF38BDF8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                val res = PluginRepositories.installCatalogPlugin(context, entry)
                                statusText = res.fold(
                                    onSuccess = { "Installed ${entry.name} (${it.id})" },
                                    onFailure = { "Install error: ${it.message}" }
                                )
                            }
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("install_plugin_${entry.id}"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Install", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
