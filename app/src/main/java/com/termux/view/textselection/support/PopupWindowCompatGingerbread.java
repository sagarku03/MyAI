package com.termux.view.textselection.support;

import android.view.WindowManager;
import android.widget.PopupWindow;
import java.lang.reflect.Method;

/**
 * Compatibility helper for PopupWindow window layout type on Android.
 */
public final class PopupWindowCompatGingerbread {

    private static Method sSetWindowLayoutTypeMethod;
    private static boolean sSetWindowLayoutTypeMethodAttempted;

    private PopupWindowCompatGingerbread() {
    }

    public static void setWindowLayoutType(PopupWindow popupWindow, int layoutType) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            popupWindow.setWindowLayoutType(layoutType);
            return;
        }
        if (!sSetWindowLayoutTypeMethodAttempted) {
            try {
                sSetWindowLayoutTypeMethod = PopupWindow.class.getDeclaredMethod("setWindowLayoutType", int.class);
                sSetWindowLayoutTypeMethod.setAccessible(true);
            } catch (Exception e) {
                // Ignore
            }
            sSetWindowLayoutTypeMethodAttempted = true;
        }
        if (sSetWindowLayoutTypeMethod != null) {
            try {
                sSetWindowLayoutTypeMethod.invoke(popupWindow, layoutType);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public static int getDefaultWindowType() {
        return WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL;
    }
}
