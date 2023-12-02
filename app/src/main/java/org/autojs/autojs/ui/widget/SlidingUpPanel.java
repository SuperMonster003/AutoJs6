package org.autojs.autojs.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import org.autojs.autojs6.R;

/**
 * Created by Stardust on Jan 24, 2017.
 */
public class SlidingUpPanel extends FrameLayout {

    private static final long DEFAULT_ANIMATION_DURATION = 300;

    private Animation mSlideUpAnimation, mSlideDownAnimation;
    private View mShadow;
    private boolean mShowing = false;
    private FrameLayout mContentContainer;

    public SlidingUpPanel(Context context) {
        super(context);
        init();
    }

    public SlidingUpPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SlidingUpPanel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public SlidingUpPanel(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public void show() {
        setVisibility(VISIBLE);
        setClickable(true);
        mContentContainer.startAnimation(mSlideUpAnimation);
        mShowing = true;
    }

    public void dismiss() {
        mContentContainer.startAnimation(mSlideDownAnimation);
        postDelayed(() -> SlidingUpPanel.this.setVisibility(GONE), mSlideDownAnimation.getDuration());
        mShowing = false;
    }

    public void setAnimationDuration(long animationDuration) {
        mSlideUpAnimation.setDuration(animationDuration);
        mSlideDownAnimation.setDuration(animationDuration / 2);
    }

    public boolean isShowing() {
        return mShowing;
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.sliding_up_panel, this, true);
        initShadow();
        initAnimation();
        setVisibility(GONE);
        mContentContainer = findViewById(R.id.content_container);
    }

    private void initAnimation() {
        mSlideUpAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.slide_up);
        mSlideDownAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.slide_down);
        setAnimationDuration(DEFAULT_ANIMATION_DURATION);
    }

    private void initShadow() {
        mShadow = findViewById(R.id.shadow);
        mShadow.setOnClickListener(v -> SlidingUpPanel.this.dismiss());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_UP && isShowing()) {
            dismiss();
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (mContentContainer != null)
            mContentContainer.addView(child, index, params);
        else
            super.addView(child, index, params);
    }


}
