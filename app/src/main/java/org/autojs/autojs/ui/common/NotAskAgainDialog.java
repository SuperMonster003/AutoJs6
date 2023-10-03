package org.autojs.autojs.ui.common;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.afollestad.materialdialogs.MaterialDialog;

import org.autojs.autojs.util.MD5Utils;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on 2017/1/30.
 */
public class NotAskAgainDialog extends MaterialDialog {

    protected NotAskAgainDialog(Builder builder) {
        super(builder);
    }

    public static class Builder extends MaterialDialog.Builder {

        private String mKeyRemind;
        private boolean mRemind;

        public Builder(@NonNull Context context) {
            this(context, null);
        }

        public Builder(Context context, String key) {
            super(context);
            mKeyRemind = key;
            readRemindStatus();
            checkBoxPrompt(context.getString(R.string.text_do_not_show_again), false, (buttonView, isChecked) -> setRemindState(!isChecked));
        }

        @Nullable
        public MaterialDialog show() {
            return mRemind ? super.show() : null;
        }

        private void setRemindState(boolean remind) {
            mRemind = remind;
            PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                    .putBoolean(mKeyRemind, remind).apply();
        }

        private void readRemindStatus() {
            generatePreferenceKeyIfNeeded();
            mRemind = PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(mKeyRemind, true);
        }

        private void generatePreferenceKeyIfNeeded() {
            if (mKeyRemind == null) {
                mKeyRemind = MD5Utils.toHash(TextUtils.join("", Thread.currentThread().getStackTrace()));
            }
        }

    }

}
