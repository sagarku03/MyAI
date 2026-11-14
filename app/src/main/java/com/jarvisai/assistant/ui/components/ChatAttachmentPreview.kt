package com.jarvisai.assistant.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jarvisai.assistant.data.media.AttachmentProcessor
import com.jarvisai.assistant.model.AttachmentKind
import com.jarvisai.assistant.model.PersistedChatAttachment
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatBubbleAttachmentsSection(
    attachments: List<PersistedChatAttachment>,
    onAttachmentClick: (PersistedChatAttachment) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return
    val context = LocalContext.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val mediaItems = attachments.filter { it.kind == AttachmentKind.IMAGE || it.kind == AttachmentKind.VIDEO }
        val fileItems = attachments.filter { it.kind == AttachmentKind.DOCUMENT || it.kind == AttachmentKind.FILE }

        if (mediaItems.isNotEmpty()) {
            val singleItem = mediaItems.size == 1
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                mediaItems.forEach { item ->
                    if (item.kind == AttachmentKind.IMAGE) {
                        val imageModel: Any? = when {
                            !item.localPath.isNullOrBlank() && File(item.localPath).exists() -> File(item.localPath)
                            !item.fallbackUri.isNullOrBlank() -> Uri.parse(item.fallbackUri)
                            else -> null
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
                            modifier = Modifier
                                .then(
                                    if (singleItem) {
                                        Modifier
                                            .widthIn(min = 160.dp, max = 265.dp)
                                            .heightIn(min = 120.dp, max = 230.dp)
                                    } else {
                                        Modifier.size(124.dp)
                                    }
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onAttachmentClick(item) }
                        ) {
                            Box {
                                if (imageModel != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(imageModel)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = item.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = item.name,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                                            )
                                        )
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "${item.name} • ${AttachmentProcessor.formatBytes(item.sizeBytes)}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else {
                        // VIDEO preview card with extracted frame thumbnail + play badge
                        val thumbModel: Any? = when {
                            !item.thumbnailPath.isNullOrBlank() && File(item.thumbnailPath).exists() -> File(item.thumbnailPath)
                            else -> null
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.55f)),
                            modifier = Modifier
                                .then(
                                    if (singleItem) {
                                        Modifier
                                            .widthIn(min = 180.dp, max = 265.dp)
                                            .height(160.dp)
                                    } else {
                                        Modifier
                                            .width(136.dp)
                                            .height(124.dp)
                                    }
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onAttachmentClick(item) }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (thumbModel != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(thumbModel)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = item.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF1E1B4B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VideoFile,
                                            contentDescription = item.name,
                                            tint = Color(0xFF818CF8).copy(alpha = 0.45f),
                                            modifier = Modifier.size(44.dp)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.62f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play video",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                                            )
                                        )
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "🎬 ${item.name} • ${AttachmentProcessor.formatBytes(item.sizeBytes)}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        fileItems.forEach { fileItem ->
            val icon = when {
                fileItem.mimeType == "application/pdf" || fileItem.name.endsWith(".pdf", ignoreCase = true) ->
                    Icons.Default.PictureAsPdf
                fileItem.kind == AttachmentKind.DOCUMENT -> Icons.Default.Description
                else -> Icons.Default.InsertDriveFile
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onAttachmentClick(fileItem) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = fileItem.name,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileItem.name,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${fileItem.mimeType} • ${AttachmentProcessor.formatBytes(fileItem.sizeBytes)}",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Open file",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FullscreenImagePreviewDialog(
    attachment: PersistedChatAttachment,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val model: Any? = when {
        !attachment.localPath.isNullOrBlank() && File(attachment.localPath).exists() -> File(attachment.localPath)
        !attachment.fallbackUri.isNullOrBlank() -> Uri.parse(attachment.fallbackUri)
        else -> null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss)
        ) {
            if (model != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(model)
                        .crossfade(true)
                        .build(),
                    contentDescription = attachment.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = attachment.name,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = AttachmentProcessor.formatBytes(attachment.sizeBytes),
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { openAttachmentExternally(context, attachment) }) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open externally",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close preview",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

fun openAttachmentExternally(context: Context, attachment: PersistedChatAttachment) {
    try {
        val localFile = attachment.localPath?.let { File(it) }?.takeIf { it.exists() }
        val uri = if (localFile != null) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
        } else if (!attachment.fallbackUri.isNullOrBlank()) {
            Uri.parse(attachment.fallbackUri)
        } else {
            Toast.makeText(context, "Attachment file not found on device.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachment.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open ${attachment.name}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open file: ${e.localizedMessage ?: "No viewer app"}", Toast.LENGTH_SHORT).show()
    }
}
