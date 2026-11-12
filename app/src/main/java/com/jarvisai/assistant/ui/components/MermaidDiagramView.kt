package com.jarvisai.assistant.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidDiagramView(
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Diagram, 1 = Code
    var isFullscreen by remember { mutableStateOf(false) }

    val safeCode = remember(code) {
        code.trim()
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")
    }

    val html = remember(safeCode) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
            <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
            <style>
                body { margin: 0; padding: 16px; background: #070B14; display: flex; justify-content: center; align-items: center; min-height: 100vh; }
                .mermaid { color: #E2E8F0; font-family: sans-serif; }
            </style>
        </head>
        <body>
            <div class="mermaid">
                $safeCode
            </div>
            <script>
                mermaid.initialize({ startOnLoad: true, theme: 'dark' });
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
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
                Text(
                    text = "MERMAID DIAGRAM",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = Color(0xFF00E5FF),
                    fontFamily = FontFamily.Monospace
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // [Diagram | Code] Tabs
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B))
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTab == 0) Color(0xFF00E5FF) else Color.Transparent)
                                .clickable { selectedTab = 0 }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("Diagram", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selectedTab == 0) Color.Black else Color(0xFF94A3B8))
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTab == 1) Color(0xFF00E5FF) else Color.Transparent)
                                .clickable { selectedTab = 1 }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("Code", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selectedTab == 1) Color.Black else Color(0xFF94A3B8))
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { isFullscreen = true },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Outlined.OpenInFull, contentDescription = "Fullscreen", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                    }

                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Mermaid", code))
                            Toast.makeText(context, "Copied diagram code", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Body
            if (selectedTab == 0) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            webViewClient = WebViewClient()
                            loadDataWithBaseURL("https://mermaid.local", html, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(text = code, color = Color(0xFFE2E8F0), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF070B14)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mermaid Diagram (Zoomable)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IconButton(onClick = { isFullscreen = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.builtInZoomControls = true
                                loadDataWithBaseURL("https://mermaid.local", html, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
