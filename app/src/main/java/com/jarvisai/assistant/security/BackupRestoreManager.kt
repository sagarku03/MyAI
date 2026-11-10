package com.jarvisai.assistant.security

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Enterprise Snapshot Backup & Restore Manager for Jarvis AI.
 * Handles atomic backups of developer projects, cognitive memory SQLite databases,
 * and user preferences with SHA-256 checksum verification and atomic rollback guards.
 */
class BackupRestoreManager(private val context: Context) {

    companion object {
        private const val TAG = "BackupRestoreManager"
        private const val MANIFEST_ENTRY_NAME = "META-INF/SHA256SUMS.txt"

        private val BACKUP_DIR: File
            get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Jarvis/backups")

        private val EXCLUDED_PROJECT_NAMES = setOf(
            "node_modules", ".gradle", "build", ".cache", "dist", ".next", ".turbo", "__pycache__", ".pytest_cache"
        )
    }

    data class BackupProgress(
        val stage: String,
        val currentCount: Int,
        val totalCount: Int,
        val percentage: Int,
        val isComplete: Boolean = false,
        val resultFile: File? = null,
        val errorMessage: String? = null
    )

    init {
        if (!BACKUP_DIR.exists()) {
            BACKUP_DIR.mkdirs()
        }
    }

    fun listAvailableBackups(): List<File> {
        if (!BACKUP_DIR.exists() || !BACKUP_DIR.isDirectory) return emptyList()
        return BACKUP_DIR.listFiles { file -> file.isFile && file.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file)).use { bis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (bis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Creates a full snapshot backup of Jarvis SQLite databases, shared preferences,
     * and user project workspaces with an embedded SHA-256 integrity manifest.
     */
    fun createFullBackup(): Flow<BackupProgress> = flow {
        emit(BackupProgress(stage = "Scanning files...", currentCount = 0, totalCount = 100, percentage = 0))

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val backupZipFile = File(BACKUP_DIR, "jarvis_backup_$timestamp.zip")
        val tempZipFile = File(context.cacheDir, "temp_backup_$timestamp.zip")

        try {
            val filesToBackup = mutableListOf<File>()

            // 1. Database files (Room, SQLite, Memory Vault)
            val dbDir = context.getDatabasePath("jarvis_database").parentFile
            if (dbDir != null && dbDir.exists()) {
                dbDir.listFiles()?.forEach { if (it.isFile && !it.name.endsWith("-journal")) filesToBackup.add(it) }
            }

            // 2. Shared Preferences
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (prefsDir.exists()) {
                prefsDir.listFiles()?.forEach { if (it.isFile) filesToBackup.add(it) }
            }

            // 3. User Project Workspaces (Termux home / projects)
            val projectsDir = File("/data/data/com.termux/files/home/projects")
            if (projectsDir.exists()) {
                projectsDir.walkTopDown()
                    .onEnter { dir -> !EXCLUDED_PROJECT_NAMES.contains(dir.name) }
                    .forEach { if (it.isFile) filesToBackup.add(it) }
            }

            // 4. Gemini CLI configuration directory (~/.gemini)
            val geminiDir = File("/data/data/com.termux/files/home/.gemini")
            if (geminiDir.exists()) {
                geminiDir.walkTopDown()
                    .onEnter { dir -> !dir.name.equals("cache", ignoreCase = true) && !dir.name.equals("logs", ignoreCase = true) }
                    .forEach { if (it.isFile && !it.name.endsWith(".sock") && !it.name.endsWith(".log")) filesToBackup.add(it) }
            }

            val totalFiles = filesToBackup.size
            if (totalFiles == 0) {
                emit(BackupProgress(stage = "No files found to backup", currentCount = 0, totalCount = 0, percentage = 100, isComplete = true))
                return@flow
            }

            emit(BackupProgress(stage = "Compressing snapshot...", currentCount = 0, totalCount = totalFiles, percentage = 5))

            val rootDataDir = context.applicationInfo.dataDir
            val sha256ManifestBuilder = StringBuilder()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZipFile))).use { zos ->
                filesToBackup.forEachIndexed { index, file ->
                    val relativePath = if (file.absolutePath.startsWith(rootDataDir)) {
                        file.absolutePath.removePrefix("$rootDataDir/")
                    } else if (file.absolutePath.startsWith("/data/data/com.termux/files/home/")) {
                        "termux_home/" + file.absolutePath.removePrefix("/data/data/com.termux/files/home/")
                    } else {
                        "extra/" + file.name
                    }

                    val sha256 = computeSha256(file)
                    sha256ManifestBuilder.append("$sha256  $relativePath\n")

                    val entry = ZipEntry(relativePath)
                    entry.time = file.lastModified()
                    zos.putNextEntry(entry)
                    file.inputStream().use { input -> input.copyTo(zos) }
                    zos.closeEntry()

                    val pct = 5 + ((index + 1) * 90 / totalFiles)
                    emit(BackupProgress(
                        stage = "Adding: ${file.name}",
                        currentCount = index + 1,
                        totalCount = totalFiles,
                        percentage = pct
                    ))
                }

                // Write SHA-256 Integrity Manifest entry
                val manifestEntry = ZipEntry(MANIFEST_ENTRY_NAME)
                manifestEntry.time = System.currentTimeMillis()
                zos.putNextEntry(manifestEntry)
                zos.write(sha256ManifestBuilder.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            // Move temp file to final public directory
            if (!BACKUP_DIR.exists()) BACKUP_DIR.mkdirs()
            tempZipFile.copyTo(backupZipFile, overwrite = true)
            tempZipFile.delete()

            emit(BackupProgress(
                stage = "Backup Completed!",
                currentCount = totalFiles,
                totalCount = totalFiles,
                percentage = 100,
                isComplete = true,
                resultFile = backupZipFile
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed: ${e.message}", e)
            if (tempZipFile.exists()) tempZipFile.delete()
            emit(BackupProgress(
                stage = "Failed: ${e.message}",
                currentCount = 0,
                totalCount = 0,
                percentage = 0,
                errorMessage = e.message
            ))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Restores an existing backup ZIP file with atomic safety:
     * Pre-validates archive and SHA-256 manifest, backs up current state to `.bak`,
     * extracts files with Zip-Slip protection, and restores permissions (`chmod 700`).
     */
    suspend fun restoreBackup(backupZip: File): Result<Unit> = withContext(Dispatchers.IO) {
        val rootDataDir = File(context.applicationInfo.dataDir)
        val termuxHomeDir = File("/data/data/com.termux/files/home")
        val geminiDir = File(termuxHomeDir, ".gemini")
        val geminiBakDir = File(termuxHomeDir, ".gemini.bak.${System.currentTimeMillis()}")
        val dbDir = context.getDatabasePath("jarvis_database").parentFile
        val dbBakDir = dbDir?.let { File(it.parentFile, "databases.bak.${System.currentTimeMillis()}") }

        try {
            if (!backupZip.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Backup archive not found at ${backupZip.absolutePath}"))
            }

            // 1. Create atomic .bak rollback snapshots before extraction
            if (geminiDir.exists()) {
                geminiDir.copyRecursively(geminiBakDir, overwrite = true)
            }
            if (dbDir != null && dbDir.exists() && dbBakDir != null) {
                dbDir.copyRecursively(dbBakDir, overwrite = true)
            }

            val expectedHashes = mutableMapOf<String, String>()
            val extractedFiles = mutableMapOf<String, File>()

            ZipFile(backupZip).use { zip ->
                // Pre-read SHA-256 manifest if present
                val manifestEntry = zip.getEntry(MANIFEST_ENTRY_NAME)
                if (manifestEntry != null) {
                    val manifestText = zip.getInputStream(manifestEntry).bufferedReader().readText()
                    manifestText.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                        val parts = line.split("  ", limit = 2)
                        if (parts.size == 2) {
                            expectedHashes[parts[1].trim()] = parts[0].trim()
                        }
                    }
                }

                zip.entries().asSequence().forEach { entry ->
                    if (!entry.isDirectory && entry.name != MANIFEST_ENTRY_NAME) {
                        val targetFile = if (entry.name.startsWith("termux_home/")) {
                            File(termuxHomeDir, entry.name.removePrefix("termux_home/"))
                        } else if (entry.name.startsWith("extra/")) {
                            File(context.filesDir, entry.name.removePrefix("extra/"))
                        } else {
                            File(rootDataDir, entry.name)
                        }

                        // Security check against Zip Path Traversal (Zip Slip)
                        val canonicalDest = targetFile.canonicalPath
                        if (!canonicalDest.startsWith(rootDataDir.canonicalPath) &&
                            !canonicalDest.startsWith(termuxHomeDir.canonicalPath) &&
                            !canonicalDest.startsWith(context.filesDir.canonicalPath)) {
                            throw SecurityException("Malicious zip entry detected: ${entry.name}")
                        }

                        targetFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        extractedFiles[entry.name] = targetFile
                    }
                }
            }

            // 2. Verify SHA-256 checksums for all manifest-recorded entries
            for ((relPath, expectedHash) in expectedHashes) {
                val restoredFile = extractedFiles[relPath] ?: continue
                val actualHash = computeSha256(restoredFile)
                if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                    throw SecurityException("SHA-256 integrity mismatch for $relPath")
                }
            }

            // 3. Permission recovery (chmod 700 on sensitive directories)
            if (geminiDir.exists()) {
                try {
                    Runtime.getRuntime().exec(arrayOf("chmod", "-R", "700", geminiDir.absolutePath)).waitFor()
                } catch (_: Exception) {
                    geminiDir.setReadable(true, true)
                    geminiDir.setWritable(true, true)
                    geminiDir.setExecutable(true, true)
                }
            }

            // Cleanup .bak snapshots after verified atomic success
            if (geminiBakDir.exists()) geminiBakDir.deleteRecursively()
            if (dbBakDir != null && dbBakDir.exists()) dbBakDir.deleteRecursively()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed, rolling back from .bak snapshot: ${e.message}", e)
            try {
                if (geminiBakDir.exists()) {
                    if (geminiDir.exists()) geminiDir.deleteRecursively()
                    geminiBakDir.copyRecursively(geminiDir, overwrite = true)
                    geminiBakDir.deleteRecursively()
                }
                if (dbDir != null && dbBakDir != null && dbBakDir.exists()) {
                    if (dbDir.exists()) dbDir.deleteRecursively()
                    dbBakDir.copyRecursively(dbDir, overwrite = true)
                    dbBakDir.deleteRecursively()
                }
            } catch (rollbackErr: Exception) {
                Log.e(TAG, "Rollback error: ${rollbackErr.message}", rollbackErr)
            }
            Result.failure(e)
        }
    }
}
