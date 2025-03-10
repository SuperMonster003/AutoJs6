package org.autojs.autojs.ui.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by Stardust on Aug 20, 2017.
 */
public class ScrollAwareFABBehavior extends FloatingActionButton.Behavior {

    private static final TimeInterpolator INTERPOLATOR = new FastOutSlowInInterpolator();
    private boolean mHidden = false;

    public static final long DURATION = 200;

    public ScrollAwareFABBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ScrollAwareFABBehavior() {
        super();
    }

    @Override
    public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull FloatingActionButton child, @NonNull View directTargetChild, @NonNull View target, int nestedScrollAxes) {
        return true;
    }

    @Override
    public void onNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull FloatingActionButton child, @NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
        if (dyConsumed > 0) {
            onScrollDown(coordinatorLayout, child);
        } else if (dyConsumed < 0) {
            onScrollUp(coordinatorLayout, child);
        }
    }

    private void onScrollUp(CoordinatorLayout parent, FloatingActionButton button) {
        if (!mHidden) {
            return;
        }
        startShowingAnimation(parent, button);
    }

    private void startShowingAnimation(CoordinatorLayout parent, FloatingActionButton button) {
        button.animate()
                .translationY(0)
                .setDuration(DURATION)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        setHidden(false);
                    }
                })
                .start();
    }

    private void onScrollDown(CoordinatorLayout parent, FloatingActionButton button) {
        if (mHidden) {
            return;
        }
        startHidingAnimation(parent, button);
    }

    private void startHidingAnimation(CoordinatorLayout parent, FloatingActionButton button) {
        button.animate()
                .translationY(parent.getY() + parent.getHeight() - button.getY())
                .setDuration(DURATION)
                .setInterpolator(INTERPOLATOR)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        setHidden(true);
                    }
                })
                .start();
    }

    public void setHidden(boolean b) {
        mHidden = b;
    }

}
