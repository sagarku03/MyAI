package com.jarvisai.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Dedicated Accessibility Automation Engine (`AutomationAccessibilityService`) powering
 * `android_automation_mcp`. Tracks UI quiescence settling time (`lastEventAt`), records
 * active foreground window transitions (`foregroundPackage`, `foregroundClass`), dispatches
 * non-blocking coroutine gestures, and captures hardware-accelerated screenshots on Android 11+.
 */
class AutomationAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutomationA11yService"

        @Volatile
        var instance: AutomationAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    @Volatile
    var lastEventAt: Long = 0L

    @Volatile
    var foregroundPackage: String? = null

    @Volatile
    var foregroundClass: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        lastEventAt = SystemClock.uptimeMillis()
        Log.i(TAG, "AutomationAccessibilityService connected and ready.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        lastEventAt = SystemClock.uptimeMillis()

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val pkg = event.packageName?.toString()
                val cls = event.className?.toString()
                if (!pkg.isNullOrBlank()) {
                    foregroundPackage = pkg
                }
                if (!cls.isNullOrBlank()) {
                    foregroundClass = cls
                }
            }
            else -> {
                if (foregroundPackage == null) {
                    event.packageName?.toString()?.takeIf { it.isNotBlank() }?.let {
                        foregroundPackage = it
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutomationAccessibilityService interrupted.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) {
            instance = null
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    /**
     * Suspends until the UI tree has been quiet (no accessibility events) for at least [quietMs],
     * or until [timeoutMs] elapses. Returns `true` if quiescence was reached before timeout.
     */
    suspend fun waitForIdle(quietMs: Long = 400L, timeoutMs: Long = 5_000L): Boolean {
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            val sinceLast = SystemClock.uptimeMillis() - lastEventAt
            if (sinceLast >= quietMs) {
                return true
            }
            val sleepMs = (quietMs - sinceLast).coerceIn(25L, 150L)
            delay(sleepMs)
        }
        return false
    }

    /**
     * Dispatches a continuous [Path] gesture over [durationMs] and suspends until the system
     * reports completion or cancellation.
     */
    suspend fun gesture(path: Path, durationMs: Long = 100L): Boolean {
        val clampedDuration = durationMs.coerceIn(10L, 60_000L)
        val stroke = GestureDescription.StrokeDescription(path, 0L, clampedDuration)
        val description = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        val deferred = CompletableDeferred<Boolean>()
        val dispatched = dispatchGesture(
            description,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    deferred.complete(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    deferred.complete(false)
                }
            },
            null
        )

        if (!dispatched) {
            return false
        }
        return withTimeoutOrNull(clampedDuration + 3_000L) {
            deferred.await()
        } ?: false
    }

    /**
     * Convenience single-tap primitive at screen coordinates ([x], [y]).
     */
    suspend fun tap(x: Float, y: Float, durationMs: Long = 60L): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return gesture(path, durationMs)
    }

    /**
     * Convenience linear swipe primitive from ([startX], [startY]) to ([endX], [endY]).
     */
    suspend fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        return gesture(path, durationMs)
    }

    /**
     * Captures a full-screen screenshot on Android 11+ (`API 30+`) via
     * `takeScreenshot(Display.DEFAULT_DISPLAY, ...)` and converts the hardware buffer
     * into a readable software [Bitmap].
     */
    suspend fun captureScreenshot(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "captureScreenshot requires Android 11 (API 30) or higher.")
            return null
        }

        val deferred = CompletableDeferred<Bitmap?>()
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        val bitmap = try {
                            val hwBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                            val swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            hwBitmap?.recycle()
                            hwBuffer.close()
                            swBitmap
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed wrapping screenshot hardware buffer: ${e.message}", e)
                            null
                        }
                        deferred.complete(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "takeScreenshot failed with errorCode=$errorCode")
                        deferred.complete(null)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "takeScreenshot invocation error: ${e.message}", e)
            deferred.complete(null)
        }

        return withTimeoutOrNull(5_000L) {
            deferred.await()
        }
    }

    /**
     * Returns the current active window's root [AccessibilityNodeInfo] if available.
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        return runCatching { rootInActiveWindow }.getOrNull()
    }
}
