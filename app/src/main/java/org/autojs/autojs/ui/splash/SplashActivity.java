package org.autojs.autojs.ui.splash;

import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.Nullable;

import org.autojs.autojs.app.tool.FloatingButtonTool;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.ui.floating.FloatyWindowManger;
import org.autojs.autojs.ui.main.MainActivity_;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on 2017/7/7.
 */
public class SplashActivity extends BaseActivity {

    private static final long INIT_TIMEOUT = 700;

    private boolean mAlreadyEnterNextActivity = false;
    private boolean mPaused;
    private Handler mHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        init();
        mHandler.postDelayed(SplashActivity.this::enterNextActivity, INIT_TIMEOUT);
        if (FloatyWindowManger.isCircularMenuShowing()) {
            FloatyWindowManger.hideCircularMenu();
        }
        super.onCreate(savedInstanceState);
    }

    private void init() {
        setContentView(R.layout.activity_splash);
        mHandler = new Handler();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPaused = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPaused) {
            mPaused = false;
            enterNextActivity();
        }
    }

    void enterNextActivity() {
        if (mAlreadyEnterNextActivity)
            return;
        if (mPaused) {
            return;
        }
        mAlreadyEnterNextActivity = true;
        MainActivity_.intent(this).start();
        finish();
    }

}
