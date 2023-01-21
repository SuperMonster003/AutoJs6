package org.autojs.autojs.ui.pager;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import java.util.concurrent.locks.ReentrantLock;

public class ViewPager extends androidx.viewpager.widget.ViewPager {

    private static boolean mCanScroll = true;
    private static final ReentrantLock mLock = new ReentrantLock();

    public ViewPager(Context context) {
        super(context);
    }

    public ViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mCanScroll && super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return mCanScroll && super.onInterceptTouchEvent(event);
    }

    public static void setPageScrollEnabled(boolean b) {
        mLock.lock();
        mCanScroll = b;
        mLock.unlock();
    }

}
