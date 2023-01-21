package org.autojs.autojs.util;

import static org.autojs.autojs.permission.Base.WRITE_SECURE_SETTINGS_PERMISSION;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.runtime.api.ProcessShell;

import java.text.MessageFormat;

public class SettingsUtils {

    public static class SecureSettings {

        public static boolean isGranted(@Nullable Context context) {
            if (context != null) {
                int checkVal = context.checkCallingOrSelfPermission(WRITE_SECURE_SETTINGS_PERMISSION);
                return checkVal == PackageManager.PERMISSION_GRANTED;
            }
            return false;
        }

        public static boolean grantAccess(Context context) {
            return isGranted(context) && setAccessWithAdb(context, true) ||
                    RootUtils.isRootAvailable() && setAccessWithRoot(context, true);
        }

        public static boolean revokeAccess(Context context) {
            return isGranted(context) && setAccessWithAdb(context, false) ||
                    RootUtils.isRootAvailable() && setAccessWithRoot(context, false);
        }

        private static boolean setAccessWithRoot(Context context, boolean isGranted) {
            ProcessShell.execCommand(getScript(context, isGranted), true);
            return isGranted == isGranted(context);
        }

        private static boolean setAccessWithAdb(Context context, boolean isGranted) {
            ProcessShell.execCommand(getScript(context, isGranted), false);
            return isGranted == isGranted(context);
        }

        @NonNull
        private static String getScript(Context context, boolean isGranted) {
            String scriptAction = isGranted ? "request" : "revoke";
            return MessageFormat.format("adb shell pm {0} {1} {2}",
                    scriptAction,
                    context.getPackageName(),
                    WRITE_SECURE_SETTINGS_PERMISSION);
        }

    }
}
