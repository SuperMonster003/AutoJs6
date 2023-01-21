package org.autojs.autojs.ui.floating;

import android.content.Context;
import android.util.AttributeSet;
import android.view.WindowManager;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CircularActionView extends LinearLayout {

    public CircularActionView(@NonNull Context context) {
        super(context);
    }

    public CircularActionView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CircularActionView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void removeFromWindow(WindowManager windowManager) {
        if (isAttachedToWindow()) {
            windowManager.removeView(this);
        }
    }

}
