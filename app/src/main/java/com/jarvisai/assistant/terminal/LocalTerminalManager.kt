package com.jarvisai.assistant.terminal

import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocalPtySession(
    val id: String,
    initialTitle: String,
    val context: Context,
    initialWorkingDir: String? = null,
    val initialCommand: String? = null,
    initialCols: Int = 80,
    initialRows: Int = 24
) : TerminalSessionClient {

    private val TAG = "LocalPtySession"
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

    private val textChangedListeners = mutableListOf<() -> Unit>()

    init {
        val shell = LocalEnvironmentManager.getDefaultShell(context)
        val prefix = LocalEnvironmentManager.getPrefixDir(context).absolutePath
        val home = LocalEnvironmentManager.getHomeDir(context).absolutePath
        val tmp = LocalEnvironmentManager.getTmpDir(context).absolutePath

        val env = arrayOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "HOME=$home",
            "PREFIX=$prefix",
            "TMPDIR=$tmp",
            "PATH=$prefix/bin:$prefix/bin/applets:/system/bin:/system/xbin",
            "LANG=en_US.UTF-8",
            "LC_ALL=en_US.UTF-8"
        )

        val args = if (initialCommand != null) {
            arrayOf("-c", initialCommand)
        } else {
            emptyArray()
        }

        terminalSession = createTerminalSessionCompat(
            shellPath = shell,
            cwd = workingDirectory,
            args = args,
            env = env,
            transcriptRows = 3000,
            client = this
        )
        terminalSession.updateSize(initialCols, initialRows)
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
        _title.value = changedSession.title ?: "Terminal"
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        val exitCode = finishedSession.exitStatus
        Log.i(TAG, "PTY session $id finished with exit code $exitCode")
        _isExited.value = true
    }

    fun onSessionFinished(finishedSession: TerminalSession, exitCode: Int) {
        Log.i(TAG, "PTY session $id finished with exit code $exitCode")
        _isExited.value = true
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        if (text.isNullOrEmpty()) return
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("Terminal", text))
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
    override fun getTerminalCursorStyle(): Int = 0

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
}

object LocalTerminalManager {

    private val _sessions = MutableStateFlow<List<LocalPtySession>>(emptyList())
    val sessions: StateFlow<List<LocalPtySession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    fun getOrCreateOrRestoreSessions(context: Context): LocalPtySession {
        val current = getActiveSession()
        if (current != null) return current
        return createSession(context, "Bash 1")
    }

    fun createSession(context: Context, title: String = "Bash", initialCommand: String? = null): LocalPtySession {
        val count = _sessions.value.size + 1
        val finalTitle = if (title == "Bash") "Bash $count" else title
        val id = java.util.UUID.randomUUID().toString().substring(0, 8)
        val session = LocalPtySession(
            id = id,
            initialTitle = finalTitle,
            context = context.applicationContext,
            initialCommand = initialCommand
        )
        _sessions.value = _sessions.value + session
        _activeSessionId.value = id
        return session
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

    fun selectNextSession(context: Context) {
        val list = _sessions.value
        if (list.isEmpty()) {
            createSession(context, "Bash 1")
            return
        }
        val currentIdx = list.indexOfFirst { it.id == _activeSessionId.value }
        val nextIdx = if (currentIdx == -1 || currentIdx >= list.size - 1) 0 else currentIdx + 1
        _activeSessionId.value = list[nextIdx].id
    }

    fun selectPreviousSession(context: Context) {
        val list = _sessions.value
        if (list.isEmpty()) {
            createSession(context, "Bash 1")
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
}
