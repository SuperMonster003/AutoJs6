package org.autojs.autojs.util

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import androidx.annotation.RequiresApi

/**
 * Created by Stardust on Feb 12, 2017.
 * Modified by SuperMonster003 as of Jan 21, 2023.
 */
@Suppress("DEPRECATION", "unused")
object DrawableUtils {

    @JvmStatic
    fun setDrawableColorFilterAdd(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            setColorFilter(color, PorterDuff.Mode.ADD)
        }
    }

    @JvmStatic
    fun setDrawableColorFilterClear(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.CLEAR)
                else -> setColorFilter(color, PorterDuff.Mode.CLEAR)
            }
        }
    }

    @RequiresApi(Q)
    @JvmStatic
    fun setDrawableColorFilterColor(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            colorFilter = BlendModeColorFilter(color, BlendMode.COLOR)
        }
    }

    @RequiresApi(Q)
    @JvmStatic
    fun setDrawableColorFilterColorBurn(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            colorFilter = BlendModeColorFilter(color, BlendMode.COLOR_BURN)
        }
    }

    @RequiresApi(Q)
    @JvmStatic
    fun setDrawableColorFilterColorDodge(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            colorFilter = BlendModeColorFilter(color, BlendMode.COLOR_DODGE)
        }
    }

    @JvmStatic
    fun setDrawableColorFilterDarken(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.DARKEN)
                else -> setColorFilter(color, PorterDuff.Mode.DARKEN)
            }
        }
    }

    @RequiresApi(Q)
    @JvmStatic
    fun setDrawableColorFilterDifference(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            colorFilter = BlendModeColorFilter(color, BlendMode.DIFFERENCE)
        }
    }

    @JvmStatic
    fun setDrawableColorFilterDst(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.DST)
                else -> setColorFilter(color, PorterDuff.Mode.DST)
            }
        }
    }

    @JvmStatic
    fun setDrawableColorFilterDstAtop(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.DST_ATOP)
                else -> setColorFilter(color, PorterDuff.Mode.DST_ATOP)
            }
        }
    }

    @JvmStatic
    fun setDrawableColorFilterDstIn(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.DST_IN)
                else -> setColorFilter(color, PorterDuff.Mode.DST_IN)
            }
        }
    }

    @JvmStatic
    fun setDrawableColorFilterDstOut(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.DST_OUT)
                else -> setColorFilter(color, PorterDuff.Mode.DST_OUT)
            }
        }
    }

    @JvmStatic
    fun setDrawableColorFilterDstOver(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.DST_OVER)
                else -> setColorFilter(color, PorterDuff.Mode.DST_OVER)
            }
        }
    }

    @RequiresApi(Q)
    @JvmStatic
    fun setDrawableColorFilterExclusion(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            colorFilter = BlendModeColorFilter(color, BlendMode.EXCLUSION)
        }
    }

    @RequiresApi(Q)
    @JvmStatic
    fun setDrawableColorFilterHardLight(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            colorFilter = BlendModeColorFilter(color, BlendMode.HARD_LIGHT)
        }
    }

    @RequiresApi(Q)
    @JvmStatic
    fun setDrawableColorFilterHue(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            colorFilter = BlendModeColorFilter(color, BlendMode.HUE)
        }
    }

    @JvmStatic
    fun setDrawableColorFilterLighten(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.LIGHTEN)
                else -> setColorFilter(color, PorterDuff.Mode.LIGHTEN)
            }
        }
    }

    @RequiresApi(Q)
    @JvmStatic
    fun setDrawableColorFilterLuminosity(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            colorFilter = BlendModeColorFilter(color, BlendMode.LUMINOSITY)
        }
    }

    @RequiresApi(Q)
    @JvmStatic
    fun setDrawableColorFilterModulate(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            colorFilter = BlendModeColorFilter(color, BlendMode.MODULATE)
        }
    }

    @JvmStatic
    fun setDrawableColorFilterMultiply(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.MULTIPLY)
                else -> setColorFilter(color, PorterDuff.Mode.MULTIPLY)
            }
        }
    }

    @JvmStatic
    fun setDrawableColorFilterOverlay(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.OVERLAY)
                else -> setColorFilter(color, PorterDuff.Mode.OVERLAY)
            }
        }
    }

    @RequiresApi(Q)
    @JvmStatic
    fun setDrawableColorFilterPlus(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            colorFilter = BlendModeColorFilter(color, BlendMode.PLUS)
        }
    }

    @RequiresApi(Q)
    @JvmStatic
    fun setDrawableColorFilterSaturation(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            colorFilter = BlendModeColorFilter(color, BlendMode.SATURATION)
        }
    }

    @JvmStatic
    fun setDrawableColorFilterScreen(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.SCREEN)
                else -> setColorFilter(color, PorterDuff.Mode.SCREEN)
            }
        }
    }

    @RequiresApi(Q)
    @JvmStatic
    fun setDrawableColorFilterSoftLight(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            colorFilter = BlendModeColorFilter(color, BlendMode.SOFT_LIGHT)
        }
    }

    @JvmStatic
    fun setDrawableColorFilterSrc(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.SRC)
                else -> setColorFilter(color, PorterDuff.Mode.SRC)
            }
        }
    }

    @JvmStatic
    fun setDrawableColorFilterSrcAtop(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.SRC_ATOP)
                else -> setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
            }
        }
    }

    @JvmStatic
    fun setDrawableColorFilterSrcIn(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.SRC_IN)
                else -> setColorFilter(color, PorterDuff.Mode.SRC_IN)
            }
        }
    }

    @JvmStatic
    fun setDrawableColorFilterSrcOut(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.SRC_OUT)
                else -> setColorFilter(color, PorterDuff.Mode.SRC_OUT)
            }
        }
    }

    @JvmStatic
    fun setDrawableColorFilterSrcOver(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.SRC_OVER)
                else -> setColorFilter(color, PorterDuff.Mode.SRC_OVER)
            }
        }
    }

    @JvmStatic
    fun setDrawableColorFilterXor(drawable: Drawable?, color: Int): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()?.apply {
            when {
                SDK_INT >= Q -> colorFilter = BlendModeColorFilter(color, BlendMode.XOR)
                else -> setColorFilter(color, PorterDuff.Mode.XOR)
            }
        }
    }

}