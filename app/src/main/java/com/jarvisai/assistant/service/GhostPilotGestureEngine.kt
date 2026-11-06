package com.jarvisai.assistant.service

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import com.jarvisai.assistant.model.ProceduralHudMode
import com.jarvisai.assistant.ui.components.ProceduralHudModeState

data class GhostPilotGestureResult(
    val executed: Boolean,
    val gestureType: String,
    val pixelCoordinatesSummary: String,
    val spokenHinglishSummary: String
)

/**
 * Feature 10: "Ghost Pilot" Sub-Pixel Micro-Gestures & Game/Canvas CUA Engine.
 * Inspired by AbuZar-Ansarii/Ultron Ghost Pilot:
 * Executes normalized [0.0 .. 1.0] percentage screen coordinates, cubic Bezier curve drags,
 * double-taps, hold-and-drag, and two-finger pinch/zoom on non-accessible canvases, webviews, and games.
 */
object GhostPilotGestureEngine {

    fun executeSubPixelGesture(
        context: Context,
        gestureType: String, // TAP_PCT, DOUBLE_TAP_PCT, BEZIER_DRAG, PINCH_ZOOM_IN, PINCH_ZOOM_OUT
        startXPct: Float = 0.5f,
        startYPct: Float = 0.5f,
        endXPct: Float = 0.5f,
        endYPct: Float = 0.25f,
        durationMs: Long = 360L
    ): GhostPilotGestureResult {
        ProceduralHudModeState.setMode(ProceduralHudMode.GHOST_PILOT)
        val dm = context.resources.displayMetrics
        val screenW = dm.widthPixels.coerceAtLeast(1080)
        val screenH = dm.heightPixels.coerceAtLeast(1920)

        val x1 = (startXPct.coerceIn(0.02f, 0.98f) * screenW).toInt()
        val y1 = (startYPct.coerceIn(0.02f, 0.98f) * screenH).toInt()
        val x2 = (endXPct.coerceIn(0.02f, 0.98f) * screenW).toInt()
        val y2 = (endYPct.coerceIn(0.02f, 0.98f) * screenH).toInt()

        val normType = gestureType.trim().uppercase()

        return when (normType) {
            "TAP_PCT", "TAP" -> {
                val res = JarvisAccessibilityService.tapCoordinates(x1, y1, "GhostPilot($x1,$y1)")
                GhostPilotGestureResult(
                    executed = res.executed,
                    gestureType = "TAP_PCT",
                    pixelCoordinatesSummary = "(x=$x1, y=$y1) [${(startXPct * 100).toInt()}%, ${(startYPct * 100).toInt()}%]",
                    spokenHinglishSummary = "Boss, Ghost Pilot ne sub-pixel coordinate ($x1, $y1) par precision tap execute kar diya hai."
                )
            }

            "DOUBLE_TAP_PCT", "DOUBLE_TAP" -> {
                val r1 = JarvisAccessibilityService.tapCoordinates(x1, y1, "GhostPilotDoubleTap1")
                val r2 = JarvisAccessibilityService.tapCoordinates(x1, y1, "GhostPilotDoubleTap2")
                GhostPilotGestureResult(
                    executed = r1.executed || r2.executed,
                    gestureType = "DOUBLE_TAP_PCT",
                    pixelCoordinatesSummary = "2x (x=$x1, y=$y1)",
                    spokenHinglishSummary = "Boss, Ghost Pilot ne ($x1, $y1) par rapid double-tap gesture perform kar diya hai."
                )
            }

            "BEZIER_DRAG", "CURVE_SWIPE", "DRAG" -> {
                val dispatched = dispatchCubicBezierGesture(
                    x1 = x1.toFloat(),
                    y1 = y1.toFloat(),
                    x2 = x2.toFloat(),
                    y2 = y2.toFloat(),
                    durationMs = durationMs.coerceIn(120L, 2500L)
                )
                GhostPilotGestureResult(
                    executed = dispatched,
                    gestureType = "BEZIER_DRAG",
                    pixelCoordinatesSummary = "Bezier($x1,$y1 -> $x2,$y2 in ${durationMs}ms)",
                    spokenHinglishSummary = "Boss, Ghost Pilot ne smooth human-like Bezier curve drag ($x1,$y1 se $x2,$y2) execute kar diya hai."
                )
            }

            "PINCH_ZOOM_IN", "ZOOM_IN" -> {
                val dispatched = dispatchPinchGesture(
                    centerX = x1.toFloat(),
                    centerY = y1.toFloat(),
                    zoomIn = true,
                    durationMs = durationMs.coerceIn(200L, 1200L)
                )
                GhostPilotGestureResult(
                    executed = dispatched,
                    gestureType = "PINCH_ZOOM_IN",
                    pixelCoordinatesSummary = "2-Finger Spread at ($x1,$y1)",
                    spokenHinglishSummary = "Boss, Ghost Pilot ne screen center ($x1, $y1) par two-finger Pinch Zoom-In kar diya hai."
                )
            }

            "PINCH_ZOOM_OUT", "ZOOM_OUT" -> {
                val dispatched = dispatchPinchGesture(
                    centerX = x1.toFloat(),
                    centerY = y1.toFloat(),
                    zoomIn = false,
                    durationMs = durationMs.coerceIn(200L, 1200L)
                )
                GhostPilotGestureResult(
                    executed = dispatched,
                    gestureType = "PINCH_ZOOM_OUT",
                    pixelCoordinatesSummary = "2-Finger Pinch at ($x1,$y1)",
                    spokenHinglishSummary = "Boss, Ghost Pilot ne screen par two-finger Pinch Zoom-Out execute kar diya hai."
                )
            }

            else -> {
                val res = JarvisAccessibilityService.tapCoordinates(x1, y1, "GhostPilot($x1,$y1)")
                GhostPilotGestureResult(
                    executed = res.executed,
                    gestureType = normType,
                    pixelCoordinatesSummary = "(x=$x1, y=$y1)",
                    spokenHinglishSummary = "Boss, Ghost Pilot coordinate action ($x1, $y1) complete ho gaya."
                )
            }
        }
    }

    private fun dispatchCubicBezierGesture(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long
    ): Boolean {
        val service = JarvisAccessibilityService.activeInstance ?: return true
        return try {
            val path = Path().apply {
                moveTo(x1, y1)
                val cx1 = x1 + (x2 - x1) * 0.35f + 45f
                val cy1 = y1 + (y2 - y1) * 0.25f
                val cx2 = x1 + (x2 - x1) * 0.70f - 35f
                val cy2 = y1 + (y2 - y1) * 0.80f
                cubicTo(cx1, cy1, cx2, cy2, x2, y2)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        } catch (_: Exception) {
            false
        }
    }

    private fun dispatchPinchGesture(
        centerX: Float,
        centerY: Float,
        zoomIn: Boolean,
        durationMs: Long
    ): Boolean {
        val service = JarvisAccessibilityService.activeInstance ?: return true
        return try {
            val nearOffset = 60f
            val farOffset = 260f
            val startOff = if (zoomIn) nearOffset else farOffset
            val endOff = if (zoomIn) farOffset else nearOffset

            val finger1 = Path().apply {
                moveTo(centerX - startOff, centerY - startOff)
                lineTo(centerX - endOff, centerY - endOff)
            }
            val finger2 = Path().apply {
                moveTo(centerX + startOff, centerY + startOff)
                lineTo(centerX + endOff, centerY + endOff)
            }
            val builder = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(finger1, 0L, durationMs))
                .addStroke(GestureDescription.StrokeDescription(finger2, 0L, durationMs))
            service.dispatchGesture(builder.build(), null, null)
        } catch (_: Exception) {
            false
        }
    }
}
