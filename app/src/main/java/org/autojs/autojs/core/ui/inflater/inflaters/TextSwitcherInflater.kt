package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.TextSwitcher
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

open class TextSwitcherInflater<V : TextSwitcher>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : ViewSwitcherInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<TextSwitcher> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): TextSwitcher {
            return TextSwitcher(context)
        }
    }

}
