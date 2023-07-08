package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

/**
 * Created by SuperMonster003 on May 15, 2023.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
open class CardViewInflater<V : CardView>(resourceParser: ResourceParser) : FrameLayoutInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<CardView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): CardView {
            return CardView(context)
        }
    }

}