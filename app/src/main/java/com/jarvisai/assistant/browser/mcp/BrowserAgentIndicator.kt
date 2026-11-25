package com.jarvisai.assistant.browser.mcp

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * AntiGem-style native visual overlay controller for browser MCP automation.
 * Draws a pulsing indigo-blue glowing WebView frame, a crisp arrow mouse pointer
 * that smoothly animates to each target with click ripples, and a floating rounded
 * action tooltip pill riding next to the cursor (never injected into DOM so
 * screenshots stay 100% clean and touches are never intercepted).
 */
object BrowserAgentIndicatorState {
    private const val LINGER_MS = 6_000L

    class TabState {
        var x by mutableStateOf(0.5f)
        var y by mutableStateOf(0.5f)
        var hasPointer by mutableStateOf(false)
        var pressed by mutableStateOf(false)
        var clicks by mutableIntStateOf(0)
        var label by mutableStateOf<String?>(null)
        var activeUntil by mutableLongStateOf(0L)
    }

    private val states = mutableStateMapOf<String, TabState>()
    private val main = Handler(Looper.getMainLooper())
    var latestActiveTabId by mutableStateOf<String?>(null)
        private set

    fun state(tabId: String?): TabState? {
        val key = tabId?.takeIf { it.isNotBlank() } ?: latestActiveTabId ?: return null
        return states[key]
    }

    private fun update(tabId: String, block: TabState.() -> Unit) {
        val key = tabId.ifBlank { "default" }
        main.post {
            latestActiveTabId = key
            val s = states.getOrPut(key) { TabState() }
            s.block()
            s.activeUntil = SystemClock.uptimeMillis() + LINGER_MS
        }
    }

    fun moveTo(tabId: String, fx: Double, fy: Double, label: String?) = update(tabId) {
        x = fx.toFloat().coerceIn(0.02f, 0.98f)
        y = fy.toFloat().coerceIn(0.02f, 0.98f)
        hasPointer = true
        if (!label.isNullOrBlank()) this.label = label
    }

    fun press(tabId: String, down: Boolean) = update(tabId) {
        pressed = down
    }

    fun click(tabId: String) = update(tabId) {
        clicks++
        pressed = false
    }

    fun activity(tabId: String, label: String?) = update(tabId) {
        if (!label.isNullOrBlank()) this.label = label
    }

    fun clear(tabId: String) = main.post {
        states.remove(tabId)
        if (latestActiveTabId == tabId) {
            latestActiveTabId = states.keys.lastOrNull()
        }
    }
}

private val AgentOverlayAccent = Color(0xFF5B6DF6) // Vibrant AntiGem indigo-blue glow

@Composable
fun BrowserAgentOverlay(
    tabId: String?,
    modifier: Modifier = Modifier,
    topInset: Dp = 56.dp
) {
    val s = BrowserAgentIndicatorState.state(tabId) ?: return
    var active by remember(s) { mutableStateOf(false) }
    LaunchedEffect(s.activeUntil) {
        val left = s.activeUntil - SystemClock.uptimeMillis()
        active = left > 0
        if (left > 0) {
            delay(left)
            active = false
        }
    }
    val shown by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (active) 180 else 600),
        label = "agentShown"
    )
    if (shown <= 0.01f) return

    val pulse by rememberInfiniteTransition(label = "agentPulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "agentPulseValue"
    )
    val cx by animateFloatAsState(
        targetValue = s.x,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "agentX"
    )
    val cy by animateFloatAsState(
        targetValue = s.y,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "agentY"
    )
    val ripple = remember { Animatable(1f) }
    LaunchedEffect(s.clicks) {
        if (s.clicks > 0) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        }
    }

    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .alpha(shown)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Glowing animated frame around WebView
            val glowLayers = listOf(2.5.dp to 0.95f, 7.dp to 0.36f, 14.dp to 0.14f)
            for ((widthDp, a) in glowLayers) {
                val w = widthDp.toPx()
                drawRoundRect(
                    color = AgentOverlayAccent.copy(alpha = a * pulse),
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = w)
                )
            }

            if (s.hasPointer) {
                val px = cx * size.width
                val py = cy * size.height

                // Click ripple wave
                if (ripple.value < 1f) {
                    val r = 6.dp.toPx() + 26.dp.toPx() * ripple.value
                    drawCircle(
                        color = AgentOverlayAccent.copy(alpha = 0.42f * (1f - ripple.value)),
                        radius = r,
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = AgentOverlayAccent.copy(alpha = 0.9f * (1f - ripple.value)),
                        radius = r,
                        center = Offset(px, py),
                        style = Stroke(2.dp.toPx())
                    )
                }
                if (s.pressed) {
                    drawCircle(
                        color = AgentOverlayAccent.copy(alpha = 0.35f),
                        radius = 10.dp.toPx(),
                        center = Offset(px, py)
                    )
                }

                // Crisp mouse pointer arrow with drop shadow
                val u = with(density) { 1.dp.toPx() } * if (s.pressed) 0.9f else 1f
                val arrow = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(0f, 17f * u)
                    lineTo(4.4f * u, 13.2f * u)
                    lineTo(7.4f * u, 19.6f * u)
                    lineTo(10.2f * u, 18.4f * u)
                    lineTo(7.3f * u, 12.1f * u)
                    lineTo(12.6f * u, 12.1f * u)
                    close()
                }
                translate(px + 1.2f * u, py + 1.8f * u) {
                    drawPath(arrow, Color.Black.copy(alpha = 0.32f))
                }
                translate(px, py) {
                    drawPath(arrow, Color.White)
                    drawPath(
                        arrow,
                        Color(0xFF18181B),
                        style = Stroke(width = 1.4f * u, join = StrokeJoin.Round)
                    )
                }
            }
        }

        val label = s.label?.takeIf { it.isNotBlank() }
        if (s.hasPointer && label != null) {
            var labelW by remember { mutableIntStateOf(0) }
            var labelH by remember { mutableIntStateOf(0) }
            val areaW = constraints.maxWidth
            val areaH = constraints.maxHeight
            val gapX = with(density) { 18.dp.roundToPx() }
            val gapY = with(density) { 22.dp.roundToPx() }
            val edge = with(density) { 8.dp.roundToPx() }
            val topLimit = with(density) { (topInset - 6.dp).roundToPx() }.coerceAtLeast(edge)

            Box(
                modifier = Modifier
                    .offset {
                        val px = (cx * areaW).roundToInt()
                        val py = (cy * areaH).roundToInt()
                        var x = px + gapX
                        var y = py + gapY
                        if (x + labelW > areaW - edge) x = px - labelW - edge
                        if (y + labelH > areaH - edge) y = py - labelH - edge
                        if (y < topLimit) y = max(topLimit, py + gapY)
                        IntOffset(
                            x.coerceIn(edge, max(edge, areaW - labelW - edge)),
                            y.coerceIn(topLimit, max(topLimit, areaH - labelH - edge))
                        )
                    }
                    .onSizeChanged { labelW = it.width; labelH = it.height }
                    .widthIn(max = maxWidth * 0.72f)
                    .background(AgentOverlayAccent.copy(alpha = 0.95f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInset)
                    .widthIn(max = maxWidth * 0.86f)
                    .background(Color(0xE81C1D22), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (label == null) "AI is using this tab" else "AI · $label",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Backward-compatible top/bottom status pill used by BrowserScreen & JarvisBrowserActivity.
 * When [BrowserAgentOverlay] is already rendering the live cursor & tooltip, stays unobtrusive.
 */
@Composable
fun BrowserAgentIndicator(
    isAgentActive: Boolean,
    actionSummary: String = "AI is using this tab",
    modifier: Modifier = Modifier
) {
    val activeState = BrowserAgentIndicatorState.state(null)
    val hasRichOverlay = activeState != null && activeState.activeUntil > SystemClock.uptimeMillis()
    AnimatedVisibility(
        visible = isAgentActive && !hasRichOverlay,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 14.dp)
                .background(Color(0xE81C1D22), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "AI · ${actionSummary.ifBlank { "Working..." }}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Renders the full AntiGem-style glowing frame + animated arrow pointer + floating caption pill.
 * Keeps signature compatibility with BrowserScreen & JarvisBrowserActivity.
 */
@Composable
fun BrowserAgentCursorOverlay(
    isAgentActive: Boolean,
    cursorPosition: Pair<Float, Float>?,
    tabId: String? = null,
    isLocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    val richState = BrowserAgentIndicatorState.state(tabId)
    if (richState != null) {
        BrowserAgentOverlay(
            tabId = tabId,
            modifier = modifier,
            topInset = if (isLocked) 56.dp else 14.dp
        )
        return
    }

    if (!isAgentActive) return

    val pulse by rememberInfiniteTransition(label = "fallbackPulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "fallbackPulseVal"
    )
    val density = LocalDensity.current
    val targetX by animateFloatAsState(
        targetValue = cursorPosition?.first ?: -1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "fallback_cursor_x"
    )
    val targetY by animateFloatAsState(
        targetValue = cursorPosition?.second ?: -1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "fallback_cursor_y"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val glowLayers = listOf(2.5.dp to 0.95f, 7.dp to 0.36f, 14.dp to 0.14f)
        for ((widthDp, a) in glowLayers) {
            val w = widthDp.toPx()
            drawRoundRect(
                color = AgentOverlayAccent.copy(alpha = a * pulse),
                topLeft = Offset(w / 2f, w / 2f),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = Stroke(width = w)
            )
        }
        if (cursorPosition != null && targetX >= 0f && targetY >= 0f) {
            val px = targetX.coerceIn(0f, size.width)
            val py = targetY.coerceIn(0f, size.height)
            val u = with(density) { 1.dp.toPx() }
            val arrow = Path().apply {
                moveTo(0f, 0f)
                lineTo(0f, 17f * u)
                lineTo(4.4f * u, 13.2f * u)
                lineTo(7.4f * u, 19.6f * u)
                lineTo(10.2f * u, 18.4f * u)
                lineTo(7.3f * u, 12.1f * u)
                lineTo(12.6f * u, 12.1f * u)
                close()
            }
            translate(px + 1.2f * u, py + 1.8f * u) { drawPath(arrow, Color.Black.copy(alpha = 0.32f)) }
            translate(px, py) {
                drawPath(arrow, Color.White)
                drawPath(arrow, Color(0xFF18181B), style = Stroke(width = 1.4f * u, join = StrokeJoin.Round))
            }
        }
    }
}
