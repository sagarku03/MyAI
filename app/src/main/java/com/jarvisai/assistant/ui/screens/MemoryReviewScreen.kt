package com.jarvisai.assistant.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvisGreen
import com.example.ui.theme.JarvisSky
import com.jarvisai.assistant.data.local.MemoryEntity
import com.jarvisai.assistant.model.MemoryImportance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryReviewScreen(
    onBack: () -> Unit,
    memories: List<MemoryEntity>,
    onUpdateImportance: (Long, MemoryImportance) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onPurgeExpired: () -> Unit,
    onExportText: (onReady: (String) -> Unit) -> Unit,
    onExportJson: (onReady: (String) -> Unit) -> Unit
) {
    BackHandler { onBack() }

    val clipboardManager = LocalClipboardManager.current
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    var searchQuery by remember { mutableStateOf("") }
    var exportToast by remember { mutableStateOf<String?>(null) }

    val filtered = memories.filter {
        val q = searchQuery.trim().lowercase()
        q.isEmpty() || it.title.lowercase().contains(q) || it.content.lowercase().contains(q) || it.category.lowercase().contains(q)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("memory_review_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B).copy(alpha = 0.8f))
                            .testTag("memory_review_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "AI MEMORY TIMELINE",
                            color = JarvisSky,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Knowledge & Fact Audit",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = {
                            onExportText { text ->
                                clipboardManager.setText(AnnotatedString(text))
                                exportToast = "Copied All Memories (Text) to Clipboard"
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Export Text", tint = JarvisGreen, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onPurgeExpired,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = "Purge Expired", tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Export Feedback Toast
        if (exportToast != null) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    color = JarvisGreen.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, JarvisGreen)
                ) {
                    Text(
                        text = exportToast.orEmpty(),
                        color = JarvisGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Search Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search facts, preferences, dates...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("memory_review_search_input"),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisSky,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.7f),
                    unfocusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.5f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        // Memory List with Importance Badges
        if (filtered.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No memories matched your query.",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(filtered) { memory ->
                val importanceColor = when (memory.importance) {
                    MemoryImportance.CRITICAL.name -> Color(0xFFEF4444)
                    MemoryImportance.IMPORTANT.name -> Color(0xFFA855F7)
                    MemoryImportance.NORMAL.name -> JarvisSky
                    else -> Color(0xFF64748B)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp)),
                    color = Color(0xFF0F172A).copy(alpha = 0.8f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = memory.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )

                            Surface(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                                color = importanceColor.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, importanceColor)
                            ) {
                                Text(
                                    text = memory.importance,
                                    color = importanceColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = memory.content,
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${memory.category} • ${dateFormat.format(Date(memory.createdAt))}",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )

                            Row {
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString("${memory.title}: ${memory.content}"))
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                }

                                IconButton(
                                    onClick = { onDeleteMemory(memory.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
