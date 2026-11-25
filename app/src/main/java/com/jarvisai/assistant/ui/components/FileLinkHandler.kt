package com.jarvisai.assistant.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.ide.ActiveCodeEditorRegistry
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.WorkspaceSyncManager
import java.io.File

private val MARKDOWN_LINK_FILE_URI_REGEX = Regex("""\[[^\]]+]\(file:///([^)\s]+)\)""")
private val FILE_URI_REGEX = Regex("""file:///([^\s\)\]`"'>]+)""")

private val KNOWN_CODE_FILE_EXTENSIONS = setOf(
    "html", "htm", "css", "scss", "sass", "less", "js", "mjs", "cjs", "jsx",
    "ts", "tsx", "json", "md", "markdown", "txt", "xml", "svg", "yaml", "yml",
    "toml", "ini", "env", "sh", "bash", "zsh", "py", "kt", "kts", "java",
    "c", "h", "cpp", "hpp", "cs", "go", "rs", "rb", "php", "sql", "gradle",
    "properties", "dockerfile", "makefile", "csv", "log", "png", "jpg", "jpeg", "webp", "gif"
)

fun extractFileLinks(text: String): List<String> {
    val withoutInlineMarkdownLinks = MARKDOWN_LINK_FILE_URI_REGEX.replace(text, " ")
    return FILE_URI_REGEX.findAll(withoutInlineMarkdownLinks)
        .map { "/" + it.groupValues[1] }
        .distinct()
        .take(4)
        .toList()
}

fun looksLikeOpenableFileReference(raw: String): Boolean {
    val cleaned = raw.trim().removePrefix("file://").substringBefore('#').substringBefore('?')
    if (cleaned.isBlank() || cleaned.contains("://") || cleaned.contains('\n')) return false
    if (cleaned.startsWith("/") || cleaned.startsWith("~/") || cleaned.startsWith("./")) return true
    val name = cleaned.substringAfterLast('/')
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext.isNotEmpty() && ext in KNOWN_CODE_FILE_EXTENSIONS && !name.contains(' ')
}

/**
 * Resolves a file reference from chat (absolute `file://`, `/...`, `~/...`, or relative like `diwali-page/index.html`
 * or `index.html`) against the active workspace, home directory, and shallow project subdirectories.
 */
fun resolveChatFileTarget(rawPath: String, context: Context): File? {
    val stripped = rawPath.trim()
        .removePrefix("file://")
        .substringBefore('#')
        .substringBefore('?')
        .trim()
    if (stripped.isBlank()) return null

    val homeDir = runCatching { LocalEnvironmentManager.getHomeDir(context) }.getOrNull()
    val projectsDir = runCatching { LocalEnvironmentManager.getIsolatedProjectsDir(context) }.getOrNull()

    val expanded = if (stripped.startsWith("~/") && homeDir != null) {
        File(homeDir, stripped.removePrefix("~/")).absolutePath
    } else {
        stripped
    }

    // 1. Exact absolute path
    val direct = File(expanded)
    if (direct.isAbsolute && direct.exists() && direct.isFile) {
        return direct
    }

    // 2. Relative to active workspace / IDE project / home / projects directories
    val rel = expanded.removePrefix("./").removePrefix("/")
    val candidateRoots = buildList<File> {
        ActiveCodeEditorRegistry.currentProjectPath?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
        WorkspaceSyncManager.currentWorkspacePath.value?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
        if (homeDir != null) add(homeDir)
        if (projectsDir != null) add(projectsDir)
    }.distinctBy { it.absolutePath }.filter { it.exists() && it.isDirectory }

    for (root in candidateRoots) {
        val candidate = File(root, rel)
        if (candidate.exists() && candidate.isFile) return candidate
    }

    // 3. Shallow 1-level subdirectory lookup (e.g. tapping `index.html` when file is in `Test/diwali-page/index.html`)
    for (root in candidateRoots) {
        val subDirs = root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        for (sub in subDirs.take(24)) {
            val candidate = File(sub, rel)
            if (candidate.exists() && candidate.isFile) return candidate
        }
    }

    return null
}

/**
 * AntiGem-style direct file opening: syncs workspace root if needed and immediately opens the file
 * in the full-screen Code IDE editor tab (never a cramped dialog popup).
 */
fun openChatFileInIde(context: Context, rawPath: String): Boolean {
    val target = resolveChatFileTarget(rawPath, context)
    if (target != null && target.exists() && target.isFile) {
        val currentRoot = ActiveCodeEditorRegistry.currentProjectPath?.let { File(it) }
        val isInsideCurrentRoot = currentRoot != null &&
            target.absolutePath.startsWith(currentRoot.absolutePath.trimEnd('/') + "/")
        if (!isInsideCurrentRoot) {
            target.parentFile?.takeIf { it.exists() && it.isDirectory }?.let { parentDir ->
                ActiveCodeEditorRegistry.currentProjectPath = parentDir.absolutePath
                WorkspaceSyncManager.updateWorkspaceRoot(parentDir.absolutePath)
            }
        }
        WorkspaceSyncManager.requestOpenFileInIde(target.absolutePath)
        return true
    }
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val cleanPath = rawPath.removePrefix("file://")
    cm.setPrimaryClip(ClipData.newPlainText("File Path", cleanPath))
    Toast.makeText(context, "Copied path: $cleanPath", Toast.LENGTH_SHORT).show()
    return false
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileLinkRow(
    text: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val filePaths = remember(text) { extractFileLinks(text) }

    if (filePaths.isEmpty()) return

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        filePaths.forEach { rawPath ->
            val file = remember(rawPath) { File(rawPath) }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF131D2E),
                border = BorderStroke(0.8.dp, Color(0xFF60A5FA).copy(alpha = 0.4f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { openChatFileInIde(context, rawPath) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = file.name.ifBlank { rawPath },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF93C5FD),
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
