package com.jarvisai.assistant.ui.components

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import com.jarvisai.assistant.terminal.LocalPtySession
import com.jarvisai.assistant.terminal.LocalTerminalManager
import com.jarvisai.assistant.theme.ThemeTerminal
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlin.math.roundToInt

enum class ModifierState {
    OFF,
    ONE_SHOT,
    LOCKED
}

// Jarvis HUD Cyberpunk Color Palette for Terminal
private val JarvisTerminalBg = Color(0xFF070B14)
private val JarvisTerminalHeader = Color(0xFF0F172A)
private val JarvisKeyDefaultBg = Color(0xFF152238)
private val JarvisKeyActiveBg = Color(0xFF00E5FF).copy(alpha = 0.22f)
private val JarvisKeyLockedBg = Color(0xFFF59E0B).copy(alpha = 0.25f)
private val JarvisBorderDefault = Color(0xFF1E3A5F)
private val JarvisBorderActive = Color(0xFF00E5FF)
private val JarvisBorderLocked = Color(0xFFF59E0B)
private val JarvisCyan = Color(0xFF00E5FF)
private val JarvisGreen = Color(0xFF34D399)
private val JarvisRed = Color(0xFFFB7185)

/**
 * Backwards-compatible Dialog wrapper.
 */
@Composable
fun LocalTerminalDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        LocalTerminalContent(onClose = onDismiss)
    }
}

/**
 * Authentic Fullscreen Native Termux Console Component.
 * Supports persistent embedding in JarvisApp layered Box without state loss.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalTerminalContent(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    customSession: LocalPtySession? = null
) {
    val context = LocalContext.current

    val sessions by LocalTerminalManager.sessions.collectAsState()
    val activeSessionId by LocalTerminalManager.activeSessionId.collectAsState()

    var terminalInitError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        ThemeTerminal.applyPalette(ThemeTerminal.currentPalette)
        if (customSession == null && sessions.isEmpty()) {
            runCatching {
                LocalTerminalManager.getOrCreateOrRestoreSessions(context)
            }.onFailure { err ->
                terminalInitError = err.localizedMessage ?: "Failed to initialize PTY session"
            }
        }
    }

    val activeSession = customSession
        ?: sessions.find { it.id == activeSessionId }
        ?: sessions.firstOrNull()

    if (activeSession == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(JarvisTerminalBg),
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
                        color = JarvisRed,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    TextButton(onClick = onClose) {
                        Text("Close", color = JarvisCyan)
                    }
                } else {
                    CircularProgressIndicator(color = JarvisCyan, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                    Text("Initializing Termux PTY Session...", color = Color.LightGray, fontSize = 13.sp)
                }
            }
        }
        return
    }

    val isExited by activeSession.isExited.collectAsState()
    val title by activeSession.title.collectAsState()

    var terminalTextSizeSp by remember { mutableIntStateOf(14) }
    var currentTerminalView by remember { mutableStateOf<TerminalView?>(null) }

    var ctrlState by remember { mutableStateOf(ModifierState.OFF) }
    var altState by remember { mutableStateOf(ModifierState.OFF) }
    var isTabsMenuExpanded by remember { mutableStateOf(false) }

    val sendKeyToTerminal: (Int, String) -> Unit = sendKey@{ keyCode, fallbackString ->
        val currentPty = customSession ?: LocalTerminalManager.getActiveSession() ?: activeSession
        if (currentPty.isExited.value && (keyCode == KeyEvent.KEYCODE_ENTER || fallbackString == "\r" || fallbackString == "\n")) {
            if (customSession != null) onClose() else LocalTerminalManager.closeSession(currentPty.id)
            return@sendKey
        }

        val termView = currentTerminalView
        val ctrl = ctrlState != ModifierState.OFF
        val alt = altState != ModifierState.OFF

        when {
            ctrl && (fallbackString.equals("c", ignoreCase = true) || keyCode == KeyEvent.KEYCODE_C) -> {
                currentPty.write("\u0003") // SIGINT
            }
            ctrl && (fallbackString.equals("d", ignoreCase = true) || keyCode == KeyEvent.KEYCODE_D) -> {
                currentPty.write("\u0004") // EOF
            }
            ctrl && (fallbackString.equals("z", ignoreCase = true) || keyCode == KeyEvent.KEYCODE_Z) -> {
                currentPty.write("\u001A") // SIGTSTP
            }
            ctrl && (fallbackString.equals("l", ignoreCase = true) || keyCode == KeyEvent.KEYCODE_L) -> {
                currentPty.write("\u000C") // Clear
            }
            alt -> {
                currentPty.write("\u001B$fallbackString")
            }
            else -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_ESCAPE -> currentPty.write("\u001B")
                    KeyEvent.KEYCODE_TAB -> currentPty.write("\t")
                    KeyEvent.KEYCODE_DPAD_UP -> currentPty.write("\u001B[A")
                    KeyEvent.KEYCODE_DPAD_DOWN -> currentPty.write("\u001B[B")
                    KeyEvent.KEYCODE_DPAD_RIGHT -> currentPty.write("\u001B[C")
                    KeyEvent.KEYCODE_DPAD_LEFT -> currentPty.write("\u001B[D")
                    KeyEvent.KEYCODE_MOVE_HOME -> currentPty.write("\u0001")
                    KeyEvent.KEYCODE_MOVE_END -> currentPty.write("\u0005")
                    KeyEvent.KEYCODE_PAGE_UP -> currentPty.write("\u001B[5~")
                    KeyEvent.KEYCODE_PAGE_DOWN -> currentPty.write("\u001B[6~")
                    KeyEvent.KEYCODE_SLASH -> currentPty.write("/")
                    KeyEvent.KEYCODE_MINUS -> currentPty.write("-")
                    else -> currentPty.write(fallbackString)
                }
            }
        }

        if (ctrlState == ModifierState.ONE_SHOT) ctrlState = ModifierState.OFF
        if (altState == ModifierState.ONE_SHOT) altState = ModifierState.OFF
        termView?.requestFocus()
    }

    DisposableEffect(activeSession, currentTerminalView) {
        val tv = currentTerminalView
        if (tv != null) {
            val listener: () -> Unit = {
                tv.post {
                    tv.onScreenUpdated()
                }
            }
            activeSession.addTextChangedListener(listener)
            onDispose {
                activeSession.removeTextChangedListener(listener)
            }
        } else {
            onDispose {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisTerminalBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // --- HUD Cyber Header & Session Pill ---
            Surface(
                color = JarvisTerminalHeader,
                border = BorderStroke(0.5.dp, JarvisBorderDefault),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Terminal Pulse Status Icon
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isExited) JarvisRed else JarvisGreen)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "TERMINAL // $title",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    // Sessions Dropdown Switcher Button
                    Box {
                        TextButton(
                            onClick = { isTabsMenuExpanded = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = JarvisCyan)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tabs (${sessions.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }

                        DropdownMenu(
                            expanded = isTabsMenuExpanded,
                            onDismissRequest = { isTabsMenuExpanded = false },
                            properties = PopupProperties(focusable = false),
                            modifier = Modifier.background(Color(0xFF0F172A))
                        ) {
                            sessions.forEach { s ->
                                val isSelected = s.id == activeSession.id
                                val sExited by s.isExited.collectAsState()
                                val sTitle by s.title.collectAsState()
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (sExited) JarvisRed else JarvisGreen)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = sTitle,
                                                color = if (isSelected) JarvisCyan else Color.White,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.5.sp
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                LocalTerminalManager.closeSession(s.id)
                                                if (sessions.size <= 1) isTabsMenuExpanded = false
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                        }
                                    },
                                    onClick = {
                                        LocalTerminalManager.selectSession(s.id)
                                        isTabsMenuExpanded = false
                                    }
                                )
                            }
                            HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("New Bash Session", color = JarvisCyan, fontSize = 12.5.sp)
                                    }
                                },
                                onClick = {
                                    LocalTerminalManager.createSession(context, "Bash")
                                    isTabsMenuExpanded = false
                                }
                            )
                        }
                    }

                    // Emergency ^C Kill button in top bar
                    IconButton(
                        onClick = { activeSession.write("\u0003") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Surface(
                            color = JarvisRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, JarvisRed.copy(alpha = 0.5f))
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("^C", color = JarvisRed, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    // Close Button
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // --- NATIVE TERMUX TERMINAL CANVAS ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .background(JarvisTerminalBg)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                key(activeSession.id) {
                    AndroidView(
                        factory = { ctx ->
                            val density = ctx.resources.displayMetrics.scaledDensity.coerceAtLeast(1f)
                            TerminalView(ctx, null).apply {
                                setTextSize((terminalTextSizeSp * density).roundToInt())
                                isFocusable = true
                                isFocusableInTouchMode = true

                                val client = object : TerminalViewClient {
                                    override fun onScale(scale: Float): Float {
                                        if (scale < 0.9f || scale > 1.1f) {
                                            val delta = if (scale > 1.0f) 1 else -1
                                            val newSp = (terminalTextSizeSp + delta).coerceIn(6, 32)
                                            if (newSp != terminalTextSizeSp) {
                                                terminalTextSizeSp = newSp
                                                setTextSize((newSp * density).roundToInt())
                                            }
                                            return 1.0f
                                        }
                                        return scale
                                    }

                                    override fun onSingleTapUp(e: MotionEvent?) {
                                        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                        this@apply.requestFocus()
                                        imm?.showSoftInput(this@apply, InputMethodManager.SHOW_IMPLICIT)
                                    }

                                    override fun readControlKey(): Boolean {
                                        val active = ctrlState != ModifierState.OFF
                                        if (ctrlState == ModifierState.ONE_SHOT) ctrlState = ModifierState.OFF
                                        return active
                                    }

                                    override fun readAltKey(): Boolean {
                                        val active = altState != ModifierState.OFF
                                        if (altState == ModifierState.ONE_SHOT) altState = ModifierState.OFF
                                        return active
                                    }
                                }

                                runCatching {
                                    setTerminalViewClient(client)
                                    attachSession(activeSession.terminalSession)
                                    setTerminalCursorBlinkerRate(500)
                                    setTerminalCursorBlinkerState(true, true)
                                }
                                currentTerminalView = this

                                post {
                                    runCatching {
                                        requestFocus()
                                        onScreenUpdated()
                                        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                                    }
                                }
                            }
                        },
                        update = { tv ->
                            runCatching {
                                val density = tv.context.resources.displayMetrics.scaledDensity.coerceAtLeast(1f)
                                val targetPx = (terminalTextSizeSp * density).roundToInt()
                                if (tv.mRenderer == null || tv.mRenderer.getFontLineSpacing() <= 0) {
                                    tv.setTextSize(targetPx)
                                }
                                if (tv.currentSession != activeSession.terminalSession) {
                                    tv.attachSession(activeSession.terminalSession)
                                }
                                currentTerminalView = tv
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // --- 2-ROW DEVELOPER EXTRA-KEYS TOOLBAR ---
            Surface(
                color = Color(0xFF0A0F1D),
                border = BorderStroke(0.5.dp, JarvisBorderDefault),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // ROW 1: ESC | / | - | | | HOME | ↑ | END | PGUP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        JarvisTermuxKey(label = "ESC", modifier = Modifier.weight(1f)) {
                            sendKeyToTerminal(KeyEvent.KEYCODE_ESCAPE, "\u001B")
                        }
                        JarvisTermuxKey(label = "/", modifier = Modifier.weight(1f)) {
                            sendKeyToTerminal(KeyEvent.KEYCODE_SLASH, "/")
                        }
                        JarvisTermuxKey(label = "-", modifier = Modifier.weight(1f)) {
                            sendKeyToTerminal(KeyEvent.KEYCODE_MINUS, "-")
                        }
                        JarvisTermuxKey(label = "|", modifier = Modifier.weight(1f)) {
                            activeSession.write("|")
                        }
                        JarvisTermuxKey(label = "HOME", modifier = Modifier.weight(1f)) {
                            sendKeyToTerminal(KeyEvent.KEYCODE_MOVE_HOME, "\u0001")
                        }
                        JarvisTermuxKey(label = "↑", modifier = Modifier.weight(1f)) {
                            sendKeyToTerminal(KeyEvent.KEYCODE_DPAD_UP, "\u001B[A")
                        }
                        JarvisTermuxKey(label = "END", modifier = Modifier.weight(1f)) {
                            sendKeyToTerminal(KeyEvent.KEYCODE_MOVE_END, "\u0005")
                        }
                        JarvisTermuxKey(label = "PGUP", modifier = Modifier.weight(1f)) {
                            sendKeyToTerminal(KeyEvent.KEYCODE_PAGE_UP, "\u001B[5~")
                        }
                    }

                    // ROW 2: ↹ TAB | CTRL | ALT | ← | ↓ | → | PGDN | ^C
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        JarvisTermuxKey(label = "↹", modifier = Modifier.weight(1f)) {
                            sendKeyToTerminal(KeyEvent.KEYCODE_TAB, "\t")
                        }

                        // Real Modifier CTRL Key (Tap: ONE_SHOT, LongPress: LOCKED)
                        JarvisTermuxKey(
                            label = "CTRL",
                            isActive = ctrlState != ModifierState.OFF,
                            isLocked = ctrlState == ModifierState.LOCKED,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                ctrlState = if (ctrlState == ModifierState.OFF) ModifierState.ONE_SHOT else ModifierState.OFF
                            },
                            onLongClick = {
                                ctrlState = if (ctrlState == ModifierState.LOCKED) ModifierState.OFF else ModifierState.LOCKED
                            }
                        )

                        // Real Modifier ALT Key (Tap: ONE_SHOT, LongPress: LOCKED)
                        JarvisTermuxKey(
                            label = "ALT",
                            isActive = altState != ModifierState.OFF,
                            isLocked = altState == ModifierState.LOCKED,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                altState = if (altState == ModifierState.OFF) ModifierState.ONE_SHOT else ModifierState.OFF
                            },
                            onLongClick = {
                                altState = if (altState == ModifierState.LOCKED) ModifierState.OFF else ModifierState.LOCKED
                            }
                        )

                        JarvisTermuxKey(label = "←", modifier = Modifier.weight(1f)) {
                            sendKeyToTerminal(KeyEvent.KEYCODE_DPAD_LEFT, "\u001B[D")
                        }
                        JarvisTermuxKey(label = "↓", modifier = Modifier.weight(1f)) {
                            sendKeyToTerminal(KeyEvent.KEYCODE_DPAD_DOWN, "\u001B[B")
                        }
                        JarvisTermuxKey(label = "→", modifier = Modifier.weight(1f)) {
                            sendKeyToTerminal(KeyEvent.KEYCODE_DPAD_RIGHT, "\u001B[C")
                        }
                        JarvisTermuxKey(label = "PGDN", modifier = Modifier.weight(1f)) {
                            sendKeyToTerminal(KeyEvent.KEYCODE_PAGE_DOWN, "\u001B[6~")
                        }
                        JarvisTermuxKey(
                            label = "^C",
                            accentColor = JarvisRed,
                            modifier = Modifier.weight(1f)
                        ) {
                            activeSession.write("\u0003")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JarvisTermuxKey(
    label: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isLocked: Boolean = false,
    accentColor: Color? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val bgColor = when {
        isLocked -> JarvisKeyLockedBg
        isActive -> JarvisKeyActiveBg
        accentColor != null -> accentColor.copy(alpha = 0.15f)
        else -> JarvisKeyDefaultBg
    }

    val borderColor = when {
        isLocked -> JarvisBorderLocked
        isActive -> JarvisBorderActive
        accentColor != null -> accentColor.copy(alpha = 0.5f)
        else -> JarvisBorderDefault
    }

    val textColor = when {
        isLocked -> JarvisBorderLocked
        isActive -> JarvisCyan
        accentColor != null -> accentColor
        else -> Color.White
    }

    Surface(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = if (isLocked) "$label🔒" else label,
                fontSize = 11.sp,
                fontWeight = if (isActive || isLocked) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = textColor,
                maxLines = 1
            )
        }
    }
}
