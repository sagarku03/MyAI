package com.jarvisai.assistant.terminal

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.jarvisai.assistant.data.pc.PcConnect
import com.jarvisai.assistant.data.preferences.AuthPreferences
import com.jarvisai.assistant.data.ssh.SshSessionFactory
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.Session
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

data class TmuxWindowInfo(
    val index: Int,
    val name: String,
    val paneId: String,
    val windowId: String,
    val isActive: Boolean = false
)

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
    val showExitNotice: Boolean = true,
    val isSsh: Boolean = false,
    val sshHost: String = "127.0.0.1",
    val sshPort: Int = 8022,
    val sshUser: String = "root",
    val sshPass: String = "",
    var tmuxWindowIndex: Int = -1,
    val tmuxSessionName: String = LocalTerminalManager.TMUX_SESSION_NAME,
    initialPaneId: String = "",
    initialWindowId: String = ""
) : TerminalSessionClient {

    private val tag = "AgentraTerminal"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var workingDirectory: String = initialWorkingDir ?: LocalEnvironmentManager.getHomeDir(context).absolutePath
        private set

    val terminalSession: TerminalSession

    private val _isExited = MutableStateFlow(false)
    val isExited: StateFlow<Boolean> = _isExited.asStateFlow()

    private val _title = MutableStateFlow(initialTitle)
    val title: StateFlow<String> = _title.asStateFlow()

    private val _isSshConnected = MutableStateFlow(false)
    val isSshConnected: StateFlow<Boolean> = _isSshConnected.asStateFlow()

    var name: String
        get() = _title.value
        set(value) {
            _title.value = value
        }

    var ptyCols: Int = initialCols
    var ptyRows: Int = initialRows
    var ptyWidthPx: Int = initialWidthPx
    var ptyHeightPx: Int = initialHeightPx

    @Volatile
    var currentPaneId: String = initialPaneId
        private set

    @Volatile
    var currentWindowId: String = initialWindowId
        private set

    val tmuxPaneId: String?
        get() = currentPaneId.takeIf { it.isNotBlank() }

    val tmuxWindowId: String?
        get() = currentWindowId.takeIf { it.isNotBlank() }

    val isAlternateScreen: Boolean
        get() = terminalSession.emulator?.isAlternateBufferActive == true

    val isMouseTrackingActive: Boolean
        get() = terminalSession.emulator?.isMouseTrackingActive == true

    private val textChangedListeners = mutableListOf<() -> Unit>()
    private val pendingSshOutputBuffer = ByteArrayOutputStream()
    private val outputLock = Any()
    private val sshClosed = AtomicBoolean(false)

    @Volatile
    private var sshSession: Session? = null

    @Volatile
    private var sshControlOut: OutputStream? = null

    @Volatile
    private var sshPlainShellChannel: ChannelShell? = null

    @Volatile
    private var sshControlChannel: ChannelExec? = null

    @Volatile
    private var isUsingTmuxControl: Boolean = false

    private var resizeDebounceJob: Job? = null

    init {
        val safeCols = if (ptyCols > 0) ptyCols else if (LocalTerminalManager.lastKnownCols > 0) LocalTerminalManager.lastKnownCols else 80
        val safeRows = if (ptyRows > 0) ptyRows else if (LocalTerminalManager.lastKnownRows > 0) LocalTerminalManager.lastKnownRows else 24

        val prefixDir = LocalEnvironmentManager.getPrefixDir(context)
        val binDir = LocalEnvironmentManager.getBinDir(context)
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val tmpDir = LocalEnvironmentManager.getTmpDir(context)
        val bufferRows = LocalTerminalManager.currentBufferSize.coerceIn(500, 100000)

        if (isSsh) {
            // Host a silent sleeping PTY as an emulator/render backing canvas for SSH & tmux -CC
            val minimalEnv = arrayOf(
                "TERM=xterm-256color",
                "HOME=${context.filesDir.absolutePath}",
                "TMPDIR=${tmpDir.absolutePath}"
            )
            terminalSession = createTerminalSessionCompat(
                shellPath = "/system/bin/sh",
                cwd = context.filesDir.absolutePath,
                args = arrayOf("sh", "-c", "while true; do sleep 3600; done"),
                env = minimalEnv,
                transcriptRows = bufferRows,
                client = this
            )
            runCatching { terminalSession.updateSize(safeCols, safeRows) }
            startSshBridge(safeCols, safeRows)
        } else {
            // Ensure runtime hooks, glibc, and Agentra termux-api dispatcher are ready
            runCatching {
                LocalEnvironmentManager.ensureGlibcEnvironment(context)
                LocalEnvironmentManager.ensureTermuxApiDispatcher(context)
                TermuxRelocation.ensurePackageHooks(context)
            }.onFailure { Log.w(tag, "Hook init warning: ${it.message}") }

            val workDirFile = File(workingDirectory).apply { runCatching { mkdirs() } }
            if (!workDirFile.exists()) {
                workingDirectory = homeDir.absolutePath
            }

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
                    Log.w(tag, "PRoot PTY launch failed (${err.message}); falling back to $shellBinary")
                    launchDirectWithFallback(shellBinary, shellArgs, env, bufferRows)
                }
            } else {
                launchDirectWithFallback(shellBinary, shellArgs, env, bufferRows)
            }

            runCatching {
                terminalSession.updateSize(safeCols, safeRows)
            }.onFailure { Log.w(tag, "Initial updateSize error: ${it.message}") }
        }
    }

    private fun startSshBridge(cols: Int, rows: Int) {
        sshScope.launch {
            try {
                appendSshStatus("\r\n\u001B[36m[SSH] Connecting to $sshUser@$sshHost:$sshPort...\u001B[0m\r\n")
                val session = SshSessionFactory.openSession(
                    context = context,
                    host = sshHost,
                    port = sshPort,
                    user = sshUser,
                    password = sshPass
                )
                sshSession = session
                _isSshConnected.value = true

                // Check if remote tmux is available when tmuxWindowIndex >= 0
                val useTmux = tmuxWindowIndex >= 0 && runCatching {
                    val ch = session.openChannel("exec") as ChannelExec
                    ch.setCommand("export PATH=\"${LocalTerminalManager.UNIVERSAL_SSH_PATH}:\$PATH\"; command -v tmux >/dev/null 2>&1 && echo TMUX_OK")
                    val out = ByteArrayOutputStream()
                    ch.outputStream = out
                    ch.connect(4_000)
                    val dl = System.currentTimeMillis() + 4_000
                    while (!ch.isClosed && System.currentTimeMillis() < dl) Thread.sleep(20)
                    ch.disconnect()
                    out.toString("UTF-8").contains("TMUX_OK")
                }.getOrDefault(false)

                if (useTmux) {
                    runTmuxControlBridge(session, cols, rows)
                } else {
                    runPlainSshShell(session, cols, rows)
                }
            } catch (e: Exception) {
                Log.w(tag, "SSH session error: ${e.message}", e)
                appendSshStatus("\r\n\u001B[31m[SSH Error: ${e.message ?: "Connection failed"}]\u001B[0m\r\n")
                notifySessionClosed(-1)
            }
        }
    }

    private fun runTmuxControlBridge(session: Session, cols: Int, rows: Int) {
        isUsingTmuxControl = true
        val targetSpec = if (currentWindowId.isNotBlank()) {
            currentWindowId
        } else {
            "$tmuxSessionName:$tmuxWindowIndex"
        }

        // Capture scrollback history cleanly before attaching -CC
        runCatching {
            val histCh = session.openChannel("exec") as ChannelExec
            histCh.setCommand(
                "export PATH=\"${LocalTerminalManager.UNIVERSAL_SSH_PATH}:\$PATH\"; " +
                    "tmux capture-pane -e -p -S -2000 -t '$targetSpec' 2>/dev/null || true"
            )
            val histOut = ByteArrayOutputStream()
            histCh.outputStream = histOut
            histCh.connect(5_000)
            val dl = System.currentTimeMillis() + 5_000
            while (!histCh.isClosed && System.currentTimeMillis() < dl) Thread.sleep(20)
            histCh.disconnect()
            val rawHist = histOut.toString("UTF-8")
            if (rawHist.isNotBlank()) {
                val cleaned = TmuxControlParser.stripScreenTitle(normalizeHistoryNewlines(rawHist))
                appendBytesToEmulator(cleaned.toByteArray(Charsets.UTF_8))
            }
        }

        val ch = session.openChannel("exec") as ChannelExec
        val attachCmd = buildString {
            append("export PATH=\"${LocalTerminalManager.UNIVERSAL_SSH_PATH}:\$PATH\"; ")
            append("export LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8; ")
            append("tmux has-session -t '$tmuxSessionName' 2>/dev/null || tmux new-session -d -s '$tmuxSessionName' -x $cols -y $rows; ")
            append("tmux -CC attach-session -t '$tmuxSessionName'")
        }
        ch.setCommand(attachCmd)
        val stdin = ch.outputStream
        val stdout = ch.inputStream
        sshControlOut = stdin
        sshControlChannel = ch

        val parser = TmuxControlParser(
            onOutput = { paneId, data ->
                if (currentPaneId.isBlank()) {
                    currentPaneId = paneId
                }
                if (paneId == currentPaneId) {
                    appendBytesToEmulator(data)
                }
            },
            onWindowRenamed = { winId, newName ->
                if (winId == currentWindowId && newName.isNotBlank()) {
                    _title.value = LocalTerminalManager.formatTmuxTitle(tmuxWindowIndex.coerceAtLeast(0), newName)
                }
            },
            onWindowClosed = { winId ->
                if (winId == currentWindowId && !sshClosed.get()) {
                    notifySessionClosed(0)
                }
            },
            onExit = { reason ->
                if (!sshClosed.get()) {
                    appendSshStatus("\r\n\u001B[90m[tmux detached${if (reason.isNotBlank()) ": $reason" else ""}]\u001B[0m\r\n")
                    notifySessionClosed(0)
                }
            }
        )

        ch.connect(8_000)

        // Focus target window & set client dimensions
        runCatching {
            val initCmds = buildString {
                append("refresh-client -C ${cols}x${rows}\n")
                append("select-window -t '$targetSpec'\n")
                if (currentWindowId.isNotBlank()) {
                    append("resize-window -t '$currentWindowId' -x $cols -y $rows\n")
                }
            }
            synchronized(outputLock) {
                stdin.write(initCmds.toByteArray(Charsets.UTF_8))
                stdin.flush()
            }
        }

        val buffer = ByteArray(8192)
        while (!sshClosed.get() && !ch.isClosed) {
            val n = stdout.read(buffer)
            if (n < 0) break
            if (n > 0) {
                parser.feed(buffer, n)
            }
        }
        notifySessionClosed(if (ch.isClosed) ch.exitStatus else 0)
    }

    private fun runPlainSshShell(session: Session, cols: Int, rows: Int) {
        isUsingTmuxControl = false
        val ch = session.openChannel("shell") as ChannelShell
        ch.setPtyType("xterm-256color", cols, rows, ptyWidthPx, ptyHeightPx)
        val stdin = ch.outputStream
        val stdout = ch.inputStream
        sshControlOut = stdin
        sshPlainShellChannel = ch
        ch.connect(8_000)

        if (!initialCommand.isNullOrBlank()) {
            runCatching {
                val cmdBytes = "$initialCommand\n".toByteArray(Charsets.UTF_8)
                synchronized(outputLock) {
                    stdin.write(cmdBytes)
                    stdin.flush()
                }
            }
        }

        val buffer = ByteArray(8192)
        while (!sshClosed.get() && !ch.isClosed) {
            val n = stdout.read(buffer)
            if (n < 0) break
            if (n > 0) {
                appendBytesToEmulator(buffer.copyOf(n))
            }
        }
        notifySessionClosed(if (ch.isClosed) ch.exitStatus else 0)
    }

    private fun normalizeHistoryNewlines(raw: String): String {
        val sb = StringBuilder(raw.length + 64)
        var prev = '\u0000'
        for (ch in raw) {
            if (ch == '\n' && prev != '\r') {
                sb.append("\r\n")
            } else {
                sb.append(ch)
            }
            prev = ch
        }
        return sb.toString()
    }

    private fun appendSshStatus(msg: String) {
        appendBytesToEmulator(msg.toByteArray(Charsets.UTF_8))
    }

    private fun appendBytesToEmulator(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        mainHandler.post {
            val emu = terminalSession.emulator
            if (emu != null) {
                val buffered = synchronized(pendingSshOutputBuffer) {
                    if (pendingSshOutputBuffer.size() > 0) {
                        pendingSshOutputBuffer.toByteArray().also { pendingSshOutputBuffer.reset() }
                    } else null
                }
                if (buffered != null) {
                    emu.append(buffered, buffered.size)
                }
                emu.append(bytes, bytes.size)
                onTextChanged(terminalSession)
            } else {
                synchronized(pendingSshOutputBuffer) {
                    pendingSshOutputBuffer.write(bytes)
                }
            }
        }
    }

    fun hookEmulatorForSsh() {
        mainHandler.post {
            val emu = terminalSession.emulator ?: return@post
            val pending = synchronized(pendingSshOutputBuffer) {
                if (pendingSshOutputBuffer.size() > 0) {
                    pendingSshOutputBuffer.toByteArray().also { pendingSshOutputBuffer.reset() }
                } else null
            }
            if (pending != null) {
                emu.append(pending, pending.size)
                onTextChanged(terminalSession)
            }
        }
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
            Log.w(tag, "Preferred shell ($preferredShell) failed (${err.message}); falling back to /system/bin/sh")
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

    fun notifyTextChanged() {
        onTextChanged(terminalSession)
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
        notifySessionClosed(finishedSession.exitStatus)
    }

    fun onSessionFinished(finishedSession: TerminalSession, exitCode: Int) {
        notifySessionClosed(exitCode)
    }

    fun notifySessionClosed(exitCode: Int = 0) {
        if (!sshClosed.compareAndSet(false, true)) return
        _isSshConnected.value = false
        if (showExitNotice) {
            mainHandler.post {
                runCatching {
                    val msg = "\r\n\u001B[90m[Process completed (code $exitCode) - press Enter to close tab]\u001B[0m\r\n"
                    val bytes = msg.toByteArray(Charsets.UTF_8)
                    terminalSession.emulator?.append(bytes, bytes.size)
                    onTextChanged(terminalSession)
                }
            }
        }
        _isExited.value = true
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        if (text.isNullOrEmpty()) return
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("Agentra Terminal", text))
        } catch (e: Exception) {
            Log.w(tag, "Clipboard copy failed: ${e.message}")
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
                    if (isSsh) {
                        write(pasteText)
                    } else {
                        terminalSession.emulator?.paste(pasteText)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Clipboard paste failed: ${e.message}")
        }
    }

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {
        onTextChanged(session)
    }

    override fun onTerminalCursorStateChange(state: Boolean) {}

    fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    override fun getTerminalCursorStyle(): Int {
        return LocalTerminalManager.currentCursorStyleInt
    }

    override fun logError(tag: String?, message: String?) {
        if (!message.isNullOrBlank()) Log.e(tag ?: this.tag, message)
    }

    override fun logWarn(tag: String?, message: String?) {
        if (!message.isNullOrBlank()) Log.w(tag ?: this.tag, message)
    }

    override fun logInfo(tag: String?, message: String?) {
        if (!message.isNullOrBlank()) Log.i(tag ?: this.tag, message)
    }

    override fun logDebug(tag: String?, message: String?) {
        if (!message.isNullOrBlank()) Log.d(tag ?: this.tag, message)
    }

    override fun logVerbose(tag: String?, message: String?) {
        if (!message.isNullOrBlank()) Log.v(tag ?: this.tag, message)
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: this.tag, message ?: "Terminal exception", e)
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.e(tag ?: this.tag, "Terminal stacktrace", e)
    }

    fun write(data: String) {
        if (_isExited.value) return
        if (!isSsh) {
            terminalSession.write(data)
            return
        }
        val out = sshControlOut ?: return
        sshScope.launch {
            runCatching {
                if (isUsingTmuxControl) {
                    val target = currentPaneId.ifBlank { "$tmuxSessionName:$tmuxWindowIndex" }
                    val rawBytes = data.toByteArray(Charsets.UTF_8)
                    var offset = 0
                    val chunkSize = 180
                    while (offset < rawBytes.size) {
                        val end = minOf(offset + chunkSize, rawBytes.size)
                        val chunk = rawBytes.copyOfRange(offset, end)
                        val hex = TmuxControlParser.encodeBytesToHex(chunk)
                        val cmd = "send-keys -H -t $target $hex\n"
                        synchronized(outputLock) {
                            out.write(cmd.toByteArray(Charsets.UTF_8))
                            out.flush()
                        }
                        offset = end
                    }
                } else {
                    synchronized(outputLock) {
                        out.write(data.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }
            }.onFailure { err ->
                Log.w(tag, "SSH write failed: ${err.message}")
            }
        }
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
        runCatching {
            terminalSession.updateSize(cols, rows)
        }.onFailure { Log.w(tag, "Failed to resize terminal session: ${it.message}") }

        if (isSsh) {
            hookEmulatorForSsh()
            resizeDebounceJob?.cancel()
            resizeDebounceJob = sshScope.launch {
                delay(80)
                if (!isActive || sshClosed.get()) return@launch
                if (isUsingTmuxControl) {
                    val out = sshControlOut ?: return@launch
                    val winTarget = currentWindowId.ifBlank { "$tmuxSessionName:$tmuxWindowIndex" }
                    val cmd = "refresh-client -C ${cols}x${rows}\nresize-window -t '$winTarget' -x $cols -y $rows\n"
                    runCatching {
                        synchronized(outputLock) {
                            out.write(cmd.toByteArray(Charsets.UTF_8))
                            out.flush()
                        }
                    }
                } else {
                    runCatching {
                        sshPlainShellChannel?.setPtySize(cols, rows, widthPx, heightPx)
                    }
                }
            }
        }
    }

    fun finishIfRunning(killRemoteTmux: Boolean = false) {
        sshClosed.set(true)
        _isSshConnected.value = false
        resizeDebounceJob?.cancel()
        if (isSsh) {
            val capturedHost = sshHost
            val capturedPort = sshPort
            val capturedUser = sshUser
            val capturedPass = sshPass
            val capturedWinId = currentWindowId
            val capturedPaneId = currentPaneId
            val capturedSess = tmuxSessionName
            val capturedIdx = tmuxWindowIndex

            runCatching { sshControlOut?.close() }
            runCatching { sshControlChannel?.disconnect() }
            runCatching { sshPlainShellChannel?.disconnect() }
            runCatching { sshSession?.disconnect() }

            if (killRemoteTmux && capturedIdx >= 0) {
                sshScope.launch {
                    val target = capturedPaneId.ifBlank { capturedWinId }.ifBlank { "$capturedSess:$capturedIdx" }
                    val cmd = if (capturedPaneId.isNotBlank()) {
                        "tmux kill-pane -t '$capturedPaneId' 2>/dev/null || tmux kill-window -t '$target' 2>/dev/null || true"
                    } else {
                        "tmux kill-window -t '$target' 2>/dev/null || true"
                    }
                    runCatching {
                        SshSessionFactory.execCommand(
                            context = context,
                            host = capturedHost,
                            port = capturedPort,
                            user = capturedUser,
                            password = capturedPass,
                            command = "export PATH=\"${LocalTerminalManager.UNIVERSAL_SSH_PATH}:\$PATH\"; $cmd"
                        )
                    }
                }
            }
        }
        runCatching { terminalSession.finishIfRunning() }
        _isExited.value = true
    }

    fun close() = finishIfRunning(killRemoteTmux = isSsh)
}

object LocalTerminalManager {

    private const val TAG = "LocalTerminalManager"
    const val TMUX_SESSION_NAME = "agentra"
    const val UNIVERSAL_SSH_PATH =
        "/data/data/ai.agentra/files/usr/bin:/data/data/ai.agentra/files/home/.local/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
    private const val PREFS_TMUX_STATE = "agentra_tmux_state"
    private const val KEY_LAST_TMUX_INDEX = "last_active_tmux_index"

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sessions = MutableStateFlow<List<LocalPtySession>>(emptyList())
    val sessions: StateFlow<List<LocalPtySession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _isSyncingTmux = MutableStateFlow(false)
    val isSyncingTmux: StateFlow<Boolean> = _isSyncingTmux.asStateFlow()

    @Volatile
    var currentCursorStyle: String = "BAR"

    val currentCursorStyleInt: Int
        get() = when (currentCursorStyle.uppercase()) {
            "BLOCK" -> 0
            "UNDERLINE" -> 1
            else -> 2
        }

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

    fun formatTmuxTitle(index: Int, rawName: String? = null): String {
        val cleanName = rawName?.trim()?.takeIf { it.isNotBlank() && it != "bash" && it != "zsh" }
        return if (cleanName != null) "T$index · $cleanName" else "T$index"
    }

    fun updatePreferences(cursorStyle: String, bufferSize: Int) {
        currentCursorStyle = cursorStyle
        currentBufferSize = bufferSize
        _sessions.value.forEach { session ->
            runCatching { session.terminalSession.emulator?.setCursorStyle() }
            session.notifyTextChanged()
        }
    }

    fun saveLastActiveTmuxIndex(context: Context, index: Int) {
        if (index < 0) return
        context.applicationContext.getSharedPreferences(PREFS_TMUX_STATE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LAST_TMUX_INDEX, index).apply()
    }

    fun getLastActiveTmuxIndex(context: Context): Int {
        return context.applicationContext.getSharedPreferences(PREFS_TMUX_STATE, Context.MODE_PRIVATE)
            .getInt(KEY_LAST_TMUX_INDEX, -1)
    }

    private fun resolveSshParams(context: Context): SshTargetConfig {
        val prefs = AuthPreferences.getInstance(context)
        val activePc = PcConnect.getActivePairedPc()
        val host = prefs.getSshHostSync().let { configured ->
            if (configured == "127.0.0.1" && activePc != null && activePc.lastAddress.isNotBlank()) {
                activePc.lastAddress
            } else {
                configured
            }
        }
        val port = if (prefs.getSshHostSync() == "127.0.0.1" && activePc != null && activePc.sshPort > 0) {
            activePc.sshPort
        } else {
            prefs.getSshPortSync()
        }
        val user = if (prefs.getSshUserSync() == "root" && activePc != null && activePc.user.isNotBlank()) {
            activePc.user
        } else {
            prefs.getSshUserSync()
        }
        return SshTargetConfig(
            enabled = prefs.isUseSshTerminalSync(),
            host = host,
            port = port,
            user = user,
            pass = prefs.getSshPassSync()
        )
    }

    private data class SshTargetConfig(
        val enabled: Boolean,
        val host: String,
        val port: Int,
        val user: String,
        val pass: String
    )

    suspend fun probeRemoteTmuxWindows(
        context: Context,
        host: String,
        port: Int,
        user: String,
        pass: String
    ): List<TmuxWindowInfo> = withContext(Dispatchers.IO) {
        val cmd = buildString {
            append("export PATH=\"$UNIVERSAL_SSH_PATH:\$PATH\"; ")
            append("if command -v tmux >/dev/null 2>&1 && tmux has-session -t '$TMUX_SESSION_NAME' 2>/dev/null; then ")
            append("tmux list-windows -t '$TMUX_SESSION_NAME' -F '#{window_index}|#{window_name}|#{pane_id}|#{window_id}|#{window_active}'; ")
            append("fi")
        }
        val (_, out) = runCatching {
            SshSessionFactory.execCommand(context, host, port, user, pass, cmd, timeoutMs = 6_000)
        }.getOrDefault(-1 to "")

        out.lines()
            .mapNotNull { line ->
                val parts = line.trim().split("|")
                if (parts.size < 4) return@mapNotNull null
                val idx = parts[0].toIntOrNull() ?: return@mapNotNull null
                TmuxWindowInfo(
                    index = idx,
                    name = parts[1],
                    paneId = parts[2],
                    windowId = parts[3],
                    isActive = parts.getOrNull(4) == "1"
                )
            }
    }

    suspend fun createRemoteTmuxWindow(
        context: Context,
        host: String,
        port: Int,
        user: String,
        pass: String
    ): TmuxWindowInfo? = withContext(Dispatchers.IO) {
        val cmd = buildString {
            append("export PATH=\"$UNIVERSAL_SSH_PATH:\$PATH\"; ")
            append("tmux has-session -t '$TMUX_SESSION_NAME' 2>/dev/null || tmux new-session -d -s '$TMUX_SESSION_NAME'; ")
            append("tmux new-window -t '$TMUX_SESSION_NAME' -P -F '#{window_index}|#{window_name}|#{pane_id}|#{window_id}|1'")
        }
        val (_, out) = runCatching {
            SshSessionFactory.execCommand(context, host, port, user, pass, cmd, timeoutMs = 6_000)
        }.getOrDefault(-1 to "")

        val parts = out.trim().lines().lastOrNull().orEmpty().split("|")
        if (parts.size < 4) return@withContext null
        val idx = parts[0].toIntOrNull() ?: return@withContext null
        TmuxWindowInfo(
            index = idx,
            name = parts[1],
            paneId = parts[2],
            windowId = parts[3],
            isActive = true
        )
    }

    suspend fun getOrCreateOrRestoreSessions(context: Context): LocalPtySession {
        val existing = getActiveSession()
        if (existing != null) return existing

        val appCtx = context.applicationContext
        val sshCfg = resolveSshParams(appCtx)
        if (!sshCfg.enabled) {
            return createNewSession(appCtx)
        }

        _isSyncingTmux.value = true
        return try {
            val remoteWindows = probeRemoteTmuxWindows(appCtx, sshCfg.host, sshCfg.port, sshCfg.user, sshCfg.pass)
            if (remoteWindows.isNotEmpty()) {
                val restored = remoteWindows.map { win ->
                    LocalPtySession(
                        id = "ssh-tmux-${win.index}-${System.currentTimeMillis() % 10000}",
                        initialTitle = formatTmuxTitle(win.index, win.name),
                        context = appCtx,
                        isSsh = true,
                        sshHost = sshCfg.host,
                        sshPort = sshCfg.port,
                        sshUser = sshCfg.user,
                        sshPass = sshCfg.pass,
                        tmuxWindowIndex = win.index,
                        tmuxSessionName = TMUX_SESSION_NAME,
                        initialPaneId = win.paneId,
                        initialWindowId = win.windowId
                    )
                }
                _sessions.value = restored
                val savedIdx = getLastActiveTmuxIndex(appCtx)
                val target = restored.firstOrNull { it.tmuxWindowIndex == savedIdx }
                    ?: restored.firstOrNull { win -> remoteWindows.any { it.index == win.tmuxWindowIndex && it.isActive } }
                    ?: restored.first()
                _activeSessionId.value = target.id
                target
            } else {
                // Attempt to spawn window 0 in remote tmux session, else direct SSH shell
                val firstWin = createRemoteTmuxWindow(appCtx, sshCfg.host, sshCfg.port, sshCfg.user, sshCfg.pass)
                if (firstWin != null) {
                    val session = LocalPtySession(
                        id = "ssh-tmux-${firstWin.index}-${System.currentTimeMillis() % 10000}",
                        initialTitle = formatTmuxTitle(firstWin.index, firstWin.name),
                        context = appCtx,
                        isSsh = true,
                        sshHost = sshCfg.host,
                        sshPort = sshCfg.port,
                        sshUser = sshCfg.user,
                        sshPass = sshCfg.pass,
                        tmuxWindowIndex = firstWin.index,
                        tmuxSessionName = TMUX_SESSION_NAME,
                        initialPaneId = firstWin.paneId,
                        initialWindowId = firstWin.windowId
                    )
                    _sessions.value = _sessions.value + session
                    _activeSessionId.value = session.id
                    saveLastActiveTmuxIndex(appCtx, firstWin.index)
                    session
                } else {
                    val fallbackSsh = LocalPtySession(
                        id = "ssh-direct-${System.currentTimeMillis() % 10000}",
                        initialTitle = "SSH (${sshCfg.user}@${sshCfg.host})",
                        context = appCtx,
                        isSsh = true,
                        sshHost = sshCfg.host,
                        sshPort = sshCfg.port,
                        sshUser = sshCfg.user,
                        sshPass = sshCfg.pass,
                        tmuxWindowIndex = -1
                    )
                    _sessions.value = _sessions.value + fallbackSsh
                    _activeSessionId.value = fallbackSsh.id
                    fallbackSsh
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SSH/tmux restore failed (${e.message}); falling back to local PTY")
            createNewSession(appCtx, forceLocal = true)
        } finally {
            _isSyncingTmux.value = false
        }
    }

    fun createNewSession(
        context: Context,
        workingDir: String? = null,
        forceShell: String? = null,
        initialCommand: String? = null,
        sessionTitle: String? = null,
        forceLocal: Boolean = false
    ): LocalPtySession {
        val appCtx = context.applicationContext
        val sshCfg = resolveSshParams(appCtx)
        if (sshCfg.enabled && !forceLocal && forceShell == null && workingDir == null) {
            val nextIdx = (_sessions.value.mapNotNull { it.tmuxWindowIndex.takeIf { idx -> idx >= 0 } }.maxOrNull() ?: -1) + 1
            val session = LocalPtySession(
                id = "ssh-$nextIdx-${System.currentTimeMillis() % 10000}",
                initialTitle = sessionTitle ?: "SSH T$nextIdx",
                context = appCtx,
                initialCommand = initialCommand,
                isSsh = true,
                sshHost = sshCfg.host,
                sshPort = sshCfg.port,
                sshUser = sshCfg.user,
                sshPass = sshCfg.pass,
                tmuxWindowIndex = -1
            )
            _sessions.value = _sessions.value + session
            _activeSessionId.value = session.id
            managerScope.launch {
                val created = createRemoteTmuxWindow(appCtx, sshCfg.host, sshCfg.port, sshCfg.user, sshCfg.pass)
                if (created != null) {
                    val tmuxSess = LocalPtySession(
                        id = "ssh-tmux-${created.index}-${System.currentTimeMillis() % 10000}",
                        initialTitle = sessionTitle ?: formatTmuxTitle(created.index, created.name),
                        context = appCtx,
                        isSsh = true,
                        sshHost = sshCfg.host,
                        sshPort = sshCfg.port,
                        sshUser = sshCfg.user,
                        sshPass = sshCfg.pass,
                        tmuxWindowIndex = created.index,
                        tmuxSessionName = TMUX_SESSION_NAME,
                        initialPaneId = created.paneId,
                        initialWindowId = created.windowId
                    )
                    val updated = _sessions.value.map { if (it.id == session.id) tmuxSess else it }
                    _sessions.value = updated
                    if (_activeSessionId.value == session.id) {
                        _activeSessionId.value = tmuxSess.id
                    }
                    session.finishIfRunning(killRemoteTmux = false)
                    saveLastActiveTmuxIndex(appCtx, created.index)
                }
            }
            return session
        }

        val existingIndices = _sessions.value.mapNotNull {
            it.id.removePrefix("session-").substringBefore("-").toIntOrNull()
        }
        val nextIndex = (existingIndices.maxOrNull() ?: _sessions.value.size) + 1
        val resolvedTitle = sessionTitle ?: if (forceShell == "bash") "Bash $nextIndex" else "T $nextIndex"
        val id = "session-$nextIndex-${System.currentTimeMillis() % 10000}"
        val session = LocalPtySession(
            id = id,
            initialTitle = resolvedTitle,
            context = appCtx,
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
            sessionTitle = resolvedTitle,
            forceLocal = workingDirectory != null
        )
    }

    fun getActiveSession(): LocalPtySession? {
        val id = _activeSessionId.value ?: return _sessions.value.firstOrNull()
        return _sessions.value.firstOrNull { it.id == id } ?: _sessions.value.firstOrNull()
    }

    fun setActiveSession(id: String) {
        _activeSessionId.value = id
        val s = _sessions.value.firstOrNull { it.id == id }
        if (s != null && s.isSsh && s.tmuxWindowIndex >= 0) {
            saveLastActiveTmuxIndex(s.context, s.tmuxWindowIndex)
        }
    }

    fun selectSession(id: String) = setActiveSession(id)

    fun selectNextSession(context: Context? = null) {
        val list = _sessions.value
        if (list.isEmpty()) {
            if (context != null) createNewSession(context)
            return
        }
        val currentIdx = list.indexOfFirst { it.id == _activeSessionId.value }
        val nextIdx = if (currentIdx == -1 || currentIdx >= list.size - 1) 0 else currentIdx + 1
        setActiveSession(list[nextIdx].id)
    }

    fun selectPreviousSession(context: Context? = null) {
        val list = _sessions.value
        if (list.isEmpty()) {
            if (context != null) createNewSession(context)
            return
        }
        val currentIdx = list.indexOfFirst { it.id == _activeSessionId.value }
        val prevIdx = if (currentIdx <= 0) list.size - 1 else currentIdx - 1
        setActiveSession(list[prevIdx].id)
    }

    fun closeSession(id: String) {
        val session = _sessions.value.firstOrNull { it.id == id }
        session?.finishIfRunning(killRemoteTmux = session.isSsh)
        _sessions.value = _sessions.value.filter { it.id != id }
        if (_activeSessionId.value == id) {
            val next = _sessions.value.firstOrNull()
            _activeSessionId.value = next?.id
            if (next != null && next.isSsh && next.tmuxWindowIndex >= 0) {
                saveLastActiveTmuxIndex(next.context, next.tmuxWindowIndex)
            }
        }
    }

    fun closeAll() {
        _sessions.value.forEach { it.finishIfRunning(killRemoteTmux = false) }
        _sessions.value = emptyList()
        _activeSessionId.value = null
    }
}
