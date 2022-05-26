package com.stardust.autojs.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.R;
import com.stardust.enhancedfloaty.util.FloatingWindowPermissionUtil;

import org.autojs.autojs.tool.ThreadTool;

import ezy.assist.compat.SettingsCompat;

/**
 * Created by Stardust on 2018/1/30.
 * Modified by SuperMonster003 as of Apr 10, 2022.
 */
public class FloatingPermission {

    public static boolean ensurePermissionGranted(Context context) {
        if (!canDrawOverlays(context)) {
            GlobalAppContext.toast(R.string.text_no_draw_overlays_permission);
            manageDrawOverlays(context);
            return false;
        }
        return true;
    }

    public static void waitForPermissionGranted(Context context) throws InterruptedException {
        if (canDrawOverlays(context)) {
            return;
        }
        Runnable r = () -> {
            manageDrawOverlays(context);
            GlobalAppContext.toast(R.string.text_no_draw_overlays_permission);
        };
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(r);
        } else {
            r.run();
        }
        if (!ThreadTool.wait(() -> canDrawOverlays(context), 60 * 1000)) {
            GlobalAppContext.toast(R.string.text_draw_overlays_permission_failed, Toast.LENGTH_LONG);
        }
    }


    public static void manageDrawOverlays(Context context) {
        try {
            SettingsCompat.manageDrawOverlays(context);
        } catch (Exception ex) {
            FloatingWindowPermissionUtil.goToAppDetailSettings(context, context.getPackageName());
        }
    }

    public static boolean canDrawOverlays(Context context) {
        return SettingsCompat.canDrawOverlays(context);
    }

}
