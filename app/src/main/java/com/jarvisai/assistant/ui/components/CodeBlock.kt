package com.jarvisai.assistant.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

fun isPreviewableCode(code: String, language: String): Boolean {
    val lang = language.lowercase().trim()
    if (lang in listOf("html", "htm", "svg", "web", "xhtml")) return true
    val trimmed = code.trim()
    return trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
           trimmed.startsWith("<html", ignoreCase = true) ||
           trimmed.startsWith("<svg", ignoreCase = true)
}

fun isPreviewableHtmlOrSvg(code: String, language: String): Boolean = isPreviewableCode(code, language)

fun wrapHtmlArtifact(rawCode: String): String {
    val trimmed = rawCode.trim()
    if (trimmed.startsWith("<svg", ignoreCase = true)) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
                <style>
                    body { margin:0; padding:16px; background:#0F172A; display:flex; align-items:center; justify-content:center; min-height:100vh; }
                    svg { max-width: 100%; height: auto; }
                </style>
            </head>
            <body>$trimmed</body>
            </html>
        """.trimIndent()
    }
    return trimmed
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CodeBlock(
    code: String,
    language: String = "MARKDOWN",
    onPreviewArtifact: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lines = remember(code) { code.lines() }
    val lineCount = lines.size
    val isLong = lineCount > 12

    val previewable = remember(code, language) { isPreviewableCode(code, language) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Code, 1 = Preview
    var reloadKey by remember { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(!isLong) }

    val displayCode = remember(code, isExpanded) {
        if (!isLong || isExpanded) code else lines.take(12).joinToString("\n")
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF070B14),
        border = BorderStroke(0.8.dp, Color(0xFF1E293B))
    ) {
        Column {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Language + Line Count
                Text(
                    text = "${language.uppercase().ifBlank { "CODE" }} • $lineCount lines",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF),
                    fontFamily = FontFamily.Monospace
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // [Code | Preview] Interactive Tab Switcher
                    if (previewable) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1E293B))
                                .padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Code Tab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selectedTab == 0) Color(0xFF00E5FF) else Color.Transparent)
                                    .clickable { selectedTab = 0 }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Code,
                                        contentDescription = null,
                                        tint = if (selectedTab == 0) Color.Black else Color(0xFF94A3B8),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Code",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedTab == 0) Color.Black else Color(0xFF94A3B8)
                                    )
                                }
                            }

                            // Preview Tab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selectedTab == 1) Color(0xFF00E5FF) else Color.Transparent)
                                    .clickable {
                                        selectedTab = 1
                                        onPreviewArtifact?.invoke(code)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (selectedTab == 1) Color.Black else Color(0xFF94A3B8),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Preview",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedTab == 1) Color.Black else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }

                        if (selectedTab == 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { reloadKey++ },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Fullscreen Button
                    IconButton(
                        onClick = { isFullscreen = true },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Outlined.OpenInFull, contentDescription = "Fullscreen", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Copy Button
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Code", code))
                            Toast.makeText(context, "Copied code snippet", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Body Area
            if (previewable && selectedTab == 1) {
                // Live Interactive Artifact Runner
                key(reloadKey) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                webViewClient = WebViewClient()
                                webChromeClient = WebChromeClient()
                                loadDataWithBaseURL("https://local.artifact", wrapHtmlArtifact(code), "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp, max = 500.dp)
                    )
                }
            } else {
                // Monospaced Code Text
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = displayCode,
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }

                // Expand / Collapse long code
                if (isLong) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A).copy(alpha = 0.7f))
                            .clickable { isExpanded = !isExpanded }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isExpanded) "Collapse code block ▲" else "Show all $lineCount lines (${lineCount - 12} more) ▼",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }

    // Fullscreen Artifact Dialog
    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0B132B)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Fullscreen Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (previewable && selectedTab == 1) "Live Artifact Preview" else "${language.uppercase()} Snippet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        IconButton(onClick = { isFullscreen = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    if (previewable && selectedTab == 1) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    loadDataWithBaseURL("https://local.artifact", wrapHtmlArtifact(code), "text/html", "UTF-8", null)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = code,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
