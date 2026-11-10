package com.termux.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.TerminalSession
import kotlin.math.max
import kotlin.math.roundToInt

class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var currentSession: TerminalSession? = null
        private set

    private var viewClient: TerminalViewClient? = null

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = Color.rgb(226, 232, 240)
        textSize = spToPx(14f)
    }

    private val bgPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(56, 189, 248)
        style = Paint.Style.FILL
        alpha = 190
    }

    private var charWidthPx: Float = textPaint.measureText("M").coerceAtLeast(8f)
    private var lineHeightPx: Float = (textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent + 4f).coerceAtLeast(16f)
    private var fontAscentPx: Float = -textPaint.fontMetrics.ascent

    private var scrollOffsetLines: Int = 0

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (isAttachedToWindow && currentSession != null) {
                postInvalidateOnAnimation()
                postDelayed(this, 60L)
            }
        }
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(this@TerminalView, InputMethodManager.SHOW_IMPLICIT)
                viewClient?.onSingleTapUp(e)
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                val deltaLines = (distanceY / lineHeightPx).roundToInt()
                if (deltaLines != 0) {
                    scrollOffsetLines = (scrollOffsetLines - deltaLines).coerceIn(0, 500)
                    invalidate()
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                viewClient?.onLongPress(e)
            }
        }
    )

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentSp = pxToSp(textPaint.textSize)
                val targetSp = (currentSp * detector.scaleFactor).coerceIn(9f, 28f)
                setTextSize(targetSp.roundToInt())
                return true
            }
        }
    )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun setTerminalViewClient(client: TerminalViewClient?) {
        this.viewClient = client
    }

    fun attachSession(session: TerminalSession): Boolean {
        val changed = currentSession !== session
        currentSession = session
        scrollOffsetLines = 0
        updateTerminalSize(width, height)
        invalidate()
        return changed
    }

    fun setTextSize(textSizeSp: Int) {
        val clamped = textSizeSp.coerceIn(8, 32)
        textPaint.textSize = spToPx(clamped.toFloat())
        charWidthPx = textPaint.measureText("M").coerceAtLeast(6f)
        val fm = textPaint.fontMetrics
        lineHeightPx = (fm.descent - fm.ascent + 4f).coerceAtLeast(12f)
        fontAscentPx = -fm.ascent
        updateTerminalSize(width, height)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(refreshRunnable)
        post(refreshRunnable)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(refreshRunnable)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTerminalSize(w, h)
    }

    private fun updateTerminalSize(viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return
        val session = currentSession ?: return
        val cols = max(20, ((viewWidth - paddingLeft - paddingRight) / charWidthPx).toInt())
        val rows = max(5, ((viewHeight - paddingTop - paddingBottom) / lineHeightPx).toInt())
        try {
            session.updateSize(cols, rows)
            viewClient?.onEmulatorSet()
        } catch (_: Exception) {}
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val session = currentSession ?: return
        val emulator = session.emulator ?: return
        val screen = emulator.screen ?: return

        val transcriptText = try {
            screen.transcriptText ?: ""
        } catch (_: Exception) {
            ""
        }

        val allLines = transcriptText.split('\n')
        val visibleRows = max(1, ((height - paddingTop - paddingBottom) / lineHeightPx).toInt())
        val totalLines = allLines.size
        val endIdx = (totalLines - scrollOffsetLines).coerceIn(0, totalLines)
        val startIdx = (endIdx - visibleRows).coerceAtLeast(0)
        val visibleSlice = allLines.subList(startIdx, endIdx)

        val startX = paddingLeft + 8f
        var currentY = paddingTop + fontAscentPx + 4f

        for (line in visibleSlice) {
            canvas.drawText(line, startX, currentY, textPaint)
            currentY += lineHeightPx
        }

        // Draw cursor if at bottom of scrollback
        if (scrollOffsetLines == 0) {
            val cursorRow = emulator.cursorRow.coerceIn(0, visibleRows - 1)
            val cursorCol = emulator.cursorCol.coerceAtLeast(0)
            val cx = startX + cursorCol * charWidthPx
            val cyTop = paddingTop + cursorRow * lineHeightPx + 4f
            canvas.drawRect(cx, cyTop, cx + charWidthPx, cyTop + lineHeightPx, cursorPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE

        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!text.isNullOrEmpty()) {
                    scrollOffsetLines = 0
                    val raw = text.toString()
                    val ctrlActive = viewClient?.readControlKey() == true
                    val altActive = viewClient?.readAltKey() == true
                    if (ctrlActive && raw.length == 1 && raw[0].lowercaseChar() in 'a'..'z') {
                        val ctrlChar = (raw[0].lowercaseChar().code - 'a'.code + 1).toChar().toString()
                        currentSession?.write(ctrlChar)
                    } else if (altActive) {
                        currentSession?.write("\u001b$raw")
                    } else {
                        currentSession?.write(raw)
                    }
                    invalidate()
                }
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    repeat(beforeLength) {
                        currentSession?.write("\u007f")
                    }
                    invalidate()
                }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    handleKeyDown(event.keyCode, event)
                }
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val session = currentSession ?: return false
        if (viewClient?.onKeyDown(keyCode, event, session) == true) {
            return true
        }
        scrollOffsetLines = 0
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> session.write("\r")
            KeyEvent.KEYCODE_DEL -> session.write("\u007f")
            KeyEvent.KEYCODE_FORWARD_DEL -> session.write("\u001b[3~")
            KeyEvent.KEYCODE_TAB -> session.write("\t")
            KeyEvent.KEYCODE_ESCAPE -> session.write("\u001b")
            KeyEvent.KEYCODE_DPAD_UP -> session.write("\u001b[A")
            KeyEvent.KEYCODE_DPAD_DOWN -> session.write("\u001b[B")
            KeyEvent.KEYCODE_DPAD_RIGHT -> session.write("\u001b[C")
            KeyEvent.KEYCODE_DPAD_LEFT -> session.write("\u001b[D")
            KeyEvent.KEYCODE_MOVE_HOME -> session.write("\u001b[H")
            KeyEvent.KEYCODE_MOVE_END -> session.write("\u001b[F")
            else -> {
                val unicodeChar = event.unicodeChar
                if (unicodeChar != 0) {
                    val ctrlDown = event.isCtrlPressed || (viewClient?.readControlKey() == true)
                    if (ctrlDown && unicodeChar in 'a'.code..'z'.code) {
                        session.write((unicodeChar - 'a'.code + 1).toChar().toString())
                    } else {
                        session.write(String(Character.toChars(unicodeChar)))
                    }
                } else {
                    return false
                }
            }
        }
        invalidate()
        return true
    }

    private fun spToPx(sp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)

    private fun pxToSp(px: Float): Float =
        px / resources.displayMetrics.scaledDensity.coerceAtLeast(1f)
}
