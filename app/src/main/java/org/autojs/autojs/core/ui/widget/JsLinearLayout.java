package org.autojs.autojs.core.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import org.autojs.autojs.core.ui.JsViewHelper;

/**
 * Created by Stardust on 2017/5/14.
 */
public class JsLinearLayout extends LinearLayout {


    public JsLinearLayout(Context context) {
        super(context);
    }

    public JsLinearLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public JsLinearLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public JsLinearLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public View id(String id) {
        return JsViewHelper.findViewByStringId(this, id);
    }

}
