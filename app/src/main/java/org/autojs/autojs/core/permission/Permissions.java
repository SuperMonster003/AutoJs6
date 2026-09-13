package org.autojs.autojs.core.permission;

import android.content.Context;
import android.content.Intent;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.permission.LocalNetworkPermission;

import java.util.ArrayList;
import java.util.Locale;

import static android.content.pm.PackageManager.PERMISSION_DENIED;

public class Permissions {

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    static final int REQUEST_CODE = 18777;

    public static String[] getPermissionsNeedToRequest(Context context, String[] permissions) {
        ArrayList<String> list = new ArrayList<>();
        for (String permission : permissions) {
            if (!permission.startsWith("android.permission.")) {
                permission = "android.permission." + permission.toUpperCase(Locale.ROOT);
            }
            if (LocalNetworkPermission.PERMISSION.equals(permission) && !LocalNetworkPermission.isRequired(context)) {
                continue;
            }
            if (context.checkSelfPermission(permission) == PERMISSION_DENIED) {
                list.add(permission);
            }
        }
        return list.toArray(EMPTY_STRING_ARRAY);
    }

    public static void requestPermissions(PermissionRequestProxyActivity activity, String[] permissions, OnRequestPermissionsResultCallback callback) {
        if (callback != null) {
            activity.addRequestPermissionsCallback((code, p, grantResults) -> {
                activity.removeRequestPermissionsCallback(callback);
                callback.onRequestPermissionsResult(code, p, grantResults);
            });
        }
        activity.requestPermissions(permissions, REQUEST_CODE);
    }

    public static void requestPermissions(Context context, String[] permissions) {
        Intent intent = new Intent(context, PermissionRequestActivity.class)
                .putExtra(PermissionRequestActivity.EXTRA_PERMISSIONS, permissions);
        IntentUtils.startSafely(intent, context);
    }
}
