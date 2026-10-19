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
 * the global object, and returns the exported module value. The globals the old modules expected from the
 * pre-6.6.0 `init.js` (`global`, `util.ensureArrayType` / `ensureNumberType` / `getClassName`, `species`,
 * `isNullish`, `isObject`, `isJavaClass`, the imported classes `TimeUnit` / `StandardCharsets` / `ArrayUtils`, the
 * `runtime` object) are provided as JavaScript stubs that mirror the original definitions. A module that needs
 * another module loads it with [loadAndDefine] first (mime calls `util.format`, so its test loads the obsolete
 * util module). Members of the ScriptRuntime argument are set per test with [setRuntimeProperty].
 *
 * Android classes are not stubbed here: the JVM fakes in `app/src/test/java/android/` (Base64, Point) replace the
 * mockable android.jar classes for both sides, so the obsolete module and the Kotlin module run on the same
 * implementation and a difference can only come from the module code. Classes without a fake stay "not mocked";
 * [DifferentialRhino.render] reports such calls as `unavailable(jvm)`.
 *
 * zh-CN: 把 6.6.0 之前的 `__xxx__.js` 模块加载到纯 JVM 的 Rhino 作用域. 每个模块是 CommonJS 工厂函数, 宿主提供
 * `module` / `exports`, 以 stub 运行时与全局对象调用工厂并返回导出值. 旧模块依赖的 init.js 全局 (util 的几个函数,
 * species, isNullish, isObject, isJavaClass, 导入的 TimeUnit / StandardCharsets / ArrayUtils, runtime) 以镜像原定义的
 * JavaScript stub 提供; 依赖其他模块的先用 [loadAndDefine] 加载 (mime 调用 util.format). Android 类不在这里遮蔽,
 * 由 `app/src/test/java/android/` 的 JVM fake (Base64, Point) 同时替换两侧, 使差异只可能来自模块代码; 没有 fake 的类仍
 * "not mocked", 由 [DifferentialRhino.render] 记为 `unavailable(jvm)`.
 */
internal class ObsoleteModuleHost(private val context: Context, val scope: TopLevelScope) {

    val global: ScriptableObject = scope.globalThis

    /** Stand-in for the ScriptRuntime argument of the factories; members are added with [setRuntimeProperty]. */
    private val runtimeStub: Scriptable = context.newObject(scope)

    init {
        scope.defineProperty("global", global, PERMANENT)
        context.evaluateString(scope, STUBS, "obsolete-stubs", 1, null)
    }

    /** Sets `scriptRuntime.[name]` as the factories see it; Java values are wrapped as the engine would. */
    fun setRuntimeProperty(name: String, value: Any?) {
        runtimeStub.put(name, runtimeStub, Context.javaToJS(value, scope))
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
                // The obsolete util module delegated to JavaUtils.getClassName (the Java class name of the value).
                getClassName: function (o) {
                    return String(Packages.org.autojs.autojs.util.JavaUtils.getClassName(o));
                },
            };
            global.species = function species(o) {
                if (o === null) return "Null";
                if (o === undefined) return "Undefined";
                return Object.prototype.toString.call(o).slice(8, -1);
            };
            global.species.isObject = function (o) { return species(o) === "Object"; };
            global.isNullish = function isNullish(o) { return o === null || o === undefined; };
            global.isObject = function isObject(o) { return species(o) === "Object"; };
            global.isJavaClass = function isJavaClass(o) { return species(o) === "JavaClass"; };
            // Classes init.js imported into the global scope.
            global.TimeUnit = java.util.concurrent.TimeUnit;
            global.StandardCharsets = java.nio.charset.StandardCharsets;
            global.ArrayUtils = {
                // Mirrors the removed Java ArrayUtils.jsBytesToByteArray: Number.byteValue() per element, 0 for non-numbers.
                jsBytesToByteArray: function (values) {
                    var bytes = java.lang.reflect.Array.newInstance(java.lang.Byte.TYPE, values.length);
                    for (var i = 0; i < values.length; i++) {
                        if (typeof values[i] === "number") bytes[i] = java.lang.Double.valueOf(values[i]).byteValue();
                    }
                    return bytes;
                },
            };
            // The colors module needs android.graphics.Color, which has no JVM fake; s13n.color cases that reach it
            // are unavailable on the Kotlin side as well.
            global.Color = function Color() { throw Error("Color is not available on the JVM"); };
            // init.js ran with the ScriptRuntime as `runtime`; util.inspect compares values with its top-level scope.
            global.runtime = { topLevelScope: global };
            global.UiObjectCollection = {};
        """.trimIndent()

    }

}
