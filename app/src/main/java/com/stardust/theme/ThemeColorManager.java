package com.stardust.theme;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Paint;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

/**
 * Created by Stardust on 2016/5/10.
 */

public class ThemeColorManager {

    private static ThemeColor defaultThemeColor;
    private static ThemeColor themeColor;
    private static WeakReference<Context> contextWeakReference;
    private static boolean shouldApplyDefaultThemeColor;

    public static void init(Context context) {
        init(context, null, false);
    }

    public static void init(Context context, ThemeColor defaultThemeColor, boolean apply) {
        contextWeakReference = new WeakReference<>(context);
        ThemeColorManager.defaultThemeColor = defaultThemeColor;
        themeColor = ThemeColor.fromPreferences(PreferenceManager.getDefaultSharedPreferences(context), defaultThemeColor);
        shouldApplyDefaultThemeColor = apply;
    }


    public static void add(ThemeColorMutable colorMutable) {
        ThemeColorMutableManager.add(colorMutable);
    }

    public static void addViewBackground(View titleBarView) {
        BackgroundColorManager.add(titleBarView);
    }

    public static void addActivityStatusBar(Activity activity) {
        StatusBarManager.add(activity);
    }

    public static void addPaint(Paint paint) {
        PaintManager.add(paint);
    }

    public static void addActivityNavigationBar(final Activity activity) {
        if (Build.VERSION.SDK_INT > 19) {
            ThemeColorWidgetReferenceManager.add(new ThemeColorMutableReference() {
                WeakReference<Activity> weakReference = new WeakReference<>(activity);

                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void setThemeColor(ThemeColor color) {
                    activity.getWindow().setNavigationBarColor(color.colorPrimary);
                }

                @Override
                public boolean isNull() {
                    return weakReference.get() == null;
                }
            });
        }
    }

    public static int getColorPrimary() {
        if (themeColor == null) {
            if (defaultThemeColor == null)
                return 0;
            else
                return defaultThemeColor.colorPrimary;
        }
        return themeColor.colorPrimary;
    }

    public static ThemeColor getThemeColor() {
        return themeColor;
    }

    public static ThemeColor getDefaultThemeColor() {
        return defaultThemeColor;
    }

    public static void setDefaultThemeColor(ThemeColor defaultThemeColor) {
        ThemeColorManager.defaultThemeColor = defaultThemeColor;
    }

    public static void setThemeColor(ThemeColor themeColor) {
        ThemeColorManager.themeColor = themeColor;
        saveThemeColorIfNeeded();
        int color = themeColor.colorPrimary;
        BackgroundColorManager.setColor(color);
        StatusBarManager.setColor(color);
        PaintManager.setColor(color);
        ThemeColorMutableManager.setColor(themeColor);
        ThemeColorWidgetReferenceManager.setColor(themeColor);
    }

    /**
     * 设置主题色并为记录的状态栏和标题栏改变颜色
     *
     * @param color 主题色RGB值
     */
    public static void setThemeColor(int color) {
        themeColor = new ThemeColor(color);
        saveThemeColorIfNeeded();
        BackgroundColorManager.setColor(color);
        StatusBarManager.setColor(color);
        PaintManager.setColor(color);
        ThemeColorMutableManager.setColor(themeColor);
        ThemeColorWidgetReferenceManager.setColor(themeColor);
    }

    private static void saveThemeColorIfNeeded() {
        if (contextWeakReference != null && contextWeakReference.get() != null) {
            themeColor.saveIn(PreferenceManager.getDefaultSharedPreferences(contextWeakReference.get()));
        }
    }


    private static boolean shouldApplyThemeColor() {
        return themeColor != null && (defaultThemeColor == null || !defaultThemeColor.equals(themeColor) || shouldApplyDefaultThemeColor);
    }

    private static class BackgroundColorManager {
        private static List<WeakReference<View>> views = new LinkedList<>();

        public static void add(View view) {
            views.add(new WeakReference<>(view));
            if (shouldApplyThemeColor())
                view.setBackgroundColor(themeColor.colorPrimary);
        }


        public static void setColor(int color) {
            Iterator<WeakReference<View>> iterator = views.iterator();
            while (iterator.hasNext()) {
                View view = iterator.next().get();
                if (view != null) {
                    view.setBackgroundColor(color);
                } else {
                    iterator.remove();
                }
            }
        }
    }

    private static class StatusBarManager {
        private static Vector<WeakReference<Activity>> activities = new Vector<>();

        @TargetApi(21)
        public static void add(Activity activity) {
            activities.add(new WeakReference<>(activity));
            if (shouldApplyThemeColor())
                activity.getWindow().setStatusBarColor(themeColor.colorPrimary);
        }

        @TargetApi(21)
        public static void setColor(int color) {
            Iterator<WeakReference<Activity>> iterator = activities.iterator();
            while (iterator.hasNext()) {
                Activity activity = iterator.next().get();
                if (activity != null) {
                    activity.getWindow().setStatusBarColor(color);
                } else {
                    iterator.remove();
                }
            }
        }
    }

    private static class PaintManager {
        private static List<WeakReference<Paint>> paints = new LinkedList<>();

        public static void add(Paint paint) {
            if (shouldApplyThemeColor())
                paint.setColor(themeColor.colorPrimary);
            paints.add(new WeakReference<>(paint));
        }

        public static void setColor(int color) {
            Iterator<WeakReference<Paint>> iterator = paints.iterator();
            while (iterator.hasNext()) {
                Paint paint = iterator.next().get();
                if (paint != null) {
                    paint.setColor(color);
                } else {
                    iterator.remove();
                }
            }
        }
    }

    private static class ThemeColorWidgetReferenceManager {

        private static List<ThemeColorMutableReference> widgets = new LinkedList<>();

        public static void add(ThemeColorMutableReference widget) {
            if (shouldApplyThemeColor())
                widget.setThemeColor(themeColor);
            widgets.add(widget);
        }

        public static void setColor(ThemeColor color) {
            Iterator<ThemeColorMutableReference> iterator = widgets.iterator();
            while (iterator.hasNext()) {
                ThemeColorMutableReference widget = iterator.next();
                if (!widget.isNull()) {
                    widget.setThemeColor(color);
                } else {
                    iterator.remove();
                }
            }
        }

    }

    private static class ThemeColorMutableManager {

        private static List<WeakReference<ThemeColorMutable>> widgets = new LinkedList<>();

        public static void add(ThemeColorMutable widget) {
            if (shouldApplyThemeColor())
                widget.setThemeColor(themeColor);
            widgets.add(new WeakReference<>(widget));
        }

        public static void setColor(ThemeColor color) {
            Iterator<WeakReference<ThemeColorMutable>> iterator = widgets.iterator();
            while (iterator.hasNext()) {
                ThemeColorMutable widget = iterator.next().get();
                if (widget != null) {
                    widget.setThemeColor(color);
                } else {
                    iterator.remove();
                }
            }
        }

    }


}
