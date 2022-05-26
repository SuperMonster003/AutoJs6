package com.stardust.util;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import android.content.ContextWrapper;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

/**
 * Created by Stardust on 2017/1/24.
 */
public class ViewUtil {

    @SuppressWarnings("unchecked")
    public static <V extends View> V $(View view, @IdRes int resId) {
        return view.findViewById(resId);
    }

    // FIXME: 2018/1/23 not working in some devices (https://github.com/hyb1996/Auto.js/issues/268)
    public static int getStatusBarHeightLegacy(Context context) {
        int result = 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    public static int getStatusBarHeight(Context context) {
        Rect rectangle = new Rect();
        Window window = getWindowForContext(context);
        if (window == null) {
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, context.getResources().getDisplayMetrics());
        }
        window.getDecorView().getWindowVisibleDisplayFrame(rectangle);
        return rectangle.top;
    }

    public static int getTitleBarHeight(Context context) {
        Window window = getWindowForContext(context);
        int contentViewTop = 0;
        if (window != null) {
            contentViewTop = window.findViewById(Window.ID_ANDROID_CONTENT).getTop();
        }
        return contentViewTop - getStatusBarHeight(context);
    }

    @Nullable
    private static Window getWindowForContext(Context context) {
        if (context instanceof Activity) {
            return ((Activity) context).getWindow();
        }
        if (context instanceof ContextWrapper) {
            return getWindowForContext(((ContextWrapper) context).getBaseContext());
        }
        return null;
    }

    public static int getScreenHeight(Activity activity) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.heightPixels;
    }

    public static int getScreenWidth(Activity activity) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.widthPixels;
    }


    public static void setViewMeasure(View view, int width, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = width;
        params.height = height;
        view.setLayoutParams(params);
    }
}
