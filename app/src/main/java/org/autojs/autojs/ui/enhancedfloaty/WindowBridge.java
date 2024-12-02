package org.autojs.autojs.ui.enhancedfloaty;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

/**
 * Created by Stardust on Apr 18, 2017.
 */
public interface WindowBridge {

    String TAG = WindowBridge.class.getSimpleName();

    int getX();

    int getY();

    void updatePosition(int x, int y);

    int getWidth();

    int getHeight();

    void updateMeasure(int width, int height);

    int getScreenWidth();

    int getScreenHeight();

    class DefaultImpl implements WindowBridge {

        DisplayMetrics mDisplayMetrics;
        private final WindowManager.LayoutParams mWindowLayoutParams;
        private final WindowManager mWindowManager;
        private final View mWindowView;

        public DefaultImpl(WindowManager.LayoutParams windowLayoutParams, WindowManager windowManager, View windowView) {
            mWindowLayoutParams = windowLayoutParams;
            mWindowManager = windowManager;
            mWindowView = windowView;
        }

        @Override
        public int getX() {
            return mWindowLayoutParams.x;
        }

        @Override
        public int getY() {
            return mWindowLayoutParams.y;
        }

        @Override
        public void updatePosition(int x, int y) {
            Log.d(TAG, "updatePosition(x: " + x + ", y: " + y + ")");
            mWindowLayoutParams.x = x;
            mWindowLayoutParams.y = y;
            try {
                mWindowManager.updateViewLayout(mWindowView, mWindowLayoutParams);
            } catch (Exception e) {
                // @Hint by SuperMonster003 on Nov 9, 2023.
                //  ! If the user repeatedly switches the switch "Floating button" rapidly, an exception may happen.
                //  # java.lang.IllegalArgumentException: View=org.autojs.autojs.ui.floating.CircularActionView
                //  # not attached to window manager.
                //  ! zh-CN: 用户快速反复切换 "浮动按钮" 开关时, 可能导致异常.
                //  # java.lang.IllegalArgumentException: View=org.autojs.autojs.ui.floating.CircularActionView
                //  # 尚未附加到窗口管理器上.
                e.printStackTrace();
            }
        }

        @Override
        public int getWidth() {
            return mWindowView.getWidth();
        }

        @Override
        public int getHeight() {
            return mWindowView.getHeight();
        }

        @Override
        public void updateMeasure(int width, int height) {
            mWindowLayoutParams.width = width;
            mWindowLayoutParams.height = height;
            mWindowManager.updateViewLayout(mWindowView, mWindowLayoutParams);
        }

        @Override
        public int getScreenWidth() {
            ensureDisplayMetrics();
            return mDisplayMetrics.widthPixels;
        }

        @Override
        public int getScreenHeight() {
            ensureDisplayMetrics();
            return mDisplayMetrics.heightPixels;
        }

        private void ensureDisplayMetrics() {
            if (mDisplayMetrics == null) {
                mDisplayMetrics = new DisplayMetrics();
                mWindowManager.getDefaultDisplay().getMetrics(mDisplayMetrics);
            }
        }
    }
}