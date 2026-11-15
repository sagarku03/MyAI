package com.jarvisai.assistant.ide

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarvisai.assistant.agent.agy.CloudPreviewServer
import com.jarvisai.assistant.browser.JarvisBrowserActivity
import com.jarvisai.assistant.data.ide.RunConfigManager
import com.jarvisai.assistant.data.ide.RunConfiguration
import com.jarvisai.assistant.terminal.LocalPtySession
import com.jarvisai.assistant.theme.ThemeTerminal
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Phase 4: Interactive Code Execution & Full-Screen Live Web Preview (`IdeExecutionRunnerSheet`).
 * Opens HTML/Web projects in 100% Full-Screen interactive WebView mode with zero PTY errors,
 * and supports split/full-screen toggle plus native `TerminalView` for CLI scripts.
 */
data class IdeExecutionRequest(
    val title: String,
    val projectPath: String,
    val activeFile: File? = null,
    val configuration: RunConfiguration
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IdeExecutionRunnerSheet(
    request: IdeExecutionRequest,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isWebConfig = remember(request) {
        request.configuration.type.equals("webview", ignoreCase = true) ||
            request.activeFile?.extension?.lowercase() in setOf("html", "htm")
    }
    val execCommand = remember(request) {
        RunConfigManager.buildExecutionCommand(
            config = request.configuration,
            projectPath = request.projectPath,
            activeFile = request.activeFile
        )
    }

    var rerunNonce by remember { mutableIntStateOf(0) }
    var showWebPreview by remember(request) { mutableStateOf(isWebConfig) }
    var isFullScreen by remember(request) { mutableStateOf(true) }
    var ctrlArmed by remember { mutableStateOf(false) }
    var altArmed by remember { mutableStateOf(false) }
    var terminalFontSizePx by remember { mutableIntStateOf(30) }
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webProgress by remember { mutableIntStateOf(0) }

    var session by remember { mutableStateOf<LocalPtySession?>(null) }
    var sessionError by remember { mutableStateOf<String?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }

    // Create PTY session ONLY when running non-web CLI commands (prevents PTY permission error on Web Preview)
    LaunchedEffect(request, rerunNonce, showWebPreview) {
        if (showWebPreview && isWebConfig) {
            sessionError = null
            return@LaunchedEffect
        }
        sessionError = null
        val previous = session
        val createdResult = withContext(Dispatchers.IO) {
            runCatching {
                LocalPtySession(
                    id = "run_" + java.util.UUID.randomUUID().toString().substring(0, 6),
                    initialTitle = "Run: ${request.title}",
                    context = context.applicationContext,
                    initialWorkingDir = request.projectPath,
                    initialCommand = "$execCommand\necho '\n[Process Completed]'\nexec sh"
                )
            }
        }
        createdResult.onSuccess { newSession ->
            session = newSession
            runCatching { previous?.finishIfRunning() }
        }.onFailure { err ->
            sessionError = err.localizedMessage ?: "Failed to initialize PTY runner"
            IdeDaemonLogBuffer.append("ERROR", "Execution runner PTY init failed: ${err.message}")
        }
    }

    // Start local HTTP preview server off the main thread when Web Preview is active
    LaunchedEffect(request.projectPath, request.activeFile, showWebPreview, rerunNonce) {
        if (showWebPreview) {
            val baseServerUrl = withContext(Dispatchers.IO) {
                runCatching { CloudPreviewServer.start(File(request.projectPath)) }.getOrNull()
            }
            val activeFile = request.activeFile
            previewUrl = when {
                baseServerUrl != null && activeFile != null && activeFile.extension.lowercase() in setOf("html", "htm") -> {
                    val relPath = activeFile.absolutePath
                        .removePrefix(File(request.projectPath).absolutePath)
                        .removePrefix("/")
                        .ifBlank { activeFile.name }
                    "$baseServerUrl/$relPath"
                }
                baseServerUrl != null -> baseServerUrl
                activeFile != null && activeFile.exists() -> "file://${activeFile.absolutePath}"
                else -> null
            }
            webViewRef?.let { view ->
                previewUrl?.let { url ->
                    runCatching { view.loadUrl(url) }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { session?.finishIfRunning() }
        }
    }

    val activeSession = session
    val isExited by (activeSession?.isExited?.collectAsState() ?: remember { mutableStateOf(false) })

    DisposableEffect(activeSession) {
        if (activeSession == null) {
            onDispose {}
        } else {
            val listener: () -> Unit = {
                terminalViewRef?.post {
                    runCatching { terminalViewRef?.onScreenUpdated() }
                }
            }
            activeSession.addTextChangedListener(listener)
            onDispose {
                activeSession.removeTextChangedListener(listener)
            }
        }
    }

    val sheetShape = if (isFullScreen || showWebPreview) {
        RoundedCornerShape(0.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isFullScreen || showWebPreview) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxHeight(0.68f)
                        .border(1.dp, Color(0xFF1E3A5F), sheetShape)
                }
            )
            .testTag("ide_execution_runner_sheet"),
        color = Color(0xFF070B14),
        shape = sheetShape
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    showWebPreview -> Color(0xFF34D399)
                                    sessionError != null -> Color(0xFFF85149)
                                    isExited -> Color(0xFF64748B)
                                    else -> Color(0xFF34D399)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = request.configuration.name,
                            color = Color(0xFFF0F6FC),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = if (showWebPreview) {
                                previewUrl ?: (request.activeFile?.name ?: "Starting Live Web Server...")
                            } else {
                                execCommand.take(64)
                            },
                            color = if (showWebPreview) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isFullScreen = !isFullScreen },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("ide_runner_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullScreen) "Split Screen" else "Full Screen",
                            tint = Color(0xFF38BDF8)
                        )
                    }

                    IconButton(
                        onClick = { showWebPreview = !showWebPreview },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("ide_runner_toggle_web_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Web,
                            contentDescription = "Toggle Web Preview",
                            tint = if (showWebPreview) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                        )
                    }

                    if (showWebPreview && previewUrl != null) {
                        IconButton(
                            onClick = {
                                runCatching {
                                    val intent = Intent(context, JarvisBrowserActivity::class.java).apply {
                                        putExtra(JarvisBrowserActivity.EXTRA_URL, previewUrl)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("ide_runner_open_browser_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in Jarvis Browser",
                                tint = Color(0xFFFBBF24)
                            )
                        }
                    }

                    if (!showWebPreview) {
                        IconButton(
                            onClick = { activeSession?.write("\u0003") },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("ide_runner_kill_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Send Ctrl+C Interrupt",
                                tint = Color(0xFFF85149)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (showWebPreview) {
                                runCatching { webViewRef?.reload() }
                            }
                            rerunNonce++
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("ide_runner_rerun_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload / Re-run",
                            tint = Color(0xFF34D399)
                        )
                    }

                    IconButton(
                        onClick = {
                            runCatching { activeSession?.finishIfRunning() }
                            onClose()
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("ide_runner_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Runner",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            if (showWebPreview && webProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { webProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF00E5FF),
                    trackColor = Color(0xFF0F172A)
                )
            }

            // Content body: Full-Screen Interactive WebView OR Native Termux TerminalView
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    showWebPreview -> {
                        val effectiveUrl = previewUrl
                            ?: request.activeFile?.takeIf { it.exists() }?.let { "file://${it.absolutePath}" }
                        if (effectiveUrl != null) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        runCatching {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.databaseEnabled = true
                                            settings.allowFileAccess = true
                                            settings.allowContentAccess = true
                                            settings.useWideViewPort = true
                                            settings.loadWithOverviewMode = true
                                            settings.mediaPlaybackRequiresUserGesture = false
                                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            settings.setSupportZoom(true)
                                            settings.builtInZoomControls = true
                                            settings.displayZoomControls = false
                                            webViewClient = WebViewClient()
                                            webChromeClient = object : WebChromeClient() {
                                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                    webProgress = newProgress
                                                }
                                            }
                                            loadUrl(effectiveUrl)
                                        }
                                        webViewRef = this
                                    }
                                },
                                update = { view ->
                                    webViewRef = view
                                    if (view.url != effectiveUrl && effectiveUrl.startsWith("http")) {
                                        runCatching { view.loadUrl(effectiveUrl) }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                                Text("Starting Live Web Preview...", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        }
                    }

                    sessionError != null -> {
                        Text(
                            text = "Runner Error: $sessionError",
                            color = Color(0xFFF87171),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    activeSession == null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                            Text("Starting runner process...", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }

                    else -> {
                        key(activeSession.id) {
                            AndroidView(
                                factory = { ctx ->
                                    TerminalView(ctx, null).apply {
                                        runCatching {
                                            setTextSize(terminalFontSizePx)
                                            ThemeTerminal.applyPalette()
                                            setTerminalViewClient(object : TerminalViewClient {
                                                override fun onScale(scale: Float): Float {
                                                    val updated = (terminalFontSizePx * scale).roundToInt().coerceIn(18, 64)
                                                    terminalFontSizePx = updated
                                                    setTextSize(updated)
                                                    return 1.0f
                                                }

                                                override fun onSingleTapUp(e: MotionEvent?) {
                                                    requestFocus()
                                                    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                                    imm?.showSoftInput(this@apply, InputMethodManager.SHOW_IMPLICIT)
                                                }

                                                override fun shouldBackButtonBeMappedToEscape(): Boolean = false
                                                override fun shouldEnforceCharBasedInput(): Boolean = true
                                                override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
                                                override fun isTerminalViewSelected(): Boolean = true
                                                override fun copyModeChanged(copyMode: Boolean) {}

                                                override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: com.termux.terminal.TerminalSession?): Boolean = false
                                                override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
                                                override fun onLongPress(event: MotionEvent?): Boolean = false

                                                override fun readControlKey(): Boolean {
                                                    val armed = ctrlArmed
                                                    ctrlArmed = false
                                                    return armed
                                                }

                                                override fun readAltKey(): Boolean {
                                                    val armed = altArmed
                                                    altArmed = false
                                                    return armed
                                                }

                                                override fun readShiftKey(): Boolean = false
                                                override fun readFnKey(): Boolean = false

                                                override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: com.termux.terminal.TerminalSession?): Boolean {
                                                    return false
                                                }

                                                override fun onEmulatorSet() {
                                                    ThemeTerminal.applyPalette()
                                                }

                                                override fun logError(tag: String?, message: String?) {}
                                                override fun logWarn(tag: String?, message: String?) {}
                                                override fun logInfo(tag: String?, message: String?) {}
                                                override fun logDebug(tag: String?, message: String?) {}
                                                override fun logVerbose(tag: String?, message: String?) {}
                                                override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
                                                override fun logStackTrace(tag: String?, e: Exception?) {}
                                            })
                                            attachSession(activeSession.terminalSession)
                                        }.onFailure { err ->
                                            IdeDaemonLogBuffer.append("ERROR", "TerminalView attach failed: ${err.message}")
                                        }
                                        terminalViewRef = this
                                    }
                                },
                                update = { view ->
                                    runCatching {
                                        if (view.currentSession != activeSession.terminalSession) {
                                            view.attachSession(activeSession.terminalSession)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Quick Key Modifier & Signal Bar (Shown only in Terminal mode, hidden in Web Preview mode)
            if (!showWebPreview) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RunnerKeyPill(label = "CTRL", active = ctrlArmed, onClick = { ctrlArmed = !ctrlArmed })
                    RunnerKeyPill(label = "ALT", active = altArmed, onClick = { altArmed = !altArmed })
                    RunnerKeyPill(label = "ESC", onClick = { activeSession?.write("\u001B") })
                    RunnerKeyPill(label = "TAB", onClick = { activeSession?.write("\t") })
                    RunnerKeyPill(label = "CTRL+C", danger = true, onClick = { activeSession?.write("\u0003") })
                    RunnerKeyPill(label = "↑", onClick = { activeSession?.write("\u001B[A") })
                    RunnerKeyPill(label = "↓", onClick = { activeSession?.write("\u001B[B") })
                    RunnerKeyPill(label = "←", onClick = { activeSession?.write("\u001B[D") })
                    RunnerKeyPill(label = "→", onClick = { activeSession?.write("\u001B[C") })
                    RunnerKeyPill(label = "ENTER", onClick = { activeSession?.write("\r") })
                }
            }
        }
    }
}

@Composable
private fun RunnerKeyPill(
    label: String,
    active: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        danger -> Color(0xFFF85149).copy(alpha = 0.2f)
        active -> Color(0xFF00E5FF).copy(alpha = 0.24f)
        else -> Color(0xFF1E293B)
    }
    val border = when {
        danger -> Color(0xFFF85149)
        active -> Color(0xFF00E5FF)
        else -> Color(0xFF334155)
    }
    val textColor = when {
        danger -> Color(0xFFF85149)
        active -> Color(0xFF00E5FF)
        else -> Color(0xFFE2E8F0)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
