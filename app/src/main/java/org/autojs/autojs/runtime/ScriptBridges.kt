package org.autojs.autojs.runtime

import android.os.Looper
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.BoundFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeJavaMethod
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

/**
 * Created by Stardust on 2017/7/21.
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on Nov 7, 2023.
 *
 * @Reference to aiselp (https://github.com/aiselp) on Nov 7, 2023.
 * https://github.com/kkevsekk1/AutoX/pull/529/commits/782f1c3c12dee64d9b1ad70aba462afaf60313a4#diff-b8e544bc658140f04a7fa7171cc938032f18c92495ca25bb08e2d2eb546b70f5
 */
class ScriptBridges : IScriptBridges {

    var engine: RhinoJavaScriptEngine? = null

    fun setup(engine: RhinoJavaScriptEngine) {
        this.engine = engine
    }

    private fun <T> useJsContext(f: (context: Context) -> T): T {
        val context = Context.getCurrentContext()
        val cx: Context = context ?: with(Context.enter()) {
            engine?.setupContext(this)
            this
        }
        try {
            return f(cx)
        } finally {
            context ?: Context.exit()
        }
    }

    override fun call(func: Any?, target: Any?, args: Array<*>) = useJsContext<Any?> { context ->
        val jsFn = func as BaseFunction
        val scope = jsFn.parentScope
        val arg = args.map { Context.javaToJS(it, scope) }.toTypedArray()

        try {
            val thisObj = Context.javaToJS(target, scope) as? Scriptable ?: Undefined.SCRIPTABLE_UNDEFINED
            jsFn.call(context, scope, thisObj, arg)
        } catch (e: Exception) {
            if (Looper.getMainLooper() == Looper.myLooper()) {
                engine?.runtime?.exit(e) ?: throw e
            } else {
                throw e
            }
        }
    }

    override fun toArray(o: Iterable<*>?): NativeArray? = useJsContext { context ->
        val scope = context.initStandardObjects()
        context.newArray(scope, o?.map { Context.javaToJS(it, scope) }?.toTypedArray()) as? NativeArray
    }

    override fun toString(obj: Any?): String {
        return Context.toString(obj)
    }

    override fun toPrimitive(obj: Any?): Any = useJsContext { context ->
        val scope = context.initStandardObjects()
        Context.javaToJS(obj, scope)
    }

    override fun asArray(uiObjectCollection: Any?): NativeArray? = useJsContext { context ->
        if (uiObjectCollection !is UiObjectCollection) {
            return@useJsContext null
        }
        val arr = toArray(uiObjectCollection.nodes) ?: NativeArray(emptyArray())
        val boundThis = Context.javaToJS(uiObjectCollection, arr) as Scriptable
        uiObjectCollection::class.java.methods.forEach {
            val name = it.name
            val method = NativeJavaMethod(it, name)
            val bound = BoundFunction(context, arr, method, boundThis, emptyArray())
            arr.put(name, arr, bound)
        }
        return@useJsContext arr
    }

}
