package org.autojs.autojs.runtime.api.augment.floaty

import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.floaty.BaseResizableFloatyWindow
import org.autojs.autojs.core.ui.ViewExtras
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.ProxyJavaObject
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_GETTER_KEY
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_OBJECT_KEY
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_SETTER_KEY
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Floaty
import org.autojs.autojs.runtime.api.Floaty.JsWindow
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.undefined
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.xmlimpl.XML
import android.content.Context as AndroidContext

@Suppress("unused", "UNUSED_PARAMETER")
class Floaty(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentFunctions = listOf(
        ::window.name,
        ::rawWindow.name,
        ::hasPermission.name,
        ::requestPermission.name,
        ::ensurePermission.name,
        ::closeAll.name,
        ::getClip.name,
    )

    companion object : FlexibleArray() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun window(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyJavaObject = ensureArgumentsOnlyOne(args) { xml ->
            wrap(scriptRuntime, { scriptRuntime.floaty.window(it) }, xml)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun rawWindow(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ProxyJavaObject = ensureArgumentsOnlyOne(args) { xml ->
            wrap(scriptRuntime, { scriptRuntime.floaty.rawWindow(it) }, xml)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun hasPermission(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.floaty.hasPermission()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun requestPermission(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            undefined { scriptRuntime.floaty.requestPermission() }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun ensurePermission(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            undefined { scriptRuntime.floaty.ensurePermission() }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun closeAll(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsIsEmpty(args) {
            scriptRuntime.floaty.closeAll()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getClip(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsAtMost(args, 1) { argList ->
            val (delay) = argList
            when {
                delay.isJsNullish() -> scriptRuntime.floaty.getClip()
                else -> scriptRuntime.floaty.getClip(coerceLongNumber(delay!!, 0L))
            }
        }

        private fun wrap(scriptRuntime: ScriptRuntime, windowFunction: (supplier: BaseResizableFloatyWindow.ViewSupplier) -> JsWindow, xml: Any?): ProxyJavaObject {
            require(xml is XML) { "Argument xml ${xml.jsBrief()} is invalid for Floaty#wrap" }
            val storage = mutableMapOf<String, Any?>()
            val layoutInflater = scriptRuntime.ui.layoutInflater
            val window = windowFunction(object : BaseResizableFloatyWindow.ViewSupplier {
                override fun inflate(context: AndroidContext, parent: ViewGroup?): View {
                    layoutInflater.context = context
                    return layoutInflater.inflate(toXMLString(xml), parent, true)
                }
            })
            val getter = fun(args: Array<out Any?>): Any? {
                val (keyArg) = args
                val id = coerceString(keyArg)
                val value = storage[id]
                if (!value.isJsNullish()) return value!!

                val view = when (window) {
                    is Floaty.JsRawWindow -> window.findView(id)
                    is Floaty.JsResizableWindow -> window.findView(id)
                    else -> null
                }
                return when {
                    view.isJsNullish() -> UNDEFINED
                    else -> ViewExtras.getNativeView(scriptRuntime.topLevelScope, view!!, null, scriptRuntime)
                }
            }
            val setter = fun(args: Array<out Any?>): Undefined {
                val (keyArg, value) = args
                val id = coerceString(keyArg)
                storage[id] = value
                return UNDEFINED
            }
            val proxyObject = newNativeObject().also {
                it.defineProp(PROXY_GETTER_KEY, newBaseFunction(PROXY_GETTER_KEY, getter, NOT_CONSTRUCTABLE), READONLY or DONTENUM or PERMANENT)
                it.defineProp(PROXY_SETTER_KEY, newBaseFunction(PROXY_GETTER_KEY, setter, NOT_CONSTRUCTABLE), READONLY or DONTENUM or PERMANENT)
            }
            return ProxyJavaObject(scriptRuntime.topLevelScope, window, window::class.java).also {
                it.defineProp(PROXY_OBJECT_KEY, proxyObject, READONLY or DONTENUM or PERMANENT)
            }
        }

        private fun toXMLString(xml: Any) = when (xml) {
            is XML -> xml.toXMLString()
            else -> xml.toString()
        }

    }

}