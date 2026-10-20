package org.autojs.autojs.runtime.api.modules.bootstrap

import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Callable
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope

/**
 * Installs the polyfills `Object.observe` / `Object.unobserve` / `Object.getNotifier` / `Object.deliverChangeRecords`,
 * `Array.observe` / `Array.unobserve` and the global `structuredClone` (d.ts `aj6-int-object-observe.d.ts` /
 * `aj6-int-array-observe.d.ts`) on first access instead of running their modules for every script in
 * `ScriptRuntime.initEpilogue` (roadmap P2.5).
 *
 * Each name is a [LazyDataSlot] defined where the module assignment put it: a data property that is enumerable, writable
 * and configurable from the start, so `Object.keys`, `hasOwnProperty`, `in` and the key order are what they were, while
 * reading its value or descriptor runs the module once and yields the very function it assigned. The two observe
 * modules (`object-observe-lite.min.js`, then `array-observe.min.js`, which also wraps `Array.prototype.push`, `pop`,
 * `shift`, `unshift` and `splice` to report splices) run together on the first read of any of the six observe names;
 * `structured-clone.min.js` runs on the first read of `structuredClone`. The modules assign to holder objects passed
 * as arguments rather than to `Object` / `Array` / the global object, so they never read a pending slot, and a name a
 * script assigned before the first read keeps that value. Until the first read, the five array methods are Rhino's own
 * (only observers rely on the wrappers, and observing starts with reading one of the names).
 *
 * A name that the target already has (a future Rhino with `structuredClone`) is left alone, as the modules' checks did.
 * The modules are loaded with [load] (the engine's own `require`, which shares the compiled scripts across the process)
 * and called with [call] (with the runtime's Context handling, since a slot may be read from any thread).
 *
 * zh-CN: 在首次访问时安装 polyfill: Object.observe / unobserve / getNotifier / deliverChangeRecords, Array.observe / unobserve 与全局
 * structuredClone (d.ts `aj6-int-object-observe.d.ts` / `aj6-int-array-observe.d.ts`), 不再在 `ScriptRuntime.initEpilogue` 中为每个脚本执行这些模块
 * (ROADMAP P2.5). 每个名称都是定义在原模块赋值位置的 LazyDataSlot: 从一开始就是可枚举, 可写, 可配置的数据属性, Object.keys, hasOwnProperty, in 与键顺序不变;
 * 读取值或描述符时执行一次模块, 得到的正是模块赋的函数. 两个 observe 模块 (先 object-observe-lite.min.js, 再 array-observe.min.js, 后者还包装
 * Array.prototype 的 push / pop / shift / unshift / splice 以报告 splice) 在六个 observe 名称中任一个首次读取时一起执行; structured-clone.min.js
 * 在首次读取 structuredClone 时执行. 模块把函数赋给作为参数传入的容器对象而不是 Object / Array / 全局对象, 因此不会读到待构建的槽,
 * 脚本在首次读取前赋的值也得以保留. 首次读取之前, 上述五个数组方法是 Rhino 自身的实现 (只有观察者依赖包装, 而观察必然先读取其中一个名称).
 * 目标上已有的名称 (例如以后 Rhino 自带 structuredClone) 不做处理, 与原模块的检查一致. 模块经 [load] 加载 (引擎自己的 require, 编译结果进程内共享),
 * 经 [call] 调用 (由运行时处理 Context, 因为槽可能在任意线程被读取).
 */
class ScriptPolyfills(
    private val scope: VarScope,
    private val global: ScriptableObject,
    private val load: (moduleId: String) -> Any?,
    private val call: (function: Callable, thisObj: Scriptable, args: Array<Any?>) -> Any?,
) {

    private var observeFunctions: Map<String, Any?>? = null
    private var installingObserve = false
    private var structuredCloneFunction: Any? = null

    fun install() {
        val objectConstructor = ScriptableObject.getProperty(global, "Object") as ScriptableObject
        val arrayConstructor = ScriptableObject.getProperty(global, "Array") as ScriptableObject
        if (!objectConstructor.has(OBJECT_OBSERVE_NAMES.first(), objectConstructor)) {
            OBJECT_OBSERVE_NAMES.forEach { name -> LazyDataSlot.define(objectConstructor, name, ScriptableObject.EMPTY) { observe()["Object.$name"] } }
            if (!arrayConstructor.has(ARRAY_OBSERVE_NAMES.first(), arrayConstructor)) {
                ARRAY_OBSERVE_NAMES.forEach { name -> LazyDataSlot.define(arrayConstructor, name, ScriptableObject.EMPTY) { observe()["Array.$name"] } }
            }
        }
        if (!global.has(STRUCTURED_CLONE, global)) {
            LazyDataSlot.define(global, STRUCTURED_CLONE, ScriptableObject.EMPTY) { structuredClone() }
        }
    }

    @Synchronized
    private fun observe(): Map<String, Any?> {
        observeFunctions?.let { return it }
        check(!installingObserve) { "Recursive installation of the observe polyfills" }
        installingObserve = true
        try {
            val objectHolder = RhinoUtils.newNativeObject(scope)
            val arrayHolder = RhinoUtils.newNativeObject(scope)
            run(OBJECT_OBSERVE_MODULE, objectHolder)
            run(ARRAY_OBSERVE_MODULE, arrayHolder, objectHolder)
            return (OBJECT_OBSERVE_NAMES.map { "Object.$it" to valueOf(objectHolder, it) } + ARRAY_OBSERVE_NAMES.map { "Array.$it" to valueOf(arrayHolder, it) })
                .toMap()
                .also { observeFunctions = it }
        } finally {
            installingObserve = false
        }
    }

    @Synchronized
    private fun structuredClone(): Any? {
        structuredCloneFunction?.let { return it }
        val holder = RhinoUtils.newNativeObject(scope)
        run(STRUCTURED_CLONE_MODULE, holder)
        return valueOf(holder, STRUCTURED_CLONE).also { structuredCloneFunction = it }
    }

    /** Calls the function the module [moduleId] exports with the global object as `this`, as the old epilogue did. */
    private fun run(moduleId: String, vararg args: Any?) {
        val exported = load(moduleId) as? Callable ?: throw IllegalStateException("Module $moduleId does not export a function")
        call(exported, global, arrayOf(*args))
    }

    private fun valueOf(holder: NativeObject, name: String): Any? = holder.get(name, holder).takeUnless { it === Scriptable.NOT_FOUND } ?: Undefined.instance

    companion object {

        const val OBJECT_OBSERVE_MODULE = "object-observe-lite.min"
        const val ARRAY_OBSERVE_MODULE = "array-observe.min"
        const val STRUCTURED_CLONE_MODULE = "structured-clone.min"
        const val STRUCTURED_CLONE = "structuredClone"

        /** In the order the module assigns them. */
        val OBJECT_OBSERVE_NAMES = listOf("observe", "unobserve", "getNotifier", "deliverChangeRecords")
        val ARRAY_OBSERVE_NAMES = listOf("observe", "unobserve")

    }

}
