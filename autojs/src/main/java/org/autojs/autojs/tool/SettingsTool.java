package org.autojs.autojs.tool;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.stardust.autojs.core.util.ProcessShell;

import java.text.MessageFormat;

public class SettingsTool {

    public static class SecureSettings {

        private static final String WRITE_SECURE_SETTINGS_PERMISSION = "android.permission.WRITE_SECURE_SETTINGS";

        public static boolean isGranted(@Nullable Context context) {
            if (context != null) {
                int checkVal = context.checkCallingOrSelfPermission(WRITE_SECURE_SETTINGS_PERMISSION);
                return checkVal == PackageManager.PERMISSION_GRANTED;
            }
            return false;
        }

        public static boolean grantAccess(Context context) {
            return isGranted(context) && setAccessByAdb(context, true) ||
                    RootTool.isRootAvailable() && setAccessByRoot(context, true);
        }

        public static boolean revokeAccess(Context context) {
            return isGranted(context) && setAccessByAdb(context, false) ||
                    RootTool.isRootAvailable() && setAccessByRoot(context, false);
        }

        private static boolean setAccessByRoot(Context context, boolean isGranted) {
            ProcessShell.execCommand(getScript(context, isGranted), true);
            return isGranted == isGranted(context);
        }

        private static boolean setAccessByAdb(Context context, boolean isGranted) {
            ProcessShell.execCommand(getScript(context, isGranted), false);
            return isGranted == isGranted(context);
        }

        @NonNull
        private static String getScript(Context context, boolean isGranted) {
            String scriptAction = isGranted ? "grant" : "revoke";
            return MessageFormat.format("adb shell pm {0} {1} {2}",
                    scriptAction,
                    context.getPackageName(),
                    WRITE_SECURE_SETTINGS_PERMISSION);
        }

    }
}
