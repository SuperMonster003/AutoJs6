package org.autojs.autojs.ui.enhancedfloaty.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import org.autojs.autojs.annotation.ReservedForCompatibility;
import org.autojs.autojs.util.IntentUtils;

/**
 * Created by Stardust on Mar 10, 2017.
 * Modified by SuperMonster003 as of Jan 15, 2026.
 */
public class FloatingWindowPermissionUtil {

    public static void goToFloatingWindowPermissionSettingIfNeeded(Context context) {
        if (!hasFloatingWindowPermission(context)) {
            goToFloatingWindowPermissionSetting(context);
        }
    }

    public static void goToFloatingWindowPermissionSetting(Context context) {
        String packageName = context.getPackageName();
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + packageName));
            IntentUtils.start(intent, context);
        } catch (Exception e) {
            IntentUtils.launchAppDetailsSettings(context, packageName);
        }
    }

    public static boolean hasFloatingWindowPermission(Context context) {
        return Settings.canDrawOverlays(context);
    }

    @Deprecated
    @ReservedForCompatibility
    public static boolean goToAppDetailSettings(Context context, String packageName) {
        return IntentUtils.launchAppDetailsSettings(context, packageName);
    }

}
