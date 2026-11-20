package com.termux.view;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityManager;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.OverScroller;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.termux.terminal.KeyHandler;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.view.textselection.TextSelectionCursorController;

/** View displaying and interacting with a {@link TerminalSession}. */
public final class TerminalView extends View {

    /** Log terminal view key and IME events. */
    private static boolean TERMINAL_VIEW_KEY_LOGGING_ENABLED = true;

    /** The currently displayed {@link TerminalSession}, null if none. */
    public TerminalSession mTermSession;
    /** Our {@link TerminalEmulator} metrics from the session. */
    public TerminalEmulator mEmulator;

    public TerminalRenderer mRenderer;

    public TerminalViewClient mClient;

    private TextSelectionCursorController mTextSelectionCursorController;

    private Handler mTerminalCursorBlinkerHandler;
    private TerminalCursorBlinkerRunnable mTerminalCursorBlinkerRunnable;
    private int mTerminalCursorBlinkerRate;
    private boolean mCursorInvisibleIgnoreOnce;
    public static final int TERMINAL_CURSOR_BLINK_RATE_MIN = 100;
    public static final int TERMINAL_CURSOR_BLINK_RATE_MAX = 2000;

    /** The continuous pixel scroll position. 0 is bottom prompt, max is top of transcript. */
    float mScrollPixelY = 0f;
    int mTopRow;
    float mSubRowOffset = 0.0f;
    int[] mDefaultSelectors = new int[]{-1, -1, -1, -1};

    float mScaleFactor = 1.f;
    final GestureAndScaleRecognizer mGestureRecognizer;

    /** Keep track of where mouse touch event started which we report as mouse scroll. */
    private int mMouseScrollStartX = -1, mMouseScrollStartY = -1;
    /** Keep track of the time when a touch event leading to sending mouse scroll events started. */
    private long mMouseStartDownTime = -1;

    final OverScroller mScroller;
    private Runnable mCurrentFlingRunnable = null;

    private final Paint mScrollBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float mScrollBarAlpha = 0f;
    private long mScrollBarFadeStartTime = 0;
    private static final long SCROLLBAR_FADE_DELAY = 800;
    private static final long SCROLLBAR_FADE_DURATION = 300;
    private final Runnable mScrollBarFadeRunnable = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            long elapsed = now - mScrollBarFadeStartTime;
            if (elapsed < SCROLLBAR_FADE_DELAY) {
                postDelayed(this, SCROLLBAR_FADE_DELAY - elapsed);
            } else {
                long fadeElapsed = elapsed - SCROLLBAR_FADE_DELAY;
                if (fadeElapsed >= SCROLLBAR_FADE_DURATION) {
                    mScrollBarAlpha = 0f;
                    invalidate();
                } else {
                    mScrollBarAlpha = 1.0f - ((float) fadeElapsed / SCROLLBAR_FADE_DURATION);
                    invalidate();
                    postOnAnimation(this);
                }
            }
        }
    };

    public void showScrollBar() {
        mScrollBarAlpha = 1.0f;
        mScrollBarFadeStartTime = SystemClock.uptimeMillis();
        removeCallbacks(mScrollBarFadeRunnable);
        postDelayed(mScrollBarFadeRunnable, SCROLLBAR_FADE_DELAY);
        invalidate();
    }

    /** What was left in from scrolling movement. */
    float mScrollRemainder;

    /** If non-zero, this is the last unicode code point received if that was a combining character. */
    int mCombiningAccent;

    @RequiresApi(api = Build.VERSION_CODES.O)
    private int mAutoFillType = AUTOFILL_TYPE_NONE;
    @RequiresApi(api = Build.VERSION_CODES.O)
    private int mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_NO;
    private String[] mAutoFillHints = new String[0];

    private final boolean mAccessibilityEnabled;

    public final static int KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD = KeyCharacterMap.VIRTUAL_KEYBOARD;
    public final static int KEY_EVENT_SOURCE_SOFT_KEYBOARD = 0;

    private static final String LOG_TAG = "TerminalView";

    public TerminalView(Context context, AttributeSet attributes) { // NO_UCD (unused code)
        super(context, attributes);
        mGestureRecognizer = new GestureAndScaleRecognizer(context, new GestureAndScaleRecognizer.Listener() {

            boolean scrolledWithFinger;

            @Override
            public boolean onUp(MotionEvent event) {
                mScrollRemainder = 0.0f;
                boolean handled = false;
                boolean isMouseActive = mEmulator != null && mEmulator.isMouseTrackingActive();
                if (isMouseActive && !event.isFromSource(InputDevice.SOURCE_MOUSE) && !isSelectingText() && !scrolledWithFinger) {
                    sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, true);
                    sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, false);
                    handled = true;
                }
                scrolledWithFinger = false;
                return handled;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent event) {
                if (mEmulator == null) return true;

                if (isSelectingText()) {
                    stopTextSelectionMode();
                    return true;
                }
                requestFocus();
                if (mClient != null) mClient.onSingleTapUp(event);
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e, float distanceX, float distanceY) {
                if (mEmulator == null) return true;
                boolean isAltScreen = mEmulator.isAlternateBufferActive();
                boolean isMouseActive = mEmulator.isMouseTrackingActive();
                if (isMouseActive && e.isFromSource(InputDevice.SOURCE_MOUSE)) {
                    sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true);
                } else if (isMouseActive || isAltScreen) {
                    scrolledWithFinger = true;
                    mScrollRemainder += distanceY * 1.35f;
                    int lineSpacing = (mRenderer != null && mRenderer.mFontLineSpacing > 0) ? mRenderer.mFontLineSpacing : 40;
                    int deltaRows = (int) (mScrollRemainder / lineSpacing);
                    if (deltaRows != 0) {
                        mScrollRemainder -= deltaRows * lineSpacing;
                        doScroll(e, deltaRows);
                    }
                } else {
                    scrolledWithFinger = true;
                    showScrollBar();
                    int lineSpacing = (mRenderer != null && mRenderer.mFontLineSpacing > 0) ? mRenderer.mFontLineSpacing : 40;
                    int maxScrollPixels = mEmulator.getScreen().getActiveTranscriptRows() * lineSpacing;
                    mScrollPixelY = Math.max(0f, Math.min(maxScrollPixels, mScrollPixelY - distanceY * 1.3f));
                    if (lineSpacing > 0) {
                        mTopRow = -(int) (mScrollPixelY / lineSpacing);
                        float remainder = mScrollPixelY - (-mTopRow * lineSpacing);
                        mSubRowOffset = remainder / lineSpacing;
                    }
                    invalidate();
                }
                return true;
            }

            @Override
            public boolean onScale(float focusX, float focusY, float scale) {
                if (mEmulator == null || isSelectingText()) return true;
                mScaleFactor *= scale;
                if (mClient != null) mScaleFactor = mClient.onScale(mScaleFactor);
                return true;
            }

            @Override
            public boolean onFling(final MotionEvent e2, float velocityX, float velocityY) {
                if (mEmulator == null) return true;
                if (mCurrentFlingRunnable != null) {
                    removeCallbacks(mCurrentFlingRunnable);
                    mCurrentFlingRunnable = null;
                }
                if (!mScroller.isFinished()) {
                    mScroller.forceFinished(true);
                }
                showScrollBar();

                final boolean mouseOrAltAtStartOfFling = mEmulator.isMouseTrackingActive() || mEmulator.isAlternateBufferActive();
                if (mouseOrAltAtStartOfFling) {
                    float SCALE = 0.85f;
                    mScroller.fling(0, 0, 0, -(int) (velocityY * SCALE), 0, 0, -50000, 50000);
                } else {
                    int lineSpacing = (mRenderer != null && mRenderer.mFontLineSpacing > 0) ? mRenderer.mFontLineSpacing : 40;
                    int maxScrollPixels = mEmulator.getScreen().getActiveTranscriptRows() * lineSpacing;
                    mScroller.fling(0, (int) mScrollPixelY, 0, (int) (velocityY * 1.15f), 0, 0, 0, maxScrollPixels);
                }

                mCurrentFlingRunnable = new Runnable() {
                    private int mLastY = 0;
                    private float mFlingRemainder = 0.0f;

                    @Override
                    public void run() {
                        if (this != mCurrentFlingRunnable) return;
                        boolean currentMouseOrAlt = mEmulator != null && (mEmulator.isMouseTrackingActive() || mEmulator.isAlternateBufferActive());
                        if (mouseOrAltAtStartOfFling != currentMouseOrAlt || mScroller.isFinished()) {
                            mScroller.forceFinished(true);
                            mCurrentFlingRunnable = null;
                            return;
                        }
                        boolean more = mScroller.computeScrollOffset();
                        if (mouseOrAltAtStartOfFling) {
                            int newY = mScroller.getCurrY();
                            int diffPixels = (newY - mLastY);
                            mLastY = newY;
                            mFlingRemainder += diffPixels;
                            int fontHeight = (mRenderer != null && mRenderer.mFontLineSpacing > 0) ? mRenderer.mFontLineSpacing : 40;
                            int deltaRows = (int) (mFlingRemainder / fontHeight);
                            if (deltaRows != 0) {
                                mFlingRemainder -= deltaRows * fontHeight;
                                doScroll(e2, deltaRows);
                            }
                        } else {
                            mScrollPixelY = mScroller.getCurrY();
                            int lineSpacing = (mRenderer != null && mRenderer.mFontLineSpacing > 0) ? mRenderer.mFontLineSpacing : 40;
                            if (lineSpacing > 0) {
                                mTopRow = -(int) (mScrollPixelY / lineSpacing);
                                float remainder = mScrollPixelY - (-mTopRow * lineSpacing);
                                mSubRowOffset = remainder / lineSpacing;
                            }
                            invalidate();
                        }

                        if (more) {
                            postOnAnimation(this);
                        } else {
                            mCurrentFlingRunnable = null;
                        }
                    }
                };
                postOnAnimation(mCurrentFlingRunnable);
                return true;
            }

            @Override
            public boolean onDown(float x, float y) {
                if (mCurrentFlingRunnable != null) {
                    removeCallbacks(mCurrentFlingRunnable);
                    mCurrentFlingRunnable = null;
                }
                if (!mScroller.isFinished()) {
                    mScroller.forceFinished(true);
                }
                mScrollRemainder = 0.0f;
                scrolledWithFinger = false;
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (mGestureRecognizer.isInProgress()) return;
                if (mClient != null && mClient.onLongPress(e)) return;
                if (!isSelectingText()) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    startTextSelectionMode(e);
                }
            }
        });
        mScroller = new OverScroller(context);
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        mAccessibilityEnabled = am != null && am.isEnabled();
        mRenderer = new TerminalRenderer(28, Typeface.MONOSPACE);
    }

    public void setTerminalViewClient(TerminalViewClient client) {
        this.mClient = client;
    }

    public interface TerminalInputListener {
        void onTerminalInput(String text);
        void onTerminalInputCodePoint(boolean prependEscape, int codePoint);
    }
    private TerminalInputListener mInputListener;

    public void setTerminalInputListener(TerminalInputListener listener) {
        this.mInputListener = listener;
    }

    public interface TerminalSizeListener {
        void onTerminalSizeChanged(int cols, int rows, int widthPx, int heightPx);
    }
    private TerminalSizeListener mSizeListener;

    public void setTerminalSizeListener(TerminalSizeListener listener) {
        this.mSizeListener = listener;
        if (mEmulator != null && listener != null) {
            listener.onTerminalSizeChanged(mEmulator.mColumns, mEmulator.mRows, getWidth(), getHeight());
        }
    }

    public void sendInputToSession(String text) {
        if (mInputListener != null) {
            mInputListener.onTerminalInput(text);
        } else if (mTermSession != null) {
            mTermSession.write(text);
        }
    }

    public void sendInputCodePointToSession(boolean prependEscape, int codePoint) {
        if (mInputListener != null) {
            mInputListener.onTerminalInputCodePoint(prependEscape, codePoint);
        } else if (mTermSession != null) {
            mTermSession.writeCodePoint(prependEscape, codePoint);
        }
    }

    public void setIsTerminalViewKeyLoggingEnabled(boolean value) {
        TERMINAL_VIEW_KEY_LOGGING_ENABLED = value;
    }

    public boolean attachSession(TerminalSession session) {
        if (session == mTermSession) return false;
        mTopRow = 0;
        mScrollPixelY = 0f;
        mSubRowOffset = 0f;

        mTermSession = session;
        mEmulator = null;
        mCombiningAccent = 0;

        updateSize();

        if (mSizeListener != null && mEmulator != null) {
            mSizeListener.onTerminalSizeChanged(mEmulator.mColumns, mEmulator.mRows, getWidth(), getHeight());
        }

        setVerticalScrollBarEnabled(true);
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (mClient == null || mClient.isTerminalViewSelected()) {
            if (mClient != null && mClient.shouldEnforceCharBasedInput()) {
                outAttrs.inputType = InputType.TYPE_NULL;
            } else {
                outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
            }
        } else {
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;
        }

        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_FLAG_NO_EXTRACT_UI;

        return new BaseInputConnection(this, true) {

            @Override
            public boolean finishComposingText() {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED && mClient != null) mClient.logInfo(LOG_TAG, "IME: finishComposingText()");
                super.finishComposingText();
                getEditable().clear();
                return true;
            }

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED && mClient != null) {
                    mClient.logInfo(LOG_TAG, "IME: commitText(\"" + text + "\", " + newCursorPosition + ")");
                }
                if (mEmulator == null) return true;
                if (text != null && text.length() > 0) {
                    sendTextToTerminal(text);
                }
                getEditable().clear();
                return true;
            }

            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED && mClient != null) {
                    mClient.logInfo(LOG_TAG, "IME: setComposingText(\"" + text + "\", " + newCursorPosition + ")");
                }
                return super.setComposingText(text, newCursorPosition);
            }

            @Override
            public boolean deleteSurroundingText(int leftLength, int rightLength) {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED && mClient != null) {
                    mClient.logInfo(LOG_TAG, "IME: deleteSurroundingText(" + leftLength + ", " + rightLength + ")");
                }
                // Stock Samsung keyboard with 'Auto check spelling' enabled sends leftLength > 1.
                KeyEvent deleteKey = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
                for (int i = 0; i < leftLength; i++) sendKeyEvent(deleteKey);
                return super.deleteSurroundingText(leftLength, rightLength);
            }

            void sendTextToTerminal(CharSequence text) {
                stopTextSelectionMode();
                final int textLengthInChars = text.length();
                for (int i = 0; i < textLengthInChars; i++) {
                    char firstChar = text.charAt(i);
                    int codePoint;
                    if (Character.isHighSurrogate(firstChar)) {
                        if (++i < textLengthInChars) {
                            codePoint = Character.toCodePoint(firstChar, text.charAt(i));
                        } else {
                            // At end of string, with no low surrogate following the high:
                            codePoint = TerminalEmulator.UNICODE_REPLACEMENT_CHAR;
                        }
                    } else {
                        codePoint = firstChar;
                    }

                    // Check onKeyDown() for details.
                    if (mClient != null && mClient.readShiftKey())
                        codePoint = Character.toUpperCase(codePoint);

                    boolean ctrlHeld = false;
                    if (codePoint <= 31 && codePoint != 27) {
                        if (codePoint == '\n') {
                            // The AOSP keyboard and descendants seems to send \n as text when the enter key is pressed,
                            // instead of a key event like most other keyboard apps. A terminal expects \r for the enter
                            // key (although when icrnl is enabled this doesn't make a difference - run 'stty -icrnl' to
                            // check the behaviour).
                            codePoint = '\r';
                        }

                        // E.g. penti keyboard for ctrl input.
                        ctrlHeld = true;
                        switch (codePoint) {
                            case 31:
                                codePoint = '_';
                                break;
                            case 30:
                                codePoint = '^';
                                break;
                            case 29:
                                codePoint = ']';
                                break;
                            case 28:
                                codePoint = '\\';
                                break;
                            default:
                                codePoint += 96;
                                break;
                        }
                    }

                    inputCodePoint(KEY_EVENT_SOURCE_SOFT_KEYBOARD, codePoint, ctrlHeld, false);
                }
            }
        };
    }

    @Override
    protected int computeVerticalScrollRange() {
        return mEmulator == null ? 1 : mEmulator.getScreen().getActiveRows();
    }

    @Override
    protected int computeVerticalScrollExtent() {
        return mEmulator == null ? 1 : mEmulator.mRows;
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return mEmulator == null ? 1 : mEmulator.getScreen().getActiveRows() + mTopRow - mEmulator.mRows;
    }

    public void onScreenUpdated() {
        onScreenUpdated(false);
    }

    public void onScreenUpdated(boolean skipScrolling) {
        if (mEmulator == null) return;

        int rowsInHistory = mEmulator.getScreen().getActiveTranscriptRows();
        if (mTopRow < -rowsInHistory) mTopRow = -rowsInHistory;

        if (isSelectingText()) {
            int rowShift = mEmulator.getScrollCounter();
            if (-mTopRow + rowShift > rowsInHistory) {
                stopTextSelectionMode();
            } else {
                skipScrolling = true;
                mTopRow -= rowShift;
                decrementYTextSelectionCursors(rowShift);
                int lineSpacing = (mRenderer != null && mRenderer.mFontLineSpacing > 0) ? mRenderer.mFontLineSpacing : 40;
                mScrollPixelY = -mTopRow * lineSpacing;
                mSubRowOffset = 0f;
            }
        } else if (mTopRow != 0) {
            int rowShift = mEmulator.getScrollCounter();
            if (-mTopRow + rowShift > rowsInHistory) {
                mTopRow = -rowsInHistory;
            } else {
                mTopRow -= rowShift;
            }
            int lineSpacing = (mRenderer != null && mRenderer.mFontLineSpacing > 0) ? mRenderer.mFontLineSpacing : 40;
            mScrollPixelY = -mTopRow * lineSpacing;
            mSubRowOffset = 0f;
        }

        mEmulator.clearScrollCounter();

        invalidate();
        if (mAccessibilityEnabled) setContentDescription(getText());
    }

    public void onContextMenuClosed(Menu menu) {
        unsetStoredSelectedText();
        if (mClient != null) mClient.logInfo(LOG_TAG, "onContextMenuClosed");
    }

    public int getTextSize() {
        return mRenderer == null ? 0 : mRenderer.mTextSize;
    }

    public void setTextSize(int textSize) {
        if (mRenderer != null && mRenderer.mTextSize == textSize) {
            return;
        }
        mRenderer = new TerminalRenderer(textSize, mRenderer == null ? Typeface.MONOSPACE : mRenderer.mTypeface);
        updateSize();
    }

    public void setTypeface(Typeface newTypeface) {
        mRenderer = new TerminalRenderer(mRenderer.mTextSize, newTypeface);
        updateSize();
        invalidate();
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public boolean isOpaque() {
        return true;
    }

    /**
     * Get the zero indexed column and row of the terminal view for the
     * position of the event.
     *
     * @param event The event with the position to get the column and row for.
     * @param relativeToScroll If true the column number will take the scroll
     * position into account. E.g. if scrolled 3 lines up and the event
     * position is in the top left corner, the column and row will be -3 and 0
     * (if relativeToScroll is true) or 0 and 0 (if relativeToScroll is false).
     * @return Array with the column and row.
     */
    public int[] getColumnAndRow(MotionEvent event, boolean relativeToScroll) {
        int column = (int) (event.getX() / mRenderer.mFontWidth);
        int row;
        if (relativeToScroll) {
            row = (int) Math.floor((event.getY() - mTopPadding - mScrollPixelY) / (double) mRenderer.mFontLineSpacing);
        } else {
            row = (int) Math.floor((event.getY() - mTopPadding) / (double) mRenderer.mFontLineSpacing);
        }
        return new int[]{column, row};
    }

    /** Send a single mouse event code to the terminal. */
    void sendMouseEventCode(MotionEvent e, int button, boolean pressed) {
        int[] columnAndRow = getColumnAndRow(e, false);
        int x = columnAndRow[0] + 1;
        int y = columnAndRow[1] + 1;
        if (pressed && (button == TerminalEmulator.MOUSE_WHEELDOWN_BUTTON || button == TerminalEmulator.MOUSE_WHEELUP_BUTTON)) {
            if (mMouseStartDownTime == e.getDownTime()) {
                x = mMouseScrollStartX;
                y = mMouseScrollStartY;
            } else {
                mMouseStartDownTime = e.getDownTime();
                mMouseScrollStartX = x;
                mMouseScrollStartY = y;
            }
        }
        if (mEmulator != null && mEmulator.isMouseTrackingActive()) {
            mEmulator.sendMouseEvent(button, x, y, pressed);
        }
    }

    /** Perform a scroll, either from dragging the screen or by scrolling a mouse wheel. */
    void doScroll(MotionEvent event, int rowsDown) {
        boolean up = rowsDown < 0;
        int amount = Math.abs(rowsDown);
        boolean isAltScreen = mEmulator != null && mEmulator.isAlternateBufferActive();
        boolean isMouseActive = mEmulator != null && mEmulator.isMouseTrackingActive();
        for (int i = 0; i < amount; i++) {
            if (isMouseActive) {
                sendMouseEventCode(event, up ? TerminalEmulator.MOUSE_WHEELUP_BUTTON : TerminalEmulator.MOUSE_WHEELDOWN_BUTTON, true);
            } else if (isAltScreen) {
                sendInputToSession(up ? (mEmulator != null && mEmulator.isCursorKeysApplicationMode() ? "\033OA" : "\033[A")
                                       : (mEmulator != null && mEmulator.isCursorKeysApplicationMode() ? "\033OB" : "\033[B"));
            } else {
                mTopRow = Math.min(0, Math.max(-(mEmulator.getScreen().getActiveTranscriptRows()), mTopRow + (up ? -1 : 1)));
                int lineSpacing = (mRenderer != null && mRenderer.mFontLineSpacing > 0) ? mRenderer.mFontLineSpacing : 40;
                mScrollPixelY = -mTopRow * lineSpacing;
                mSubRowOffset = 0f;
                showScrollBar();
                invalidate();
            }
        }
    }

    /** Overriding {@link View#onGenericMotionEvent(MotionEvent)}. */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mEmulator != null && event.isFromSource(InputDevice.SOURCE_MOUSE) && event.getAction() == MotionEvent.ACTION_SCROLL) {
            boolean up = event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0.0f;
            doScroll(event, up ? -3 : 3);
            return true;
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    @TargetApi(23)
    public boolean onTouchEvent(MotionEvent event) {
        if (mEmulator == null) return true;
        final int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            if (mCurrentFlingRunnable != null) {
                removeCallbacks(mCurrentFlingRunnable);
                mCurrentFlingRunnable = null;
            }
            if (!mScroller.isFinished()) {
                mScroller.forceFinished(true);
            }
        }

        if (isSelectingText()) {
            updateFloatingToolbarVisibility(event);
            mGestureRecognizer.onTouchEvent(event);
            return true;
        } else if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                if (action == MotionEvent.ACTION_DOWN) showContextMenu();
                return true;
            } else if (event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) {
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = clipboard.getPrimaryClip();
                if (clipData != null) {
                    ClipData.Item clipItem = clipData.getItemAt(0);
                    if (clipItem != null) {
                        CharSequence text = clipItem.coerceToText(getContext());
                        if (!TextUtils.isEmpty(text)) mEmulator.paste(text.toString());
                    }
                }
            } else if (mEmulator.isMouseTrackingActive()) { // BUTTON_PRIMARY.
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_UP:
                        sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, event.getAction() == MotionEvent.ACTION_DOWN);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true);
                        break;
                }
            }
        }

        mGestureRecognizer.onTouchEvent(event);
        return true;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED && mClient != null)
            mClient.logInfo(LOG_TAG, "onKeyPreIme(keyCode=" + keyCode + ", event=" + event + ")");
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            cancelRequestAutoFill();
            if (isSelectingText()) {
                stopTextSelectionMode();
                return true;
            } else if (mClient != null && mClient.shouldBackButtonBeMappedToEscape()) {
                switch (event.getAction()) {
                    case KeyEvent.ACTION_DOWN:
                        return onKeyDown(keyCode, event);
                    case KeyEvent.ACTION_UP:
                        return onKeyUp(keyCode, event);
                }
            }
        } else if (mClient != null && mClient.shouldUseCtrlSpaceWorkaround() &&
            keyCode == KeyEvent.KEYCODE_SPACE && event.isCtrlPressed()) {
            return onKeyDown(keyCode, event);
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED && mClient != null)
            mClient.logInfo(LOG_TAG, "onKeyDown(keyCode=" + keyCode + ", isSystem()=" + event.isSystem() + ", event=" + event + ")");
        if (mEmulator == null) return true;
        if (isSelectingText()) {
            stopTextSelectionMode();
        }

        if (mClient != null && mClient.onKeyDown(keyCode, event, mTermSession)) {
            invalidate();
            return true;
        } else if (event.isSystem() && (mClient == null || !mClient.shouldBackButtonBeMappedToEscape() || keyCode != KeyEvent.KEYCODE_BACK)) {
            return super.onKeyDown(keyCode, event);
        } else if (event.getAction() == KeyEvent.ACTION_MULTIPLE && keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            sendInputToSession(event.getCharacters());
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH) {
            return super.onKeyDown(keyCode, event);
        }

        final int metaState = event.getMetaState();
        final boolean controlDown = event.isCtrlPressed() || (mClient != null && mClient.readControlKey());
        final boolean leftAltDown = (metaState & KeyEvent.META_ALT_LEFT_ON) != 0 || (mClient != null && mClient.readAltKey());
        final boolean shiftDown = event.isShiftPressed() || (mClient != null && mClient.readShiftKey());
        final boolean rightAltDownFromEvent = (metaState & KeyEvent.META_ALT_RIGHT_ON) != 0;

        int keyMod = 0;
        if (controlDown) keyMod |= KeyHandler.KEYMOD_CTRL;
        if (event.isAltPressed() || leftAltDown) keyMod |= KeyHandler.KEYMOD_ALT;
        if (shiftDown) keyMod |= KeyHandler.KEYMOD_SHIFT;
        if (event.isNumLockOn()) keyMod |= KeyHandler.KEYMOD_NUM_LOCK;
        if (!event.isFunctionPressed() && handleKeyCode(keyCode, keyMod)) {
            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED && mClient != null) mClient.logInfo(LOG_TAG, "handleKeyCode() took key event");
            return true;
        }

        int bitsToClear = KeyEvent.META_CTRL_MASK;
        if (!rightAltDownFromEvent) {
            bitsToClear |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        }
        int effectiveMetaState = event.getMetaState() & ~bitsToClear;

        if (shiftDown) effectiveMetaState |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        if (mClient != null && mClient.readFnKey()) effectiveMetaState |= KeyEvent.META_FUNCTION_ON;

        int result = event.getUnicodeChar(effectiveMetaState);
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED && mClient != null)
            mClient.logInfo(LOG_TAG, "KeyEvent#getUnicodeChar(" + effectiveMetaState + ") returned: " + result);
        if (result == 0) {
            return false;
        }

        int oldCombiningAccent = mCombiningAccent;
        if ((result & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            if (mCombiningAccent != 0)
                inputCodePoint(event.getDeviceId(), mCombiningAccent, controlDown, leftAltDown);
            mCombiningAccent = result & KeyCharacterMap.COMBINING_ACCENT_MASK;
        } else {
            if (mCombiningAccent != 0) {
                int combinedChar = KeyCharacterMap.getDeadChar(mCombiningAccent, result);
                if (combinedChar > 0) result = combinedChar;
                mCombiningAccent = 0;
            }
            inputCodePoint(event.getDeviceId(), result, controlDown, leftAltDown);
        }

        if (mCombiningAccent != oldCombiningAccent) invalidate();

        return true;
    }

    public void inputCodePoint(int eventSource, int codePoint, boolean controlDownFromEvent, boolean leftAltDownFromEvent) {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED && mClient != null) {
            mClient.logInfo(LOG_TAG, "inputCodePoint(eventSource=" + eventSource + ", codePoint=" + codePoint + ", controlDownFromEvent=" + controlDownFromEvent + ", leftAltDownFromEvent="
                + leftAltDownFromEvent + ")");
        }

        if (mTermSession == null) return;

        if (mEmulator != null)
            mEmulator.setCursorBlinkState(true);

        final boolean controlDown = controlDownFromEvent || (mClient != null && mClient.readControlKey());
        final boolean altDown = leftAltDownFromEvent || (mClient != null && mClient.readAltKey());

        if (mClient != null && mClient.onCodePoint(codePoint, controlDown, mTermSession)) return;

        if (controlDown) {
            if (codePoint >= 'a' && codePoint <= 'z') {
                codePoint = codePoint - 'a' + 1;
            } else if (codePoint >= 'A' && codePoint <= 'Z') {
                codePoint = codePoint - 'A' + 1;
            } else if (codePoint == ' ' || codePoint == '2') {
                codePoint = 0;
            } else if (codePoint == '[' || codePoint == '3') {
                codePoint = 27;
            } else if (codePoint == '\\' || codePoint == '4') {
                codePoint = 28;
            } else if (codePoint == ']' || codePoint == '5') {
                codePoint = 29;
            } else if (codePoint == '^' || codePoint == '6') {
                codePoint = 30;
            } else if (codePoint == '_' || codePoint == '7' || codePoint == '/') {
                codePoint = 31;
            } else if (codePoint == '8') {
                codePoint = 127;
            }
        }

        if (codePoint > -1) {
            if (eventSource > KEY_EVENT_SOURCE_SOFT_KEYBOARD) {
                switch (codePoint) {
                    case 0x02DC:
                        codePoint = 0x007E;
                        break;
                    case 0x02CB:
                        codePoint = 0x0060;
                        break;
                    case 0x02C6:
                        codePoint = 0x005E;
                        break;
                }
            }

            sendInputCodePointToSession(altDown, codePoint);
        }
    }

    /** Input the specified keyCode if applicable and return if the input was consumed. */
    public boolean handleKeyCode(int keyCode, int keyMod) {
        if (mEmulator != null)
            mEmulator.setCursorBlinkState(true);

        if (handleKeyCodeAction(keyCode, keyMod))
            return true;

        boolean isAltBuffer = mEmulator != null && mEmulator.isAlternateBufferActive();
        switch (keyCode) {
            case KeyEvent.KEYCODE_MOVE_HOME:
            case KeyEvent.KEYCODE_HOME:
                if (isAltBuffer) {
                    sendInputToSession("\033OH");
                } else {
                    sendInputToSession("\001");
                }
                return true;
            case KeyEvent.KEYCODE_MOVE_END:
                if (isAltBuffer) {
                    sendInputToSession("\033OF");
                } else {
                    sendInputToSession("\005");
                }
                return true;
        }

        TerminalEmulator term = mTermSession.getEmulator();
        String code = KeyHandler.getCode(keyCode, keyMod, term.isCursorKeysApplicationMode(), term.isKeypadApplicationMode());
        if (code == null) return false;
        sendInputToSession(code);
        return true;
    }

    public boolean handleKeyCodeAction(int keyCode, int keyMod) {
        boolean shiftDown = (keyMod & KeyHandler.KEYMOD_SHIFT) != 0;
        switch (keyCode) {
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (shiftDown) {
                    long time = SystemClock.uptimeMillis();
                    MotionEvent motionEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0, 0, 0);
                    doScroll(motionEvent, keyCode == KeyEvent.KEYCODE_PAGE_UP ? -mEmulator.mRows : mEmulator.mRows);
                    motionEvent.recycle();
                    return true;
                }
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED && mClient != null)
            mClient.logInfo(LOG_TAG, "onKeyUp(keyCode=" + keyCode + ", event=" + event + ")");

        if (mEmulator == null && keyCode != KeyEvent.KEYCODE_BACK) return true;

        if (mClient != null && mClient.onKeyUp(keyCode, event)) {
            invalidate();
            return true;
        } else if (event.isSystem()) {
            return super.onKeyUp(keyCode, event);
        }

        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        updateSizeInternal();
    }

    public void updateSize() {
        updateSizeInternal();
    }

    public int mTopPadding = 0;

    public void setTopPadding(int topPadding) {
        if (mTopPadding != topPadding) {
            mTopPadding = topPadding;
            updateSizeInternal();
            invalidate();
        }
    }

    private void updateSizeInternal() {
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth == 0 || viewHeight == 0 || mTermSession == null) return;

        int usableHeight = Math.max(0, viewHeight - mTopPadding);

        int newColumns = Math.max(4, (int) (viewWidth / mRenderer.mFontWidth));
        int newRows = Math.max(4, (usableHeight - mRenderer.mFontLineSpacingAndAscent) / mRenderer.mFontLineSpacing);

        if (mEmulator == null || (newColumns != mEmulator.mColumns || newRows != mEmulator.mRows)) {
            mTermSession.updateSize(newColumns, newRows);
            mEmulator = mTermSession.getEmulator();
            if (mClient != null) mClient.onEmulatorSet();

            if (mSizeListener != null) {
                mSizeListener.onTerminalSizeChanged(newColumns, newRows, viewWidth, viewHeight);
            }

            if (mTerminalCursorBlinkerRunnable != null)
                mTerminalCursorBlinkerRunnable.setEmulator(mEmulator);

            mTopRow = 0;
            mScrollPixelY = 0f;
            mSubRowOffset = 0f;
            scrollTo(0, 0);
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mEmulator == null) {
            canvas.drawColor(0XFF000000);
        } else {
            canvas.save();
            canvas.clipRect(0, 0, getWidth(), getHeight());

            int[] sel = mDefaultSelectors;
            if (mTextSelectionCursorController != null) {
                mTextSelectionCursorController.getSelectors(sel);
            }

            mRenderer.render(mEmulator, canvas, mTopRow, mSubRowOffset, sel[0], sel[1], sel[2], sel[3], mTopPadding, mScrollPixelY);

            renderTextSelection();

            canvas.restore();

            if (mScrollBarAlpha > 0f) {
                int totalRows = mEmulator.getScreen().getActiveRows();
                int visibleRows = mEmulator.mRows;
                if (totalRows > visibleRows) {
                    float density = getResources().getDisplayMetrics().density;
                    float barWidth = 2.0f * density;
                    float rightMargin = 2.0f * density;
                    float trackTop = mTopPadding;
                    float trackHeight = getHeight() - mTopPadding;
                    if (trackHeight > 0) {
                        float ratio = (float) visibleRows / (float) totalRows;
                        float thumbHeight = Math.max(20f * density, trackHeight * ratio);
                        int transcriptRows = mEmulator.getScreen().getActiveTranscriptRows();
                        int lineSpacing = (mRenderer != null && mRenderer.mFontLineSpacing > 0) ? mRenderer.mFontLineSpacing : 40;
                        float maxScrollPixels = transcriptRows * lineSpacing;
                        float scrollFraction = (maxScrollPixels > 0) ? (1f - (mScrollPixelY / maxScrollPixels)) : 1f;
                        scrollFraction = Math.max(0f, Math.min(1f, scrollFraction));
                        float thumbTop = trackTop + (trackHeight - thumbHeight) * scrollFraction;
                        float left = getWidth() - rightMargin - barWidth;
                        float right = left + barWidth;
                        mScrollBarPaint.setColor(0xFFFFFFFF);
                        mScrollBarPaint.setAlpha((int) (mScrollBarAlpha * 125));
                        canvas.drawRoundRect(left, thumbTop, right, thumbTop + thumbHeight, barWidth / 2f, barWidth / 2f, mScrollBarPaint);
                    }
                }
            }
        }
    }

    public TerminalSession getCurrentSession() {
        return mTermSession;
    }

    private CharSequence getText() {
        return mEmulator.getScreen().getSelectedText(0, mTopRow, mEmulator.mColumns, mTopRow + mEmulator.mRows);
    }

    public int getCursorX(float x) {
        return (int) (x / mRenderer.mFontWidth);
    }

    public int getCursorY(float y) {
        if (mRenderer == null || mRenderer.mFontLineSpacing <= 0) return mTopRow;
        return (int) Math.floor((y - mTopPadding - mScrollPixelY) / (double) mRenderer.mFontLineSpacing);
    }

    public int getPointX(int cx) {
        if (cx > mEmulator.mColumns) {
            cx = mEmulator.mColumns;
        }
        return Math.round(cx * mRenderer.mFontWidth);
    }

    public int getPointY(int cy) {
        if (mRenderer == null) return mTopPadding;
        return Math.round(mTopPadding + cy * mRenderer.mFontLineSpacing + mScrollPixelY);
    }

    public int getTopRow() {
        return mTopRow;
    }

    public void setTopRow(int mTopRow) {
        this.mTopRow = mTopRow;
        this.mScrollPixelY = -mTopRow * (mRenderer != null ? mRenderer.mFontLineSpacing : 0);
        this.mSubRowOffset = 0f;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void autofill(AutofillValue value) {
        if (value.isText()) {
            sendInputToSession(value.getTextValue().toString());
        }
        resetAutoFill();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int getAutofillType() {
        return mAutoFillType;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public String[] getAutofillHints() {
        return mAutoFillHints;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public AutofillValue getAutofillValue() {
        return AutofillValue.forText("");
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int getImportantForAutofill() {
        return mAutoFillImportance;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private synchronized void resetAutoFill() {
        mAutoFillType = AUTOFILL_TYPE_NONE;
        mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_NO;
        mAutoFillHints = new String[0];
    }

    public AutofillManager getAutoFillManagerService() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null;
        try {
            Context context = getContext();
            if (context == null) return null;
            return context.getSystemService(AutofillManager.class);
        } catch (Exception e) {
            if (mClient != null) mClient.logStackTraceWithMessage(LOG_TAG, "Failed to get AutofillManager service", e);
            return null;
        }
    }

    public boolean isAutoFillEnabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;
        try {
            AutofillManager autofillManager = getAutoFillManagerService();
            return autofillManager != null && autofillManager.isEnabled();
        } catch (Exception e) {
            if (mClient != null) mClient.logStackTraceWithMessage(LOG_TAG, "Failed to check if Autofill is enabled", e);
            return false;
        }
    }

    public synchronized void requestAutoFillUsername() {
        requestAutoFill(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new String[]{View.AUTOFILL_HINT_USERNAME} : null);
    }

    public synchronized void requestAutoFillPassword() {
        requestAutoFill(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new String[]{View.AUTOFILL_HINT_PASSWORD} : null);
    }

    public synchronized void requestAutoFill(String[] autoFillHints) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        if (autoFillHints == null || autoFillHints.length < 1) return;
        try {
            AutofillManager autofillManager = getAutoFillManagerService();
            if (autofillManager != null && autofillManager.isEnabled()) {
                mAutoFillType = AUTOFILL_TYPE_TEXT;
                mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_YES;
                mAutoFillHints = autoFillHints;
                autofillManager.requestAutofill(this);
            }
        } catch (Exception e) {
            if (mClient != null) mClient.logStackTraceWithMessage(LOG_TAG, "Failed to request Autofill", e);
        }
    }

    public synchronized void cancelRequestAutoFill() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        if (mAutoFillType == AUTOFILL_TYPE_NONE) return;
        try {
            AutofillManager autofillManager = getAutoFillManagerService();
            if (autofillManager != null && autofillManager.isEnabled()) {
                resetAutoFill();
                autofillManager.cancel();
            }
        } catch (Exception e) {
            if (mClient != null) mClient.logStackTraceWithMessage(LOG_TAG, "Failed to cancel Autofill request", e);
        }
    }

    /**
     * Set terminal cursor blinker rate. It must be between {@link #TERMINAL_CURSOR_BLINK_RATE_MIN}
     * and {@link #TERMINAL_CURSOR_BLINK_RATE_MAX}, otherwise it will be disabled.
     *
     * @param blinkRate The value to set.
     * @return Returns {@code true} if setting blinker rate was successful, otherwise [@code false}.
     */
    public synchronized boolean setTerminalCursorBlinkerRate(int blinkRate) {
        boolean result;

        // If cursor blinking rate is not valid
        if (blinkRate != 0 && (blinkRate < TERMINAL_CURSOR_BLINK_RATE_MIN || blinkRate > TERMINAL_CURSOR_BLINK_RATE_MAX)) {
            if (mClient != null)
                mClient.logError(LOG_TAG, "The cursor blink rate must be in between " + TERMINAL_CURSOR_BLINK_RATE_MIN + "-" + TERMINAL_CURSOR_BLINK_RATE_MAX + ": " + blinkRate);
            mTerminalCursorBlinkerRate = 0;
            result = false;
        } else {
            if (mClient != null)
                mClient.logVerbose(LOG_TAG, "Setting cursor blinker rate to " + blinkRate);
            mTerminalCursorBlinkerRate = blinkRate;
            result = true;
        }

        if (mTerminalCursorBlinkerRate == 0) {
            stopTerminalCursorBlinker();
        }

        return result;
    }

    /**
     * Sets whether cursor blinker should be started or stopped.
     */
    public synchronized void setTerminalCursorBlinkerState(boolean start, boolean startOnlyIfCursorEnabled) {
        // Stop any existing cursor blinker callbacks
        stopTerminalCursorBlinker();

        if (mEmulator == null) return;

        mEmulator.setCursorBlinkingEnabled(false);

        if (start) {
            // If cursor blinker is not enabled or is not valid
            if (mTerminalCursorBlinkerRate < TERMINAL_CURSOR_BLINK_RATE_MIN || mTerminalCursorBlinkerRate > TERMINAL_CURSOR_BLINK_RATE_MAX)
                return;
            // If cursor blinder is to be started only if cursor is enabled
            else if (startOnlyIfCursorEnabled && !mEmulator.isCursorEnabled()) {
                return;
            }

            // Start cursor blinker runnable
            if (mTerminalCursorBlinkerHandler == null)
                mTerminalCursorBlinkerHandler = new Handler(Looper.getMainLooper());
            mTerminalCursorBlinkerRunnable = new TerminalCursorBlinkerRunnable(mEmulator, mTerminalCursorBlinkerRate);
            mEmulator.setCursorBlinkingEnabled(true);
            mTerminalCursorBlinkerRunnable.run();
        }
    }

    /**
     * Cancel the terminal cursor blinkerCallbacks
     */
    private void stopTerminalCursorBlinker() {
        if (mTerminalCursorBlinkerHandler != null && mTerminalCursorBlinkerRunnable != null) {
            mTerminalCursorBlinkerHandler.removeCallbacks(mTerminalCursorBlinkerRunnable);
        }
    }

    private class TerminalCursorBlinkerRunnable implements Runnable {

        private TerminalEmulator mEmulator;
        private final int mBlinkRate;

        // Initialize with false so that initial blink state is visible after toggling
        boolean mCursorVisible = false;

        public TerminalCursorBlinkerRunnable(TerminalEmulator emulator, int blinkRate) {
            mEmulator = emulator;
            mBlinkRate = blinkRate;
        }

        public void setEmulator(TerminalEmulator emulator) {
            mEmulator = emulator;
        }

        public void run() {
            try {
                if (mEmulator != null) {
                    // Toggle the blink state and then invalidate() the view so
                    // that onDraw() is called, which then calls TerminalRenderer.render()
                    // which checks TerminalEmulator.shouldCursorBeVisible() to decide whether
                    // to draw the cursor or not
                    mCursorVisible = !mCursorVisible;
                    mEmulator.setCursorBlinkState(mCursorVisible);
                    invalidate();
                }
            } finally {
                // Recall the Runnable after mBlinkRate milliseconds to toggle the blink state
                mTerminalCursorBlinkerHandler.postDelayed(this, mBlinkRate);
            }
        }
    }

    /**
     * Define functions required for text selection and its handles.
     */
    TextSelectionCursorController getTextSelectionCursorController() {
        if (mTextSelectionCursorController == null) {
            mTextSelectionCursorController = new TextSelectionCursorController(this);

            final ViewTreeObserver observer = getViewTreeObserver();
            if (observer != null) {
                observer.addOnTouchModeChangeListener(mTextSelectionCursorController);
            }
        }

        return mTextSelectionCursorController;
    }

    private void showTextSelectionCursors(MotionEvent event) {
        getTextSelectionCursorController().show(event);
    }

    private boolean hideTextSelectionCursors() {
        return getTextSelectionCursorController().hide();
    }

    private void renderTextSelection() {
        if (mTextSelectionCursorController != null)
            mTextSelectionCursorController.render();
    }

    public boolean isSelectingText() {
        if (mTextSelectionCursorController != null) {
            return mTextSelectionCursorController.isActive();
        } else {
            return false;
        }
    }

    /** Get the currently selected text if selecting. */
    public String getSelectedText() {
        if (isSelectingText() && mTextSelectionCursorController != null)
            return mTextSelectionCursorController.getSelectedText();
        else
            return null;
    }

    /** Get the selected text stored before "MORE" button was pressed on the context menu. */
    @Nullable
    public String getStoredSelectedText() {
        return mTextSelectionCursorController != null ? mTextSelectionCursorController.getStoredSelectedText() : null;
    }

    /** Unset the selected text stored before "MORE" button was pressed on the context menu. */
    public void unsetStoredSelectedText() {
        if (mTextSelectionCursorController != null) mTextSelectionCursorController.unsetStoredSelectedText();
    }

    private ActionMode getTextSelectionActionMode() {
        if (mTextSelectionCursorController != null) {
            return mTextSelectionCursorController.getActionMode();
        } else {
            return null;
        }
    }

    public void startTextSelectionMode(MotionEvent event) {
        if (!requestFocus()) {
            return;
        }

        showTextSelectionCursors(event);
        if (mClient != null) mClient.copyModeChanged(isSelectingText());

        invalidate();
    }

    public void stopTextSelectionMode() {
        if (hideTextSelectionCursors()) {
            if (mClient != null) mClient.copyModeChanged(isSelectingText());
            invalidate();
        }
    }

    private void decrementYTextSelectionCursors(int decrement) {
        if (mTextSelectionCursorController != null) {
            mTextSelectionCursorController.decrementYTextSelectionCursors(decrement);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mTextSelectionCursorController != null) {
            getViewTreeObserver().addOnTouchModeChangeListener(mTextSelectionCursorController);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mTextSelectionCursorController != null) {
            // Might solve the following exception
            // android.view.WindowLeaked: Activity com.termux.app.TermuxActivity has leaked window android.widget.PopupWindow
            stopTextSelectionMode();

            getViewTreeObserver().removeOnTouchModeChangeListener(mTextSelectionCursorController);
            mTextSelectionCursorController.onDetached();
        }
    }

    /**
     * Define functions required for long hold toolbar.
     */
    private final Runnable mShowFloatingToolbar = new Runnable() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void run() {
            if (getTextSelectionActionMode() != null) {
                getTextSelectionActionMode().hide(0);
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void showFloatingToolbar() {
        if (getTextSelectionActionMode() != null) {
            int delay = ViewConfiguration.getDoubleTapTimeout();
            postDelayed(mShowFloatingToolbar, delay);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    void hideFloatingToolbar() {
        if (getTextSelectionActionMode() != null) {
            removeCallbacks(mShowFloatingToolbar);
            getTextSelectionActionMode().hide(-1);
        }
    }

    public void updateFloatingToolbarVisibility(MotionEvent event) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && getTextSelectionActionMode() != null) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    hideFloatingToolbar();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    showFloatingToolbar();
            }
        }
    }
}
