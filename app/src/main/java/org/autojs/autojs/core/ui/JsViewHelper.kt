package org.autojs.autojs.core.ui

import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.util.Ids

/**
 * Created by Stardust on May 14, 2017.
 * Transformed by 抠脚本人 (https://github.com/little-alei) on Jul 10, 2023.
 */
object JsViewHelper {
    @JvmStatic
    fun findViewByStringId(view: View, id: String?): View? {
        view.findViewById<View>(Ids.parse(id))?.let { return it }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findViewByStringId(view.getChildAt(i), id)?.let { return it }
            }
        }
        return null
    }
}
