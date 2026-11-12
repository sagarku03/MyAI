package com.termux.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.system.Os
import android.util.Log
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.theme.ThemeTerminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Handles Termux system broadcasts:
 * - `com.termux.app.request_storage_permissions` (triggered by `termux-setup-storage`)
 * - `com.termux.app.reload_style` (triggered by `termux-reload-settings`)
 */
class TermuxSystemReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TermuxSystemReceiver"
        const val ACTION_REQUEST_STORAGE = "com.termux.app.request_storage_permissions"
        const val ACTION_RELOAD_STYLE = "com.termux.app.reload_style"
        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Received Termux system broadcast: $action")

        when (action) {
            ACTION_REQUEST_STORAGE -> {
                ioScope.launch {
                    TermuxStorageSetupManager.setupStorageSymlinks(context.applicationContext)
                }
            }
            ACTION_RELOAD_STYLE -> {
                ThemeTerminal.reloadActiveSessions()
            }
        }
    }
}

object TermuxStorageSetupManager {
    private const val TAG = "TermuxStorageSetup"

    fun setupStorageSymlinks(context: Context) {
        try {
            val homeDir = LocalEnvironmentManager.getHomeDir(context)
            val storageDir = File(homeDir, "storage")
            if (storageDir.exists()) {
                storageDir.deleteRecursively()
            }
            storageDir.mkdirs()

            val sharedRoot = Environment.getExternalStorageDirectory()
            createSymlinkSafe(sharedRoot.absolutePath, File(storageDir, "shared").absolutePath)

            val standardDirs = mapOf(
                "downloads" to Environment.DIRECTORY_DOWNLOADS,
                "dcim" to Environment.DIRECTORY_DCIM,
                "pictures" to Environment.DIRECTORY_PICTURES,
                "music" to Environment.DIRECTORY_MUSIC,
                "movies" to Environment.DIRECTORY_MOVIES,
                "documents" to Environment.DIRECTORY_DOCUMENTS
            )

            for ((linkName, envType) in standardDirs) {
                val targetDir = Environment.getExternalStoragePublicDirectory(envType)
                targetDir.mkdirs()
                createSymlinkSafe(targetDir.absolutePath, File(storageDir, linkName).absolutePath)
            }

            // App-specific external files directory
            context.getExternalFilesDir(null)?.let { extFiles ->
                createSymlinkSafe(extFiles.absolutePath, File(storageDir, "external-1").absolutePath)
            }

            Log.i(TAG, "Successfully created ~/storage symlinks at ${storageDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup storage symlinks: ${e.message}", e)
        }
    }

    private fun createSymlinkSafe(targetPath: String, linkPath: String) {
        try {
            val linkFile = File(linkPath)
            if (linkFile.exists()) linkFile.delete()
            Os.symlink(targetPath, linkPath)
        } catch (e: Exception) {
            Log.w(TAG, "Symlink skipped ($linkPath -> $targetPath): ${e.message}")
        }
    }
}
