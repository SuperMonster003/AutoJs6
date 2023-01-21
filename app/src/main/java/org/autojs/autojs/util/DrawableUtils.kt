package org.autojs.autojs.util

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * Created by Stardust on 2017/2/12.
 */
object DrawableUtils {

    @JvmStatic
    fun filterDrawableColor(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                colorFilter = BlendModeColorFilter(color, BlendMode.SRC_IN)
            } else {
                @Suppress("DEPRECATION")
                setColorFilter(color, PorterDuff.Mode.SRC_IN)
            }
        }
    }

}