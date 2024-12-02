package org.autojs.autojs.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;

/**
 * Modified by SuperMonster003 as of May 26, 2022.
 */
public class NestedWebView extends WebView {

    public NestedWebView(Context context) {
        this(context, null);
    }

    public NestedWebView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.webViewStyle);
    }

    public NestedWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    // @Hint by SuperMonster003 on Nov 24, 2024.
    //  ! References:
    //  ! https://droidyue.com/blog/2019/01/27/webview-javascript-scrolling-issue/
    //  ! https://github.com/androidyue/WebViewViewPagerScrollingIssue
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return super.onTouchEvent(event);
    }

    // @Hint by SuperMonster003 on Nov 24, 2024.
    //  ! References:
    //  ! https://droidyue.com/blog/2019/01/27/webview-javascript-scrolling-issue/
    //  ! https://github.com/androidyue/WebViewViewPagerScrollingIssue
    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        if (clampedX) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
    }

}