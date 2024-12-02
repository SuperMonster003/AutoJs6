package org.autojs.autojs.ui.pager;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import java.util.concurrent.locks.ReentrantLock;

public class ScrollControllableViewPager extends androidx.viewpager.widget.ViewPager {

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
        return !mCanScroll || super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        //  @Hint by SuperMonster003 on Nov 24, 2024.
        //  ! If mCanScroll is false, prevent intercepting touch events by returning false
        //  ! zh-CN: 当 mCanScroll 为 false 时, 应阻止拦截触摸事件并返回 false.
        return mCanScroll && super.onInterceptTouchEvent(event);
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
