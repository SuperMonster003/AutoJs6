package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.rhino.ProxyObject.Companion.AUGMENTED_CUSTOM_TO_STRING_KEY
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.LambdaConstructor
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.NativeJavaClass
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.SerializableCallable
import org.mozilla.javascript.SerializableConstructable
import org.mozilla.javascript.VarScope
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.Supplier
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * Mounts the modules of a [ScriptModuleRegistry] on the global object of one script runtime (roadmap D4 / T3).
 *
 * A top-level module is mounted on demand: every name of the module object (name, aliases, `$` forms) and every
 * member it binds on the global object becomes a lazy data property ([LazyDataSlot]) with its final attributes, so
 * `Object.keys(global)`, `hasOwnProperty`, `in` and property descriptors look the same as with eager mounting, and
 * the module object is built on the first read of any of them (all names then hold the same object). Global getters
 * are accessor-like properties that only create the Kotlin module instance. `@ScriptModule(eager = true)` modules
 * are built at install. Sub-modules (`@ScriptModule(parent = ...)`) are built and bound together with their parent
 * module object, in the order of their paths (`parent.name`) in [order] (`ScriptModuleLayer.ORDER`, appendix C Q8;
 * sub-modules not listed follow in registry order); their global members are installed with the top-level module.
 *
 * Within a module object: aliases of a function share one function object, the prototypes of `@ScriptPrototype`
 * (a list is a chain) come first and an event emitter object ends the chain when `emitter = true`, a FUNCTIONAL
 * `toString` adds the hidden marker `__augmented_custom_to_string__`, and everything a member throws goes through
 * [ScriptErrorMapper]. Members are bound in the order of `Augmentable.assign` (data properties, functions, getters, Java
 * classes, then sub-modules; P2.1 corrected the first ones, which came after the functions), global members in the
 * same order.
 *
 * zh-CN: 把注册表中的模块挂载到一个脚本运行时的全局对象上 (D4 / T3). 顶层模块按需挂载: 模块对象的每个名称 (名称, 别名, `$` 形式) 与它挂到全局对象上的
 * 每个成员都定义为带最终特性的惰性数据属性 (LazyDataSlot), Object.keys(global), hasOwnProperty, in 与属性描述符与急切挂载时相同, 首次读取其中任一个时
 * 构建模块对象 (此后所有名称指向同一对象). 全局 getter 是只创建 Kotlin 模块实例的访问器式属性. eager 模块在安装时构建. 子模块随父模块对象一起构建并挂载,
 * 顺序按其路径 (`父.名称`) 在 [order] 中的位置 (ScriptModuleLayer.ORDER, 附录 C Q8; 未列出的子模块按注册表顺序排在后面), 其全局成员随顶层模块一起安装. 模块对象内: 函数的别名共用一个函数对象, @ScriptPrototype 的原型 (列表即原型链) 在前, emitter = true 时事件发射器对象
 * 位于链尾, FUNCTIONAL 的 toString 附加隐藏标记 `__augmented_custom_to_string__`, 成员抛出的一切经 ScriptErrorMapper 处理.
 */
class ScriptModuleMounter @JvmOverloads constructor(
    private val registry: ScriptModuleRegistry,
    private val runtime: ScriptRuntime?,
    private val scope: VarScope,
    private val global: ScriptableObject,
    private val emitterFactory: EmitterFactory = EmitterFactory.DEFAULT,
    /** Receives the debug entries of [ScriptErrorMapper]: the runtime's script console unless a test passes its own. */
    private val debugConsole: ScriptErrorMapper.DebugConsole? = runtime?.let { r -> ScriptErrorMapper.DebugConsole { level, text -> r.console.println(level, text) } },
    /** Module paths in mounting order (`ScriptModuleLayer.ORDER`); it orders the sub-modules of a module object. */
    private val order: List<String> = emptyList(),
) {

    /** Creates the event emitter object appended to the prototype chain of `emitter = true` modules. */
    fun interface EmitterFactory {

        fun create(runtime: ScriptRuntime?, scope: VarScope): Scriptable

        companion object {

            private val METHOD_NAMES = ConcurrentHashMap<Class<*>, List<String>>()
            private val WORD = Regex("\\w+")

            /** A plain object with the public methods of `runtime.events.emitter()` (as `events.__asEmitter__`). */
            val DEFAULT = EmitterFactory { runtime, scope ->
                emitterObject(requireNotNull(runtime) { "An emitter module needs a ScriptRuntime" }.events.emitter(), scope)
            }

            /** A plain object whose functions call the public methods of [emitter] through Rhino's Java bridge. */
            fun emitterObject(emitter: Any, scope: VarScope): Scriptable {
                val obj = RhinoUtils.newNativeObject(scope)
                val names = METHOD_NAMES.getOrPut(emitter.javaClass) {
                    emitter.javaClass.declaredMethods.filter { Modifier.isPublic(it.modifiers) && WORD.matches(it.name) }.map { it.name }.distinct()
                }
                names.forEach { name ->
                    obj.defineProperty(name, LambdaFunction(scope, name, 0, SerializableCallable { cx, s, _, args ->
                        val target = Context.javaToJS(emitter, s) as Scriptable
                        (ScriptableObject.getProperty(target, name) as Callable).call(cx, s, target, args)
                    }, false), ScriptableObject.EMPTY)
                }
                return obj
            }

        }

    }

    private val lock = Any()
    private val installed = LinkedHashMap<String, Node>()

    /** Installs the top-level module bound to [name] and its sub-modules; false when the registry has no such module. */
    fun install(name: String): Boolean {
        val binding = registry.find(name) ?: return false
        install(binding)
        return true
    }

    /** Installs every top-level module of the registry, in registry order. */
    fun installAll() {
        registry.bindings.filter { it.spec.parent == null }.forEach(::install)
    }

    fun install(binding: ScriptModuleBinding<*>) {
        require(binding.spec.parent == null) { "${binding.spec.className} is a sub-module; install its top-level module" }
        val node = Node(binding, null)
        installed[binding.spec.name] = node
        if (node.spec.eager) node.moduleObject()
        node.installGlobalMembers()
        node.spec.names.forEach { name ->
            if (node.isBuilt) {
                global.defineProperty(name, node.moduleObject(), node.spec.attributes)
            } else {
                LazyDataSlot.define(global, name, node.spec.attributes) { node.moduleObject() }
            }
        }
        node.descendants().forEach { it.installGlobalMembers() }
    }

    /** The module object of the installed top-level module [name], built if needed (for host code holding a module). */
    fun moduleObject(name: String): ScriptableObject? = installed[name]?.moduleObject()

    /** Whether the installed top-level module [name] has been built. */
    fun isBuilt(name: String): Boolean = installed[name]?.isBuilt == true

    /** Extends the JavaScript built-in objects with the modules of this registry declaring `@ScriptModule(builtIn)`. */
    val builtIns: ScriptBuiltInExtender by lazy { ScriptBuiltInExtender(registry, runtime, scope, global, debugConsole) }

    /** Runs [block] for [member] (as `base64.encode`) of the top-level module [module], mapping what it throws (T5). */
    private inline fun <T> guarded(member: String, module: String, block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        throw ScriptErrorMapper.map(e, member, module, debugConsole)
    }

    @Suppress("UNCHECKED_CAST")
    private inner class Node(anyBinding: ScriptModuleBinding<*>, val parent: Node?) {

        val binding = anyBinding as ScriptModuleBinding<Any>
        val spec = binding.spec
        val path: String = parent?.let { "${it.path}.${spec.name}" } ?: spec.name

        /** The top-level module, which the debug printing switches name (appendix C Q4). */
        val topLevel: String = parent?.topLevel ?: spec.name
        val children: List<Node> = registry.childrenOf(spec.className).map { Node(it, this) }.sortedBy { child ->
            order.indexOf(child.path).let { if (it < 0) Int.MAX_VALUE else it }
        }

        @Volatile
        private var instance: Any? = null

        @Volatile
        private var built: ScriptableObject? = null

        private var building = false
        private val globalValues = HashMap<String, Any?>()

        val isBuilt get() = built != null

        fun descendants(): List<Node> = children.flatMap { listOf(it) + it.descendants() }

        fun instance(): Any = instance ?: synchronized(lock) {
            instance ?: guarded(path, topLevel) { binding.create(runtime) }.also { instance = it }
        }

        /** The module object; building it builds the whole tree from the top-level module down. */
        fun moduleObject(): ScriptableObject {
            built?.let { return it }
            synchronized(lock) {
                built?.let { return it }
                generateSequence(this) { it.parent }.last().build()
                return checkNotNull(built) { "Script module $path was not built with its parent" }
            }
        }

        private fun build(): ScriptableObject {
            check(!building) { "Recursive initialization of script module $path" }
            building = true
            try {
                val module = instance()
                val obj = newModuleObject(module)
                applyPrototypes(obj, module)
                spec.accessors.filter { !it.getter }.forEach { a ->
                    val value = guarded("$path.${a.name}", topLevel) { binding.get(module, a.id, scope) }
                    if (a.onModule) obj.defineProperty(a.name, value, a.attributes)
                    if (a.onGlobal) globalValues[a.name] = value
                }
                spec.functions.forEach { f -> bindFunction(obj, module, f) }
                spec.accessors.filter { it.getter && it.onModule }.forEach { a -> obj.defineProperty(a.name, getter({ module }, a), setter({ module }, a), a.attributes) }
                spec.javaClasses.forEach { j ->
                    val javaClass = NativeJavaClass(scope, j.type)
                    if (j.onModule) obj.defineProperty(j.name, javaClass, j.attributes)
                    if (j.onGlobal) globalValues[j.name] = javaClass
                }
                children.forEach { child ->
                    val childObject = child.build()
                    child.spec.names.forEach { name -> obj.defineProperty(name, childObject, child.spec.attributes) }
                }
                built = obj
                return obj
            } finally {
                building = false
            }
        }

        private fun bindFunction(obj: ScriptableObject, module: Any, f: ScriptFunctionSpec) {
            val function = function(module, f, f.name, f.length)
            (listOf(f.name) + f.aliases).forEach { name ->
                if (f.onModule) obj.defineProperty(name, function, f.attributes)
                if (f.onGlobal) globalValues[name] = function
            }
            if (f.functionalToString) {
                obj.defineProperty(AUGMENTED_CUSTOM_TO_STRING_KEY, function(module, f, AUGMENTED_CUSTOM_TO_STRING_KEY, 0), ScriptableObject.DONTENUM or ScriptableObject.PERMANENT)
            }
        }

        private fun function(module: Any, f: ScriptFunctionSpec, name: String, length: Int): LambdaFunction {
            val member = "$path.${f.name}"
            return LambdaFunction(scope, name, length, SerializableCallable { cx, s, thisObj, args ->
                guarded(member, topLevel) { binding.call(module, f.id, cx, s, thisObj, args) }
            }, false)
        }

        private fun getter(module: () -> Any, a: ScriptAccessorSpec): Supplier<Any?> {
            val member = "$path.${a.name}"
            return Supplier { guarded(member, topLevel) { binding.get(module(), a.id, scope) } }
        }

        private fun setter(module: () -> Any, a: ScriptAccessorSpec): Consumer<Any?>? {
            if (!a.writable) return null
            val member = "$path.${a.name}"
            return Consumer { value -> guarded(member, topLevel) { binding.set(module(), a.id, value) } }
        }

        private fun newModuleObject(module: Any): ScriptableObject {
            val invoke = SerializableCallable { cx, s, thisObj, args ->
                guarded(path, topLevel) { binding.call(module, ScriptModuleBinding.INVOKE, cx, s, thisObj, args) }
            }
            val construct = SerializableConstructable { cx, _, s, args ->
                guarded("new $path", topLevel) { binding.call(module, ScriptModuleBinding.CONSTRUCT, cx, s, null, args) as Scriptable }
            }
            return when {
                spec.invokeLength >= 0 && spec.constructLength >= 0 -> LambdaConstructor(scope, spec.name, spec.invokeLength, invoke, construct)
                spec.constructLength >= 0 -> LambdaConstructor(scope, spec.name, spec.constructLength, construct)
                spec.invokeLength >= 0 -> LambdaFunction(scope, spec.name, spec.invokeLength, invoke, false)
                else -> RhinoUtils.newNativeObject(scope)
            }
        }

        /** Module object -> declared prototypes (nearest first) -> emitter object -> the original prototype. */
        private fun applyPrototypes(obj: ScriptableObject, module: Any) {
            val declared = guarded(path, topLevel) { binding.prototype(module) }
            val links = when {
                declared == null -> emptyList()
                declared is List<*> && declared !is Scriptable -> declared.filterNotNull()
                else -> listOf(declared)
            }.map { it as? Scriptable ?: RhinoScriptRuntime.toObject(scope, it) }.let { links ->
                if (spec.emitter) links + emitterFactory.create(runtime, scope) else links
            }
            if (links.isEmpty()) return
            val original = obj.prototype
            links.forEachIndexed { index, link -> link.prototype = links.getOrNull(index + 1) ?: original }
            obj.prototype = links.first()
        }

        /** Binds the members placed on the global object, lazily unless the module object is already built. */
        fun installGlobalMembers() {
            spec.accessors.filter { !it.getter && it.onGlobal }.forEach { defineGlobal(it.name, it.attributes) }
            spec.functions.filter { it.onGlobal }.forEach { f -> (listOf(f.name) + f.aliases).forEach { defineGlobal(it, f.attributes) } }
            spec.accessors.filter { it.getter && it.onGlobal }.forEach { a ->
                global.defineProperty(a.name, getter({ instance() }, a), setter({ instance() }, a), a.attributes)
            }
            spec.javaClasses.filter { it.onGlobal }.forEach { defineGlobal(it.name, it.attributes) }
        }

        private fun defineGlobal(name: String, attributes: Int) {
            if (isBuilt) {
                global.defineProperty(name, globalValues[name], attributes)
            } else {
                LazyDataSlot.define(global, name, attributes) {
                    moduleObject()
                    globalValues[name]
                }
            }
        }

    }

}
