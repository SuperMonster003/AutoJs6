package org.autojs.autojs.runtime.api.modules.bootstrap

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino.Side
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.ScriptableObject

/**
 * Roadmap P2.5: the observe and structuredClone polyfills installed on first access ([ScriptPolyfills], with the modules
 * as shipped now) against the old epilogue, which ran the previous modules for every script.
 */
class ScriptPolyfillsTest {

    private lateinit var sides: BootstrapSides
    private val loaded = mutableListOf<String>()

    @Before
    fun setUp() {
        sides = BootstrapSides()
        sides.both("var timers = []; var setTimeout = function (f, millis) { timers.push(millis); };")
        // The old epilogue: object observe, then array observe, then structured clone, each called with the global object.
        // zh-CN: 原 epilogue: 先 object observe, 再 array observe, 最后 structured clone, 都以全局对象调用.
        listOf("object-observe-lite.min.js", "array-observe.min.js").forEach { name ->
            val f = sides.module(sides.jsScope, sides.original(name)) as Callable
            f.call(sides.cx, sides.jsScope, sides.jsGlobal, arrayOf(sides.jsGlobal))
        }
        val clone = sides.module(sides.jsScope, sides.asset("structured-clone.min.js")) as Callable
        clone.call(sides.cx, sides.jsScope, sides.jsGlobal, arrayOf(sides.jsGlobal))
        polyfills(sides.ktScope).install()
    }

    @After
    fun tearDown() = sides.close()

    private fun polyfills(scope: TopLevelScope) = ScriptPolyfills(
        scope,
        scope.globalThis as ScriptableObject,
        load = { id ->
            loaded += id
            sides.module(scope, sides.asset("$id.js"))
        },
        call = { f, thisObj, args -> f.call(sides.cx, scope, thisObj, args) },
    )

    private fun pending(owner: String, name: String): Boolean {
        val target = if (owner == "global") sides.ktGlobal else ScriptableObject.getProperty(sides.ktGlobal, owner) as ScriptableObject
        return LazyDataSlot.isPending(target, name)
    }

    @Test
    fun namesAreVisibleWithoutInstalling() {
        sides.assertSame(
            listOf(
                "Object.keys(Object).join()",
                "Object.keys(Array).join()",
                "Object.keys(global).indexOf('structuredClone') === Object.keys(global).length - 1",
                "['observe', 'unobserve', 'getNotifier', 'deliverChangeRecords'].map(function (k) { return [k in Object, Object.prototype.hasOwnProperty.call(Object, k), Object.prototype.propertyIsEnumerable.call(Object, k)]; })",
                "['observe', 'unobserve'].map(function (k) { return [k in Array, Array.hasOwnProperty(k)]; })",
                "['structuredClone' in global, global.hasOwnProperty('structuredClone')]",
                "Object.getOwnPropertyNames(Object).join()",
            )
        )
        assertTrue(pending("Object", "observe") && pending("Array", "observe") && pending("global", "structuredClone"))
        assertEquals(emptyList<String>(), loaded)
        // Until the polyfill is installed, the array methods are Rhino's own (the old epilogue wrapped them for every script).
        // zh-CN: 安装之前数组方法是 Rhino 自身的 (原 epilogue 为每个脚本包装它们).
        sides.assertRenders(Side.KT, listOf("[Array.prototype.push.name, Array.prototype.splice.hasOwnProperty('_original')]" to "[\"push\",false]"))
        sides.assertRenders(Side.JS, listOf("[Array.prototype.push.name, Array.prototype.splice.hasOwnProperty('_original')]" to "[\"h\",true]"))
    }

    @Test
    fun firstReadInstallsBothObserveModulesOnce() {
        sides.assertSame(
            listOf(
                "['observe', 'unobserve', 'getNotifier', 'deliverChangeRecords'].map(function (k) { var d = Object.getOwnPropertyDescriptor(Object, k); return [typeof d.value, d.value.length, d.value.name, d.enumerable, d.writable, d.configurable]; })",
                "['observe', 'unobserve'].map(function (k) { var d = Object.getOwnPropertyDescriptor(Array, k); return [typeof d.value, d.value.length, d.enumerable, d.writable, d.configurable]; })",
                "['push', 'pop', 'shift', 'unshift', 'splice'].map(function (k) { var f = Array.prototype[k]; return [f.name, f.length, typeof f._original, Object.prototype.propertyIsEnumerable.call(f, '_original'), Object.prototype.propertyIsEnumerable.call(Array.prototype, k)]; })",
                "Array.unobserve === Object.unobserve",
                "Object.keys(Object).join()",
            )
        )
        sides.assertRenders(
            Side.KT,
            listOf(
                "(function () { var d = Object.getOwnPropertyDescriptor(Object, 'observe'); return [typeof d.value, d.value.length, d.enumerable, d.writable, d.configurable, Array.prototype.push.name]; })()" to "[\"function\",3,true,true,true,\"h\"]",
            )
        )
        assertEquals(listOf(ScriptPolyfills.OBJECT_OBSERVE_MODULE, ScriptPolyfills.ARRAY_OBSERVE_MODULE), loaded)
        assertFalse(pending("Object", "observe"))
        assertTrue("structuredClone stays pending", pending("global", "structuredClone"))
    }

    @Test
    fun observingRecordsSplices() = sides.assertRenders(
        Side.KT,
        listOf(
            "(function () { var log = []; var h = function (cs) { cs.forEach(function (c) { log.push(c.type + ':' + c.index + ':' + c.addedCount); }); }; var a = [1]; Array.observe(a, h); a.push(2); a.splice(0, 1); Object.deliverChangeRecords(h); return log.join(); })()" to "\"splice:1:1,splice:0:0\"",
            "(function () { var c = structuredClone({ a: [1, { b: 2 }] }); return [c.a[1].b, typeof structuredClone]; })()" to "[2,\"function\"]",
        )
    )

    @Test
    fun observingBehavesAsBefore() = sides.assertSame(
        listOf(
            """
            (function () {
                var log = [];
                var handler = function (changes) { changes.forEach(function (c) { log.push([c.type, c.name, c.index, c.addedCount, c.removed && c.removed.length, c.oldValue].join(':')); }); };
                var arr = [1];
                Array.observe(arr, handler);
                arr.push(2, 3);
                arr.pop();
                arr.unshift(0);
                arr.splice(1, 1, 'x', 'y');
                arr[0] = 9;
                Object.deliverChangeRecords(handler);
                Array.unobserve(arr, handler);
                arr.push(4);
                Object.deliverChangeRecords(handler);
                return log.join(' | ') + ' / ' + JSON.stringify(arr) + ' / ' + timers.join();
            })()
            """.trimIndent(),
            """
            (function () {
                var log = [];
                var handler = function (changes) { changes.forEach(function (c) { log.push(c.type + ':' + c.name + ':' + c.oldValue); }); };
                var o = { a: 1 };
                Object.observe(o, handler);
                o.a = 2;
                o.b = 3;
                delete o.a;
                Object.getNotifier(o).notify({ type: 'update', name: 'custom', oldValue: 0 });
                Object.deliverChangeRecords(handler);
                var accepted = [];
                Object.observe(o, function (cs) { accepted.push(cs.length); }, ['delete']);
                o.c = 1;
                delete o.c;
                Object.deliverChangeRecords(handler);
                return log.join(' | ');
            })()
            """.trimIndent(),
            "Object.observe(1, function () {})",
            "Object.observe({}, 1)",
            "Array.observe([])",
        )
    )

    @Test
    fun structuredCloneInstallsOnItsOwnFirstRead() {
        sides.assertSame(
            listOf(
                "(function () { var d = Object.getOwnPropertyDescriptor(global, 'structuredClone'); return [typeof d.value, d.value.length, d.value.name, d.value.hasOwnProperty('prototype'), d.enumerable, d.writable, d.configurable]; })()",
                "(function () { var src = { a: [1, { b: 2 }], d: new Date(5), m: new Map([[1, { c: 3 }]]), s: new Set([1]), r: /x/gi }; src.self = src; var c = structuredClone(src); return [c !== src, c.a !== src.a, JSON.stringify(c.a), c.d.getTime(), c.m.get(1).c, c.m.get(1) !== src.m.get(1), c.s.has(1), String(c.r), c.self === c]; })()",
                "structuredClone(function () {})",
                "structuredClone(1)",
            )
        )
        assertEquals(listOf(ScriptPolyfills.STRUCTURED_CLONE_MODULE), loaded)
        assertTrue("the observe names stay pending", pending("Object", "observe"))
    }

    @Test
    fun aNameAssignedBeforeTheFirstReadKeepsItsValue() {
        sides.kt("Object.observe = 1; structuredClone = 'mine';")
        sides.assertRenders(
            Side.KT,
            listOf(
                "[Object.observe, typeof Object.unobserve, typeof Array.observe, structuredClone]" to "[1,\"function\",\"function\",\"mine\"]",
                "Object.keys(Object).join()" to "\"observe,unobserve,getNotifier,deliverChangeRecords\"",
            )
        )
        assertEquals(listOf(ScriptPolyfills.OBJECT_OBSERVE_MODULE, ScriptPolyfills.ARRAY_OBSERVE_MODULE), loaded)
    }

    @Test
    fun theModulesStillInstallWhenRequiredDirectly() {
        // A script may require('object-observe-lite.min') (d.ts lists the name) and call it without arguments: a no-op once
        // Object.observe exists, as before; in a scope without the polyfill it installs on Object and Array.
        // zh-CN: 脚本可能 require('object-observe-lite.min') (d.ts 列出了该名称) 并无参调用: Object.observe 已存在时与原来一样不做任何事;
        // 在没有 polyfill 的作用域中则安装到 Object 与 Array 上.
        sides.kt("var beforeValues = [Object.observe, Array.observe, Array.prototype.push];")
        listOf("object-observe-lite.min.js", "array-observe.min.js").forEach { name ->
            (sides.module(sides.ktScope, sides.asset(name)) as Callable).call(sides.cx, sides.ktScope, sides.ktGlobal, emptyArray())
        }
        assertEquals("true,true,true", sides.kt("[Object.observe === beforeValues[0], Array.observe === beforeValues[1], Array.prototype.push === beforeValues[2]].join()"))

        val scope = sides.freshScope()
        sides.eval(scope, "var setTimeout = function () {};")
        listOf("object-observe-lite.min.js", "array-observe.min.js").forEach { name ->
            (sides.module(scope, sides.asset(name)) as Callable).call(sides.cx, scope, scope.globalThis, emptyArray())
        }
        assertEquals(
            "function,function,function,h,observe,unobserve,getNotifier,deliverChangeRecords",
            sides.eval(scope, "[typeof Object.observe, typeof Object.deliverChangeRecords, typeof Array.observe, Array.prototype.push.name, Object.keys(Object)].join()"),
        )
    }

    @Test
    fun anExistingNameIsLeftAlone() {
        val scope = sides.freshScope()
        sides.eval(scope, "Object.observe = function native() {}; var structuredClone = function nativeClone() {};")
        val names = mutableListOf<String>()
        ScriptPolyfills(scope, scope.globalThis as ScriptableObject, load = { names += it; null }, call = { _, _, _ -> null }).install()
        assertEquals("native,false,nativeClone", sides.eval(scope, "[Object.observe.name, 'observe' in Array, structuredClone.name].join()"))
        assertEquals(emptyList<String>(), names)
    }

}
