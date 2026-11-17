package com.jarvisai.assistant.ui.files

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

internal val AntiGemBg = Color(0xFF0D0E13)
internal val AntiGemSheetBg = Color(0xFF14161F)
internal val AntiGemCardBg = Color(0xFF1B1E2B)
internal val AntiGemIconBoxBg = Color(0xFF1A1E2E)
internal val AntiGemAccent = Color(0xFF8AB4F8)
internal val AntiGemTextPrimary = Color(0xFFF1F5F9)
internal val AntiGemTextSecondary = Color(0xFF8E95A5)
internal val AntiGemDanger = Color(0xFFEF4444)
internal val AntiGemDangerBg = Color(0xFF2B161B)

@Composable
fun FileOrFolderLeadingThumb(
    file: File,
    isDirectory: Boolean,
    isProjectDir: Boolean = false,
    sizeDp: Int = 44
) {
    val thumb = if (!isDirectory) FileManagerOperations.rememberFileThumbnail(file) else null
    val ext = remember(file.name) { file.extension.lowercase(Locale.ROOT) }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(AntiGemIconBoxBg),
        contentAlignment = Alignment.Center
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val icon: ImageVector = when {
                isDirectory -> Icons.Outlined.Folder
                ext in FileManagerOperations.IMAGE_EXTENSIONS -> Icons.Outlined.Image
                ext in FileManagerOperations.VIDEO_EXTENSIONS -> Icons.Outlined.VideoFile
                ext in FileManagerOperations.AUDIO_EXTENSIONS -> Icons.Outlined.AudioFile
                ext in FileManagerOperations.ARCHIVE_EXTENSIONS -> Icons.Outlined.FolderZip
                ext in listOf("kt", "kts", "java", "py", "js", "ts", "tsx", "html", "css", "c", "cpp", "rs", "go", "sh") ->
                    Icons.Default.Code
                ext in FileManagerOperations.CODE_EXTENSIONS -> Icons.Outlined.Description
                else -> Icons.AutoMirrored.Outlined.InsertDriveFile
            }
            val tint = when {
                isDirectory -> AntiGemAccent
                ext in FileManagerOperations.ARCHIVE_EXTENSIONS -> Color(0xFFF59E0B)
                else -> Color(0xFF94A3B8)
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size((sizeDp * 0.52f).dp)
            )
        }

        if (isDirectory && isProjectDir) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF28324E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "Project folder",
                    tint = AntiGemAccent,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesBottomSheet(
    places: List<PlaceEntry>,
    currentDirectory: File,
    onSelectPlace: (PlaceEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val mainPlaces = remember(places) {
        places.filter { it.kind != PlaceKind.PROJECT_ITEM && it.kind != PlaceKind.RECENT_ITEM }
    }
    val projectPlaces = remember(places) {
        places.filter { it.kind == PlaceKind.PROJECT_ITEM }
    }
    val recentPlaces = remember(places) {
        places.filter { it.kind == PlaceKind.RECENT_ITEM }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AntiGemSheetBg,
        contentColor = AntiGemTextPrimary
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(
                    text = "Places",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AntiGemTextPrimary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }

            items(mainPlaces, key = { "${it.kind}_${it.directory.absolutePath}" }) { place ->
                val isSelected = currentDirectory.absolutePath.startsWith(place.directory.absolutePath) &&
                    (place.kind == PlaceKind.HOME || currentDirectory.absolutePath == place.directory.absolutePath)
                PlaceItemRow(
                    place = place,
                    isSelected = isSelected,
                    onClick = { onSelectPlace(place) }
                )
            }

            if (projectPlaces.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "PROJECTS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = AntiGemTextSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                items(projectPlaces, key = { "proj_${it.directory.absolutePath}" }) { place ->
                    PlaceItemRow(
                        place = place,
                        isSelected = currentDirectory.absolutePath == place.directory.absolutePath,
                        onClick = { onSelectPlace(place) }
                    )
                }
            }

            if (recentPlaces.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "RECENT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = AntiGemTextSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                items(recentPlaces, key = { "rec_${it.directory.absolutePath}" }) { place ->
                    PlaceItemRow(
                        place = place,
                        isSelected = currentDirectory.absolutePath == place.directory.absolutePath,
                        onClick = { onSelectPlace(place) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun PlaceItemRow(
    place: PlaceEntry,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (place.kind) {
        PlaceKind.HOME -> Icons.Outlined.Home
        PlaceKind.PROJECTS -> Icons.Outlined.AccountTree
        PlaceKind.INTERNAL_STORAGE -> Icons.Outlined.Smartphone
        PlaceKind.DOWNLOADS -> Icons.Outlined.Download
        PlaceKind.DOCUMENTS -> Icons.Outlined.Description
        PlaceKind.CAMERA -> Icons.Outlined.PhotoCamera
        PlaceKind.PICTURES -> Icons.Outlined.Image
        PlaceKind.MOVIES -> Icons.Outlined.Movie
        PlaceKind.MUSIC -> Icons.Outlined.MusicNote
        PlaceKind.PROJECT_ITEM -> Icons.Outlined.Folder
        PlaceKind.RECENT_ITEM -> Icons.Default.History
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFF22283A) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) Color(0xFF2C3550) else AntiGemIconBoxBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = place.title,
                    tint = AntiGemAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AntiGemTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (place.freeBytes != null && place.totalBytes != null && place.totalBytes > 0L) {
                    Spacer(modifier = Modifier.height(5.dp))
                    val usedFraction = ((place.totalBytes - place.freeBytes).toFloat() / place.totalBytes.toFloat())
                        .coerceIn(0.05f, 1f)
                    LinearProgressIndicator(
                        progress = { usedFraction },
                        color = AntiGemAccent,
                        trackColor = Color(0xFF2B3040),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = place.subtitle,
                    fontSize = 12.sp,
                    color = AntiGemTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateItemBottomSheet(
    onCreateFolder: () -> Unit,
    onCreateFile: () -> Unit,
    onCreateProjectFromTemplate: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AntiGemSheetBg,
        contentColor = AntiGemTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Create",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = AntiGemTextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            CreateOptionRow(
                icon = Icons.Outlined.CreateNewFolder,
                title = "Folder",
                subtitle = null,
                onClick = {
                    onDismiss()
                    onCreateFolder()
                }
            )
            CreateOptionRow(
                icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
                title = "File",
                subtitle = "Empty file, any extension",
                onClick = {
                    onDismiss()
                    onCreateFile()
                }
            )
            CreateOptionRow(
                icon = Icons.Outlined.RocketLaunch,
                title = "Project from template",
                subtitle = "Python, Node.js, Web, Kotlin, C++...",
                onClick = {
                    onDismiss()
                    onCreateProjectFromTemplate()
                }
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun CreateOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = AntiGemAccent,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column {
            Text(
                text = title,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
                color = AntiGemTextPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    color = AntiGemTextSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemActionBottomSheet(
    file: File,
    showHidden: Boolean,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpenInIde: () -> Unit,
    onCompressToZip: () -> Unit,
    onExtractArchive: (() -> Unit)? = null,
    onCopyTo: () -> Unit,
    onMoveTo: () -> Unit,
    onDuplicate: () -> Unit,
    onCopyPath: () -> Unit,
    onDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDir = remember(file) { FileManagerOperations.isEffectiveDirectory(file) }
    val subtitle = remember(file, showHidden) {
        val dateStr = FileManagerOperations.formatTimestamp(file.lastModified())
        if (isDir) {
            "${FileManagerOperations.getDirectoryItemCountLabel(file, showHidden)} • $dateStr"
        } else {
            "${FileManagerOperations.formatBytes(file.length())} • $dateStr"
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AntiGemSheetBg,
        contentColor = AntiGemTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FileOrFolderLeadingThumb(
                    file = file,
                    isDirectory = isDir,
                    isProjectDir = isDir && FileManagerOperations.isProjectDirectory(file),
                    sizeDp = 44
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AntiGemTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.5.sp,
                        color = AntiGemTextSecondary,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5 Quick Action Square Tiles (Open, Copy, Cut, Rename, Delete)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionTile(
                    icon = if (isDir) Icons.Outlined.FolderOpen else Icons.AutoMirrored.Outlined.OpenInNew,
                    label = "Open",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismiss()
                        onOpen()
                    }
                )
                QuickActionTile(
                    icon = Icons.Outlined.ContentCopy,
                    label = "Copy",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismiss()
                        onCopy()
                    }
                )
                QuickActionTile(
                    icon = Icons.Outlined.ContentCut,
                    label = "Cut",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismiss()
                        onCut()
                    }
                )
                QuickActionTile(
                    icon = Icons.Outlined.Edit,
                    label = "Rename",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismiss()
                        onRename()
                    }
                )
                QuickActionTile(
                    icon = Icons.Outlined.Delete,
                    label = "Delete",
                    isDestructive = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismiss()
                        onDelete()
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF232736))
            Spacer(modifier = Modifier.height(4.dp))

            // Action List Rows
            SheetActionRow(
                icon = Icons.Default.Code,
                title = if (isDir) "Open folder in IDE" else "Open in IDE",
                subtitle = if (isDir) "Use it as the active project" else "Edit in Sora Code IDE",
                onClick = {
                    onDismiss()
                    onOpenInIde()
                }
            )
            SheetActionRow(
                icon = Icons.Outlined.Archive,
                title = "Compress to ZIP",
                onClick = {
                    onDismiss()
                    onCompressToZip()
                }
            )
            if (onExtractArchive != null) {
                SheetActionRow(
                    icon = Icons.Outlined.Unarchive,
                    title = "Extract archive here",
                    onClick = {
                        onDismiss()
                        onExtractArchive()
                    }
                )
            }
            SheetActionRow(
                icon = Icons.Outlined.FolderCopy,
                title = "Copy to...",
                onClick = {
                    onDismiss()
                    onCopyTo()
                }
            )
            SheetActionRow(
                icon = Icons.Outlined.DriveFileMove,
                title = "Move to...",
                onClick = {
                    onDismiss()
                    onMoveTo()
                }
            )
            SheetActionRow(
                icon = Icons.Outlined.LibraryAdd,
                title = "Duplicate",
                onClick = {
                    onDismiss()
                    onDuplicate()
                }
            )
            SheetActionRow(
                icon = Icons.Outlined.ContentCopy,
                title = "Copy path",
                subtitle = file.absolutePath,
                onClick = {
                    onDismiss()
                    onCopyPath()
                }
            )
            SheetActionRow(
                icon = Icons.Outlined.Info,
                title = "Details",
                onClick = {
                    onDismiss()
                    onDetails()
                }
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (isDestructive) AntiGemDangerBg else AntiGemCardBg
    val border = if (isDestructive) AntiGemDanger.copy(alpha = 0.45f) else Color(0xFF292E40)
    val contentColor = if (isDestructive) Color(0xFFFCA5A5) else AntiGemTextPrimary

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color(0xFFD1D5DB),
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = AntiGemTextPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = AntiGemTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderOrFileDetailsSheet(
    file: File,
    onCopyPath: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isDir = remember(file) { FileManagerOperations.isEffectiveDirectory(file) }
    var calculatedStats by remember(file) { mutableStateOf<Pair<Long, Int>?>(null) }

    LaunchedEffect(file) {
        calculatedStats = FileManagerOperations.calculateRecursiveStats(file)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AntiGemSheetBg,
        contentColor = AntiGemTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileOrFolderLeadingThumb(file = file, isDirectory = isDir, sizeDp = 48)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = file.name.ifBlank { file.absolutePath },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AntiGemTextPrimary
                    )
                    Text(
                        text = if (isDir) "Folder" else "${file.extension.uppercase(Locale.ROOT).ifBlank { "FILE" }} File",
                        fontSize = 13.sp,
                        color = AntiGemTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            DetailRow(
                label = "Location",
                value = file.parent ?: file.absolutePath,
                trailingCopy = { onCopyPath(file.absolutePath) }
            )
            DetailRow(
                label = "Size",
                value = calculatedStats?.let { (bytes, count) ->
                    if (isDir) "${FileManagerOperations.formatBytes(bytes)} ($count files)"
                    else FileManagerOperations.formatBytes(bytes)
                } ?: "Calculating..."
            )
            DetailRow(
                label = "Modified",
                value = FileManagerOperations.formatTimestamp(file.lastModified())
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    trailingCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = AntiGemTextSecondary,
            modifier = Modifier.width(88.dp)
        )
        Text(
            text = value,
            fontSize = 13.5.sp,
            color = AntiGemTextPrimary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (trailingCopy != null) {
            IconButton(onClick = trailingCopy, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy path",
                    tint = AntiGemAccent,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenWithBottomSheet(
    file: File,
    onViewBuiltIn: (Boolean) -> Unit,
    onOpenExternal: (Boolean) -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    var alwaysUse by remember { mutableStateOf(false) }
    val ext = remember(file.name) { file.extension.lowercase(Locale.ROOT) }
    val isImage = ext in FileManagerOperations.IMAGE_EXTENSIONS
    val isVideo = ext in FileManagerOperations.VIDEO_EXTENSIONS
    val isAudio = ext in FileManagerOperations.AUDIO_EXTENSIONS
    val supportsBuiltIn = isImage || isVideo || isAudio
    val subtitle = remember(file) {
        "${FileManagerOperations.formatBytes(file.length())} • ${FileManagerOperations.formatTimestamp(file.lastModified())}"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AntiGemSheetBg,
        contentColor = AntiGemTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.jarvis_app_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Open with",
                        fontSize = 12.sp,
                        color = AntiGemTextSecondary
                    )
                    Text(
                        text = file.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AntiGemTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = AntiGemTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (supportsBuiltIn) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E2438),
                    border = BorderStroke(1.dp, AntiGemAccent.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            onDismiss()
                            onViewBuiltIn(alwaysUse)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2B3554)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isImage -> Icons.Outlined.Image
                                    isAudio -> Icons.Outlined.AudioFile
                                    else -> Icons.Outlined.PlayCircle
                                },
                                contentDescription = null,
                                tint = AntiGemAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isImage) "View in Jarvis" else "Play in Jarvis",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AntiGemTextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF2D3B62)
                                ) {
                                    Text(
                                        text = "Recommended",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AntiGemAccent,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    isImage -> "Built-in viewer: zoom, save, share"
                                    isAudio -> "Built-in audio player: seek, speed, share"
                                    else -> "Built-in player: seek, speed, fullscreen, share"
                                },
                                fontSize = 12.5.sp,
                                color = AntiGemTextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Open in gallery / external app
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AntiGemCardBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        onOpenExternal(alwaysUse)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AntiGemIconBoxBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = Color(0xFFD1D5DB),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = when {
                                isImage -> "Open in gallery"
                                isVideo -> "Open in video player"
                                isAudio -> "Open in music player"
                                else -> "Open in external app"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AntiGemTextPrimary
                        )
                        Text(
                            text = "Pick any app installed on this phone",
                            fontSize = 12.5.sp,
                            color = AntiGemTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Share
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AntiGemCardBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        onShare()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AntiGemIconBoxBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color(0xFFD1D5DB),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Share",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AntiGemTextPrimary
                        )
                        Text(
                            text = "Send it to another app",
                            fontSize = 12.5.sp,
                            color = AntiGemTextSecondary
                        )
                    }
                }
            }

            if (ext.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { alwaysUse = !alwaysUse }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = alwaysUse,
                        onCheckedChange = { alwaysUse = it },
                        colors = CheckboxDefaults.colors(checkedColor = AntiGemAccent)
                    )
                    Text(
                        text = "Always open .$ext files this way",
                        fontSize = 13.5.sp,
                        color = AntiGemTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun BuiltInImageViewerDialog(
    file: File,
    onShare: () -> Unit,
    onSaveToDownloads: () -> Unit,
    onDismiss: () -> Unit
) {
    var fullBitmap by remember(file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(file.absolutePath) {
        fullBitmap = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sampleSize = 1
                while (bounds.outWidth / sampleSize > 2560 || bounds.outHeight / sampleSize > 2560) {
                    sampleSize *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090A0F))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.05f) {
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
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale
                        if (newScale > 1f) {
                            offset += pan
                        } else {
                            offset = Offset.Zero
                        }
                    }
                }
        ) {
            if (fullBitmap != null) {
                Image(
                    bitmap = fullBitmap!!,
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            } else {
                CircularProgressIndicator(
                    color = AntiGemAccent,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Top Floating Toolbar matching 01:02 in AntiGem video
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1C202E).copy(alpha = 0.9f),
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close viewer",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF1C202E).copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = file.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1C202E).copy(alpha = 0.9f),
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onShare)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share image",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1C202E).copy(alpha = 0.9f),
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onSaveToDownloads)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Save to Downloads",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMediaDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000L
    val hrs = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return if (hrs > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%d:%02d", mins, secs)
    }
}

@Composable
fun BuiltInVideoPlayerDialog(
    file: File,
    onShare: () -> Unit,
    onSaveToDownloads: () -> Unit,
    onOpenExternal: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val resolvedFile = remember(file.absolutePath) {
        runCatching { file.canonicalFile }.getOrDefault(file)
    }
    val ext = remember(file.name) { file.extension.lowercase(Locale.ROOT) }
    val isAudio = ext in FileManagerOperations.AUDIO_EXTENSIONS

    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var audioPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    var isPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var showHud by remember { mutableStateOf(true) }
    var speedIndex by remember { mutableIntStateOf(1) } // 1.0x default
    val speeds = remember { listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f) }

    fun applyVolumeAndSpeed(mp: MediaPlayer?) {
        mp ?: return
        runCatching {
            val vol = if (isMuted) 0f else 1f
            mp.setVolume(vol, vol)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val targetSpeed = speeds[speedIndex]
                val params = mp.playbackParams
                if (params.speed != targetSpeed) {
                    params.speed = targetSpeed
                    mp.playbackParams = params
                }
            }
        }
    }

    fun togglePlayPause() {
        if (isAudio) {
            val mp = audioPlayerRef ?: return
            if (mp.isPlaying) {
                mp.pause()
                isPlaying = false
            } else {
                mp.start()
                applyVolumeAndSpeed(mp)
                isPlaying = true
            }
        } else {
            val vv = videoViewRef ?: return
            if (vv.isPlaying) {
                vv.pause()
                isPlaying = false
            } else {
                vv.start()
                applyVolumeAndSpeed(mediaPlayerRef)
                isPlaying = true
            }
        }
    }

    fun seekToMs(target: Long) {
        val clamped = target.coerceIn(0L, durationMs.coerceAtLeast(1L)).toInt()
        positionMs = clamped.toLong()
        if (isAudio) {
            runCatching { audioPlayerRef?.seekTo(clamped) }
        } else {
            runCatching { videoViewRef?.seekTo(clamped) }
        }
    }

    // Audio player lifecycle when playing .mp3/.m4a/.wav/etc.
    if (isAudio) {
        DisposableEffect(resolvedFile.absolutePath) {
            val mp = MediaPlayer()
            audioPlayerRef = mp
            runCatching {
                mp.setDataSource(context, Uri.fromFile(resolvedFile))
                mp.setOnPreparedListener { prepared ->
                    isPrepared = true
                    durationMs = prepared.duration.toLong().coerceAtLeast(0L)
                    prepared.start()
                    isPlaying = true
                }
                mp.setOnCompletionListener {
                    isPlaying = false
                    positionMs = durationMs
                }
                mp.setOnErrorListener { _, what, extra ->
                    playbackError = "Cannot play audio (code $what/$extra)"
                    true
                }
                mp.prepareAsync()
            }.onFailure { err ->
                playbackError = err.localizedMessage ?: "Failed to load audio"
            }

            onDispose {
                runCatching {
                    if (mp.isPlaying) mp.stop()
                    mp.release()
                }
                audioPlayerRef = null
            }
        }
    } else {
        DisposableEffect(resolvedFile.absolutePath) {
            onDispose {
                runCatching { videoViewRef?.stopPlayback() }
                videoViewRef = null
                mediaPlayerRef = null
            }
        }
    }

    // Poll current playback position
    LaunchedEffect(isPrepared, isPlaying, isScrubbing) {
        while (isPrepared && !isScrubbing) {
            val current = if (isAudio) {
                runCatching { audioPlayerRef?.currentPosition?.toLong() ?: positionMs }.getOrDefault(positionMs)
            } else {
                runCatching { videoViewRef?.currentPosition?.toLong() ?: positionMs }.getOrDefault(positionMs)
            }
            positionMs = current.coerceAtLeast(0L)
            if (isAudio) {
                isPlaying = runCatching { audioPlayerRef?.isPlaying == true }.getOrDefault(isPlaying)
            } else {
                isPlaying = runCatching { videoViewRef?.isPlaying == true }.getOrDefault(isPlaying)
            }
            delay(250L)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF06070B))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { showHud = !showHud }
        ) {
            if (isAudio) {
                // Center Artwork Card for Audio Files
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color(0xFF1A1F33)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = AntiGemAccent,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = file.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = FileManagerOperations.formatBytes(file.length()),
                        fontSize = 13.sp,
                        color = AntiGemTextSecondary
                    )
                }
            } else {
                // Fullscreen Hardware-Accelerated Video Surface
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(Uri.fromFile(resolvedFile))
                            setOnPreparedListener { mp ->
                                mediaPlayerRef = mp
                                isPrepared = true
                                durationMs = mp.duration.toLong().coerceAtLeast(0L)
                                mp.isLooping = false
                                applyVolumeAndSpeed(mp)
                                start()
                                isPlaying = true
                            }
                            setOnCompletionListener {
                                isPlaying = false
                                positionMs = durationMs
                                showHud = true
                            }
                            setOnErrorListener { _, what, extra ->
                                playbackError = "Codec/playback error ($what/$extra)"
                                true
                            }
                            videoViewRef = this
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                )
            }

            if (!isPrepared && playbackError == null) {
                CircularProgressIndicator(
                    color = AntiGemAccent,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            playbackError?.let { errText ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = AntiGemSheetBg,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Unable to play inside Jarvis",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errText,
                            fontSize = 12.5.sp,
                            color = AntiGemTextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                onDismiss()
                                onOpenExternal()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AntiGemAccent)
                        ) {
                            Text("Open in external video player", color = Color(0xFF0B0C10), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Top Floating Toolbar (Always accessible, toggles smoothly with HUD)
            if (showHud || playbackError != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1C202E).copy(alpha = 0.9f),
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onDismiss)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close player",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xFF1C202E).copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = file.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1C202E).copy(alpha = 0.9f),
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onShare)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share media",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1C202E).copy(alpha = 0.9f),
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onSaveToDownloads)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Save to Downloads",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Player Scrubber & Transport Controls
            if ((showHud || isAudio) && playbackError == null) {
                Surface(
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = Color(0xFF12141E).copy(alpha = 0.92f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        val maxDur = durationMs.coerceAtLeast(1L)
                        Slider(
                            value = (positionMs.toFloat() / maxDur.toFloat()).coerceIn(0f, 1f),
                            onValueChange = { fraction ->
                                isScrubbing = true
                                positionMs = (fraction * maxDur).toLong()
                            },
                            onValueChangeFinished = {
                                seekToMs(positionMs)
                                isScrubbing = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = AntiGemAccent,
                                activeTrackColor = AntiGemAccent,
                                inactiveTrackColor = Color(0xFF2D3348)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatMediaDuration(positionMs),
                                fontSize = 12.sp,
                                color = AntiGemTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = formatMediaDuration(durationMs),
                                fontSize = 12.sp,
                                color = AntiGemTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Mute toggle
                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    applyVolumeAndSpeed(if (isAudio) audioPlayerRef else mediaPlayerRef)
                                }
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = Color.White
                                )
                            }

                            // Center transport (-10s, Play/Pause, +10s)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IconButton(onClick = { seekToMs(positionMs - 10_000L) }) {
                                    Icon(
                                        imageVector = Icons.Default.Replay10,
                                        contentDescription = "Rewind 10 seconds",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = AntiGemAccent,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .clickable { togglePlayPause() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = Color(0xFF0B0C10),
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }

                                IconButton(onClick = { seekToMs(positionMs + 10_000L) }) {
                                    Icon(
                                        imageVector = Icons.Default.Forward10,
                                        contentDescription = "Forward 10 seconds",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Playback Speed chip
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AntiGemCardBg,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        speedIndex = (speedIndex + 1) % speeds.size
                                        applyVolumeAndSpeed(if (isAudio) audioPlayerRef else mediaPlayerRef)
                                    }
                            ) {
                                val spVal = speeds[speedIndex]
                                val label = if (spVal == spVal.toInt().toFloat()) "${spVal.toInt()}x" else "${spVal}x"
                                Text(
                                    text = label,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AntiGemAccent,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortAndViewBottomSheet(
    sortField: FileSortField,
    sortOrder: FileSortOrder,
    isGridView: Boolean,
    showHiddenFiles: Boolean,
    onSelectSortField: (FileSortField) -> Unit,
    onSelectSortOrder: (FileSortOrder) -> Unit,
    onToggleGridView: (Boolean) -> Unit,
    onToggleShowHidden: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AntiGemSheetBg,
        contentColor = AntiGemTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Sort & view",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = AntiGemTextPrimary,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Text(
                text = "VIEW MODE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AntiGemTextSecondary,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (!isGridView) Color(0xFF252D44) else AntiGemCardBg,
                    border = BorderStroke(1.dp, if (!isGridView) AntiGemAccent else Color.Transparent),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleGridView(false) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ViewList, contentDescription = null, tint = AntiGemTextPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("List", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AntiGemTextPrimary)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isGridView) Color(0xFF252D44) else AntiGemCardBg,
                    border = BorderStroke(1.dp, if (isGridView) AntiGemAccent else Color.Transparent),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleGridView(true) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.GridView, contentDescription = null, tint = AntiGemTextPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grid", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AntiGemTextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "SORT BY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AntiGemTextSecondary,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            FileSortField.entries.forEach { field ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectSortField(field) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = sortField == field,
                        onClick = { onSelectSortField(field) },
                        colors = RadioButtonDefaults.colors(selectedColor = AntiGemAccent)
                    )
                    Text(
                        text = field.label,
                        fontSize = 14.5.sp,
                        color = AntiGemTextPrimary
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF232736), modifier = Modifier.padding(vertical = 6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FileSortOrder.entries.forEach { order ->
                    val selected = sortOrder == order
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Color(0xFF252D44) else AntiGemCardBg,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectSortOrder(order) }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = order.label,
                                fontSize = 13.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) AntiGemAccent else AntiGemTextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleShowHidden(!showHiddenFiles) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = showHiddenFiles,
                    onCheckedChange = { onToggleShowHidden(it) },
                    colors = CheckboxDefaults.colors(checkedColor = AntiGemAccent)
                )
                Text("Show hidden files (dotfiles)", fontSize = 14.sp, color = AntiGemTextPrimary)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun ProjectFromTemplateDialog(
    onConfirmCreate: (ProjectTemplate, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val templates = FileManagerOperations.PROJECT_TEMPLATES
    var selectedTemplate by remember { mutableStateOf(templates.first()) }
    var folderName by remember { mutableStateOf(templates.first().defaultFolderName) }
    var openInIdeAfter by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AntiGemSheetBg,
        title = {
            Text(
                text = "Project from template",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AntiGemTextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                templates.forEach { tmpl ->
                    val isSelected = selectedTemplate.id == tmpl.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF232B42) else AntiGemCardBg,
                        border = BorderStroke(1.dp, if (isSelected) AntiGemAccent else Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedTemplate = tmpl
                                folderName = tmpl.defaultFolderName
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tmpl.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AntiGemTextPrimary)
                                Text(tmpl.subtitle, fontSize = 11.5.sp, color = AntiGemTextSecondary)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = AntiGemAccent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Project folder name", color = AntiGemTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AntiGemAccent,
                        unfocusedBorderColor = Color(0xFF2D3348),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { openInIdeAfter = !openInIdeAfter }
                ) {
                    Checkbox(
                        checked = openInIdeAfter,
                        onCheckedChange = { openInIdeAfter = it },
                        colors = CheckboxDefaults.colors(checkedColor = AntiGemAccent)
                    )
                    Text("Open project in Sora IDE", fontSize = 13.sp, color = AntiGemTextSecondary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (folderName.isNotBlank()) {
                        onConfirmCreate(selectedTemplate, folderName.trim(), openInIdeAfter)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AntiGemAccent)
            ) {
                Text("Create", color = Color(0xFF0B0C10), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AntiGemTextSecondary)
            }
        }
    )
}
