package org.autojs.autojs.ui.pager;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import java.util.concurrent.locks.ReentrantLock;

public class ScrollControllableViewPager extends androidx.viewpager.widget.ViewPager {

    private static final String LOG_TAG = "ScrollCtrlViewPager";
    private static boolean mCanScroll = true;
    private static final ReentrantLock mLock = new ReentrantLock();

    public ScrollControllableViewPager(Context context) {
        super(context);
    }

    public ScrollControllableViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //  @Hint by SuperMonster003 on Nov 24, 2024.
        //  ! If mCanScroll is false, consume the event by returning true
        //  ! zh-CN: 当 mCanScroll 为 false 时, 应消耗事件并返回 true.
        //  !
        //  # return mCanScroll && super.onTouchEvent(event);
        //  !
        if (!mCanScroll) {
            return true;
        }
        try {
            return super.onTouchEvent(event);
        } catch (IllegalArgumentException exception) {
            logInconsistentPointerEvent("onTouchEvent", exception);
            cancelTouchHandling(event, false);
            return true;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        //  @Hint by SuperMonster003 on Nov 24, 2024.
        //  ! If mCanScroll is false, prevent intercepting touch events by returning false
        //  ! zh-CN: 当 mCanScroll 为 false 时, 应阻止拦截触摸事件并返回 false.
        if (!mCanScroll) {
            return false;
        }
        try {
            return super.onInterceptTouchEvent(event);
        } catch (IllegalArgumentException exception) {
            // ViewPager 1.0.0 does not guard a missing active pointer in this method.
            logInconsistentPointerEvent("onInterceptTouchEvent", exception);
            cancelTouchHandling(event, true);
            return false;
        }
    }

    private void cancelTouchHandling(MotionEvent event, boolean intercepting) {
        MotionEvent cancelEvent = null;
        try {
            cancelEvent = MotionEvent.obtain(event);
            cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
            if (intercepting) {
                super.onInterceptTouchEvent(cancelEvent);
            } else {
                super.onTouchEvent(cancelEvent);
            }
        } catch (RuntimeException exception) {
            Log.w(LOG_TAG, "Unable to cancel inconsistent pointer event: " + exception.getMessage());
        } finally {
            if (cancelEvent != null) {
                cancelEvent.recycle();
            }
        }
    }

    private static void logInconsistentPointerEvent(String method, IllegalArgumentException exception) {
        Log.w(LOG_TAG, "Ignoring an inconsistent pointer event in " + method + ": " + exception.getMessage());
    }

    public static void setPageScrollEnabled(boolean enabled) {
        mLock.lock();
        try {
            mCanScroll = enabled;
        } finally {
            mLock.unlock();
        }
    }

}
