package org.autojs.autojs.util

import org.autojs.autojs.AutoJs
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.tools.shell.Global
import java.lang.reflect.InvocationTargetException

object RhinoUtils {

    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(func: BaseFunction, paramsToFunction: Array<*>): Any? {
        val context = Context.getCurrentContext()
        val scriptable: Scriptable = Global(context)
        return func.javaClass
            .getMethod("call", Context::class.java, Scriptable::class.java, Scriptable::class.java, Array<Any>::class.java)
            .invoke(
                /* invokeFrom: Object */ func,
                /* cx: Context */ context,
                /* scope: Scriptable */ scriptable,
                /* thisObj: Scriptable */ scriptable,
                /* args: Object[] */ paramsToFunction
            )
    }

    fun wrap(obj: Any?): Any = Context.getCurrentContext().let { cx ->
        cx.wrapFactory.wrap(cx, AutoJs.instance.runtime.topLevelScope, obj, obj?.let { it::class.java })
    }

    fun toArray(obj: Iterable<*>): Any = AutoJs.instance.runtime.bridges.toArray(obj)

}