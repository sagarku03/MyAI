package com.jarvisai.assistant.terminal

import android.content.Context
import android.os.FileObserver
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Real-Time Workspace File Watcher and Synchronization Coordinator.
 * Emits reactive events whenever files are created, modified, or deleted by
 * bash scripts, git operations, or AI coding subagents.
 */
object WorkspaceSyncManager {

    private const val TAG = "WorkspaceSyncManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _syncTimestamp = MutableStateFlow<Long>(System.currentTimeMillis())
    val syncTimestamp: StateFlow<Long> = _syncTimestamp.asStateFlow()

    private val _lastChangedPath = MutableStateFlow<String?>(null)
    val lastChangedPath: StateFlow<String?> = _lastChangedPath.asStateFlow()

    private var activeObserver: FileObserver? = null
    private var watchedDirectory: File? = null

    fun init(context: Context) {
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val projectsDir = File(homeDir, "projects").apply { if (!exists()) mkdirs() }
        startWatching(projectsDir)
    }

    /**
     * Starts watching target workspace root directory recursively.
     */
    fun startWatching(directory: File) {
        runCatching {
            if (!directory.exists()) directory.mkdirs()
            watchedDirectory = directory

            activeObserver?.stopWatching()
            val mask = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MODIFY or FileObserver.MOVED_TO or FileObserver.MOVED_FROM

            @Suppress("DEPRECATION")
            activeObserver = object : FileObserver(directory.absolutePath, mask) {
                override fun onEvent(event: Int, path: String?) {
                    if (path.isNullOrBlank()) return
                    if (path.contains(".git") || path.contains("build/") || path.contains(".gradle")) return
                    val fullPath = "${directory.absolutePath}/$path"
                    notifyExternalChange(fullPath)
                }
            }
            activeObserver?.startWatching()
            Log.i(TAG, "Workspace file watcher started on: ${directory.absolutePath}")
        }.onFailure { e ->
            Log.w(TAG, "FileObserver could not be started safely: ${e.message}")
        }
    }

    /**
     * Programmatically notify file watcher (called by terminal execution or subagent write).
     */
    fun notifyExternalChange(filePath: String) {
        scope.launch {
            _lastChangedPath.value = filePath
            _syncTimestamp.value = System.currentTimeMillis()
        }
    }

    fun stopWatching() {
        try {
            activeObserver?.stopWatching()
            activeObserver = null
        } catch (_: Exception) {}
    }
}
