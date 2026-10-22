package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.bootstrap.ContinuationPrototype
import org.autojs.autojs.runtime.api.modules.generated.ScriptModules
import org.junit.Assert.assertTrue
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.autojs.autojs.runtime.api.Engines as ApiEngines

/**
 * Shared helpers of the roadmap P3.4 tests (the runtime modules app / autojs / device / engines / files / storages /
 * shell / shizuku / sysprops / settings / powerManager / tasks / workManager / threads / timers / continuation / plugins).
 *
 * These modules sit on host objects of the runtime (`ScriptRuntime.timers`, `.threads`, `.files`, ...) that need Android
 * to be constructed. Their surface does not: a module object only needs the host object as its prototype, and Rhino reads
 * its members by reflection. [prepareHost] therefore gives the JVM test runtime instances allocated without their
 * constructors, so the new modules and the current augment calls can be mounted side by side and their surfaces compared
 * ([describe]: names, `$` aliases, the order on the global object, attributes, kinds, prototype chains and the members
 * of each module object, without calling getters); behaviour that needs Android goes to the device cases ([exportForDevice]).
 *
 * zh-CN: P3.4 测试的共用辅助. 这些模块依赖运行时的宿主对象 (ScriptRuntime.timers / threads / files 等), 构造它们需要 Android; 模块表面却不需要: 模块对象只把宿主对象
 * 作为原型, Rhino 以反射读取其成员. 因此 [prepareHost] 给 JVM 测试运行时设置不经构造函数分配的实例, 新模块与现有 augment 调用可以并排挂载并比较表面 ([describe]:
 * 名称, $ 别名, 全局对象上的顺序, 特性, 种类, 原型链与各模块对象的成员, 不调用 getter); 需要 Android 的行为写入设备用例 ([exportForDevice]).
 */
internal object RuntimeModulesFixture {

    fun <T> allocate(type: Class<T>): T = DifferentialRhino.allocateWithoutConstructor(type)

    fun setField(target: Any, owner: Class<*>, name: String, value: Any?) {
        owner.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }

    /**
     * Gives [runtime] the host objects [fields] the P3.4 module surfaces read, each allocated without its constructor
     * (fields left null), the script thread, the prototype of `continuation` (a lazy property, unset in a runtime allocated
     * without its constructor) and an engine without execution config.
     */
    fun prepareHost(runtime: ScriptRuntime, fields: List<String> = CORE_FIELDS) {
        val type = ScriptRuntime::class.java
        fields.forEach { name -> setField(runtime, type, name, allocate(type.getDeclaredField(name).type)) }
        setField(runtime, type, "mThread", Thread.currentThread())
        setField(runtime, type, "js_mod_continuation\$delegate", lazyOf(ContinuationPrototype.create(runtime.topLevelScope)))
        // The engine of the runtime, without execution config: the engines module reads its tags when installed.
        val engine = allocate(RhinoJavaScriptEngine::class.java)
        setField(engine, ScriptEngine.AbstractScriptEngine::class.java, "mTags", ConcurrentHashMap<String, Any?>())
        setField(runtime.engines, ApiEngines::class.java, "mScriptEngine", engine)
    }

    /**
     * The runtime fields [prepareHost] sets by default. Host classes whose static initializer needs Android (`Device`)
     * cannot even be allocated on the JVM; their modules' surfaces are compared on the device only.
     */
    val CORE_FIELDS = listOf("timers", "threads", "engines", "events", "files")

    /**
     * Mounts the top-level modules [names] in the order of `ScriptModuleLayer.ORDER`: from the new layer when [newLayer],
     * otherwise each by [legacy] (its current augment call).
     */
    fun mount(rhino: DifferentialRhino, names: Collection<String>, newLayer: Boolean, legacy: (name: String, global: ScriptableObject, runtime: ScriptRuntime) -> Unit): ScriptModuleMounter? {
        val runtime = rhino.ktRuntime
        val global = rhino.ktScope.globalThis
        val mounter = if (newLayer) ScriptModuleMounter(ScriptModules, runtime, runtime.topLevelScope, global, order = ScriptModuleLayer.ORDER) else null
        ScriptModuleLayer.TOP_LEVEL_ORDER.filter { it in names }.forEach { name ->
            ScriptModuleLayer.mountOr(mounter, name) {
                check(!newLayer) { "Script module $name is not migrated to the new layer" }
                legacy(name, global, runtime)
            }
        }
        return mounter
    }

    /**
     * The surface of the global names [globals] (attributes and kind, plus their relative order in `Object.keys`) and of
     * the module objects at [paths] (`timers`, `files.batchRename`): prototype chain, `Object.keys`, and every own property
     * with its attributes and kind. Accessors are described without being called.
     */
    fun describe(side: DifferentialRhino, globals: List<String>, paths: List<String>): String = side.evalKt(
        """
        (function () {
            var out = [];
            var globals = ${globals.joinToString(", ", "[", "]") { "'$it'" }};
            var paths = ${paths.joinToString(", ", "[", "]") { "'$it'" }};
            function attrs(d) { return (d.enumerable ? 'E' : '-') + (d.writable ? 'W' : '-') + (d.configurable ? 'C' : '-') + (d.get || d.set ? 'A' : ''); }
            function tag(v) { return v === null ? 'null' : v === Object.prototype ? 'Object.prototype' : v === Function.prototype ? 'Function.prototype' : Object.prototype.toString.call(v); }
            function names(v) { return Object.getOwnPropertyNames(v).filter(function (k) { return ['length', 'arity', 'name'].indexOf(k) < 0; }); }
            function chain(v) {
                var c = [];
                for (var p = Object.getPrototypeOf(v), i = 0; p !== null && i < 6; p = Object.getPrototypeOf(p), i++) {
                    var t = tag(p);
                    if (t === '[object Object]' && p !== Object.prototype) t += '{' + names(p).sort().join(',') + '}';
                    if (t === '[object JavaObject]') t += '<' + String(p.getClass().getName()) + '>';
                    c.push(t);
                }
                return c.join(' > ');
            }
            function kind(d) {
                if (d.get || d.set) return 'accessor';
                var v = d.value;
                if (typeof v === 'function') {
                    var extra = names(v).map(function (k) { return k + ' ' + attrs(Object.getOwnPropertyDescriptor(v, k)); });
                    return 'function ' + v.name + (extra.length ? ' [' + extra.join(',') + ']' : '');
                }
                if (v === null) return 'null';
                return typeof v === 'object' ? tag(v) : typeof v + ' ' + String(v);
            }
            out.push('keys ' + Object.keys(globalThis).filter(function (k) { return globals.indexOf(k) >= 0; }).join(','));
            globals.forEach(function (n) {
                var d = Object.getOwnPropertyDescriptor(globalThis, n);
                out.push(n + ' ' + (d ? attrs(d) + ' ' + kind(d) : 'missing'));
            });
            paths.forEach(function (path) {
                var o = path.split('.').reduce(function (a, k) { return a[k]; }, globalThis);
                out.push(path + ' ' + typeof o + ' proto ' + chain(o));
                out.push(path + ' keys ' + Object.keys(o).join(','));
                names(o).forEach(function (k) {
                    out.push(path + '.' + k + ' ' + attrs(Object.getOwnPropertyDescriptor(o, k)) + ' ' + kind(Object.getOwnPropertyDescriptor(o, k)));
                });
            });
            return out.join('\n');
        })()
        """.trimIndent()
    ).toString()

    /** Evaluates [cases] on the "kt" side of [rhino] and fails with every case whose rendered result is not the expected one. */
    fun check(rhino: DifferentialRhino, cases: List<Pair<String, String>>) {
        val failures = cases.mapNotNull { (expression, expected) ->
            val actual = rhino.render(DifferentialRhino.Side.KT, expression)
            if (actual.key == expected) null else "$expression\n    expected $expected\n    actual   $actual"
        }
        assertTrue("Unexpected results:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private val exported = LinkedHashMap<String, LinkedHashMap<String, String>>()

    /**
     * Adds [cases] to `build/script-modules-device/<fileName>.json` for `device_eval.py --new-modules on --strict-errors`;
     * cases of one file run in the order they were added, in one script.
     */
    @Synchronized
    fun exportForDevice(fileName: String, cases: List<Pair<String, String>>) {
        val file = exported.getOrPut(fileName) { LinkedHashMap() }
        cases.forEach { (expression, expected) -> file[expression] = expected }
        val dir = File("build/script-modules-device").apply { mkdirs() }
        val entries = file.entries.joinToString(",\n") { (expression, expected) ->
            "  {\"expression\": ${json(expression)}, \"js\": null, \"kt\": ${json(expected)}, \"reason\": \"new script module layer (P3.4)\"}"
        }
        File(dir, "$fileName.json").writeText("[\n$entries\n]\n", Charsets.UTF_8)
    }

    private fun json(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when {
                c == '"' || c == BACKSLASH -> append(BACKSLASH).append(c)
                c == '\n' -> append(BACKSLASH).append('n')
                c < ' ' || c.code > 0x7e -> append(BACKSLASH).append("u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    private const val BACKSLASH = '\\'

}
