package com.jarvisai.assistant.ide

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.jarvisai.assistant.data.ide.RunConfigManager
import com.jarvisai.assistant.data.ide.RunConfiguration
import com.jarvisai.assistant.terminal.LocalTerminalManager
import com.jarvisai.assistant.theme.ThemeTerminal
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File
import kotlin.math.roundToInt

/**
 * Phase 4: Interactive Code Execution Bottom Sheet (`IdeExecutionRunnerSheet`).
 * Embeds the native `com.termux.view.TerminalView`, binds a tactile modifier/signal row
 * (`Ctrl`, `Alt`, `Esc`, `Tab`, Arrow keys, `Ctrl+C Kill`), and supports toggling between
 * live PTY terminal output and an embedded WebView preview (`CloudPreviewServer`).
 */
data class IdeExecutionRequest(
    val title: String,
    val projectPath: String,
    val activeFile: File? = null,
    val configuration: RunConfiguration
)

@Composable
fun IdeExecutionRunnerSheet(
    request: IdeExecutionRequest,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val execCommand = remember(request) {
        RunConfigManager.buildExecutionCommand(
            config = request.configuration,
            projectPath = request.projectPath,
            activeFile = request.activeFile
        )
    }

    var rerunNonce by remember { mutableIntStateOf(0) }
    var showWebPreview by remember(request) {
        mutableStateOf(request.configuration.type.equals("webview", ignoreCase = true))
    }
    var ctrlArmed by remember { mutableStateOf(false) }
    var altArmed by remember { mutableStateOf(false) }
    var terminalFontSizePx by remember { mutableIntStateOf(30) }
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }

    val session = remember(request, rerunNonce) {
        LocalTerminalManager.createSession(
            context = context,
            title = "Run: ${request.title}",
            initialCommand = "$execCommand\necho '\n[Process Completed]'\nexec bash -l"
        )
    }

    val isExited by session.isExited.collectAsState()

    val previewUrl = remember(request, showWebPreview) {
        if (showWebPreview) {
            CloudPreviewServer.start(File(request.projectPath))
        } else {
            null
        }
    }

    DisposableEffect(session) {
        val listener: () -> Unit = {
            terminalViewRef?.post {
                terminalViewRef?.onScreenUpdated()
            }
        }
        session.addTextChangedListener(listener)
        onDispose {
            session.removeTextChangedListener(listener)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.62f)
            .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .testTag("ide_execution_runner_sheet"),
        color = Color(0xFF070B14),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isExited) Color(0xFF64748B) else Color(0xFF34D399))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = request.configuration.name,
                            color = Color(0xFFF0F6FC),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = execCommand.take(64),
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
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

                    IconButton(
                        onClick = {
                            session.write("\u0003") // SIGINT Ctrl+C
                        },
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

                    IconButton(
                        onClick = { rerunNonce++ },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("ide_runner_rerun_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-run Configuration",
                            tint = Color(0xFF34D399)
                        )
                    }

                    IconButton(
                        onClick = onClose,
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

            // Content body: either Native Termux TerminalView or Split Web Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (showWebPreview && previewUrl != null) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = WebViewClient()
                                loadUrl(previewUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AndroidView(
                        factory = { ctx ->
                            TerminalView(ctx, null).apply {
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
                                attachSession(session.terminalSession)
                                terminalViewRef = this
                            }
                        },
                        update = { view ->
                            if (view.currentSession != session.terminalSession) {
                                view.attachSession(session.terminalSession)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Quick Key Modifier & Signal Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RunnerKeyPill(
                    label = "CTRL",
                    active = ctrlArmed,
                    onClick = { ctrlArmed = !ctrlArmed }
                )
                RunnerKeyPill(
                    label = "ALT",
                    active = altArmed,
                    onClick = { altArmed = !altArmed }
                )
                RunnerKeyPill(
                    label = "ESC",
                    onClick = { session.write("\u001B") }
                )
                RunnerKeyPill(
                    label = "TAB",
                    onClick = { session.write("\t") }
                )
                RunnerKeyPill(
                    label = "CTRL+C",
                    danger = true,
                    onClick = { session.write("\u0003") }
                )
                RunnerKeyPill(
                    label = "↑",
                    onClick = { session.write("\u001B[A") }
                )
                RunnerKeyPill(
                    label = "↓",
                    onClick = { session.write("\u001B[B") }
                )
                RunnerKeyPill(
                    label = "←",
                    onClick = { session.write("\u001B[D") }
                )
                RunnerKeyPill(
                    label = "→",
                    onClick = { session.write("\u001B[C") }
                )
                RunnerKeyPill(
                    label = "ENTER",
                    onClick = { session.write("\r") }
                )
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
