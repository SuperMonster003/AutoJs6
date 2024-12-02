package org.autojs.autojs.runtime.api

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import org.autojs.autojs.core.ui.inflater.DynamicLayoutInflater
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Drawables
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.ProxyObject
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.Scriptable
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Stardust on May 14, 2017.
 * Modified by SuperMonster003 as of Dec 5, 2021.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
class UI(context: Context, private val scriptRuntime: ScriptRuntime) : ProxyObject() {

    var isAndroidLayout: Boolean? = null
        internal set

    val widgets by lazy { newNativeObject() }

    var view: View? = null

    var bindingContext: Any?
        get() = mProperties["bindingContext"]
        set(context) {
            context?.let { mProperties["bindingContext"] = it } ?: mProperties.remove("bindingContext")
        }

    val resourceParser = ResourceParser(object : Drawables() {
        override fun decodeImage(context: Context, path: String?): Drawable? {
            return super.decodeImage(context, scriptRuntime.files.path(path))
        }
    })

    val layoutInflater = DynamicLayoutInflater(resourceParser, scriptRuntime).also {
        it.context = context
    }

    private val mProperties = ConcurrentHashMap<String, Any?>().also {
        it["layoutInflater"] = layoutInflater
    }

    override fun getClassName(): String = UI::class.java.simpleName

    override fun get(key: String, start: Scriptable) = when {
        key == "view" -> view
        mProperties.containsKey(key) -> mProperties[key]
        else -> super.get(key, start)
    }

    override fun put(key: String, start: Scriptable, value: Any?) {
        when {
            key == "view" -> {
                require(value is View?) {
                    "Property \"ui.view\" must be a View instead of ${value.jsBrief()}"
                }
                view = value
            }
            mProperties.containsKey(key) -> value?.let { mProperties[key] = it } ?: mProperties.remove(key)
            else -> super.put(key, start, value)
        }
    }

    fun recycle() {
        layoutInflater.privateContext = null
    }

}