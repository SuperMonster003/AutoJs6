package org.autojs.autojs.ui.log;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import org.autojs.autojs.AutoJs;
import org.autojs.autojs.core.console.ConsoleView;
import org.autojs.autojs.core.console.GlobalConsole;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ActivityLogBinding;
import org.autojs.autojs6.databinding.ConsoleViewBinding;
import org.jetbrains.annotations.NotNull;

public class LogActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityLogBinding activityLogBinding = ActivityLogBinding.inflate(getLayoutInflater());
        setContentView(activityLogBinding.getRoot());

        setToolbarAsBack(R.string.text_log);

        ConsoleView mConsoleView = activityLogBinding.console;

        GlobalConsole mConsoleImpl = AutoJs.getInstance().getGlobalConsole();
        mConsoleView.setConsole(mConsoleImpl);
        mConsoleView.setPinchToZoomEnabled(true);

        ConsoleViewBinding consoleViewBinding = ConsoleViewBinding.inflate(getLayoutInflater());
        consoleViewBinding.inputContainer.setVisibility(View.GONE);

        activityLogBinding.fab.setOnClickListener(v -> mConsoleImpl.clear());
    }

    public static void launch(@NotNull Context context) {
        context.startActivity(new Intent(context, LogActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
