package org.autojs.autojs.app;

import android.content.Context;

import androidx.annotation.NonNull;

import com.afollestad.materialdialogs.MaterialDialog;

import org.autojs.autojs6.R;

import static org.autojs.autojs.util.ContextUtils.withHostConfiguration;

public class AppLevelThemeDialogBuilder extends MaterialDialog.Builder {

    public AppLevelThemeDialogBuilder(@NonNull Context context) {
        // Application context so that the dialog is not tied to an Activity, configured to follow
        // the host app's language and night mode rather than the system's.
        // zh-CN: 使用 application context 使对话框不依附于 Activity, 并使其跟随宿主应用的语言与夜间模式而非系统设置.
        super(withHostConfiguration(context.getApplicationContext()));
        titleColor(context.getColor(R.color.day_night));
        contentColor(context.getColor(R.color.day_night));
        backgroundColor(context.getColor(R.color.window_background));
        positiveColorRes(R.color.dialog_button_default);
        negativeColorRes(R.color.dialog_button_default);
        neutralColorRes(R.color.dialog_button_default);
    }

}
