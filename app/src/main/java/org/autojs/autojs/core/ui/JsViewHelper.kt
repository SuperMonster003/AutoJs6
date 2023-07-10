package org.autojs.autojs.core.ui

import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.util.Ids

/**
 * Created by Stardust on 2017/5/14.
 */
object JsViewHelper {
    @JvmStatic
    fun findViewByStringId(view: View, id: String?): View? {
        var result = view.findViewById<View>(Ids.parse(id))
        if (result != null) return result
        if (view !is ViewGroup) {
            return null
        }
        for (i in 0 until view.childCount) {
            result = findViewByStringId(view.getChildAt(i), id)
            if (result != null) return result
        }
        return null
    }
}
