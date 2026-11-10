package com.jarvisai.assistant.terminal

import android.content.Context
import android.system.Os
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.*
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

    private var onTextChangedCallback: (() -> Unit)? = null

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
            transcriptRows = 2000,
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

    fun setOnTextChangedListener(listener: (() -> Unit)?) {
        onTextChangedCallback = listener
    }

    override fun onTextChanged(changedSession: TerminalSession) {
        onTextChangedCallback?.invoke()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        _title.value = changedSession.title ?: "Terminal"
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        val exitCode = finishedSession.exitStatus
        onSessionFinished(finishedSession, exitCode)
    }

    fun onSessionFinished(finishedSession: TerminalSession, exitCode: Int) {
        Log.i(TAG, "PTY session $id finished with exit code $exitCode")
        _isExited.value = true
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}
    fun onClipboardText(session: TerminalSession, text: String) {}

    override fun onPasteTextFromClipboard(session: TerminalSession?) {}

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {}

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
        terminalSession.write(data)
    }

    fun finishIfRunning() {
        terminalSession.finishIfRunning()
    }
}

object LocalTerminalManager {

    private val _sessions = MutableStateFlow<List<LocalPtySession>>(emptyList())
    val sessions: StateFlow<List<LocalPtySession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    fun createSession(context: Context, title: String = "Terminal", initialCommand: String? = null): LocalPtySession {
        val id = java.util.UUID.randomUUID().toString().substring(0, 8)
        val session = LocalPtySession(
            id = id,
            initialTitle = title,
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

    fun closeSession(id: String) {
        val session = _sessions.value.firstOrNull { it.id == id }
        session?.finishIfRunning()
        _sessions.value = _sessions.value.filter { it.id != id }
        if (_activeSessionId.value == id) {
            _activeSessionId.value = _sessions.value.firstOrNull()?.id
        }
    }
}
