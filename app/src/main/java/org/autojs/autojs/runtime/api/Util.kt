package org.autojs.autojs.runtime.api

import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief

object Util {

    fun `class`(o: Any?) = getClassInternal(o, "class")

    fun getClass(o: Any?) = getClassInternal(o, "getClass")

    fun className(o: Any?) = getClassNameInternal(o, "className")

    fun getClassName(o: Any?) = getClassNameInternal(o, "getClassName")

    private fun getClassInternal(o: Any?, methodName: String): Class<out Any> {
        requireNotNull(o) { "Argument \"o\" ${o.jsBrief()} for util.$methodName must be non-null" }
        return o as? Class<*> ?: o.javaClass
    }

    private fun getClassNameInternal(o: Any?, methodName: String): String = getClassInternal(o, methodName).name

}
