package org.autojs.autojs.ui.error;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.widget.Toolbar;

import com.afollestad.materialdialogs.MaterialDialog;

import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.BuildConfig;
import org.autojs.autojs6.R;

import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Stardust on 2017/2/2.
 */
public class ErrorReportActivity extends BaseActivity {

    private static final String TAG = "ErrorReportActivity";

    private String mTitle;

    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            mTitle = getString(R.string.text_app_crashed);
            setUpUI();
            handleIntent();
        } catch (Throwable throwable) {
            Log.e(TAG, "", throwable);
            exit();
        }

    }

    private void handleIntent() {
        String message = getIntent().getStringExtra("message");
        final String errorDetail = getIntent().getStringExtra("error");
        showErrorMessageByDialog(message, errorDetail);
    }

    private void showErrorMessageByDialog(String message, final String errorDetail) {
        new MaterialDialog.Builder(this)
                .title(mTitle)
                .content(R.string.crash_feedback)
                .positiveText(R.string.text_exit)
                .negativeText(R.string.text_copy_debug_info)
                .onPositive((dialog, which) -> exit())
                .onNegative((dialog, which) -> {
                    copyToClip(getDeviceMessage() + message + "\n" + errorDetail);
                    exitAfter();
                })
                .cancelable(false)
                .show();
    }

    private String getDeviceMessage() {
        return String.format(Locale.getDefault(), "Version: %s\nAndroid: %d\n", BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT);
    }

    private void exitAfter() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                exit();
            }
        }, 1000);
    }

    private void copyToClip(String text) {
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("Debug", text));
        ViewUtils.showToast(ErrorReportActivity.this, R.string.text_already_copied_to_clip);
    }

    private void setUpUI() {
        setContentView(R.layout.activity_error_report);
        setUpToolbar();
    }

    private void setUpToolbar() {
        Toolbar toolbar;
        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(getString(R.string.text_error_report));
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setHomeButtonEnabled(false);
    }

    @Override
    public void onBackPressed() {
        exit();
    }

    private void exit() {
        finishAffinity();
    }

}


