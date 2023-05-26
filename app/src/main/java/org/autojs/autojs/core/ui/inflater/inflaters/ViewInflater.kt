package org.autojs.autojs.core.ui.inflater.inflaters

import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.DynamicLayoutInflater
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.w3c.dom.Node

/**
 * Created by Stardust on 2017/11/3.
 */
interface ViewInflater<V : View> {

    fun getCreator(): ViewCreator<in V>? = null

    fun setAttr(view: V, attrName: String, value: String, parent: ViewGroup?): Boolean

    fun setAttr(view: V, ns: String?, attrName: String, value: String, parent: ViewGroup?): Boolean {
        return if (ns == null || ns == "android" || ns == "app") setAttr(view, attrName, value, parent) else false
    }

    fun applyPendingAttributes(view: V, parent: ViewGroup?) {
        // Empty default interface method.
    }

    fun inflateChildren(inflater: DynamicLayoutInflater?, node: Node?, parent: V?): Boolean = false

}