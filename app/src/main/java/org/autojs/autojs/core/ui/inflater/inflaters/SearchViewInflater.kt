package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.SearchView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class SearchViewInflater<V : SearchView>(resourceParser: ResourceParser) : LinearLayoutInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<SearchView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): SearchView {
            return SearchView(context)
        }
    }

}
