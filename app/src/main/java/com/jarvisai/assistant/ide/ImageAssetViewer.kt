package com.jarvisai.assistant.ide

import android.webkit.WebView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlin.math.roundToInt

/**
 * Phase 4: Interactive Image & Vector SVG Asset Viewer (`ImageAssetViewer`).
 * Features pinch-to-zoom (0.5x to 5.0x), pan gestures, alpha-channel checkerboard grid canvas,
 * file metadata header, and native SVG + raster (PNG, WebP, JPG, GIF, BMP) rendering.
 */
@Composable
fun ImageAssetViewer(
    file: File,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var scale by remember(file.absolutePath) { mutableFloatStateOf(1f) }
    var offsetX by remember(file.absolutePath) { mutableFloatStateOf(0f) }
    var offsetY by remember(file.absolutePath) { mutableFloatStateOf(0f) }
    var showCheckerboard by remember { mutableStateOf(true) }

    val isSvg = remember(file.name) { file.extension.equals("svg", ignoreCase = true) }
    val sizeKb = remember(file) { (file.length() / 1024.0 * 10.0).roundToInt() / 10.0 }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("image_asset_viewer"),
        color = Color(0xFF0A0F1D)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top asset toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111C35))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        color = Color(0xFFF0F6FC),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${file.extension.uppercase()} • ${sizeKb} KB • ${(scale * 100).roundToInt()}%",
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { scale = (scale - 0.25f).coerceAtLeast(0.5f) },
                        modifier = Modifier.testTag("asset_zoom_out_button")
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color(0xFFE2E8F0))
                    }
                    IconButton(
                        onClick = { scale = (scale + 0.25f).coerceAtMost(5.0f) },
                        modifier = Modifier.testTag("asset_zoom_in_button")
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color(0xFFE2E8F0))
                    }
                    IconButton(
                        onClick = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        modifier = Modifier.testTag("asset_reset_zoom_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset View", tint = Color(0xFF38BDF8))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("asset_viewer_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Asset Viewer", tint = Color(0xFFF85149))
                    }
                }
            }

            // Interactive viewport with alpha checkerboard grid
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(file.absolutePath) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(0.5f, 5.0f)
                            scale = newScale
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (showCheckerboard) {
                    CheckerboardCanvas(modifier = Modifier.fillMaxSize())
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSvg) {
                        val svgContent = remember(file.absolutePath) {
                            runCatching { file.readText() }.getOrDefault("")
                        }
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    settings.javaScriptEnabled = false
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    val html = """
                                        <!DOCTYPE html>
                                        <html>
                                        <head>
                                          <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                          <style>
                                            html, body { margin:0; padding:0; width:100%; height:100%; display:flex; align-items:center; justify-content:center; background:transparent; }
                                            svg { max-width:95vw; max-height:85vh; width:auto; height:auto; }
                                          </style>
                                        </head>
                                        <body>$svgContent</body>
                                        </html>
                                    """.trimIndent()
                                    loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(file)
                                .crossfade(true)
                                .build(),
                            contentDescription = file.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Bottom pill to toggle checkerboard background
                Surface(
                    onClick = { showCheckerboard = !showCheckerboard },
                    color = Color(0xFF111C35).copy(alpha = 0.88f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = if (showCheckerboard) "Grid: ON" else "Grid: OFF",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckerboardCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cellPx = 16.dp.toPx()
        val cols = (size.width / cellPx).toInt() + 1
        val rows = (size.height / cellPx).toInt() + 1
        val darkCell = Color(0xFF141B2D)
        val lightCell = Color(0xFF1E293B)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val color = if ((r + c) % 2 == 0) darkCell else lightCell
                drawRect(
                    color = color,
                    topLeft = Offset(c * cellPx, r * cellPx),
                    size = Size(cellPx, cellPx)
                )
            }
        }
    }
}
