package org.autojs.autojs.core.ui.inflater.inflaters

import android.view.ViewGroup
import android.widget.RadioGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Ids

/**
 * Created by Stardust on 2017/11/29.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
open class RadioGroupInflater<V : RadioGroup>(resourceParser: ResourceParser) : LinearLayoutInflater<V>(resourceParser) {

    private var mCheckedButton: Int? = null

    override fun setAttr(view: V, attrName: String, value: String, parent: ViewGroup?): Boolean {
        when (attrName) {
            "checkedButton" -> mCheckedButton = Ids.parse(value)
            else -> return super.setAttr(view, attrName, value, parent)
        }
        return true
    }

    override fun applyPendingAttributesOfChildren(view: V) {
        mCheckedButton?.let { view.check(it) }
        mCheckedButton = null
    }

}