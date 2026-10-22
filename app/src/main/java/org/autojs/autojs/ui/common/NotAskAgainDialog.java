package org.autojs.autojs.ui.common;

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.afollestad.materialdialogs.MaterialDialog;
import org.autojs.autojs.core.pref.StateKey;
import org.autojs.autojs.core.pref.keys.DialogStateKeys;
import org.autojs.autojs.util.MD5Utils;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on Jan 30, 2017.
 * Modified by SuperMonster003 as of Feb 3, 2026.
 */
public class NotAskAgainDialog extends MaterialDialog {

    protected NotAskAgainDialog(Builder builder) {
        super(builder);
    }

    public static class Builder extends MaterialDialog.Builder {

        private final StateKey<Boolean> mRemindKey;
        private boolean mRemind;

        /**
         * A dialog without a declared key: the key name is made from the stack trace of the caller, so it changes with
         * the code (docs/settings/ROADMAP.md P4.1). No caller uses it since appendix C Q23; it stays as the fallback.
         * zh-CN: 未声明键的对话框: 键名由调用方的调用栈生成, 随代码变化 (ROADMAP P4.1). 自附录 C Q23 起已无调用方, 作为兜底保留.
         */
        public Builder(@NonNull Context context) {
            this(context, (StateKey<Boolean>) null);
        }

        /**
         * @deprecated Declare the key in {@link DialogStateKeys} and use {@link #Builder(Context, StateKey)}
         * (docs/settings/ROADMAP.md P4.1); the name is read and written as before.
         */
        @Deprecated
        public Builder(Context context, @Nullable String key) {
            this(context, key == null ? null : DialogStateKeys.forName(key));
        }

        /**
         * The remembered state is {@code key}: true while the dialog is shown, false after "do not show again"
         * ({@link DialogStateKeys}, docs/settings/ROADMAP.md P4.1); a null key is made from the stack trace.
         * zh-CN: 记忆型状态为 {@code key}: 对话框仍会显示时为 true, 勾选 "不再提示" 后为 false; key 为 null 时由调用栈生成.
         */
        public Builder(Context context, @Nullable StateKey<Boolean> key) {
            super(context);
            mRemindKey = key != null ? key : DialogStateKeys.forName(MD5Utils.toHash(TextUtils.join("", Thread.currentThread().getStackTrace())));
            mRemind = mRemindKey.get();
            checkBoxPrompt(context.getString(R.string.text_do_not_show_again), false, (buttonView, isChecked) -> setRemindState(!isChecked));
        }

        @Nullable
        @Override
        public MaterialDialog build() {
            return mRemind ? super.build() : null;
        }

        @Nullable
        @Override
        public MaterialDialog show() {
            return mRemind ? super.show() : null;
        }

        private void setRemindState(boolean remind) {
            mRemind = remind;
            mRemindKey.set(remind);
        }

    }

}
