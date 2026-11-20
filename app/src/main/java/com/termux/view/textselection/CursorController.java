package com.termux.view.textselection;

import android.view.MotionEvent;
import android.view.ViewTreeObserver;

import com.termux.view.TerminalView;

/**
 * A CursorController instance can be used to control cursors in the text.
 * It is not used outside of {@link TerminalView}.
 */
public interface CursorController extends ViewTreeObserver.OnTouchModeChangeListener {
    void show(MotionEvent event);

    boolean hide();

    void render();

    void updatePosition(TextSelectionHandleView handle, int x, int y);

    boolean onTouchEvent(MotionEvent event);

    void onDetached();

    boolean isActive();
}
