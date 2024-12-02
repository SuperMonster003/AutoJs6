package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.appcompat.R
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.inflater.util.Res
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by Stardust on Nov 29, 2017.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
open class ProgressBarInflater<V : ProgressBar>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : BaseViewInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<ProgressBar> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): ProgressBar {
            fun hasTrueAttr(name: String) = attrs["android:$name"] == "true"

            attrs["style"]?.let { return ProgressBar(context, null, 0, Res.parseStyle(context, it)) }

            if (hasTrueAttr("isHorizontal") || hasTrueAttr("horizontal")) {
                return ProgressBar(context, null, 0, R.style.Base_Widget_AppCompat_ProgressBar_Horizontal)
            }
            return ProgressBar(context)
        }
    }

}