package org.autojs.autojs.model.script;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;

import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.io.File;

/**
 * Created by Stardust on Apr 1, 2017.
 */
public class PathChecker {
    public static final int CHECK_RESULT_OK = 0;

    private final Context mContext;

    public PathChecker(Context context) {
        mContext = context;
    }

    public static int check(final String path) {
        if (TextUtils.isEmpty(path))
            return R.string.text_path_is_empty;
        if (!new File(path).exists())
            return R.string.text_file_not_exists;
        return CHECK_RESULT_OK;
    }

    public boolean checkAndToastError(String path) {
        int result = checkWithStoragePermission(path);
        if (result != CHECK_RESULT_OK) {
            String message = mContext.getString(result) + ": " + path;
            ViewUtils.showToast(mContext, message, true);
            return false;
        }
        return true;
    }

    private int checkWithStoragePermission(String path) {
        if (mContext instanceof Activity && !hasStorageReadPermission((Activity) mContext)) {
            return R.string.error_no_storage_rw_permission;
        }
        return check(path);
    }

    private static boolean hasStorageReadPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return activity.checkSelfPermission(READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED;
    }

}
