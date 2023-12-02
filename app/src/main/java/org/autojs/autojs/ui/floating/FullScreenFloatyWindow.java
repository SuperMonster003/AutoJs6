package org.autojs.autojs.ui.floating;

import android.graphics.PixelFormat;
import android.view.WindowManager;

import org.autojs.autojs.ui.enhancedfloaty.FloatyWindow;

/**
 * Created by Stardust on Oct 18, 2017.
 */
public abstract class FullScreenFloatyWindow extends FloatyWindow {

    @Override
    protected WindowManager.LayoutParams onCreateWindowLayoutParams() {
        return new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                FloatyWindowManger.getWindowType(),
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
    }

}
