package org.autojs.autojs.ui.enhancedfloaty.gesture;

import androidx.core.view.GestureDetectorCompat;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import org.autojs.autojs.ui.enhancedfloaty.WindowBridge;

/**
 * Created by Stardust on Apr 18, 2017.
 */
public class DragGesture extends GestureDetector.SimpleOnGestureListener {

    /**
     * Adjusts the position a drag is about to apply (for example, to keep a window on screen).
     * zh-CN: 调整拖动即将应用的位置 (例如让窗口保持在屏幕内).
     */
    public interface PositionFilter {
        int[] filter(int x, int y);
    }

    protected WindowBridge mWindowBridge;
    protected View mView;

    private PositionFilter mPositionFilter;
    private Runnable mOnDragEndListener;
    private boolean mDragged = false;

    private float mKeepToSideHiddenWidthRadio = 0.5f;
    private int mInitialX;
    private int mInitialY;
    private float mInitialTouchX;
    private float mInitialTouchY;
    private View.OnClickListener mOnClickListener;
    private boolean mFlung = false;
    private boolean mKeepToSide;
    private float mPressedAlpha = 0.7f;
    private float mUnpressedAlpha = 1.0f;

    public DragGesture(WindowBridge windowBridge, View view) {
        mWindowBridge = windowBridge;
        mView = view;
        setupView();
    }

    private void setupView() {
        final GestureDetectorCompat gestureDetector = new GestureDetectorCompat(mView.getContext(), this);
        mView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    mView.setAlpha(mUnpressedAlpha);
                    if (action == MotionEvent.ACTION_UP && !mFlung && isKeepToSide()) {
                        keepToSide();
                    }
                    if (mDragged && mOnDragEndListener != null) {
                        mOnDragEndListener.run();
                    }
                    mDragged = false;
                }
                return true;
            }
        });
    }

    public float getPressedAlpha() {
        return mPressedAlpha;
    }

    public void setPressedAlpha(float pressedAlpha) {
        mPressedAlpha = pressedAlpha;
    }

    public float getUnpressedAlpha() {
        return mUnpressedAlpha;
    }

    public void setUnpressedAlpha(float unpressedAlpha) {
        mUnpressedAlpha = unpressedAlpha;
    }

    public void setKeepToSide(boolean keepToSide) {
        mKeepToSide = keepToSide;
    }

    public boolean isKeepToSide() {
        return mKeepToSide;
    }

    public void setKeepToSideHiddenWidthRadio(float keepToSideHiddenWidthRadio) {
        mKeepToSideHiddenWidthRadio = keepToSideHiddenWidthRadio;
    }

    public float getKeepToSideHiddenWidthRadio() {
        return mKeepToSideHiddenWidthRadio;
    }

    public void setPositionFilter(PositionFilter positionFilter) {
        mPositionFilter = positionFilter;
    }

    /** Invoked after a touch sequence that actually moved the view ends. zh-CN: 在实际移动了视图的触摸序列结束后调用. */
    public void setOnDragEndListener(Runnable onDragEndListener) {
        mOnDragEndListener = onDragEndListener;
    }

    @Override
    public boolean onDown(MotionEvent event) {
        mInitialX = mWindowBridge.getX();
        mInitialY = mWindowBridge.getY();
        mInitialTouchX = event.getRawX();
        mInitialTouchY = event.getRawY();
        mFlung = false;
        mDragged = false;
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        int x = mInitialX + (int) ((e2.getRawX() - mInitialTouchX));
        int y = mInitialY + (int) ((e2.getRawY() - mInitialTouchY));
        if (mPositionFilter != null) {
            int[] filtered = mPositionFilter.filter(x, y);
            x = filtered[0];
            y = filtered[1];
        }
        mDragged = true;
        mWindowBridge.updatePosition(x, y);
        mView.setAlpha(mPressedAlpha);
        return false;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        mFlung = true;
        if (mKeepToSide)
            keepToSide();
        return false;
    }

    public void keepToSide() {
        int x = mWindowBridge.getX();
        int hiddenWidth = (int) (mKeepToSideHiddenWidthRadio * mView.getWidth());
        if (x > mWindowBridge.getScreenWidth() / 2)
            mWindowBridge.updatePosition(mWindowBridge.getScreenWidth() - mView.getWidth() + hiddenWidth, mWindowBridge.getY());
        else
            mWindowBridge.updatePosition(-hiddenWidth, mWindowBridge.getY());
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (mOnClickListener != null)
            mOnClickListener.onClick(mView);
        return super.onSingleTapConfirmed(e);
    }

    public void setOnDraggedViewClickListener(View.OnClickListener onClickListener) {
        mOnClickListener = onClickListener;
    }
}
