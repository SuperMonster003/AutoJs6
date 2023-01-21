package org.autojs.autojs.app;

import android.content.Context;

import androidx.annotation.NonNull;

import com.afollestad.materialdialogs.MaterialDialog;

import org.autojs.autojs6.R;

public class AppLevelThemeDialogBuilder extends MaterialDialog.Builder {

    public AppLevelThemeDialogBuilder(@NonNull Context context) {
        super(context.getApplicationContext());
        titleColor(context.getColor(R.color.day_night));
        contentColor(context.getColor(R.color.day_night));
        backgroundColor(context.getColor(R.color.window_background));
    }

}
