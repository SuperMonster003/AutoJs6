package org.autojs.autojs.core.ui.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompatlegacy.widget.AppCompatTextView;

/**
 * Created by SuperMonster003 on Mar 20, 2022.
 */
// @Reference to TonyJiangWJ/Auto.js on Mar 20, 2022
public class JsTextViewLegacy extends AppCompatTextView {

    public JsTextViewLegacy(Context context) {
        super(context);
    }

    public JsTextViewLegacy(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public JsTextViewLegacy(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public String text() {
        return getText().toString();
    }

    public void text(CharSequence text) {
        setText(text);
    }

}
