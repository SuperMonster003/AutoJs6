package org.autojs.autojs.core.ui.inflater.util

import android.content.Context
import android.view.View
import org.autojs.autojs.util.ColorUtils

/**
 * Created by Stardust on Nov 3, 2017.
 * Modified by SuperMonster003 as of May 19, 2023.
 * Transformed by SuperMonster003 on May 19, 2023.
 */
object Colors {

    @JvmStatic
    fun parse(context: Context, color: String) = ColorUtils.parse(context, color)

    @JvmStatic
    fun parse(view: View, color: String) = parse(view.context, color)

}