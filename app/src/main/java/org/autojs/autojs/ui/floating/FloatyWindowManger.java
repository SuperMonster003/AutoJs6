package org.autojs.autojs.ui.floating;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.ui.enhancedfloaty.FloatyService;
import org.autojs.autojs.ui.enhancedfloaty.FloatyWindow;

import org.autojs.autojs.permission.DisplayOverOtherAppsPermission;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.lang.ref.WeakReference;

/**
 * Created by Stardust on 2017/9/30.
 * Modified by SuperMonster003 as of Jun 21, 2022.
 */
public class FloatyWindowManger {

    private static WeakReference<CircularMenu> sCircularMenu;

    private static DisplayOverOtherAppsPermission sDisplayOverOtherAppsPerm;
    private static boolean sCircularMenuShown;

    public static boolean addWindow(Context context, FloatyWindow window) {
        context.startService(new Intent(context, FloatyService.class));
        getDisplayOverOtherAppsPerm(context).requestIfNeeded();
        try {
            FloatyService.addWindow(window);
            return true;
            // SecurityException: https://github.com/hyb1996-guest/AutoJsIssueReport/issues/4781
        } catch (Exception e) {
            e.printStackTrace();
            // if (!getDisplayOverOtherAppsPerm(context).has()) {
            //     ViewUtils.showToast(context, R.string.error_no_draw_overlays_permission, true);
            // }
            return false;
        }
    }

    public static boolean isCircularMenuShowing() {
        return sCircularMenuShown && isCircularMenuSetUp();
    }

    public static boolean isCircularMenuSetUp() {
        return sCircularMenu != null && sCircularMenu.get() != null;
    }

    public static void showCircularMenuIfNeeded(Context context) {
        if (!isCircularMenuShowing()) {
            showCircularMenu(context);
        }
    }

    @Nullable
    public static CircularMenu getCircularMenu() {
        if (sCircularMenu != null) {
            return sCircularMenu.get();
        }
        return null;
    }

    private static DisplayOverOtherAppsPermission getDisplayOverOtherAppsPerm(Context context) {
        if (sDisplayOverOtherAppsPerm == null) {
            sDisplayOverOtherAppsPerm = new DisplayOverOtherAppsPermission(context);
        }
        return sDisplayOverOtherAppsPerm;
    }

    public static void showCircularMenu(@NonNull Context context) {
        if (getDisplayOverOtherAppsPerm(context).has()) {
            context.startService(new Intent(context, FloatyService.class));
            setCircularMenuContext(context);
        } else {
            ViewUtils.showToast(context, R.string.error_no_display_over_other_apps_permission);
            getDisplayOverOtherAppsPerm(context).config();
        }
        sCircularMenuShown = true;
    }

    public static void setCircularMenuContext(@NonNull Context context) {
        sCircularMenu = new WeakReference<>(new CircularMenu(context));
    }

    public static void hideCircularMenu() {
        hideCircularMenu(false);
    }

    public static void hideCircularMenuIfNeeded() {
        if (isCircularMenuShowing()) {
            hideCircularMenu();
        }
    }

    public static void hideCircularMenu(boolean isSaveState) {
        if (sCircularMenu != null) {
            CircularMenu menu = sCircularMenu.get();
            if (menu != null) {
                if (isSaveState) {
                    menu.closeAndSaveState();
                } else {
                    menu.close();
                }
            }
        }
        clearCircularMenu();
        sCircularMenuShown = false;
    }

    public static void clearCircularMenu() {
        sCircularMenu = null;
    }

    public static void hideCircularMenuAndSaveState() {
        hideCircularMenu(true);
    }

    public static int getWindowType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
    }

    public static void refreshCircularMenuIfNeeded(Context context) {
        if (isCircularMenuShowing()) {
            hideCircularMenu();
            showCircularMenu(context);
        }
    }

}
