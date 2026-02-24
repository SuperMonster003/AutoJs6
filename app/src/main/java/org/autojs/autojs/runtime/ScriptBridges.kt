package org.autojs.autojs.runtime

import org.autojs.autojs.annotation.ScriptInterface
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
 * Transformed by SuperMonster003 on Nov 7, 2023.
 * Modified by SuperMonster003 as of Feb 13, 2026.
 *
 * @Reference to aiselp (https://github.com/aiselp) on Nov 7, 2023.
 * https://github.com/kkevsekk1/AutoX/pull/529/commits/782f1c3c12dee64d9b1ad70aba462afaf60313a4#diff-b8e544bc658140f04a7fa7171cc938032f18c92495ca25bb08e2d2eb546b70f5
 */
class ScriptBridges : IScriptBridges {

    @get:ScriptInterface
    var engine: RhinoJavaScriptEngine? = null
        private set

    fun setup(engine: RhinoJavaScriptEngine) {
        this.engine = engine
    }

    fun setJavaPrimitiveWrap(b: Boolean) = useJsContext { cx ->
        cx.wrapFactory.isJavaPrimitiveWrap = b
    }

    fun isJavaPrimitiveWrap(): Boolean = useJsContext { cx ->
        cx.wrapFactory.isJavaPrimitiveWrap
    }

    override fun call(func: BaseFunction, target: Any?, args: Array<*>) = useJsContext { cx ->
        val scope = func.parentScope
        val niceScope = scope ?: ImporterTopLevel(cx)
        val niceArgs = args.map { Context.javaToJS(it, niceScope) }.toTypedArray()
        val thisObj = Context.javaToJS(target, niceScope) as? Scriptable ?: Undefined.SCRIPTABLE_UNDEFINED

        callFunction(engine?.runtime, func, niceScope, thisObj, niceArgs)
    }

    override fun toArray(o: Iterable<*>?): NativeArray = useJsContext { cx ->
        val scope = engine?.runtime?.topLevelScope ?: cx.initStandardObjects()
        cx.newArray(scope, o?.map { Context.javaToJS(it, scope) }?.toTypedArray()) as NativeArray
    }

    override fun asArray(listLike: Any): NativeArray = useJsContext { cx ->
        if (listLike is Iterable<*>) {
            return@useJsContext toArray(listLike)
        }
        if (listLike is UiObjectCollection) {
            val arr = toArray(listLike.nodes)
            val boundThis = Context.javaToJS(listLike, arr) as Scriptable
            listLike::class.java.methods.forEach {
                val name = it.name
                val method = NativeJavaMethod(it, name)
                val bound = BoundFunction(cx, arr, method, boundThis, emptyArray())
                arr.put(name, arr, bound)
            }
            return@useJsContext arr
        }
        return@useJsContext newNativeArray()
    }

    override fun toString(obj: Any?): String =
        Context.toString(obj)

    override fun toPrimitive(obj: Any?): Any? = useJsContext { cx ->
        val scope = engine?.runtime?.topLevelScope ?: cx.initStandardObjects()
        Context.javaToJS(obj, scope)
    }

    private fun <T> useJsContext(f: (Context) -> T): T {
        val currentContext = Context.getCurrentContext()
        return try {
            val engine = engine ?: return f(Context.enter())
            // Enter Context via engine to ensure WrapFactory and AutoJsContext binding are installed.
            // zh-CN: 通过 engine 进入 Context, 确保 WrapFactory 与 AutoJsContext 绑定已安装.
            f(currentContext ?: engine.enterContext())
        } finally {
            currentContext ?: Context.exit()
        }
    }

}