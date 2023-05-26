package org.autojs.autojs.core.ui.inflater.inflaters

import androidx.cardview.widget.CardView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

/**
 * Created by SuperMonster003 on May 15, 2023.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
open class CardViewInflater<V : CardView>(resourceParser: ResourceParser) : FrameLayoutInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> CardView(context) }

}