package org.autojs.autojs.ui.edit.keyboard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import org.autojs.autojs.event.BackPressedHandler;

import java.lang.ref.WeakReference;

/**
 * Created by Stardust on Dec 9, 2017.
 * <a href="https://github.com/dss886/Android-FunctionsInputDetector">Android-FunctionsInputDetector</a>
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 11, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 8, 2026.
 */
public class FunctionsKeyboardHelper implements BackPressedHandler {

    private static final String SHARE_PREFERENCE_NAME = "FunctionsKeyboardHelper";
    private static final String SHARE_PREFERENCE_SOFT_INPUT_HEIGHT = "soft_input_height";

    // Minimum IME height threshold (dp) to filter out navigation bar / gesture insets.
    // zh-CN: IME 高度的最小阈值 (dp), 用于过滤导航栏/手势区域等非 IME 的小高度.
    private static final int MIN_IME_HEIGHT_DP = 80;

    private final WeakReference<Activity> mActivityRef;
    private final InputMethodManager mInputManager;
    private final SharedPreferences mPreferences;

    private View mFunctionsLayout;
    private View mEditView;
    private View mContentView;

    private FunctionsKeyboardHelper(Activity activity) {
        mActivityRef = new WeakReference<>(activity);
        mInputManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        mPreferences = activity.getSharedPreferences(SHARE_PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    public static FunctionsKeyboardHelper with(Activity activity) {
        return new FunctionsKeyboardHelper(activity);
    }

    public FunctionsKeyboardHelper setContent(View contentView) {
        mContentView = contentView;
        return this;
    }

    public void onSoftKeyboardShown() {
        if (mFunctionsLayout.isShown()) {
            lockContentHeight();
            hideFunctionsLayout(false);
            mEditView.postDelayed(FunctionsKeyboardHelper.this::unlockContentHeightDelayed, 200L);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public FunctionsKeyboardHelper setEditView(View editView) {
        mEditView = editView;
        mEditView.requestFocus();
        editView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN)
                onSoftKeyboardShown();
            return false;
        });
        return this;
    }

    public FunctionsKeyboardHelper setFunctionsTrigger(View triggerButton) {
        triggerButton.setOnClickListener(v -> {
            if (mFunctionsLayout.isShown()) {
                lockContentHeight();
                hideFunctionsLayout(true);
                unlockContentHeightDelayed();
            } else {
                if (isSoftInputShown()) {
                    lockContentHeight();
                    showFunctionsLayout();
                    unlockContentHeightDelayed();
                } else {
                    showFunctionsLayout();
                }
            }
        });
        return this;
    }

    public FunctionsKeyboardHelper setFunctionsView(View FunctionsView) {
        mFunctionsLayout = FunctionsView;
        return this;
    }

    public FunctionsKeyboardHelper build() {
        mActivityRef.get().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN |
                                                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        hideSoftInput();
        return this;
    }

    private void showFunctionsLayout() {
        int softInputHeight = getSupportSoftInputHeight();
        if (softInputHeight <= 0) {
            softInputHeight = getKeyBoardHeight();
        }
        hideSoftInput();
        mFunctionsLayout.getLayoutParams().height = softInputHeight;

        // Ensure layout params update is applied immediately.
        // zh-CN: 确保布局参数的更新可以立即生效.
        mFunctionsLayout.requestLayout();

        mFunctionsLayout.setVisibility(View.VISIBLE);
    }

    public void hideFunctionsLayout(boolean showSoftInput) {
        if (mFunctionsLayout.isShown()) {
            mFunctionsLayout.setVisibility(View.GONE);

            // Reset height so the next show() won't inherit an old value unexpectedly.
            // zh-CN: 重置高度, 避免下次 show() 意外继承旧高度.
            mFunctionsLayout.getLayoutParams().height = 0;
            mFunctionsLayout.requestLayout();

            if (showSoftInput) {
                showSoftInput();
            }
        }
    }

    private void lockContentHeight() {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mContentView.getLayoutParams();
        params.height = mContentView.getHeight();
        params.weight = 0.0F;

        // Apply params so the lock really takes effect.
        // zh-CN: 应用参数, 让锁定高度真正生效.
        mContentView.setLayoutParams(params);
        mContentView.requestLayout();
    }

    private void unlockContentHeightDelayed() {
        mEditView.postDelayed(() -> {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mContentView.getLayoutParams();

            // Restore "0dp + weight=1" so resize works correctly again.
            // zh-CN: 恢复为 "0dp + weight=1", 让 resize 行为回到正常状态.
            params.height = 0;
            params.weight = 1.0F;

            mContentView.setLayoutParams(params);
            mContentView.requestLayout();
        }, 200L);
    }

    private void showSoftInput() {
        mEditView.requestFocus();
        mEditView.post(() -> mInputManager.showSoftInput(mEditView, InputMethodManager.SHOW_FORCED));
    }

    private void hideSoftInput() {
        mInputManager.hideSoftInputFromWindow(mEditView.getWindowToken(), 0);
    }

    private boolean isSoftInputShown() {
        // Soft input is considered shown only when the computed height is above a minimum threshold.
        // zh-CN: 仅当计算出的高度超过最小阈值时, 才认为软键盘处于显示状态.
        return getSupportSoftInputHeight() > 0;
    }

    private int getSupportSoftInputHeight() {
        Rect r = new Rect();
        View decorView = mActivityRef.get().getWindow().getDecorView();
        decorView.getWindowVisibleDisplayFrame(r);
        int screenHeight = decorView.getRootView().getHeight();
        int softInputHeight = screenHeight - r.bottom;
        // When SDK Level >= 20 (Android L), the softInputHeight will contain the height of softButtonsBar (if has)
        softInputHeight = softInputHeight - getSoftKeyButtonsHeight();

        // Filter out small non-IME insets (e.g., gesture navigation bar height on Android 15+).
        // zh-CN: 过滤较小的非 IME inset (例如 Android 15+ 上的手势导航栏高度).
        final int minImeHeightPx = dpToPx(MIN_IME_HEIGHT_DP);
        if (softInputHeight > 0 && softInputHeight < minImeHeightPx) {
            return 0;
        }

        if (softInputHeight >= minImeHeightPx) {
            mPreferences.edit().putInt(SHARE_PREFERENCE_SOFT_INPUT_HEIGHT, softInputHeight).apply();
        }
        return softInputHeight;
    }

    private int dpToPx(int dp) {
        final Activity activity = mActivityRef.get();
        if (activity == null) {
            return dp;
        }
        final float density = activity.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private int getSoftKeyButtonsHeight() {
        DisplayMetrics metrics = new DisplayMetrics();
        mActivityRef.get().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int usableHeight = metrics.heightPixels;
        mActivityRef.get().getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int realHeight = metrics.heightPixels;
        if (realHeight > usableHeight) {
            return realHeight - usableHeight;
        } else {
            return 0;
        }
    }

    public int getKeyBoardHeight() {
        return mPreferences.getInt(SHARE_PREFERENCE_SOFT_INPUT_HEIGHT, 400);
    }

    @Override
    public boolean onBackPressed(Activity activity) {
        if (mFunctionsLayout.isShown()) {
            hideFunctionsLayout(false);
            return true;
        }
        return false;
    }
}
