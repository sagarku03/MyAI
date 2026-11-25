package com.jarvisai.assistant.ui.components

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jarvisai.assistant.ide.ActiveCodeEditorRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Resolves a markdown image source (`file://...`, absolute path, `~/...`, relative workspace path,
 * `data:image/...`, or `http(s)://...`) into a Coil-loadable model (`File`, `ByteArray`, or `String` URL).
 */
fun resolveChatImageModel(context: Context, rawSrc: String): Any? {
    val trimmed = rawSrc.trim().removeSurrounding("<", ">")
    if (trimmed.isBlank()) return null

    if (trimmed.startsWith("data:image/")) {
        val b64 = trimmed.substringAfter("base64,", "")
        if (b64.isNotBlank()) {
            return runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: trimmed
        }
    }

    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed
    }

    val unprefixed = trimmed.removePrefix("file://").substringBefore('?')
    val candidates = buildList {
        if (unprefixed.startsWith("~/")) {
            val rel = unprefixed.removePrefix("~/")
            add(File(context.filesDir, rel))
            add(File(context.filesDir, "home/$rel"))
            add(File(context.filesDir, "projects/$rel"))
        } else if (unprefixed.startsWith("/")) {
            add(File(unprefixed))
        } else {
            val activeProject = ActiveCodeEditorRegistry.currentProjectPath
            if (!activeProject.isNullOrBlank()) {
                add(File(activeProject, unprefixed))
            }
            add(File(context.filesDir, unprefixed))
            add(File(context.filesDir, "browser_captures/$unprefixed"))
            add(File(context.filesDir, "projects/$unprefixed"))
        }
    }

    for (candidate in candidates) {
        if (candidate.exists() && candidate.isFile && candidate.canRead() && candidate.length() > 0L) {
            return candidate
        }
    }

    return trimmed
}

/**
 * Saves an inline chat image or screenshot to the device's Downloads/Pictures gallery using
 * scoped MediaStore APIs on Android 10+ (zero broad storage permissions required).
 */
suspend fun saveChatImageToGallery(
    context: Context,
    rawSrc: String,
    title: String
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val model = resolveChatImageModel(context, rawSrc)
        val bytes: ByteArray = when (model) {
            is ByteArray -> model
            is File -> model.readBytes()
            is String -> {
                if (model.startsWith("http://") || model.startsWith("https://")) {
                    val req = Request.Builder().url(model).get().build()
                    OkHttpClient().newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        resp.body?.bytes() ?: error("Empty response")
                    }
                } else {
                    val f = File(model.removePrefix("file://"))
                    if (f.exists() && f.canRead()) f.readBytes() else error("Image file unavailable")
                }
            }
            else -> error("Unsupported image source")
        }

        val lower = rawSrc.lowercase(Locale.US)
        val (mimeType, ext) = when {
            lower.contains(".png") || (bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) ->
                "image/png" to ".png"
            lower.contains(".webp") -> "image/webp" to ".webp"
            lower.contains(".gif") -> "image/gif" to ".gif"
            else -> "image/jpeg" to ".jpg"
        }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeTitle = title.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_").take(28).trimEnd('_')
        val fileName = if (safeTitle.isNotBlank()) "Agentra_${safeTitle}_$stamp$ext" else "Agentra_$stamp$ext"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Agentra")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create MediaStore entry")
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: error("Could not write image stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Saved to Pictures/Agentra/$fileName"
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Agentra").apply { mkdirs() }
            val outFile = File(dir, fileName)
            outFile.writeBytes(bytes)
            "Saved to ${outFile.name}"
        }
    }
}

/**
 * AntiGem-style fullscreen modal image viewer with smooth pinch-to-zoom, panning,
 * double-tap zoom toggle, reset zoom, and one-tap Download / Save to Gallery.
 */
@Composable
fun FullScreenImageDialog(
    imageSource: String,
    title: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val model = remember(imageSource) { resolveChatImageModel(context, imageSource) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF5050608))
                .testTag("fullscreen_image_dialog")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.85f, 5f)
                            if (scale <= 1f) {
                                offset = Offset.Zero
                            } else {
                                offset = Offset(offset.x + pan.x, offset.y + pan.y)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (model != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(model)
                            .crossfade(true)
                            .build(),
                        contentDescription = title.ifBlank { "Image preview" },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }
            }

            // Top bar: Close, Title, Reset Zoom, Download
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .testTag("fullscreen_image_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close image viewer",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title.ifBlank { "Image Viewer" },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (scale != 1f) {
                    IconButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset zoom",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            val res = saveChatImageToGallery(context, imageSource, title)
                            res.fold(
                                onSuccess = { msg ->
                                    snackbarHostState.showSnackbar(msg)
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                onFailure = { err ->
                                    val msg = "Download failed: ${err.message ?: "unknown"}"
                                    snackbarHostState.showSnackbar(msg)
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .testTag("fullscreen_image_download_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download image to gallery",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
            )
        }
    }
}
