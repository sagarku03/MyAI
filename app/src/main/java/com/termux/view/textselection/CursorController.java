package com.termux.view.textselection;

import android.view.MotionEvent;
import android.view.ViewTreeObserver;

/**
 * Base interface for terminal cursor and text selection controllers.
 */
public interface CursorController extends ViewTreeObserver.OnTouchModeChangeListener {
    void show(MotionEvent event);
    boolean hide();
    void render();
    boolean isActive();
    void updatePosition(TextSelectionHandleView handle, int x, int y);
    boolean onTouchEvent(MotionEvent event);
    void onDetached();
}
