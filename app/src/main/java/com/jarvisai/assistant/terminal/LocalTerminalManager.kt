package com.jarvisai.assistant.terminal

import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class LocalPtySession(
    val id: String,
    initialTitle: String,
    val context: Context,
    initialWorkingDir: String? = null,
    val initialCommand: String? = null,
    val execShellAfterCommand: Boolean = true,
    val forceShell: String? = null,
    initialCols: Int = LocalTerminalManager.lastKnownCols,
    initialRows: Int = LocalTerminalManager.lastKnownRows,
    initialWidthPx: Int = LocalTerminalManager.lastKnownWidthPx,
    initialHeightPx: Int = LocalTerminalManager.lastKnownHeightPx,
    val showExitNotice: Boolean = true
) : TerminalSessionClient {

    private val TAG = "AgentraTerminal"
    var workingDirectory: String = initialWorkingDir ?: LocalEnvironmentManager.getHomeDir(context).absolutePath
        private set

    val terminalSession: TerminalSession

    private val _isExited = MutableStateFlow(false)
    val isExited: StateFlow<Boolean> = _isExited.asStateFlow()

    private val _title = MutableStateFlow(initialTitle)
    val title: StateFlow<String> = _title.asStateFlow()

    var name: String
        get() = _title.value
        set(value) { _title.value = value }

    var ptyCols: Int = initialCols
    var ptyRows: Int = initialRows
    var ptyWidthPx: Int = initialWidthPx
    var ptyHeightPx: Int = initialHeightPx

    private val textChangedListeners = mutableListOf<() -> Unit>()

    init {
        val safeCols = if (ptyCols > 0) ptyCols else if (LocalTerminalManager.lastKnownCols > 0) LocalTerminalManager.lastKnownCols else 80
        val safeRows = if (ptyRows > 0) ptyRows else if (LocalTerminalManager.lastKnownRows > 0) LocalTerminalManager.lastKnownRows else 24

        val prefixDir = LocalEnvironmentManager.getPrefixDir(context)
        val binDir = LocalEnvironmentManager.getBinDir(context)
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val tmpDir = LocalEnvironmentManager.getTmpDir(context)

        // Ensure runtime hooks, glibc, and Agentra termux-api dispatcher are ready
        runCatching {
            LocalEnvironmentManager.ensureGlibcEnvironment(context)
            LocalEnvironmentManager.ensureTermuxApiDispatcher(context)
            TermuxRelocation.ensurePackageHooks(context)
        }.onFailure { Log.w(TAG, "Hook init warning: ${it.message}") }

        val workDirFile = File(workingDirectory).apply { runCatching { mkdirs() } }
        if (!workDirFile.exists()) {
            workingDirectory = homeDir.absolutePath
        }

        // Authentic Termux / AntiGem login shell priority:
        // login (handles motd & profile) -> bash -> zsh -> fish -> sh -> /system/bin/sh
        val loginShellBinaries = arrayOf("login", "bash", "zsh", "fish", "sh")
        val shellBinary = when {
            forceShell != null && File(binDir, forceShell).exists() && File(binDir, forceShell).canExecute() ->
                File(binDir, forceShell).absolutePath
            forceShell != null && File(binDir, forceShell).exists() ->
                File(binDir, forceShell).absolutePath
            forceShell != null && File(forceShell).exists() ->
                File(forceShell).absolutePath
            else -> {
                var found: String? = null
                for (candidate in loginShellBinaries) {
                    val f = File(binDir, candidate)
                    if (f.exists() && f.canExecute()) {
                        found = f.absolutePath
                        break
                    }
                }
                if (found == null) {
                    for (candidate in loginShellBinaries) {
                        val f = File(binDir, candidate)
                        if (f.exists()) {
                            found = f.absolutePath
                            break
                        }
                    }
                }
                found ?: LocalEnvironmentManager.getDefaultShell(context)
            }
        }

        val envMap = linkedMapOf<String, String>()
        LocalEnvironmentManager.applyIsolatedToolEnvironment(context, envMap)
        envMap["PREFIX"] = prefixDir.absolutePath
        envMap["HOME"] = homeDir.absolutePath
        envMap["PATH"] = "${homeDir.absolutePath}/.local/bin:${binDir.absolutePath}:${binDir.absolutePath}/applets:/system/bin:/system/xbin"
        envMap["TMPDIR"] = tmpDir.absolutePath
        envMap["TERM"] = "xterm-256color"
        envMap["COLORTERM"] = "truecolor"
        envMap["TERMUX_VERSION"] = "0.118.0"
        envMap["TERMUX_MAIN_PACKAGE_NAME"] = context.packageName
        envMap["TERMUX_APK_RELEASE"] = "GITHUB"
        envMap["TERMUX_APP_PID"] = "${android.os.Process.myPid()}"
        envMap["TERMUX__USER_ID"] = "0"
        envMap["SHELL"] = shellBinary
        envMap["ANDROID_DATA"] = "/data"
        envMap["ANDROID_ROOT"] = "/system"
        envMap["LANG"] = "en_US.UTF-8"
        envMap["LC_ALL"] = "en_US.UTF-8"
        envMap["COLUMNS"] = "$safeCols"
        envMap["LINES"] = "$safeRows"
        if (!envMap.containsKey("PS1")) envMap["PS1"] = "$ "

        val env = envMap.entries.map { "${it.key}=${it.value}" }.toTypedArray()

        // Match AntiGem / Termux argv[0] login shell convention ("-login" / "-bash")
        val isLoginShell = shellBinary != "/system/bin/sh"
        val processName = (if (isLoginShell) "-" else "") + File(shellBinary).name
        val shellArgs = if (!initialCommand.isNullOrBlank()) {
            if (execShellAfterCommand) {
                arrayOf(processName, "-c", "$initialCommand; exec $shellBinary")
            } else {
                arrayOf(processName, "-c", initialCommand)
            }
        } else {
            arrayOf(processName)
        }

        val prootInv = if (forceShell == null && PRootRuntime.shouldUsePRoot(context)) {
            runCatching {
                PRootRuntime.buildActiveInvocation(
                    context = context,
                    command = initialCommand,
                    workingDir = workingDirectory,
                    loginShell = initialCommand == null
                )
            }.getOrNull()
        } else {
            null
        }

        val bufferRows = LocalTerminalManager.currentBufferSize.coerceIn(500, 100000)

        terminalSession = if (prootInv != null) {
            runCatching {
                val prootEnvArray = prootInv.hostEnv.entries.map { "${it.key}=${it.value}" }.toTypedArray()
                createTerminalSessionCompat(
                    shellPath = prootInv.executablePath,
                    cwd = prootInv.hostWorkingDir.absolutePath,
                    args = prootInv.argv.toTypedArray(),
                    env = prootEnvArray,
                    transcriptRows = bufferRows,
                    client = this
                )
            }.getOrElse { err ->
                Log.w(TAG, "PRoot PTY launch failed (${err.message}); falling back to $shellBinary")
                launchDirectWithFallback(shellBinary, shellArgs, env, bufferRows)
            }
        } else {
            launchDirectWithFallback(shellBinary, shellArgs, env, bufferRows)
        }

        runCatching {
            terminalSession.updateSize(safeCols, safeRows)
        }.onFailure { Log.w(TAG, "Initial updateSize error: ${it.message}") }
    }

    private fun launchDirectWithFallback(
        preferredShell: String,
        args: Array<String>,
        env: Array<String>,
        transcriptRows: Int
    ): TerminalSession {
        return runCatching {
            createTerminalSessionCompat(
                shellPath = preferredShell,
                cwd = workingDirectory,
                args = args,
                env = env,
                transcriptRows = transcriptRows,
                client = this
            )
        }.getOrElse { err ->
            Log.w(TAG, "Preferred shell ($preferredShell) failed (${err.message}); falling back to /system/bin/sh")
            createTerminalSessionCompat(
                shellPath = "/system/bin/sh",
                cwd = workingDirectory,
                args = if (initialCommand != null) arrayOf("-c", initialCommand) else arrayOf("sh"),
                env = env,
                transcriptRows = transcriptRows,
                client = this
            )
        }
    }

    private fun createTerminalSessionCompat(
        shellPath: String,
        cwd: String,
        args: Array<String>,
        env: Array<String>,
        transcriptRows: Int,
        client: TerminalSessionClient
    ): TerminalSession {
        val constructors = TerminalSession::class.java.constructors
        for (ctor in constructors) {
            val paramTypes = ctor.parameterTypes
            if (paramTypes.size == 6) {
                return ctor.newInstance(shellPath, cwd, args, env, transcriptRows, client) as TerminalSession
            } else if (paramTypes.size == 5) {
                return ctor.newInstance(shellPath, cwd, args, env, client) as TerminalSession
            }
        }
        return TerminalSession(shellPath, cwd, args, env, transcriptRows, client)
    }

    fun addTextChangedListener(listener: () -> Unit) {
        synchronized(textChangedListeners) {
            textChangedListeners.add(listener)
        }
    }

    fun removeTextChangedListener(listener: () -> Unit) {
        synchronized(textChangedListeners) {
            textChangedListeners.remove(listener)
        }
    }

    override fun onTextChanged(changedSession: TerminalSession) {
        synchronized(textChangedListeners) {
            textChangedListeners.forEach { it.invoke() }
        }
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        val newTitle = changedSession.title
        if (!newTitle.isNullOrBlank()) {
            _title.value = newTitle
        }
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        val exitCode = finishedSession.exitStatus
        Log.i(TAG, "PTY session $id finished with exit code $exitCode")
        if (showExitNotice) {
            runCatching {
                val msg = "\r\n\u001B[90m[Process completed (code $exitCode) - press Enter to close tab]\u001B[0m\r\n"
                val bytes = msg.toByteArray(Charsets.UTF_8)
                finishedSession.emulator?.append(bytes, bytes.size)
                onTextChanged(finishedSession)
            }
        }
        _isExited.value = true
    }

    fun onSessionFinished(finishedSession: TerminalSession, exitCode: Int) {
        onSessionFinished(finishedSession)
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        if (text.isNullOrEmpty()) return
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("Agentra Terminal", text))
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard copy failed: ${e.message}")
        }
    }

    fun onClipboardText(session: TerminalSession, text: String) {
        onCopyTextToClipboard(session, text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = cm?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pasteText = clip.getItemAt(0).coerceToText(context)?.toString()
                if (!pasteText.isNullOrEmpty()) {
                    terminalSession.emulator?.paste(pasteText)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard paste failed: ${e.message}")
        }
    }

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {
        onTextChanged(session)
    }

    override fun onTerminalCursorStateChange(state: Boolean) {}

    fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    override fun getTerminalCursorStyle(): Int {
        return when (LocalTerminalManager.currentCursorStyle) {
            "BLOCK" -> 0
            "UNDERLINE" -> 1
            else -> 2 // "BAR"
        }
    }

    override fun logError(tag: String?, message: String?) {
        if (!message.isNullOrBlank()) Log.e(tag ?: TAG, message)
    }

    override fun logWarn(tag: String?, message: String?) {
        if (!message.isNullOrBlank()) Log.w(tag ?: TAG, message)
    }

    override fun logInfo(tag: String?, message: String?) {
        if (!message.isNullOrBlank()) Log.i(tag ?: TAG, message)
    }

    override fun logDebug(tag: String?, message: String?) {
        if (!message.isNullOrBlank()) Log.d(tag ?: TAG, message)
    }

    override fun logVerbose(tag: String?, message: String?) {
        if (!message.isNullOrBlank()) Log.v(tag ?: TAG, message)
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: TAG, message ?: "Terminal exception", e)
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.e(tag ?: TAG, "Terminal stacktrace", e)
    }

    fun write(data: String) {
        if (_isExited.value) return
        terminalSession.write(data)
    }

    fun writeCodePoint(prependEscape: Boolean, codePoint: Int) {
        if (_isExited.value) return
        val str = if (prependEscape) {
            "\u001B" + String(Character.toChars(codePoint))
        } else {
            String(Character.toChars(codePoint))
        }
        write(str)
    }

    fun updateSize(cols: Int, rows: Int, widthPx: Int = cols * 10, heightPx: Int = rows * 20) {
        if (cols <= 0 || rows <= 0) return
        ptyCols = cols
        ptyRows = rows
        ptyWidthPx = widthPx
        ptyHeightPx = heightPx
        LocalTerminalManager.lastKnownCols = cols
        LocalTerminalManager.lastKnownRows = rows
        LocalTerminalManager.lastKnownWidthPx = widthPx
        LocalTerminalManager.lastKnownHeightPx = heightPx
        try {
            terminalSession.updateSize(cols, rows)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resize terminal session: ${e.message}")
        }
    }

    fun finishIfRunning() {
        try {
            terminalSession.finishIfRunning()
        } catch (_: Exception) {}
        _isExited.value = true
    }

    fun close() = finishIfRunning()
}

object LocalTerminalManager {

    private val _sessions = MutableStateFlow<List<LocalPtySession>>(emptyList())
    val sessions: StateFlow<List<LocalPtySession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    @Volatile
    var currentCursorStyle: String = "BAR"

    @Volatile
    var currentBufferSize: Int = 20000

    @Volatile
    var lastKnownCols: Int = 80

    @Volatile
    var lastKnownRows: Int = 24

    @Volatile
    var lastKnownWidthPx: Int = 800

    @Volatile
    var lastKnownHeightPx: Int = 480

    fun updatePreferences(cursorStyle: String, bufferSize: Int) {
        currentCursorStyle = cursorStyle
        currentBufferSize = bufferSize
    }

    fun getOrCreateOrRestoreSessions(context: Context): LocalPtySession {
        val current = getActiveSession()
        if (current != null) return current
        return createNewSession(context)
    }

    fun createNewSession(
        context: Context,
        workingDir: String? = null,
        forceShell: String? = null,
        initialCommand: String? = null,
        sessionTitle: String? = null
    ): LocalPtySession {
        val existingIndices = _sessions.value.mapNotNull {
            it.id.removePrefix("session-").substringBefore("-").toIntOrNull()
        }
        val nextIndex = (existingIndices.maxOrNull() ?: _sessions.value.size) + 1
        val resolvedTitle = sessionTitle ?: if (forceShell == "bash") "Bash $nextIndex" else "T $nextIndex"
        val id = "session-$nextIndex-${System.currentTimeMillis() % 10000}"
        val session = LocalPtySession(
            id = id,
            initialTitle = resolvedTitle,
            context = context.applicationContext,
            initialWorkingDir = workingDir,
            initialCommand = initialCommand,
            forceShell = forceShell,
            initialCols = lastKnownCols,
            initialRows = lastKnownRows,
            initialWidthPx = lastKnownWidthPx,
            initialHeightPx = lastKnownHeightPx
        )
        _sessions.value = _sessions.value + session
        _activeSessionId.value = session.id
        return session
    }

    fun createSession(
        context: Context,
        title: String = "T 1",
        initialCommand: String? = null,
        workingDirectory: String? = null
    ): LocalPtySession {
        val resolvedTitle = if (title == "Bash" || title == "Bash 1") {
            "T ${_sessions.value.size + 1}"
        } else {
            title
        }
        return createNewSession(
            context = context,
            workingDir = workingDirectory,
            initialCommand = initialCommand,
            sessionTitle = resolvedTitle
        )
    }

    fun getActiveSession(): LocalPtySession? {
        val id = _activeSessionId.value ?: return _sessions.value.firstOrNull()
        return _sessions.value.firstOrNull { it.id == id } ?: _sessions.value.firstOrNull()
    }

    fun setActiveSession(id: String) {
        _activeSessionId.value = id
    }

    fun selectSession(id: String) {
        _activeSessionId.value = id
    }

    fun selectNextSession(context: Context? = null) {
        val list = _sessions.value
        if (list.isEmpty()) {
            if (context != null) createNewSession(context)
            return
        }
        val currentIdx = list.indexOfFirst { it.id == _activeSessionId.value }
        val nextIdx = if (currentIdx == -1 || currentIdx >= list.size - 1) 0 else currentIdx + 1
        _activeSessionId.value = list[nextIdx].id
    }

    fun selectPreviousSession(context: Context? = null) {
        val list = _sessions.value
        if (list.isEmpty()) {
            if (context != null) createNewSession(context)
            return
        }
        val currentIdx = list.indexOfFirst { it.id == _activeSessionId.value }
        val prevIdx = if (currentIdx <= 0) list.size - 1 else currentIdx - 1
        _activeSessionId.value = list[prevIdx].id
    }

    fun closeSession(id: String) {
        val session = _sessions.value.firstOrNull { it.id == id }
        session?.finishIfRunning()
        _sessions.value = _sessions.value.filter { it.id != id }
        if (_activeSessionId.value == id) {
            _activeSessionId.value = _sessions.value.firstOrNull()?.id
        }
    }

    fun closeAll() {
        _sessions.value.forEach { it.finishIfRunning() }
        _sessions.value = emptyList()
        _activeSessionId.value = null
    }
}
