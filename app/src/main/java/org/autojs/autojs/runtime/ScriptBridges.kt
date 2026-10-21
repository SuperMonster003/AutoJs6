package org.autojs.autojs.runtime

import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.getOrCreateStandardObjects
import org.autojs.autojs.util.RhinoUtils.newNativeArray
import org.mozilla.javascript.BoundFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
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

    override fun call(func: Callable, target: Any?, args: Array<*>) = useJsContext { cx ->
        val scope = when (func) {
            is Function -> func.declarationScope
            is Scriptable -> func.parentScope
            else -> null
        }
        val niceScope = scope ?: engine?.runtime?.topLevelScope ?: cx.getOrCreateStandardObjects()
        val niceArgs = args.map { Context.javaToJS(it, niceScope) }.toTypedArray()
        val thisObj = Context.javaToJS(target, niceScope) as? Scriptable ?: Undefined.SCRIPTABLE_UNDEFINED

        callFunction(engine?.runtime, func, niceScope, thisObj, niceArgs)
    }

    override fun toArray(o: Iterable<*>?): NativeArray = useJsContext { cx ->
        val scope = engine?.runtime?.topLevelScope ?: cx.getOrCreateStandardObjects()
        cx.newArray(scope, o?.map { Context.javaToJS(it, scope) }?.toTypedArray()) as NativeArray
    }

    override fun asArray(listLike: Any): NativeArray = useJsContext { cx ->
        if (listLike is Iterable<*>) {
            return@useJsContext toArray(listLike)
        }
        if (listLike is UiObjectCollection) {
            val arr = toArray(listLike.nodes)
            val scope = arr.parentScope ?: engine?.runtime?.topLevelScope ?: cx.getOrCreateStandardObjects()
            val boundThis = Context.javaToJS(listLike, scope) as Scriptable
            // The members of the wrapped collection carry every overload of a name (the former
            // per-Method binding kept only the last overload, so `slice(1)` next to `slice(1, 3)`
            // or `performAction(a)` next to `performAction(a, ...args)` would resolve to one of them).
            // zh-CN: 被包装集合的成员带有同名的全部重载 (原先逐 Method 绑定只保留最后一个重载, `slice(1)` 与 `slice(1, 3)`,
            // `performAction(a)` 与 `performAction(a, ...args)` 只会剩下其一).
            listLike::class.java.methods.map { it.name }.distinct().forEach { name ->
                val member = ScriptableObject.getProperty(boundThis, name)
                if (member is Callable) {
                    arr.put(name, arr, BoundFunction(cx, scope, member, boundThis, emptyArray()))
                }
            }
            return@useJsContext arr
        }
        val scope = engine?.runtime?.topLevelScope ?: cx.getOrCreateStandardObjects()
        return@useJsContext newNativeArray(scope)
    }

    override fun toString(obj: Any?): String =
        Context.toString(obj)

    override fun toPrimitive(obj: Any?): Any? = useJsContext { cx ->
        val scope = engine?.runtime?.topLevelScope ?: cx.getOrCreateStandardObjects()
        Context.javaToJS(obj, scope)
    }

    // Reuses the Context of the calling thread, otherwise enters one and exits it again. Every enter must be paired:
    // the bridges of the WrapFactory have no engine, and entering on a thread that already had a Context without
    // exiting (as before) left that Context entered when the thread ended, so it was never released (one per script
    // thread that called find() or children(), ROADMAP P3.4).
    // zh-CN: 复用调用线程的 Context, 否则进入一个并在结束时退出. 每次进入都必须配对退出: WrapFactory 使用的 bridges 没有 engine,
    // 原先在已有 Context 的线程上进入却不退出, 线程结束时该 Context 仍处于进入状态而永远不会释放 (每个调用过 find() 或 children() 的脚本线程一个, 路线图 P3.4).
    private fun <T> useJsContext(f: (Context) -> T): T {
        Context.getCurrentContext()?.let { return f(it) }
        // Enter Context via engine to ensure WrapFactory and AutoJsContext binding are installed.
        // zh-CN: 通过 engine 进入 Context, 确保 WrapFactory 与 AutoJsContext 绑定已安装.
        val cx = engine?.enterContext() ?: Context.enter()
        return try {
            f(cx)
        } finally {
            Context.exit()
        }
    }

}
