package org.autojs.autojs.core.ui.inflater.inflaters

import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

/**
 * Created by SuperMonster003 on May 15, 2023.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
open class FloatingActionButtonInflater<V : FloatingActionButton>(resourceParser: ResourceParser) : ImageViewInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> FloatingActionButton(context) }

}