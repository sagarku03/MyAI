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

    private val _currentWorkspacePath = MutableStateFlow<String?>(null)
    val currentWorkspacePath: StateFlow<String?> = _currentWorkspacePath.asStateFlow()

    private val _pendingOpenFile = MutableStateFlow<String?>(null)
    val pendingOpenFile: StateFlow<String?> = _pendingOpenFile.asStateFlow()

    private var activeObserver: FileObserver? = null
    private var watchedDirectory: File? = null

    fun init(context: Context) {
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val projectsDir = File(homeDir, "projects").apply { if (!exists()) mkdirs() }
        if (_currentWorkspacePath.value.isNullOrBlank()) {
            _currentWorkspacePath.value = projectsDir.absolutePath
        }
        startWatching(projectsDir)
    }

    /**
     * Starts watching target workspace root directory recursively.
     */
    fun startWatching(directory: File) {
        runCatching {
            if (!directory.exists()) directory.mkdirs()
            watchedDirectory = directory
            _currentWorkspacePath.value = directory.absolutePath

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

    fun updateWorkspaceRoot(path: String) {
        if (path.isBlank()) return
        val dir = File(path)
        if (dir.exists() && dir.isDirectory && watchedDirectory?.absolutePath != dir.absolutePath) {
            startWatching(dir)
        } else {
            _currentWorkspacePath.value = path
        }
    }

    fun requestOpenFileInIde(filePath: String) {
        _pendingOpenFile.value = filePath
        notifyExternalChange(filePath)
    }

    fun clearPendingOpenFile() {
        _pendingOpenFile.value = null
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
