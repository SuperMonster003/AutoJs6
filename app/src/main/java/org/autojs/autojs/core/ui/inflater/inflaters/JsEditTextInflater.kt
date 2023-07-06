package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsEditText

/**
 * Created by SuperMonster003 on May 19, 2023.
 */
class JsEditTextInflater(resourceParser: ResourceParser) : EditTextInflater<JsEditText>(resourceParser) {

    private var mIsSelectAll = false
    private var mExtendsSelection: String? = null
    private var mSetSelection: String? = null

    override fun setAttr(view: JsEditText, attrName: String, value: String, parent: ViewGroup?): Boolean {
        when (attrName) {
            "selectAll" -> mIsSelectAll = value.contentEquals("true")
            "extendsSelection" -> mExtendsSelection = value
            "setSelection" -> mSetSelection = value
            else -> return super.setAttr(view, attrName, value, parent)
        }
        return true
    }

    override fun applyPendingAttributes(view: JsEditText, parent: ViewGroup?) {
        super.applyPendingAttributes(view, parent)

        if (mIsSelectAll) {
            view.requestFocus()
            view.selectAll()
            return
        }
        mExtendsSelection?.let {
            view.requestFocus()
            view.extendSelection(it.toInt())
            return
        }
        mSetSelection?.let {
            if (view.text.isEmpty()) {
                return
            }
            if (it.matches(Regex("[+-]?\\d+(\\.\\d+)?"))) {
                view.requestFocus()
                view.setSelection(it.toInt())
                return
            }
            if (it.matches(Regex("[+-]?\\d+(\\.\\d+)?[,\\s]+[+-]?\\d+(\\.\\d+)?"))) {
                view.requestFocus()
                val (startIndex, stopIndex) = it.split(Regex("[,\\s]+"))
                view.setSelection(startIndex.toInt(), stopIndex.toInt())
                return
            }
            throw Exception("Can't resolve attribute { name: setSelection, value: $it } when inflating a EditText view")
        }
    }

    override fun getCreator(): ViewCreator<in JsEditText> = object : ViewCreator<JsEditText> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsEditText {
            return JsEditText(context)
        }
    }

}