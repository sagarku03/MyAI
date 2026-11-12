package com.termux.view.textselection;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.PopupWindow;
import com.termux.view.TerminalView;
import com.termux.view.textselection.support.PopupWindowCompatGingerbread;

/**
 * Draggable teardrop selection handle for {@link TerminalView} text selection.
 * Uses hardware-accelerated vector canvas drawing so it never requires external PNG/XML drawables.
 */
@SuppressLint("ViewConstructor")
public class TextSelectionHandleView extends View {

    private PopupWindow mHandle;
    private final CursorController mCursorController;
    private final TerminalView mTerminalView;

    public static final int LEFT = 0;
    public static final int RIGHT = 2;

    private final Paint mHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mHandlePath = new Path();

    private int mPointX;
    private int mPointY;
    private float mTouchToWindowOffsetX;
    private float mTouchToWindowOffsetY;
    private float mHotspotX;
    private float mHotspotY;
    private float mTouchOffsetY;
    private int mLastParentX;
    private int mLastParentY;

    private int mHandleWidth;
    private int mHandleHeight;
    private final int mInitialOrientation;
    private int mOrientation;
    private float mLastTime;

    final int[] mTempCoords = new int[2];

    public TextSelectionHandleView(TerminalView terminalView, CursorController cursorController, int initialOrientation) {
        super(terminalView.getContext());
        mTerminalView = terminalView;
        mCursorController = cursorController;
        mInitialOrientation = initialOrientation;

        mHandlePaint.setColor(Color.parseColor("#00E5FF"));
        mHandlePaint.setStyle(Paint.Style.FILL);

        int sizePx = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            24f,
            terminalView.getResources().getDisplayMetrics()
        );
        mHandleWidth = sizePx;
        mHandleHeight = sizePx;
        setOrientation(mInitialOrientation);
    }

    private void initHandle() {
        mHandle = new PopupWindow(mTerminalView.getContext(), null, android.R.attr.textSelectHandleWindowStyle);
        mHandle.setSplitTouchEnabled(true);
        mHandle.setClippingEnabled(false);
        mHandle.setBackgroundDrawable(null);
        mHandle.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        mHandle.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mHandle.setContentView(this);
        PopupWindowCompatGingerbread.setWindowLayoutType(mHandle, WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
        switch (orientation) {
            case LEFT:
                mHotspotX = (mHandleWidth * 3) / 4f;
                break;
            case RIGHT:
            default:
                mHotspotX = mHandleWidth / 4f;
                break;
        }
        mHotspotY = 0;
        mTouchOffsetY = -mHandleHeight * 0.3f;
        invalidate();
    }

    public void show() {
        if (!isPositionVisible()) {
            hide();
            return;
        }
        removeFromParent();
        initHandle();
        invalidate();

        final int[] coords = mTempCoords;
        mTerminalView.getLocationInWindow(coords);
        coords[0] += mPointX;
        coords[1] += mPointY;

        if (mHandle != null) {
            try {
                mHandle.showAtLocation(mTerminalView, 0, coords[0], coords[1]);
            } catch (Exception ignored) {
            }
        }
    }

    public void hide() {
        if (mHandle != null) {
            try {
                mHandle.dismiss();
            } catch (Exception ignored) {
            }
            removeFromParent();
            mHandle = null;
        }
        invalidate();
    }

    public void removeFromParent() {
        if (!isShowing()) {
            ViewParent parent = this.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(this);
            }
        }
    }

    public void positionAtCursor(final int cx, final int cy, boolean forceOrientationCheck) {
        int x = mTerminalView.getPointX(cx);
        int y = mTerminalView.getPointY(cy + 1);
        moveTo(x, y, forceOrientationCheck);
    }

    private void moveTo(int x, int y, boolean forceOrientationCheck) {
        float oldHotspotX = mHotspotX;
        checkChangedOrientation(x, forceOrientationCheck);
        mPointX = (int) (x - (isShowing() ? oldHotspotX : mHotspotX));
        mPointY = y;

        if (isPositionVisible()) {
            int[] coords = null;
            if (isShowing()) {
                coords = mTempCoords;
                mTerminalView.getLocationInWindow(coords);
                int x1 = coords[0] + mPointX;
                int y1 = coords[1] + mPointY;
                if (mHandle != null) {
                    try {
                        mHandle.update(x1, y1, getWidth(), getHeight());
                    } catch (Exception ignored) {
                    }
                }
            } else {
                show();
            }

            if (mIsDragging) {
                if (coords == null) {
                    coords = mTempCoords;
                    mTerminalView.getLocationInWindow(coords);
                }
                if (coords[0] != mLastParentX || coords[1] != mLastParentY) {
                    mTouchToWindowOffsetX += coords[0] - mLastParentX;
                    mTouchToWindowOffsetY += coords[1] - mLastParentY;
                    mLastParentX = coords[0];
                    mLastParentY = coords[1];
                }
            }
        } else {
            hide();
        }
    }

    public void changeOrientation(int orientation) {
        if (mOrientation != orientation) {
            setOrientation(orientation);
        }
    }

    private void checkChangedOrientation(int posX, boolean force) {
        if (!mIsDragging && !force) {
            return;
        }
        long millis = SystemClock.currentThreadTimeMillis();
        if (millis - mLastTime < 50 && !force) {
            return;
        }
        mLastTime = millis;

        final TerminalView hostView = mTerminalView;
        final int left = hostView.getLeft();
        final int width = hostView.getWidth();
        final int top = hostView.getTop();
        final int height = hostView.getHeight();

        android.graphics.Rect clip = new android.graphics.Rect(left, top, left + width, top + height);

        if (mOrientation == LEFT) {
            if (posX - mHandleWidth < clip.left) {
                changeOrientation(RIGHT);
            }
        } else {
            if (posX + mHandleWidth > clip.right) {
                changeOrientation(LEFT);
            }
        }
    }

    private boolean isPositionVisible() {
        if (mIsDragging) {
            return true;
        }
        final int width = mTerminalView.getWidth();
        final int height = mTerminalView.getHeight();
        final int x = (int) (mPointX + mHotspotX);
        final int y = mPointY;
        return x >= 0 && x <= width && y >= 0 && y <= height;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = mHandleWidth;
        float h = mHandleHeight;
        float r = w * 0.42f;
        float cx = w / 2f;
        float cy = h - r - 2f;

        mHandlePath.reset();
        if (mOrientation == LEFT) {
            mHandlePath.moveTo(w * 0.75f, 0f);
            mHandlePath.lineTo(cx - r * 0.7f, cy - r * 0.5f);
            mHandlePath.close();
        } else {
            mHandlePath.moveTo(w * 0.25f, 0f);
            mHandlePath.lineTo(cx + r * 0.7f, cy - r * 0.5f);
            mHandlePath.close();
        }
        c.drawCircle(cx, cy, r, mHandlePaint);
        c.drawPath(mHandlePath, mHandlePaint);
    }

    private boolean mIsDragging;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mTerminalView.updateFloatingToolbarVisibility(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                final float rawX = event.getRawX();
                final float rawY = event.getRawY();
                mTouchToWindowOffsetX = rawX - mPointX;
                mTouchToWindowOffsetY = rawY - mPointY;
                final int[] coords = mTempCoords;
                mTerminalView.getLocationInWindow(coords);
                mLastParentX = coords[0];
                mLastParentY = coords[1];
                mIsDragging = true;
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                final float rawX = event.getRawX();
                final float rawY = event.getRawY();
                final float newPosX = rawX - mTouchToWindowOffsetX + mHotspotX;
                final float newPosY = rawY - mTouchToWindowOffsetY + mHotspotY + mTouchOffsetY;
                mCursorController.updatePosition(this, Math.round(newPosX), Math.round(newPosY));
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsDragging = false;
                break;
        }
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mHandleWidth, mHandleHeight);
    }

    public int getHandleHeight() {
        return mHandleHeight;
    }

    public int getHandleWidth() {
        return mHandleWidth;
    }

    public boolean isShowing() {
        return mHandle != null && mHandle.isShowing();
    }

    public boolean isParentNull() {
        return this.getParent() == null;
    }

    public boolean isDragging() {
        return mIsDragging;
    }
}
