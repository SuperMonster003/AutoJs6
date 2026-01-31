package org.autojs.autojs.rhino.extension

import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.util.RhinoUtils.getOrCreateStandardObjects
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray

object ArrayExtensions {

    fun Array<*>.toNativeArray(): NativeArray {
        return withRhinoContext { cx ->
            val standardObjects = cx.getOrCreateStandardObjects()
            cx.newArray(standardObjects, this.toList().map { Context.javaToJS(it, standardObjects) }.toTypedArray()) as NativeArray
        }
    }

    fun <T> Array<T>.jsArrayBrief(separator: String = ", ", appendPaddingSpace: Boolean = true) = when (appendPaddingSpace) {
        true -> "[ ${this.joinToString(separator) { it.jsBrief() }} ]"
        else -> "[${this.joinToString(separator) { it.jsBrief() }}]"
    }

}