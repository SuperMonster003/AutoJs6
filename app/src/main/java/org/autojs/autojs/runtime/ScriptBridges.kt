package org.autojs.autojs.runtime

import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.newNativeArray
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.BoundFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ImporterTopLevel
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeJavaMethod
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

/**
 * Created by Stardust on Jul 21, 2017.
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on Nov 7, 2023.
 *
 * @Reference to aiselp (https://github.com/aiselp) on Nov 7, 2023.
 * https://github.com/kkevsekk1/AutoX/pull/529/commits/782f1c3c12dee64d9b1ad70aba462afaf60313a4#diff-b8e544bc658140f04a7fa7171cc938032f18c92495ca25bb08e2d2eb546b70f5
 */
class ScriptBridges : IScriptBridges {

    lateinit var engine: RhinoJavaScriptEngine

    fun setup(engine: RhinoJavaScriptEngine) {
        this.engine = engine
    }

    override fun call(func: BaseFunction, target: Any?, args: Array<*>) = useJsContext { cx ->
        val scope = func.parentScope
        val niceScope = scope ?: ImporterTopLevel(cx)
        val niceArgs = args.map { Context.javaToJS(it, niceScope) }.toTypedArray()
        val thisObj = Context.javaToJS(target, niceScope) as? Scriptable ?: Undefined.SCRIPTABLE_UNDEFINED

        callFunction(engine.runtime, func, niceScope, thisObj, niceArgs)
    }

    override fun toArray(o: Iterable<*>?): NativeArray = useJsContext { context ->
        val scope = context.initStandardObjects()
        context.newArray(scope, o?.map { Context.javaToJS(it, scope) }?.toTypedArray()) as NativeArray
    }

    override fun asArray(listLike: Any): NativeArray = useJsContext { context ->
        if (listLike is Iterable<*>) {
            return@useJsContext toArray(listLike)
        }
        if (listLike is UiObjectCollection) {
            val arr = toArray(listLike.nodes)
            val boundThis = Context.javaToJS(listLike, arr) as Scriptable
            listLike::class.java.methods.forEach {
                val name = it.name
                val method = NativeJavaMethod(it, name)
                val bound = BoundFunction(context, arr, method, boundThis, emptyArray())
                arr.put(name, arr, bound)
            }
            return@useJsContext arr
        }
        return@useJsContext newNativeArray()
    }

    override fun toString(obj: Any?): String = Context.toString(obj)

    override fun toPrimitive(obj: Any?): Any = useJsContext { context ->
        Context.javaToJS(obj, context.initStandardObjects())
    }

    private fun <T> useJsContext(f: (Context) -> T): T {
        val currentContext = Context.getCurrentContext()
        return try {
            f(currentContext ?: engine.setupContext(Context.enter()))
        } finally {
            currentContext ?: Context.exit()
        }
    }

}