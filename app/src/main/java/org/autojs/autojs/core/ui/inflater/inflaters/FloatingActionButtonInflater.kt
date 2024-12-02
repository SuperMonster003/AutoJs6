package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by SuperMonster003 on May 15, 2023.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
open class FloatingActionButtonInflater<V : FloatingActionButton>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : ImageViewInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<FloatingActionButton> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): FloatingActionButton {
            return FloatingActionButton(context)
        }
    }

}