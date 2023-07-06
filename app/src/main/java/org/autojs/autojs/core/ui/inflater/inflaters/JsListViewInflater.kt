package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.DynamicLayoutInflater
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsListView
import org.w3c.dom.Node

/**
 * Created by Stardust on 2018/3/28.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
open class JsListViewInflater<V : JsListView>(resourceParser: ResourceParser) : RecyclerViewInflater<V>(resourceParser) {

    override fun inflateChildren(inflater: DynamicLayoutInflater?, node: Node?, parent: V?): Boolean {
        node?: return false
        parent?: return false
        val nodeList = node.childNodes
        for (i in 0 until nodeList.length) {
            val child = nodeList.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                parent.setItemTemplate(inflater, child)
                return true
            }
        }
        return false
    }

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<JsListView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsListView {
            return JsListView(context)
        }
    }

}