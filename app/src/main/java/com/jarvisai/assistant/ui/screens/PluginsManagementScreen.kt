package com.jarvisai.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.extension.BrowserExtensionManager

@Composable
fun PluginsManagementScreen(onNavigateBack: () -> Unit = {}) {
    val extensions by BrowserExtensionManager.extensions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                @Suppress("DEPRECATION")
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("PLUGINS & EXTENSIONS", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(extensions) { ext ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111C35)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ext.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("v${ext.version} • ${ext.toolCount} tools", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            if (ext.description.isNotBlank()) {
                                Text(ext.description, color = Color(0xFFCBD5E1), fontSize = 11.sp, maxLines = 2)
                            }
                        }
                        Switch(
                            checked = ext.isEnabled,
                            onCheckedChange = { BrowserExtensionManager.toggleExtension(ext.id, it) }
                        )
                    }
                }
            }
        }
    }
}
