package com.jarvisai.assistant.ui.components

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.WindowCompat
import com.jarvisai.assistant.data.preferences.AuthPreferences
import com.jarvisai.assistant.terminal.LocalPtySession
import com.jarvisai.assistant.terminal.LocalTerminalManager
import com.jarvisai.assistant.theme.ThemeTerminal
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class ModifierState {
    OFF,
    ONE_SHOT,
    LOCKED
}

private val AccentTerracotta = Color(0xFFD97757)
private val TerminalSurfacePanel = Color(0xFF161B22)
private val TerminalSurfaceKeyPressed = Color(0xFF21262D)
private val TerminalTextMuted = Color(0xFFC9D1D9)
private val TerminalStatusSuccess = Color(0xFF3FB950)
private val TerminalStatusDanger = Color(0xFFF85149)

@Composable
fun LocalTerminalDialog(
    onDismiss: () -> Unit,
    initialWorkingDir: String? = null
) {
    val context = LocalContext.current
    var customDirSession by remember(initialWorkingDir) { mutableStateOf<LocalPtySession?>(null) }

    LaunchedEffect(initialWorkingDir) {
        if (!initialWorkingDir.isNullOrBlank() && customDirSession == null) {
            val dirName = java.io.File(initialWorkingDir).name.ifBlank { "Terminal" }
            customDirSession = LocalTerminalManager.createSession(
                context = context.applicationContext,
                title = dirName,
                workingDirectory = initialWorkingDir
            )
        }
    }

    DisposableEffect(initialWorkingDir) {
        onDispose {
            customDirSession?.let { LocalTerminalManager.closeSession(it.id) }
            customDirSession = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        LocalTerminalContent(
            onClose = onDismiss,
            customSession = customDirSession,
            isWaitingForCustomSession = !initialWorkingDir.isNullOrBlank() && customDirSession == null,
            applySystemBarInsets = true
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LocalTerminalContent(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    customSession: LocalPtySession? = null,
    isWaitingForCustomSession: Boolean = false,
    applySystemBarInsets: Boolean = true,
    showFloatingPill: Boolean = true,
    backgroundColor: Color = ThemeTerminal.backgroundColor,
    isMaximized: Boolean = true,
    onToggleMaximize: (() -> Unit)? = null,
    onDragDelta: ((Float) -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    showSessionHeader: Boolean = true,
    showKeyboardOnOpen: Boolean = true
) {
    val context = LocalContext.current
    val sessions by LocalTerminalManager.sessions.collectAsState()
    val activeSessionId by LocalTerminalManager.activeSessionId.collectAsState()
    val isSyncingTmux by LocalTerminalManager.isSyncingTmux.collectAsState()
    val focusCoordinator = LocalAppFocusCoordinator.current
    var hasEverHadSessions by remember { mutableStateOf(false) }
    var terminalInitError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(customSession, isWaitingForCustomSession) {
        ThemeTerminal.applyPalette(ThemeTerminal.currentPalette)
        if (!isWaitingForCustomSession && customSession == null && sessions.isEmpty()) {
            runCatching {
                LocalTerminalManager.getOrCreateOrRestoreSessions(context)
            }.onFailure { err ->
                terminalInitError = err.localizedMessage ?: "Failed to initialize PTY session"
            }
        }
    }

    // Auto-close terminal ONLY when user explicitly closed all sessions
    LaunchedEffect(sessions, customSession, isWaitingForCustomSession) {
        if (customSession == null && !isWaitingForCustomSession) {
            if (sessions.isNotEmpty()) {
                hasEverHadSessions = true
            } else if (hasEverHadSessions) {
                onClose()
            }
        }
    }

    val activeSession = if (isWaitingForCustomSession) {
        null
    } else {
        customSession
            ?: sessions.find { it.id == activeSessionId }
            ?: sessions.firstOrNull()
    }

    if (activeSession == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                if (terminalInitError != null) {
                    Text(
                        text = "Terminal Error: $terminalInitError",
                        color = TerminalStatusDanger,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    TextButton(onClick = onClose) {
                        Text("Close", color = AccentTerracotta)
                    }
                } else {
                    CircularProgressIndicator(
                        color = AccentTerracotta,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(30.dp)
                    )
                    Text(
                        text = if (isSyncingTmux) "Syncing active tmux sessions..." else "Initializing Terminal Session...",
                        color = TerminalTextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }
        return
    }

    val view = LocalView.current
    val isDialog = view.parent is DialogWindowProvider
    DisposableEffect(Unit) {
        val window = (view.parent as? DialogWindowProvider)?.window
            ?: (view.context as? android.app.Activity)?.window
        if (window != null) {
            if (isDialog) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
            @Suppress("DEPRECATION")
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        onDispose {}
    }

    val authPreferences = remember { AuthPreferences.getInstance(context) }
    val initialFontSizeSp = remember { authPreferences.getTerminalFontSizeSync() }
    val savedFontSize by authPreferences.terminalFontSize.collectAsState(initial = initialFontSizeSp)
    val savedCursorStyle by authPreferences.terminalCursorStyle.collectAsState(initial = authPreferences.getTerminalCursorStyleSync())
    val savedBufferSize by authPreferences.terminalBufferSize.collectAsState(initial = authPreferences.getTerminalBufferSizeSync())

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var terminalTextSize by remember {
        mutableIntStateOf((initialFontSizeSp * density.density).roundToInt().coerceIn(16, 60))
    }
    var currentTerminalView by remember { mutableStateOf<TerminalView?>(null) }

    // Live theme color scheme repaint without restarting shell
    val terminalThemeVersion = ThemeTerminal.version
    LaunchedEffect(terminalThemeVersion) {
        currentTerminalView?.onScreenUpdated()
        currentTerminalView?.invalidate()
    }

    LaunchedEffect(savedFontSize) {
        val currentSp = (terminalTextSize / density.density).roundToInt()
        if (currentSp != savedFontSize) {
            val newPx = (savedFontSize * density.density).roundToInt().coerceIn(16, 60)
            terminalTextSize = newPx
            currentTerminalView?.setTextSize(newPx)
        }
    }

    var ctrlState by remember { mutableStateOf(ModifierState.OFF) }
    var altState by remember { mutableStateOf(ModifierState.OFF) }
    var isTabsMenuExpanded by remember { mutableStateOf(false) }
    var sessionToClose by remember { mutableStateOf<LocalPtySession?>(null) }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(savedCursorStyle, savedBufferSize) {
        LocalTerminalManager.updatePreferences(savedCursorStyle, savedBufferSize)
        currentTerminalView?.currentSession?.emulator?.setCursorStyle()
        currentTerminalView?.invalidate()
    }

    val sendKeyToTerminal: (Int, String) -> Unit = sendKey@{ keyCode, fallbackString ->
        val currentPty = customSession
            ?: LocalTerminalManager.sessions.value.find { it.id == LocalTerminalManager.activeSessionId.value }
            ?: activeSession
        if (currentPty.isExited.value && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || fallbackString == "\r" || fallbackString == "\n")) {
            if (customSession != null) {
                onClose()
            } else {
                LocalTerminalManager.closeSession(currentPty.id)
            }
            return@sendKey
        }
        val termView = currentTerminalView
        var handled = false
        val ctrl = ctrlState != ModifierState.OFF
        val alt = altState != ModifierState.OFF
        if (termView != null && keyCode != 0) {
            var keyMod = 0
            if (ctrl) keyMod = keyMod or KeyHandler.KEYMOD_CTRL
            if (alt) keyMod = keyMod or KeyHandler.KEYMOD_ALT
            handled = termView.handleKeyCode(keyCode, keyMod)
        }
        if (!handled) {
            currentPty.write(fallbackString)
        }
        if (ctrlState == ModifierState.ONE_SHOT) {
            ctrlState = ModifierState.OFF
        }
        if (altState == ModifierState.ONE_SHOT) {
            altState = ModifierState.OFF
        }
        termView?.requestFocus()
    }

    DisposableEffect(activeSession, currentTerminalView) {
        val tv = currentTerminalView
        if (tv != null) {
            focusCoordinator.registerScreen(
                mode = AppViewMode.TERMINAL,
                onRequestFocus = {
                    tv.post {
                        tv.requestFocus()
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(tv, 0)
                    }
                },
                onClearFocus = {
                    tv.post { tv.clearFocus() }
                }
            )
            focusCoordinator.onScreenEntering(AppViewMode.TERMINAL, showKeyboardOnOpen)
            val listener: () -> Unit = {
                tv.post {
                    tv.onScreenUpdated()
                    tv.invalidate()
                }
            }
            activeSession.addTextChangedListener(listener)
            onDispose {
                activeSession.removeTextChangedListener(listener)
                focusCoordinator.unregisterScreen(AppViewMode.TERMINAL)
            }
        } else {
            onDispose {}
        }
    }

    val statusBarHeightPx = if (applySystemBarInsets) WindowInsets.statusBars.getTop(density) else 0
    val statusBarHeightDp = with(density) { statusBarHeightPx.toDp() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .let { if (applySystemBarInsets) it.navigationBarsPadding() else it }
            .windowInsetsPadding(WindowInsets.imeAnimationTarget)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            if (customSession != null && showSessionHeader) {
                val isSessExited by activeSession.isExited.collectAsState()
                val activeTitle by activeSession.title.collectAsState()
                val currentOnDragDelta by rememberUpdatedState(onDragDelta)

                Surface(
                    color = backgroundColor,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (onDragDelta != null) {
                                Modifier.draggable(
                                    state = rememberDraggableState { delta ->
                                        currentOnDragDelta?.invoke(delta)
                                    },
                                    orientation = Orientation.Vertical
                                )
                            } else Modifier
                        )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (onDragDelta != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 5.dp, bottom = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.35f))
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .padding(start = 12.dp, end = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isSessExited) TerminalStatusDanger else TerminalStatusSuccess)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = activeTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            if (onToggleMaximize != null) {
                                IconButton(
                                    onClick = onToggleMaximize,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isMaximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = if (isMaximized) "Minimize" else "Maximize",
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            if (onRetry != null) {
                                IconButton(
                                    onClick = onRetry,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        tint = TerminalStatusSuccess,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("custom_terminal_close_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Terminal",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = TerminalTextMuted.copy(alpha = 0.1f), thickness = 0.5.dp)
            }

            // Full-bleed native Termux canvas (top padding keeps first row below status bar)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .background(backgroundColor)
                    .padding(horizontal = 4.dp)
            ) {
                key(activeSession.id) {
                    AndroidView(
                        factory = { ctx ->
                            TerminalView(ctx, null).apply {
                                setTopPadding(if (customSession != null) 0 else statusBarHeightPx)
                                setTextSize(terminalTextSize)
                                isFocusable = true
                                isFocusableInTouchMode = true

                                val client = object : TerminalViewClient {
                                    override fun onScale(scale: Float): Float {
                                        if (scale < 0.92f || scale > 1.08f) {
                                            val doIncrease = scale > 1.0f
                                            val newSize = if (doIncrease) terminalTextSize + 1 else terminalTextSize - 1
                                            if (newSize in 16..60) {
                                                terminalTextSize = newSize
                                                currentTerminalView?.setTextSize(terminalTextSize)
                                                val newSp = (newSize / density.density).roundToInt().coerceIn(8, 28)
                                                authPreferences.saveTerminalPreferencesSync(
                                                    fontSize = newSp,
                                                    cursorStyle = savedCursorStyle,
                                                    bufferSize = savedBufferSize,
                                                    theme = "DEFAULT"
                                                )
                                                coroutineScope.launch {
                                                    authPreferences.saveTerminalPreferences(
                                                        fontSize = newSp,
                                                        cursorStyle = savedCursorStyle,
                                                        bufferSize = savedBufferSize,
                                                        theme = "DEFAULT"
                                                    )
                                                }
                                            }
                                            return 1.0f
                                        }
                                        return scale
                                    }

                                    override fun onSingleTapUp(e: MotionEvent) {
                                        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                        this@apply.requestFocus()
                                        imm.showSoftInput(this@apply, 0)
                                    }

                                    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
                                    override fun shouldEnforceCharBasedInput(): Boolean = true
                                    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
                                    override fun isTerminalViewSelected(): Boolean = true
                                    override fun copyModeChanged(copyMode: Boolean) {}

                                    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
                                        val targetPty = customSession
                                            ?: LocalTerminalManager.sessions.value.find { it.terminalSession == session }
                                            ?: activeSession
                                        if (targetPty.isExited.value) {
                                            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                                                if (customSession != null) {
                                                    onClose()
                                                } else {
                                                    LocalTerminalManager.closeSession(targetPty.id)
                                                }
                                                return true
                                            }
                                        }
                                        val isCtrl = e.isCtrlPressed || ctrlState != ModifierState.OFF
                                        if (isCtrl) {
                                            when (keyCode) {
                                                KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> {
                                                    LocalTerminalManager.selectPreviousSession(ctx)
                                                    if (ctrlState == ModifierState.ONE_SHOT) ctrlState = ModifierState.OFF
                                                    return true
                                                }
                                                KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> {
                                                    LocalTerminalManager.selectNextSession(ctx)
                                                    if (ctrlState == ModifierState.ONE_SHOT) ctrlState = ModifierState.OFF
                                                    return true
                                                }
                                                KeyEvent.KEYCODE_T -> {
                                                    LocalTerminalManager.createNewSession(ctx)
                                                    if (ctrlState == ModifierState.ONE_SHOT) ctrlState = ModifierState.OFF
                                                    return true
                                                }
                                            }
                                        }
                                        return false
                                    }

                                    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
                                    override fun onLongPress(event: MotionEvent): Boolean = false

                                    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
                                        val targetPty = customSession
                                            ?: LocalTerminalManager.sessions.value.find { it.terminalSession == session }
                                            ?: activeSession
                                        if (targetPty.isExited.value) {
                                            if (codePoint == '\n'.code || codePoint == '\r'.code) {
                                                if (customSession != null) {
                                                    onClose()
                                                } else {
                                                    LocalTerminalManager.closeSession(targetPty.id)
                                                }
                                                return true
                                            }
                                        }
                                        if (ctrlDown) {
                                            when (codePoint.toChar()) {
                                                '1' -> {
                                                    LocalTerminalManager.selectPreviousSession(ctx)
                                                    if (ctrlState == ModifierState.ONE_SHOT) ctrlState = ModifierState.OFF
                                                    return true
                                                }
                                                '2' -> {
                                                    LocalTerminalManager.selectNextSession(ctx)
                                                    if (ctrlState == ModifierState.ONE_SHOT) ctrlState = ModifierState.OFF
                                                    return true
                                                }
                                                't', 'T' -> {
                                                    LocalTerminalManager.createNewSession(ctx)
                                                    if (ctrlState == ModifierState.ONE_SHOT) ctrlState = ModifierState.OFF
                                                    return true
                                                }
                                            }
                                        }
                                        return false
                                    }

                                    override fun readControlKey(): Boolean {
                                        val active = ctrlState != ModifierState.OFF
                                        if (ctrlState == ModifierState.ONE_SHOT) {
                                            ctrlState = ModifierState.OFF
                                        }
                                        return active
                                    }

                                    override fun readAltKey(): Boolean {
                                        val active = altState != ModifierState.OFF
                                        if (altState == ModifierState.ONE_SHOT) {
                                            altState = ModifierState.OFF
                                        }
                                        return active
                                    }

                                    override fun readShiftKey(): Boolean = false
                                    override fun readFnKey(): Boolean = false
                                    override fun onEmulatorSet() {}
                                    override fun logError(tag: String, message: String) { android.util.Log.e("TerminalViewKey", message) }
                                    override fun logWarn(tag: String, message: String) { android.util.Log.w("TerminalViewKey", message) }
                                    override fun logInfo(tag: String, message: String) { android.util.Log.i("TerminalViewKey", message) }
                                    override fun logDebug(tag: String, message: String) { android.util.Log.d("TerminalViewKey", message) }
                                    override fun logVerbose(tag: String, message: String) { android.util.Log.v("TerminalViewKey", message) }
                                    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { android.util.Log.e("TerminalViewKey", message, e) }
                                    override fun logStackTrace(tag: String, e: Exception) { android.util.Log.e("TerminalViewKey", "stacktrace", e) }
                                }

                                setTerminalViewClient(client)
                                setTerminalInputListener(object : TerminalView.TerminalInputListener {
                                    override fun onTerminalInput(text: String) {
                                        val targetPty = customSession
                                            ?: LocalTerminalManager.sessions.value.find { it.terminalSession == currentSession }
                                            ?: activeSession
                                        if (targetPty.isExited.value && (text.contains("\n") || text.contains("\r"))) {
                                            if (customSession != null) {
                                                onClose()
                                            } else {
                                                LocalTerminalManager.closeSession(targetPty.id)
                                            }
                                            return
                                        }
                                        targetPty.write(text)
                                    }

                                    override fun onTerminalInputCodePoint(prependEscape: Boolean, codePoint: Int) {
                                        val targetPty = customSession
                                            ?: LocalTerminalManager.sessions.value.find { it.terminalSession == currentSession }
                                            ?: activeSession
                                        if (targetPty.isExited.value && (codePoint == '\n'.code || codePoint == '\r'.code)) {
                                            if (customSession != null) {
                                                onClose()
                                            } else {
                                                LocalTerminalManager.closeSession(targetPty.id)
                                            }
                                            return
                                        }
                                        targetPty.writeCodePoint(prependEscape, codePoint)
                                    }
                                })
                                setTerminalSizeListener { cols, rows, widthPx, heightPx ->
                                    activeSession.updateSize(cols, rows, widthPx, heightPx)
                                }

                                attachSession(activeSession.terminalSession)
                                activeSession.terminalSession.emulator?.setCursorStyle()
                                currentTerminalView = this

                                if (showKeyboardOnOpen) {
                                    post {
                                        requestFocus()
                                        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                        imm.showSoftInput(this, 0)
                                    }
                                }
                            }
                        },
                        update = { tv ->
                            tv.setTopPadding(if (customSession != null) 0 else statusBarHeightPx)
                            tv.setTextSize(terminalTextSize)
                            if (tv.currentSession != activeSession.terminalSession) {
                                tv.attachSession(activeSession.terminalSession)
                                activeSession.terminalSession.emulator?.setCursorStyle()
                            }
                            tv.setTerminalInputListener(object : TerminalView.TerminalInputListener {
                                override fun onTerminalInput(text: String) {
                                    val targetPty = customSession
                                        ?: LocalTerminalManager.sessions.value.find { it.terminalSession == tv.currentSession }
                                        ?: activeSession
                                    if (targetPty.isExited.value && (text.contains("\n") || text.contains("\r"))) {
                                        if (customSession != null) {
                                            onClose()
                                        } else {
                                            LocalTerminalManager.closeSession(targetPty.id)
                                        }
                                        return
                                    }
                                    targetPty.write(text)
                                }

                                override fun onTerminalInputCodePoint(prependEscape: Boolean, codePoint: Int) {
                                    val targetPty = customSession
                                        ?: LocalTerminalManager.sessions.value.find { it.terminalSession == tv.currentSession }
                                        ?: activeSession
                                    if (targetPty.isExited.value && (codePoint == '\n'.code || codePoint == '\r'.code)) {
                                        if (customSession != null) {
                                            onClose()
                                        } else {
                                            LocalTerminalManager.closeSession(targetPty.id)
                                        }
                                        return
                                    }
                                    targetPty.writeCodePoint(prependEscape, codePoint)
                                }
                            })
                            tv.setTerminalSizeListener { cols, rows, widthPx, heightPx ->
                                activeSession.updateSize(cols, rows, widthPx, heightPx)
                            }
                            currentTerminalView = tv
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Classic Minimalist 2-Row Transparent Extra-Keys Toolbar (Exact AntiGem)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // ROW 1: ESC | / | - | HOME | ↑ | END | PGUP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ExtraKey(label = "ESC", modifier = Modifier.weight(1f).testTag("extra_key_esc")) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_ESCAPE, "\u001B")
                    }
                    ExtraKey(label = "/", enableRepeat = true, modifier = Modifier.weight(1f).testTag("extra_key_slash")) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_SLASH, "/")
                    }
                    ExtraKey(label = "-", enableRepeat = true, modifier = Modifier.weight(1f).testTag("extra_key_dash")) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_MINUS, "-")
                    }
                    ExtraKey(label = "HOME", modifier = Modifier.weight(1f).testTag("extra_key_home")) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_MOVE_HOME, "\u0001")
                    }
                    ExtraKey(label = "↑", enableRepeat = true, modifier = Modifier.weight(1f).testTag("extra_key_up")) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_DPAD_UP, "\u001B[A")
                    }
                    ExtraKey(label = "END", modifier = Modifier.weight(1f).testTag("extra_key_end")) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_MOVE_END, "\u0005")
                    }
                    ExtraKey(label = "PGUP", enableRepeat = true, modifier = Modifier.weight(1f).testTag("extra_key_pgup")) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_PAGE_UP, "\u001B[5~")
                    }
                }

                // ROW 2: ↹ (TAB) | CTRL | ALT | ← | ↓ | → | PGDN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ExtraKey(label = "↹", enableRepeat = true, modifier = Modifier.weight(1f).testTag("extra_key_tab")) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_TAB, "\t")
                    }
                    ExtraKey(
                        label = "CTRL",
                        isActive = ctrlState != ModifierState.OFF,
                        isLocked = ctrlState == ModifierState.LOCKED,
                        modifier = Modifier.weight(1f).testTag("extra_key_ctrl"),
                        onClick = {
                            ctrlState = if (ctrlState == ModifierState.OFF) ModifierState.ONE_SHOT else ModifierState.OFF
                        },
                        onLongClick = {
                            ctrlState = if (ctrlState == ModifierState.LOCKED) ModifierState.OFF else ModifierState.LOCKED
                        }
                    )
                    ExtraKey(
                        label = "ALT",
                        isActive = altState != ModifierState.OFF,
                        isLocked = altState == ModifierState.LOCKED,
                        modifier = Modifier.weight(1f).testTag("extra_key_alt"),
                        onClick = {
                            altState = if (altState == ModifierState.OFF) ModifierState.ONE_SHOT else ModifierState.OFF
                        },
                        onLongClick = {
                            altState = if (altState == ModifierState.LOCKED) ModifierState.OFF else ModifierState.LOCKED
                        }
                    )
                    ExtraKey(label = "←", enableRepeat = true, modifier = Modifier.weight(1f).testTag("extra_key_left")) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_DPAD_LEFT, "\u001B[D")
                    }
                    ExtraKey(label = "↓", enableRepeat = true, modifier = Modifier.weight(1f).testTag("extra_key_down")) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_DPAD_DOWN, "\u001B[B")
                    }
                    ExtraKey(label = "→", enableRepeat = true, modifier = Modifier.weight(1f).testTag("extra_key_right")) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_DPAD_RIGHT, "\u001B[C")
                    }
                    ExtraKey(label = "PGDN", enableRepeat = true, modifier = Modifier.weight(1f).testTag("extra_key_pgdn")) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_PAGE_DOWN, "\u001B[6~")
                    }
                }
            }
        }

        // FROSTED GLASS BLUR STATUS BAR OVERLAY
        if (statusBarHeightDp > 0.dp && customSession == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeightDp + 6.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            0.0f to backgroundColor.copy(alpha = 0.85f),
                            0.7f to backgroundColor.copy(alpha = 0.53f),
                            1.0f to backgroundColor.copy(alpha = 0f)
                        )
                    )
            )
        }

        // DRAGGABLE FLOATING TAB PILL (Move anywhere on screen)
        if (showFloatingPill && customSession == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .let { if (applySystemBarInsets) it.statusBarsPadding() else it }
                    .padding(top = 8.dp, end = 10.dp)
                    .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            dragOffsetX += dragAmount.x
                            dragOffsetY += dragAmount.y
                        }
                    }
                    .testTag("terminal_floating_pill"),
                shape = RoundedCornerShape(20.dp),
                color = TerminalSurfacePanel.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, TerminalTextMuted.copy(alpha = 0.22f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Active Tab Name & Switcher Dropdown Anchor
                    Box {
                        val activeTitle by activeSession.title.collectAsState()
                        val isSshConnected by activeSession.isSshConnected.collectAsState()
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { isTabsMenuExpanded = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("terminal_tab_dropdown_btn"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (activeSession.isSsh) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSshConnected) TerminalStatusSuccess.copy(alpha = 0.22f) else AccentTerracotta.copy(alpha = 0.22f)
                                ) {
                                    Text(
                                        text = "SSH",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSshConnected) TerminalStatusSuccess else AccentTerracotta,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(5.dp))
                            }
                            Text(
                                text = activeTitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Switch Terminal Tab",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Floating Sessions Dropdown Menu
                        DropdownMenu(
                            expanded = isTabsMenuExpanded,
                            onDismissRequest = { isTabsMenuExpanded = false },
                            properties = PopupProperties(
                                focusable = false,
                                dismissOnClickOutside = true,
                                dismissOnBackPress = true
                            ),
                            modifier = Modifier.background(TerminalSurfacePanel)
                        ) {
                            sessions.forEach { sess ->
                                val isSelected = sess.id == activeSession.id
                                val sessExited by sess.isExited.collectAsState()
                                val sessTitle by sess.title.collectAsState()
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (sessExited) TerminalStatusDanger else TerminalStatusSuccess
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (sess.isSsh) "$sessTitle [SSH]" else sessTitle,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) AccentTerracotta else Color.White,
                                                fontSize = 13.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    sessionToClose = sess
                                                    isTabsMenuExpanded = false
                                                },
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .testTag("terminal_tab_close_${sess.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Close Tab",
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        LocalTerminalManager.selectSession(sess.id)
                                        isTabsMenuExpanded = false
                                    }
                                )
                            }

                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = TerminalTextMuted.copy(alpha = 0.2f)
                            )

                            Surface(
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            LocalTerminalManager.createNewSession(context)
                                            isTabsMenuExpanded = false
                                        },
                                        onLongClick = {
                                            LocalTerminalManager.createNewSession(context, forceShell = "bash")
                                            android.widget.Toast.makeText(
                                                context,
                                                "Created Bash session",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                            isTabsMenuExpanded = false
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("terminal_new_session_btn")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "New Tab",
                                        tint = AccentTerracotta,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "New Session",
                                            color = AccentTerracotta,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Long-press to force Bash shell",
                                            color = TerminalTextMuted.copy(alpha = 0.55f),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // Close / Hide Button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("terminal_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Terminal",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // CLOSE TAB CONFIRMATION DIALOG
        if (sessionToClose != null) {
            val targetSession = sessionToClose!!
            val sessTitle by targetSession.title.collectAsState()
            AlertDialog(
                onDismissRequest = { sessionToClose = null },
                shape = RoundedCornerShape(18.dp),
                containerColor = Color(0xFF0D1117),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(TerminalStatusDanger.copy(alpha = 0.13f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = TerminalStatusDanger,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Close Tab?",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                },
                text = {
                    Text(
                        text = "Are you sure you want to close \"$sessTitle\"?\nAny process running in this session will be terminated.",
                        fontSize = 13.sp,
                        color = TerminalTextMuted,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val idToClose = targetSession.id
                            sessionToClose = null
                            LocalTerminalManager.closeSession(idToClose)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TerminalStatusDanger),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("confirm_close_tab_btn")
                    ) {
                        Text(
                            text = "Close Tab",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { sessionToClose = null },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("cancel_close_tab_btn")
                    ) {
                        Text("Cancel", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            )
        }
    }
}

@Composable
private fun ExtraKey(
    label: String,
    isActive: Boolean = false,
    isLocked: Boolean = false,
    modifier: Modifier = Modifier,
    enableRepeat: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }

    val keyModifier = if (enableRepeat) {
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                isPressed = true
                currentOnClick()
                val repeatJob = scope.launch {
                    delay(350)
                    while (true) {
                        currentOnClick()
                        delay(55)
                    }
                }
                try {
                    waitForUpOrCancellation()
                } finally {
                    repeatJob.cancel()
                    isPressed = false
                }
            }
        }
    } else {
        modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                },
                onTap = {
                    currentOnClick()
                },
                onLongPress = {
                    currentOnLongClick?.invoke()
                }
            )
        }
    }

    val backgroundColor = when {
        isPressed -> TerminalSurfaceKeyPressed
        isLocked -> AccentTerracotta.copy(alpha = 0.5f)
        isActive -> AccentTerracotta.copy(alpha = 0.25f)
        else -> Color.Transparent
    }

    val textColor = when {
        isPressed -> Color.White
        isLocked -> Color.White
        isActive -> AccentTerracotta
        else -> TerminalTextMuted
    }

    val border = when {
        isLocked -> BorderStroke(1.dp, AccentTerracotta)
        isActive -> BorderStroke(0.5.dp, AccentTerracotta.copy(alpha = 0.5f))
        else -> null
    }

    Surface(
        modifier = keyModifier
            .height(34.dp)
            .clip(RoundedCornerShape(5.dp)),
        color = backgroundColor,
        border = border,
        shape = RoundedCornerShape(5.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isActive || isLocked || isPressed) FontWeight.Bold else FontWeight.SemiBold,
                    color = textColor
                )
                if (isLocked) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }
        }
    }
}
