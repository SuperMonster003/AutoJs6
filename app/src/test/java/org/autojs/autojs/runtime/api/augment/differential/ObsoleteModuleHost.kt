package org.autojs.autojs.runtime.api.augment.differential

import org.autojs.autojs.rhino.TopLevelScope
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.Undefined
import java.io.File

/**
 * Loads the pre-6.6.0 `assets/modules/obsolete/__xxx__.js` modules into a plain JVM Rhino scope.
 *
 * Each module is a CommonJS factory (`module.exports = function (scriptRuntime, scope) { ... }`). The host
 * wraps the source in a function that supplies `module` and `exports`, calls the factory with a stub runtime and
 * the global object, and returns the exported module value. The few globals the old modules expected from
 * `init.js` (`global`, `util.ensureArrayType` / `ensureNumberType` / `getClassName`, `species`, `isNullish`)
 * are provided as JavaScript stubs that mirror the original definitions; modules that need more (Android
 * classes, the real runtime) are out of scope for this host and get their own stubs when they are added.
 *
 * zh-CN: 把 6.6.0 之前的 `__xxx__.js` 模块加载到纯 JVM 的 Rhino 作用域. 每个模块是 CommonJS 工厂函数, 宿主提供
 * `module` / `exports`, 以 stub 运行时与全局对象调用工厂并返回导出值. 旧模块依赖的少量 init.js 全局 (util 的几个
 * ensure 函数, species, isNullish) 以镜像原定义的 JavaScript stub 提供.
 */
internal class ObsoleteModuleHost(private val context: Context, val scope: TopLevelScope) {

    val global: ScriptableObject = scope.globalThis

    /** Stand-in for the ScriptRuntime argument of the factories; the first batch of modules never touches it. */
    private val runtimeStub: Scriptable = context.newObject(scope)

    init {
        scope.defineProperty("global", global, PERMANENT)
        context.evaluateString(scope, STUBS, "obsolete-stubs", 1, null)
    }

    /** Loads `__[name]__.js` and returns its exported value without defining it anywhere. */
    fun load(name: String): Any? {
        val file = File(OBSOLETE_DIR, "__${name}__.js")
        require(file.isFile) { "Obsolete module not found: ${file.absolutePath}" }
        val wrapper = context.evaluateString(
            scope,
            "(function (module, exports) {\n${file.readText()}\n})",
            file.name,
            0,
            null,
        ) as Callable
        val module = context.newObject(scope)
        val exports = context.newObject(scope)
        module.put("exports", module, exports)
        wrapper.call(context, scope, global, arrayOf(module, exports))
        val factory = module.get("exports", module)
        require(factory is Callable) { "Module $name did not export a factory function" }
        return factory.call(context, scope, global, arrayOf(runtimeStub, global))
    }

    /** Loads the module and defines it on the global object under [name], as `init.js` used to. */
    fun loadAndDefine(name: String): Any? = load(name).also { value ->
        global.defineProperty(name, value ?: Undefined.instance, PERMANENT)
    }

    companion object {

        val OBSOLETE_DIR: File = listOf(
            File("src/main/assets/modules/obsolete"),
            File("app/src/main/assets/modules/obsolete"),
        ).firstOrNull { it.isDirectory } ?: error("assets/modules/obsolete not found from ${File(".").absolutePath}")

        private val STUBS = """
            global.util = {
                ensureArrayType: function () {
                    for (var i = 0; i < arguments.length; i++) {
                        if (!Array.isArray(arguments[i])) {
                            throw TypeError("Param " + arguments[i] + " must be type of Array instead of " + species(arguments[i]));
                        }
                    }
                },
                ensureNumberType: function () {
                    for (var i = 0; i < arguments.length; i++) {
                        if (typeof arguments[i] !== "number") {
                            throw TypeError("Param " + arguments[i] + " must be type of number instead of " + species(arguments[i]));
                        }
                    }
                },
                getClassName: function (o) {
                    if (o === null || o === undefined) return String(o);
                    return (o.constructor && o.constructor.name) || typeof o;
                },
            };
            global.species = function species(o) {
                if (o === null) return "Null";
                if (o === undefined) return "Undefined";
                return Object.prototype.toString.call(o).slice(8, -1);
            };
            global.species.isObject = function (o) { return species(o) === "Object"; };
            global.isNullish = function isNullish(o) { return o === null || o === undefined; };
            // The engine registers `android` as a top-level package; a plain scope only knows java / org / com.
            global.android = Packages.android;
        """.trimIndent()

    }

}
