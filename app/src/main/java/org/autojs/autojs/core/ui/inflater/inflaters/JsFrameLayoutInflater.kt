package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.children
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.inflater.util.Gravities
import org.autojs.autojs.core.ui.widget.JsFrameLayout
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by Stardust on Nov 29, 2017.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
class JsFrameLayoutInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : FrameLayoutInflater<JsFrameLayout>(scriptRuntime, resourceParser) {

    private var mGravity: Int? = null

    override fun setAttr(view: JsFrameLayout, attrName: String, value: String, parent: ViewGroup?): Boolean {
        when (attrName) {
            "gravity" -> mGravity = Gravities.parse(value)
            else -> return super.setAttr(view, attrName, value, parent)
        }
        return true
    }

    override fun applyPendingAttributesOfChildren(view: JsFrameLayout) {
        mGravity?.let { gravity ->
            view.children.forEach { child ->
                child.layoutParams = (child.layoutParams as FrameLayout.LayoutParams).apply {
                    this.gravity = gravity
                }
            }
        }
        mGravity = null
    }

    override fun getCreator(): ViewCreator<in JsFrameLayout> = object : ViewCreator<JsFrameLayout> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsFrameLayout {
            return JsFrameLayout(context)
        }
    }

}