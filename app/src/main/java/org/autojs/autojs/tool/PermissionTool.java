package org.autojs.autojs.tool;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.snackbar.Snackbar;
import com.stardust.util.ClipboardUtil;

import org.autojs.autojs6.R;

/**
 * Created by SuperMonster003 on Apr 30, 2022.
 */
public class PermissionTool {

    public interface ByRoot {
        void byRoot();
    }

    public interface ByAdb {
        void byAdb();
    }

    public static class AdbMaterialDialog {

        public interface Checker {
            boolean check();
        }

        public interface DismissListener {
            DialogInterface.OnDismissListener getListener();
        }

        private final Context mContext;
        private final String mCommand;
        private int mSnackBarDuration = 1000;
        private Checker mChecker;
        private DismissListener mDismissListener;

        public AdbMaterialDialog(Context context, String command) {
            mContext = context;
            mCommand = "adb shell " + command;
        }

        public AdbMaterialDialog setSnackBarDuration(int duration) {
            this.mSnackBarDuration = duration;
            return this;
        }

        public AdbMaterialDialog setChecker(Checker checker) {
            this.mChecker = checker;
            return this;
        }

        public AdbMaterialDialog setDismissListener(DismissListener listener) {
            this.mDismissListener = listener;
            return this;
        }

        public void show() {
            new MaterialDialog.Builder(mContext)
                    .title(R.string.text_adb_tool_needed)
                    .content(mCommand)
                    .neutralText(R.string.text_permission_test)
                    .onNeutral((dialog, which) -> {
                        View view = dialog.getView();
                        int resultRes = mChecker.check() ? R.string.text_granted : R.string.text_not_granted;
                        if (view != null) {
                            Snackbar.make(view, resultRes, mSnackBarDuration).show();
                        } else {
                            Toast.makeText(mContext, resultRes, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .negativeText(R.string.text_back)
                    .onNegative((dialog, which) -> dialog.dismiss())
                    .positiveText(R.string.text_copy_command)
                    .onPositive((dialog, which) -> {
                        ClipboardUtil.setClip(mContext, mCommand);
                        View view = dialog.getView();
                        int textRes = R.string.text_command_already_copied_to_clip;
                        if (view != null) {
                            Snackbar.make(view, textRes, mSnackBarDuration).show();
                        } else {
                            Toast.makeText(mContext, textRes, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .cancelable(false)
                    .autoDismiss(false)
                    .dismissListener(mDismissListener.getListener())
                    .show();
        }

    }

}
