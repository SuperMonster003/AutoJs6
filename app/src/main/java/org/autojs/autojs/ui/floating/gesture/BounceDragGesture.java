package org.autojs.autojs.ui.floating.gesture;

import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.BounceInterpolator;

import androidx.annotation.NonNull;

import org.autojs.autojs.ui.enhancedfloaty.WindowBridge;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on 2017/9/26.
 */
public class BounceDragGesture extends DragGesture {

    private long mBounceDuration = 300;
    private static final int MIN_DY_TO_SCREEN_BOTTOM = 100;
    private static final int MIN_DY_TO_SCREEN_TOP = 0;
    private final BounceInterpolator mBounceInterpolator;

    public BounceDragGesture(WindowBridge windowBridge, View view) {
        super(windowBridge, view);
        setAutoKeepToEdge(true);
        setAlphaUnpressed();
        mBounceInterpolator = new BounceInterpolator();
    }

    public void setBounceDuration(long bounceDuration) {
        mBounceDuration = bounceDuration;
    }

    @Override
    public boolean onDown(@NonNull MotionEvent event) {
        return super.onDown(event);
    }

    @Override
    public void keepToEdge() {
        int side = mView.getContext().getResources().getDimensionPixelSize(R.dimen.side_circular_menu_icon);
        int screenHeight = mWindowBridge.getScreenHeight();
        int screenWidth = mWindowBridge.getScreenWidth();
        int hiddenWidth = (int) (getKeepToSideHiddenWidthRadio() * (float) side);

        int x = mWindowBridge.getX();
        int y = Math.min(
                screenHeight - side - MIN_DY_TO_SCREEN_BOTTOM,
                Math.max(MIN_DY_TO_SCREEN_TOP, mWindowBridge.getY())
        );

        if (x > screenWidth / 2) {
            bounce(x, screenWidth - side + hiddenWidth, y);
        } else {
            bounce(x, -hiddenWidth, y);
        }
    }

    protected void bounce(final int fromX, final int toX, final int y) {
        ValueAnimator animator = ValueAnimator.ofFloat(fromX, toX);
        animator.addUpdateListener(animation -> mWindowBridge.updatePosition((int) ((float) animation.getAnimatedValue()), y));
        animator.setDuration(mBounceDuration);
        animator.setInterpolator(mBounceInterpolator);
        animator.start();
    }

}
