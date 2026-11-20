package com.jarvisai.assistant.ui.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.LruCache
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.PRootInstaller
import com.jarvisai.assistant.terminal.PRootRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class FileSortField(val label: String) {
    NAME("Name"),
    DATE("Date modified"),
    SIZE("Size"),
    TYPE("Type")
}

enum class FileSortOrder(val label: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

enum class FileClipboardMode {
    COPY,
    CUT,
    COPY_TO,
    MOVE_TO
}

data class FileClipboardState(
    val files: List<File>,
    val mode: FileClipboardMode
)

enum class PlaceKind {
    HOME,
    PROJECTS,
    INTERNAL_STORAGE,
    DOWNLOADS,
    DOCUMENTS,
    CAMERA,
    PICTURES,
    MOVIES,
    MUSIC,
    PROJECT_ITEM,
    RECENT_ITEM
}

data class PlaceEntry(
    val title: String,
    val subtitle: String,
    val directory: File,
    val kind: PlaceKind,
    val freeBytes: Long? = null,
    val totalBytes: Long? = null
)

data class NavSegment(
    val displayName: String,
    val directory: File
)

data class ProjectTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val defaultFolderName: String,
    val files: Map<String, String>
)

object FileManagerOperations {

    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "3gp", "avi")
    val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus")
    val ARCHIVE_EXTENSIONS = setOf("zip", "tar", "gz", "tgz", "xz", "txz", "deb", "7z", "rar")
    val CODE_EXTENSIONS = setOf(
        "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "html", "htm", "css", "scss",
        "json", "xml", "yaml", "yml", "toml", "ini", "cfg", "conf", "sh", "bash", "zsh",
        "c", "cpp", "h", "hpp", "rs", "go", "rb", "php", "sql", "md", "txt", "log", "gradle",
        "properties", "env", "gitignore", "dockerfile", "makefile", "cmake"
    )

    private val PROJECT_MARKERS = setOf(
        "build.gradle.kts", "build.gradle", "settings.gradle.kts", "settings.gradle",
        "package.json", "pyproject.toml", "requirements.txt", "Cargo.toml", "go.mod",
        "CMakeLists.txt", "Makefile", "index.html", "main.py", ".git"
    )

    private val thumbnailCache = object : LruCache<String, ImageBitmap>(120) {}

    val PROJECT_TEMPLATES = listOf(
        ProjectTemplate(
            id = "python",
            title = "Python Project",
            subtitle = "main.py, requirements.txt, README.md",
            defaultFolderName = "python-app",
            files = mapOf(
                "main.py" to "#!/usr/bin/env python3\n\ndef main():\n    print(\"Hello from Agentra Python Workspace!\")\n\nif __name__ == \"__main__\":\n    main()\n",
                "requirements.txt" to "# Add Python dependencies here\nrequests\n",
                "README.md" to "# Python Project\n\nRun with:\n```bash\npython3 main.py\n```\n",
                ".gitignore" to "__pycache__/\n.venv/\n*.pyc\n"
            )
        ),
        ProjectTemplate(
            id = "nodejs",
            title = "Node.js Project",
            subtitle = "index.js, package.json, README.md",
            defaultFolderName = "node-app",
            files = mapOf(
                "index.js" to "console.log('Hello from Agentra Node.js Workspace!');\n",
                "package.json" to "{\n  \"name\": \"node-app\",\n  \"version\": \"1.0.0\",\n  \"main\": \"index.js\",\n  \"scripts\": {\n    \"start\": \"node index.js\"\n  }\n}\n",
                "README.md" to "# Node.js Project\n\nRun with:\n```bash\nnpm start\n```\n",
                ".gitignore" to "node_modules/\n.env\n"
            )
        ),
        ProjectTemplate(
            id = "web",
            title = "Web App (HTML/CSS/JS)",
            subtitle = "index.html, style.css, app.js",
            defaultFolderName = "web-app",
            files = mapOf(
                "index.html" to "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\" />\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n  <title>Web App</title>\n  <link rel=\"stylesheet\" href=\"style.css\" />\n</head>\n<body>\n  <main class=\"card\">\n    <h1>Hello Web Workspace</h1>\n    <p id=\"status\">Ready</p>\n  </main>\n  <script src=\"app.js\"></script>\n</body>\n</html>\n",
                "style.css" to "body { background: #0d0e13; color: #f1f5f9; font-family: sans-serif; display: grid; place-items: center; min-height: 100vh; margin: 0; }\n.card { background: #171922; padding: 24px 32px; border-radius: 16px; }\n",
                "app.js" to "document.getElementById('status').textContent = 'Loaded at ' + new Date().toLocaleTimeString();\n"
            )
        ),
        ProjectTemplate(
            id = "kotlin",
            title = "Kotlin / Gradle Project",
            subtitle = "src/main/kotlin/Main.kt, build.gradle.kts",
            defaultFolderName = "kotlin-app",
            files = mapOf(
                "build.gradle.kts" to "plugins {\n    kotlin(\"jvm\") version \"2.0.21\"\n    application\n}\n\nrepositories {\n    mavenCentral()\n}\n\napplication {\n    mainClass.set(\"MainKt\")\n}\n",
                "settings.gradle.kts" to "rootProject.name = \"kotlin-app\"\n",
                "src/main/kotlin/Main.kt" to "fun main() {\n    println(\"Hello from Kotlin Workspace!\")\n}\n",
                "README.md" to "# Kotlin Project\n"
            )
        ),
        ProjectTemplate(
            id = "cpp",
            title = "C / C++ Project",
            subtitle = "main.cpp, Makefile, README.md",
            defaultFolderName = "cpp-app",
            files = mapOf(
                "main.cpp" to "#include <iostream>\n\nint main() {\n    std::cout << \"Hello from C++ Workspace!\" << std::endl;\n    return 0;\n}\n",
                "Makefile" to "CXX = clang++\nCXXFLAGS = -std=c++20 -O2\n\nall:\n\t\$(CXX) \$(CXXFLAGS) main.cpp -o app\n\nrun: all\n\t./app\n\nclean:\n\trm -f app\n",
                "README.md" to "# C++ Project\n\nBuild and run:\n```bash\nmake run\n```\n"
            )
        )
    )

    fun ensureHomeStorageShortcuts(context: Context) {
        runCatching {
            val homeDir = LocalEnvironmentManager.getHomeDir(context)
            if (!homeDir.exists()) homeDir.mkdirs()

            val extRoot = Environment.getExternalStorageDirectory()
            if (extRoot != null && extRoot.exists()) {
                // 1. Ensure ~/sdcard -> /storage/emulated/0
                val sdcardLink = File(homeDir, "sdcard")
                if (!sdcardLink.exists() && !Files.isSymbolicLink(sdcardLink.toPath())) {
                    PRootRuntime.createSymlinkCompat(extRoot.absolutePath, sdcardLink)
                }

                // 2. Ensure ~/storage/* shortcuts matching Termux/AntiGem layout
                val storageDir = File(homeDir, "storage").apply { mkdirs() }
                val mappings = mapOf(
                    "shared" to extRoot,
                    "downloads" to File(extRoot, "Download"),
                    "dcim" to File(extRoot, "DCIM"),
                    "pictures" to File(extRoot, "Pictures"),
                    "movies" to File(extRoot, "Movies"),
                    "music" to File(extRoot, "Music"),
                    "documents" to File(extRoot, "Documents"),
                    "audiobooks" to File(extRoot, "Audiobooks"),
                    "podcasts" to File(extRoot, "Podcasts")
                )
                for ((linkName, targetDir) in mappings) {
                    val linkFile = File(storageDir, linkName)
                    if (!linkFile.exists() && !Files.isSymbolicLink(linkFile.toPath())) {
                        PRootRuntime.createSymlinkCompat(targetDir.absolutePath, linkFile)
                    }
                }
            }
        }
    }

    fun isEffectiveDirectory(file: File): Boolean {
        if (file.isDirectory) return true
        return runCatching {
            if (Files.isSymbolicLink(file.toPath())) {
                val target = file.canonicalFile
                if (target.isDirectory) return@runCatching true
            }
            val name = file.name.lowercase(Locale.ROOT)
            if (name == "sdcard" || file.parentFile?.name == "storage") {
                val resolved = resolveDirectoryTarget(file)
                resolved.isDirectory
            } else {
                false
            }
        }.getOrDefault(false)
    }

    fun resolveDirectoryTarget(file: File): File {
        runCatching {
            if (Files.isSymbolicLink(file.toPath())) {
                val canon = file.canonicalFile
                if (canon.exists() && canon.isDirectory) return canon
                val rawTarget = Files.readSymbolicLink(file.toPath()).toString()
                val resolved = if (rawTarget.startsWith("/")) File(rawTarget) else File(file.parentFile, rawTarget)
                if (resolved.exists() && resolved.isDirectory) return resolved
            }
        }
        val extRoot = Environment.getExternalStorageDirectory()
        if (extRoot != null) {
            if (file.name.equals("sdcard", ignoreCase = true) && file.listFiles() == null) {
                return extRoot
            }
            if (file.parentFile?.name.equals("storage", ignoreCase = true) && file.listFiles() == null) {
                val mapped = when (file.name.lowercase(Locale.ROOT)) {
                    "shared", "external-0", "media-0" -> extRoot
                    "downloads", "download" -> File(extRoot, "Download")
                    "dcim" -> File(extRoot, "DCIM")
                    "pictures" -> File(extRoot, "Pictures")
                    "movies" -> File(extRoot, "Movies")
                    "music" -> File(extRoot, "Music")
                    "documents" -> File(extRoot, "Documents")
                    "audiobooks" -> File(extRoot, "Audiobooks")
                    "podcasts" -> File(extRoot, "Podcasts")
                    else -> null
                }
                if (mapped != null) return mapped
            }
        }
        return file
    }

    fun listDirectoryChildren(dir: File, showHidden: Boolean): List<File> {
        val target = resolveDirectoryTarget(dir)
        val raw = target.listFiles()?.toList() ?: emptyList()
        return if (showHidden) raw else raw.filter { !it.name.startsWith(".") }
    }

    fun getDirectoryItemCountLabel(dir: File, showHidden: Boolean): String {
        val target = resolveDirectoryTarget(dir)
        val children = target.listFiles() ?: return "Empty"
        val count = if (showHidden) children.size else children.count { !it.name.startsWith(".") }
        return when (count) {
            0 -> "Empty"
            1 -> "1 item"
            else -> "$count items"
        }
    }

    fun isProjectDirectory(dir: File): Boolean {
        val target = resolveDirectoryTarget(dir)
        val names = target.list()?.toSet() ?: return false
        if (names.any { it in PROJECT_MARKERS }) return true
        return names.any { it.endsWith(".sln") || it.endsWith(".xcodeproj") }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
            bytes >= mb -> {
                val v = bytes / mb
                if (v >= 10.0) String.format(Locale.US, "%.0f MB", v)
                else String.format(Locale.US, "%.1f MB", v)
            }
            bytes >= kb -> {
                val v = bytes / kb
                if (v >= 10.0) String.format(Locale.US, "%.1f KB", v)
                else String.format(Locale.US, "%.1f KB", v)
            }
            else -> "$bytes B"
        }
    }

    fun formatTimestamp(lastModified: Long): String {
        if (lastModified <= 0L) return "Unknown"
        val now = com.jarvisai.assistant.util.IndiaTimeUtils.calendarNow()
        val target = com.jarvisai.assistant.util.IndiaTimeUtils.calendarAt(lastModified)
        val date = Date(lastModified)

        val isToday = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        if (isToday) {
            return "Today " + com.jarvisai.assistant.util.IndiaTimeUtils.format("HH:mm", date)
        }

        val isSameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
        return if (isSameYear) {
            com.jarvisai.assistant.util.IndiaTimeUtils.format("MMM d, HH:mm", date)
        } else {
            com.jarvisai.assistant.util.IndiaTimeUtils.format("MMM d, yyyy", date)
        }
    }

    fun getStorageStats(dir: File): Pair<Long, Long>? {
        return runCatching {
            val target = resolveDirectoryTarget(dir)
            val stat = StatFs(target.absolutePath)
            val free = stat.availableBytes
            val total = stat.totalBytes
            if (total > 0L) free to total else null
        }.getOrNull()
    }

    fun buildPlacesList(context: Context, recentPaths: List<String>): List<PlaceEntry> {
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val projectsDir = File(homeDir, "projects").apply { if (!exists()) mkdirs() }
        val extRoot = Environment.getExternalStorageDirectory()

        val homeStats = getStorageStats(homeDir)
        val extStats = if (extRoot != null && extRoot.exists()) getStorageStats(extRoot) else null

        val list = mutableListOf<PlaceEntry>()
        list.add(
            PlaceEntry(
                title = "Home",
                subtitle = if (homeStats != null) "${formatBytes(homeStats.first)} free of ${formatBytes(homeStats.second)}" else homeDir.absolutePath,
                directory = homeDir,
                kind = PlaceKind.HOME,
                freeBytes = homeStats?.first,
                totalBytes = homeStats?.second
            )
        )
        list.add(
            PlaceEntry(
                title = "Projects",
                subtitle = projectsDir.absolutePath,
                directory = projectsDir,
                kind = PlaceKind.PROJECTS
            )
        )

        if (extRoot != null && extRoot.exists()) {
            list.add(
                PlaceEntry(
                    title = "Internal storage",
                    subtitle = if (extStats != null) "${formatBytes(extStats.first)} free of ${formatBytes(extStats.second)}" else extRoot.absolutePath,
                    directory = extRoot,
                    kind = PlaceKind.INTERNAL_STORAGE,
                    freeBytes = extStats?.first,
                    totalBytes = extStats?.second
                )
            )
            val stdDirs = listOf(
                Triple("Downloads", File(extRoot, "Download"), PlaceKind.DOWNLOADS),
                Triple("Documents", File(extRoot, "Documents"), PlaceKind.DOCUMENTS),
                Triple("Camera", File(extRoot, "DCIM"), PlaceKind.CAMERA),
                Triple("Pictures", File(extRoot, "Pictures"), PlaceKind.PICTURES),
                Triple("Movies", File(extRoot, "Movies"), PlaceKind.MOVIES),
                Triple("Music", File(extRoot, "Music"), PlaceKind.MUSIC)
            )
            for ((title, dir, kind) in stdDirs) {
                list.add(
                    PlaceEntry(
                        title = title,
                        subtitle = dir.absolutePath,
                        directory = dir,
                        kind = kind
                    )
                )
            }
        }

        // Discover project folders inside Home and ~/projects
        val projectCandidates = mutableListOf<File>()
        homeDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") && isProjectDirectory(it) }?.let {
            projectCandidates.addAll(it)
        }
        projectsDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.let {
            projectCandidates.addAll(it)
        }
        projectCandidates
            .distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified() }
            .take(6)
            .forEach { proj ->
                list.add(
                    PlaceEntry(
                        title = proj.name,
                        subtitle = proj.absolutePath,
                        directory = proj,
                        kind = PlaceKind.PROJECT_ITEM
                    )
                )
            }

        recentPaths
            .map { File(it) }
            .filter { it.exists() && isEffectiveDirectory(it) }
            .distinctBy { it.absolutePath }
            .take(8)
            .forEach { rec ->
                list.add(
                    PlaceEntry(
                        title = rec.name.ifBlank { rec.absolutePath },
                        subtitle = rec.absolutePath,
                        directory = rec,
                        kind = PlaceKind.RECENT_ITEM
                    )
                )
            }

        return list
    }

    private const val PREFS_NAME = "jarvis_antigem_file_manager"
    private const val KEY_RECENT_DIRS = "recent_dirs"
    private const val KEY_SHOW_HIDDEN = "show_hidden_files"
    private const val KEY_GRID_MODE = "is_grid_mode"
    private const val KEY_ALWAYS_OPEN_PREFIX = "always_open_ext_"

    fun getShowHiddenPreference(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_HIDDEN, false)
    }

    fun setShowHiddenPreference(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOW_HIDDEN, value).apply()
    }

    fun getGridModePreference(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_GRID_MODE, false)
    }

    fun setGridModePreference(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_GRID_MODE, value).apply()
    }

    fun getAlwaysOpenHandler(context: Context, ext: String): String? {
        if (ext.isBlank()) return null
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ALWAYS_OPEN_PREFIX + ext.lowercase(Locale.ROOT), null)
    }

    fun setAlwaysOpenHandler(context: Context, ext: String, handler: String?) {
        if (ext.isBlank()) return
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        val key = KEY_ALWAYS_OPEN_PREFIX + ext.lowercase(Locale.ROOT)
        if (handler == null) editor.remove(key) else editor.putString(key, handler)
        editor.apply()
    }

    fun getRecentDirs(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_DIRS, "") ?: ""
        return raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun recordRecentDir(context: Context, dir: File) {
        val homePath = LocalEnvironmentManager.getHomeDir(context).absolutePath
        val extPath = Environment.getExternalStorageDirectory()?.absolutePath
        if (dir.absolutePath == homePath || dir.absolutePath == extPath) return
        val existing = getRecentDirs(context).toMutableList()
        existing.remove(dir.absolutePath)
        existing.add(0, dir.absolutePath)
        val trimmed = existing.take(10).joinToString("\n")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_RECENT_DIRS, trimmed).apply()
    }

    suspend fun calculateRecursiveStats(file: File): Pair<Long, Int> = withContext(Dispatchers.IO) {
        var totalBytes = 0L
        var count = 0
        fun walk(current: File, depth: Int) {
            if (depth > 24) return
            val resolved = if (depth == 0) resolveDirectoryTarget(current) else current
            if (resolved.isFile) {
                totalBytes += resolved.length()
                count++
                return
            }
            val children = resolved.listFiles() ?: return
            for (child in children) {
                if (Files.isSymbolicLink(child.toPath())) continue
                if (child.isDirectory) {
                    walk(child, depth + 1)
                } else {
                    totalBytes += child.length()
                    count++
                }
            }
        }
        walk(file, 0)
        totalBytes to count
    }

    suspend fun searchInSubfolders(rootDir: File, query: String, showHidden: Boolean): List<File> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val results = mutableListOf<File>()
            fun walk(dir: File, depth: Int) {
                if (depth > 12 || results.size >= 250) return
                val children = dir.listFiles() ?: return
                for (child in children) {
                    if (!showHidden && child.name.startsWith(".")) continue
                    if (child.name.contains(trimmed, ignoreCase = true)) {
                        results.add(child)
                        if (results.size >= 250) return
                    }
                    if (child.isDirectory && !Files.isSymbolicLink(child.toPath())) {
                        walk(child, depth + 1)
                    }
                }
            }
            walk(resolveDirectoryTarget(rootDir), 0)
            results
        }

    suspend fun copyOrMoveFiles(
        sources: List<File>,
        destinationDir: File,
        isCut: Boolean
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val targetParent = resolveDirectoryTarget(destinationDir)
            if (!targetParent.exists()) targetParent.mkdirs()
            var count = 0
            for (src in sources) {
                val actualSrc = if (isEffectiveDirectory(src) && !src.isDirectory) resolveDirectoryTarget(src) else src
                if (!actualSrc.exists()) continue
                var dest = File(targetParent, src.name)
                if (dest.absolutePath == actualSrc.absolutePath) {
                    if (isCut) continue
                    dest = uniqueDestinationFile(targetParent, src.name)
                } else if (dest.exists()) {
                    dest = uniqueDestinationFile(targetParent, src.name)
                }

                if (isCut) {
                    if (!actualSrc.renameTo(dest)) {
                        actualSrc.copyRecursively(dest, overwrite = true)
                        actualSrc.deleteRecursively()
                    }
                } else {
                    actualSrc.copyRecursively(dest, overwrite = true)
                }
                count++
            }
            count
        }
    }

    suspend fun duplicateItem(source: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val parent = source.parentFile ?: error("Cannot duplicate root")
            val dest = uniqueDestinationFile(parent, source.name)
            val actualSrc = if (isEffectiveDirectory(source)) resolveDirectoryTarget(source) else source
            actualSrc.copyRecursively(dest, overwrite = true)
            dest
        }
    }

    private fun uniqueDestinationFile(parent: File, originalName: String): File {
        val dotIndex = originalName.lastIndexOf('.')
        val base = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val ext = if (dotIndex > 0) originalName.substring(dotIndex) else ""
        var index = 1
        while (true) {
            val candidateName = if (index == 1) "${base}_copy$ext" else "${base}_copy_$index$ext"
            val candidate = File(parent, candidateName)
            if (!candidate.exists()) return candidate
            index++
        }
    }

    suspend fun compressToZip(sources: List<File>, outputZip: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                outputZip.parentFile?.mkdirs()
                ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZip))).use { zos ->
                    fun addFileToZip(file: File, entryPath: String) {
                        if (file.isDirectory) {
                            val children = file.listFiles() ?: emptyArray()
                            if (children.isEmpty()) {
                                zos.putNextEntry(ZipEntry("$entryPath/"))
                                zos.closeEntry()
                            } else {
                                for (child in children) {
                                    addFileToZip(child, "$entryPath/${child.name}")
                                }
                            }
                        } else if (file.isFile) {
                            val entry = ZipEntry(entryPath).apply { time = file.lastModified() }
                            zos.putNextEntry(entry)
                            BufferedInputStream(FileInputStream(file)).use { input ->
                                input.copyTo(zos, 32 * 1024)
                            }
                            zos.closeEntry()
                        }
                    }

                    for (src in sources) {
                        val actual = if (isEffectiveDirectory(src)) resolveDirectoryTarget(src) else src
                        if (actual.exists()) {
                            addFileToZip(actual, src.name)
                        }
                    }
                }
                outputZip
            }
        }

    suspend fun extractArchive(archiveFile: File, destDir: File): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val targetDir = resolveDirectoryTarget(destDir)
                val lower = archiveFile.name.lowercase(Locale.ROOT)
                when {
                    lower.endsWith(".zip") -> {
                        var count = 0
                        ZipInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                val outFile = File(targetDir, entry.name)
                                if (outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                                    if (entry.isDirectory) {
                                        outFile.mkdirs()
                                    } else {
                                        outFile.parentFile?.mkdirs()
                                        FileOutputStream(outFile).use { out -> zis.copyTo(out) }
                                        count++
                                    }
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                        count
                    }
                    lower.endsWith(".deb") -> PRootInstaller.extractDebIntoPrefix(archiveFile, targetDir)
                    else -> PRootInstaller.extractTarArchive(archiveFile, targetDir)
                }
            }
        }

    suspend fun createProjectFromTemplate(
        parentDir: File,
        folderName: String,
        template: ProjectTemplate
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedParent = resolveDirectoryTarget(parentDir)
            val projectRoot = File(resolvedParent, folderName.trim()).apply { mkdirs() }
            for ((relPath, content) in template.files) {
                val f = File(projectRoot, relPath)
                f.parentFile?.mkdirs()
                f.writeText(content)
                if (relPath.endsWith(".py") || relPath.endsWith(".sh")) {
                    f.setExecutable(true, false)
                }
            }
            projectRoot
        }
    }

    fun copyPathToClipboard(context: Context, path: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("File Path", path))
        Toast.makeText(context, "Copied: $path", Toast.LENGTH_SHORT).show()
    }

    fun getMimeType(file: File): String {
        val ext = file.extension.lowercase(Locale.ROOT)
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg", "opus" -> "audio/ogg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "apk" -> "application/vnd.android.package-archive"
            "pdf" -> "application/pdf"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: when {
                    ext in IMAGE_EXTENSIONS -> "image/*"
                    ext in VIDEO_EXTENSIONS -> "video/*"
                    ext in AUDIO_EXTENSIONS -> "audio/*"
                    else -> "*/*"
                }
        }
    }

    fun openFileExternal(context: Context, file: File) {
        runCatching {
            val resolvedFile = runCatching { file.canonicalFile }.getOrDefault(file)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", resolvedFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(resolvedFile))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { e ->
            Toast.makeText(context, "No external app found: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFileExternal(context: Context, file: File) {
        runCatching {
            val resolvedFile = runCatching { file.canonicalFile }.getOrDefault(file)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", resolvedFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(resolvedFile)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { e ->
            Toast.makeText(context, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun saveFileToDownloads(context: Context, source: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .apply { if (!exists()) mkdirs() }
                val dest = uniqueDestinationFile(downloadsDir, source.name)
                source.copyTo(dest, overwrite = true)
                dest
            }
        }

    @Composable
    fun rememberFileThumbnail(file: File, reqSizePx: Int = 144): ImageBitmap? {
        val ext = remember(file.name) { file.extension.lowercase(Locale.ROOT) }
        val isMedia = ext in IMAGE_EXTENSIONS || ext in VIDEO_EXTENSIONS
        if (!isMedia || file.isDirectory) return null

        val cacheKey = "${file.absolutePath}:${file.lastModified()}:$reqSizePx"
        var bitmap by remember(cacheKey) { mutableStateOf(thumbnailCache.get(cacheKey)) }

        LaunchedEffect(cacheKey) {
            if (bitmap != null) return@LaunchedEffect
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    if (ext in IMAGE_EXTENSIONS) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, bounds)
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                        var sampleSize = 1
                        while (bounds.outWidth / sampleSize > reqSizePx * 2 && bounds.outHeight / sampleSize > reqSizePx * 2) {
                            sampleSize *= 2
                        }
                        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
                    } else if (ext in VIDEO_EXTENSIONS) {
                        @Suppress("DEPRECATION")
                        ThumbnailUtils.createVideoThumbnail(
                            file.absolutePath,
                            MediaStore.Images.Thumbnails.MINI_KIND
                        )?.asImageBitmap()
                    } else {
                        null
                    }
                }.getOrNull()
            }
            if (loaded != null) {
                thumbnailCache.put(cacheKey, loaded)
                bitmap = loaded
            }
        }
        return bitmap
    }
}
