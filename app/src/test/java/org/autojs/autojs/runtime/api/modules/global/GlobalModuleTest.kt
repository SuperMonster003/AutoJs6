package org.autojs.autojs.runtime.api.modules.global

import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Scale
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.global.Classes
import org.autojs.autojs.runtime.api.augment.global.GlobalClasses
import org.autojs.autojs.runtime.api.augment.global.Legacy
import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.autojs.autojs.runtime.api.modules.ScriptModuleLayer
import org.autojs.autojs.runtime.api.modules.ScriptModuleMounter
import org.autojs.autojs.runtime.api.modules.generated.ScriptModules
import org.autojs.autojs6.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import org.autojs.autojs.runtime.api.modules.global.CoordinateScale as CurrentCoordinateScale

/**
 * Roadmap P3.0: `global` and `legacy` on the new script module layer beyond the differential test against the obsolete
 * JS (`GlobalModuleDifferentialTest`): the surface against the current Kotlin modules in the same JVM (names and their
 * order on the global object, attributes, kinds, the name lookup prototype and every Java class name; `length` may
 * differ, appendix C Q9), the Java class table against `Classes`, the pure rules (sleep range, wait policy, coordinate
 * conversion) against the current `Global` / `CoordinateScale` with the new error types, and the members without obsolete
 * JS. The checked cases and the cases that need Android ([deviceOnlyCases]) go to
 * `build/script-modules-device/GlobalModuleTest.json` for `device_eval.py --new-modules on --strict-errors`.
 *
 * zh-CN: P3.0: 新层 global 与 legacy 在旧 JS 差分 (GlobalModuleDifferentialTest) 之外的测试: 与现有 Kotlin 模块在同一 JVM 中比较表面 (全局名称及顺序, 特性, 种类,
 * 名称查找原型与每个 Java 类名; length 可不同, 附录 C Q9), Java 类表对照 Classes, 纯规则 (sleep 范围, wait 轮询计划, 坐标换算) 对照现有 Global / CoordinateScale
 * 并采用新错误类型, 以及没有旧 JS 的成员. 用例与需要 Android 的用例 ([deviceOnlyCases]) 写入 build/script-modules-device/GlobalModuleTest.json.
 */
class GlobalModuleTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        prepare(rhino.ktRuntime)
        mountInOrder(rhino.ktScope.globalThis, rhino.ktRuntime, newLayer = true)
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun check(cases: List<Pair<String, String>>, export: Boolean = true) {
        val failures = cases.mapNotNull { (expression, expected) ->
            val actual = rhino.render(DifferentialRhino.Side.KT, expression)
            if (actual.key == expected) null else "$expression\n    expected $expected\n    actual   $actual"
        }
        assertTrue("Unexpected results:\n" + failures.joinToString("\n"), failures.isEmpty())
        if (export) exportForDevice(cases)
    }

    /** Names, order, attributes and kinds against the current modules; the Java class names read through the prototype. */
    @Test
    fun surfaceMatchesTheCurrentModules() {
        val actual = describe(rhino)
        val expected = ScriptModuleExpectations.obsolete("describe") {
            DifferentialRhino().use { current ->
                prepare(current.ktRuntime)
                mountInOrder(current.ktScope.globalThis, current.ktRuntime, newLayer = false)
                // Documented: Image is an alias of ImageWrapper; the current Classes.Image is the class of a KClass value
                // (ROADMAP appendix E, P3.0).
                describe(current).replace(Regex("(?<=;Image function )\\[JavaClass [\\w.]+]"), "[JavaClass ${ImageWrapper::class.java.name}]")
            }
        }
        assertEquals(expected.replace(';', '\n'), actual.replace(';', '\n'))
    }

    /** [DESCRIBE] plus the attributes of the getters, read from their slots without calling them (they need a display). */
    private fun describe(side: DifferentialRhino): String {
        val global = side.ktScope.globalThis
        val getters = GETTERS.joinToString(";") { name ->
            val attributes = global.getAttributes(name)
            "$name " + (if (attributes and ScriptableObject.DONTENUM == 0) "E" else "-") +
                (if (attributes and ScriptableObject.READONLY == 0) "W" else "-") +
                (if (attributes and ScriptableObject.PERMANENT == 0) "C" else "-")
        }
        return side.evalKt(DESCRIBE).toString() + ";" + getters
    }

    /** Installing global and legacy builds nothing; the members are pending global slots until read. */
    @Test
    fun installingBuildsNothing() {
        val global = rhino.ktScope.globalThis
        listOf("TODO", "wait", "cXy", "isObjectSpecies").forEach { assertTrue(it, LazyDataSlot.isPending(global, it)) }
    }

    /** `GlobalJavaClasses` lists exactly the fields of the current `Classes`, each with the same class. */
    @Test
    fun javaClassesMatchTheCurrentClasses() {
        val current = Classes()
        val fields = Classes::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        assertEquals(156, fields.size)
        assertEquals(fields.map { it.name }.sorted(), GlobalJavaClasses.NAMES.sorted())
        fields.forEach { field ->
            // Documented alias (appendix E, P3.0): the current field holds the class of a KClass value.
            val type = if (field.name == "Image") ImageWrapper::class.java else (field.get(current) as KClass<*>).java
            assertEquals(field.name, type, GlobalJavaClasses.classOf(field.name))
        }
        assertTrue(KClass::class.java.isAssignableFrom((Classes::class.java.getDeclaredField("Image").get(current) as KClass<*>).java))
        assertEquals(null, GlobalJavaClasses.classOf("NoSuchClass"))
    }

    @Test
    fun propertiesAndToString() = check(
        listOf(
            "isAutoJs6" to "true",
            "String(globalThis)" to "\"[object global]\"",
            "toString()" to "\"[object global]\"",
            "toString(1)" to "throws TypeError",
        )
    )

    /** TODO always throws NotImplementedError: the reason as given, the host's message when nullish or empty. */
    @Test
    fun todo() = check(
        listOf(
            "TODO()" to "throws JavaException NotImplementedError",
            "(function () { try { TODO('why'); } catch (e) { return e.javaException.getMessage(); } })()" to "\"why\"",
            "(function () { try { TODO(42); } catch (e) { return e.javaException.getMessage(); } })()" to "\"42\"",
            "(function () { try { TODO(''); } catch (e) { return e.javaException.getMessage().length > 0; } })()" to "true",
            "(function () { try { TODO(null); } catch (e) { return String(e.javaException.getClass().getSimpleName()); } })()" to "\"NotImplementedError\"",
            "TODO('a', 'b')" to "throws TypeError",
        )
    )

    /** The type predicates are the new util's (P2.3), with exactly one argument (appendix C Q7). */
    @Test
    fun typePredicates() = check(
        listOf(
            "[isJavaObject(new java.lang.Object()), isJavaObject(java.lang.String), isJavaObject({}), isJavaObject(null)]" to "[true,false,false,false]",
            "[isInteger(1), isInteger(1.5), isInteger('1'), isInteger(NaN), isInteger(Infinity)]" to "[true,false,false,false,false]",
            "[isBigInt(1n), isBigInt(1)]" to "[true,false]",
            "[isPrimitive(1), isPrimitive('a'), isPrimitive(null), isPrimitive({}), isPrimitive(function () {})]" to "[true,true,true,false,false]",
            "[isReference({}), isReference([]), isReference(function () {}), isReference(1)]" to "[true,true,true,false]",
            "[isEmptyObject({}), isEmptyObject({ a: 1 }), isEmptyObject([]), isEmptyObject(null)]" to "[true,false,true,false]",
            "typeof unwrapJavaObject(new java.lang.StringBuilder('ab'))" to "\"object\"",
            "unwrapJavaObject(1)" to "1",
            "isInteger()" to "throws TypeError",
            "isPrimitive(1, 2)" to "throws TypeError",
        )
    )

    /** isObjectSpecies is the documented typeof check; the global isObject is species.isObject (species mounts later). */
    @Test
    fun legacy() = check(
        listOf(
            "[isObjectSpecies({}), isObjectSpecies([]), isObjectSpecies(new Date(0)), isObjectSpecies(null), isObjectSpecies(function () {}), isObjectSpecies(1)]" to
                    "[true,true,true,false,false,false]",
            "isObject === species.isObject" to "true",
            "[isObject({}), isObject([])]" to "[true,false]",
            "isObjectSpecies()" to "throws TypeError",
        )
    )

    /** random: Math.random() without arguments, NaN with one (as documented), an integer in [min, max] with two. */
    @Test
    fun random() = check(
        listOf(
            "typeof random()" to "\"number\"",
            "(function () { for (var i = 0; i < 100; i++) { var r = random(); if (!(r >= 0 && r < 1)) return false; } return true; })()" to "true",
            "random(5)" to "NaN",
            "random(1, 1)" to "1",
            "random('2', '2')" to "2",
            "random(null, null)" to "0",
            "random(undefined, 2)" to "NaN",
            "(function () { for (var i = 0; i < 300; i++) { var r = random(1, 3); if (r < 1 || r > 3 || r !== Math.floor(r)) return false; } return true; })()" to "true",
            "random(1, 2, 3)" to "throws TypeError",
        )
    )

    /** wait with function conditions: truthiness, attempts, the callback positions and the errors. */
    @Test
    fun waitWithFunctions() = check(
        listOf(
            "wait(function () { return 1; })" to "true",
            "wait(function () { return 0; }, 1)" to "true",
            "wait(function () { return ''; }, 1)" to "true",
            "wait(function () { return false; }, 2, 0)" to "false",
            "wait(function () { return null; }, 1)" to "false",
            "wait(function () { return NaN; }, 1)" to "false",
            "(function () { var n = 0; wait(function () { n++; return false; }, 3, 0); return n; })()" to "3",
            "(function () { var n = 0; wait(function () { return ++n === 2; }, 5, 0); return n; })()" to "2",
            "wait(function () { return 'ok'; }, { then: function (v) { return v + '!'; } })" to "\"ok!\"",
            "wait(function () { return false; }, 1, { else: function () { return 'no'; } })" to "\"no\"",
            "wait(function () { return false; }, 1, 0, { else: function () {} })" to "false",
            "wait(function () { return 'x'; }, 1, 0, { then: function (v) { return v; } }, undefined)" to "\"x\"",
            "wait(function () { return true; }, 0)" to "false",
            "(function () { var n = 0; var r = wait(function () { n++; return true; }, 0, { else: function (v) { return 'else:' + v; } }); return [n, r]; })()" to "[0,\"else:undefined\"]",
            "wait(function () { return true; }, -1)" to "throws RangeError",
            "wait(function () { return true; }, 1, Infinity)" to "throws RangeError",
            "wait(function () { return true; }, 1, -1)" to "throws RangeError",
            "wait(function () { return true; }, 1, 0, 5)" to "throws TypeError",
            "wait(function () { return true; }, { then: 1 })" to "throws TypeError",
            "wait(function () { throw new Error('inner'); }, 1)" to "throws Error",
            "wait()" to "throws TypeError",
            "wait(function () { return true; }, 1, 0, {}, 5)" to "throws TypeError",
        )
    )

    /** requiresAutojsVersion against the version of this build. */
    @Test
    fun requiresAutojsVersion() = check(
        listOf(
            "requiresAutojsVersion(461)" to "undefined",
            "requiresAutojsVersion(${BuildConfig.VERSION_CODE})" to "undefined",
            "requiresAutojsVersion(6)" to "undefined",
            "requiresAutojsVersion('6.0.0')" to "undefined",
            "requiresAutojsVersion(300)" to "throws RangeError",
            "requiresAutojsVersion(${BuildConfig.VERSION_CODE + 1})" to "throws JavaException IllegalArgumentException",
            "requiresAutojsVersion('99.0.0')" to "throws JavaException IllegalArgumentException",
            "requiresAutojsVersion(null)" to "throws TypeError",
            "requiresAutojsVersion()" to "throws TypeError",
        ),
        // The version code of the device build differs from this JVM build's.
        export = false,
    )

    /** isStopped and its forms on a running script; exit and stop take no argument but the error. */
    @Test
    fun runState() = check(
        listOf(
            "[isStopped(), isShuttingDown(), notStopped(), isRunning()]" to "[false,false,true,true]",
            "isStopped(1)" to "throws TypeError",
            "exit(1, 2)" to "throws TypeError",
            "stop(1)" to "throws TypeError",
        )
    )

    /** Scale bases: read, validated before the host's set-once rule, set once. */
    @Test
    fun scaleBases() = check(
        listOf(
            "getScaleBases()" to "{\"x\":720,\"y\":1280}",
            "[getScaleBaseX(), getScaleBaseY()]" to "[720,1280]",
            "setScaleBaseX(0)" to "throws RangeError",
            "setScaleBaseX(-1.5)" to "throws RangeError",
            "setScaleBaseX('a')" to "throws TypeError",
            "setScaleBases(1080)" to "throws TypeError",
            "getScaleBaseX(1)" to "throws TypeError",
            "(setScaleBases(1080.7, '1920'), [getScaleBaseX(), getScaleBaseY()])" to "[1080,1920]",
            "setScaleBaseX(720)" to "throws JavaException IllegalArgumentException",
            "getScaleBases()" to "{\"x\":1080,\"y\":1920}",
        ),
        // Run last on the device (deviceOnlyCases), the bases are set once per script.
        export = false,
    )

    /** The Java class names: readable, but neither own properties nor visible to `in`, `typeof` of a bare name or `Object.keys`. */
    @Test
    fun javaClassNames() = check(
        listOf(
            "typeof globalThis.Intent" to "\"function\"",
            "String(globalThis.Intent)" to "\"[JavaClass android.content.Intent]\"",
            "Intent === globalThis.Intent" to "true",
            "typeof Intent" to "\"undefined\"",
            "['Intent' in globalThis, globalThis.hasOwnProperty('Intent'), Object.keys(globalThis).indexOf('Intent')]" to "[false,false,-1]",
            "String(EvaluatorException)" to "\"[JavaClass org.mozilla.javascript.EvaluatorException]\"",
            "typeof globalThis.NoSuchJavaClassName" to "\"undefined\"",
            "Object.getOwnPropertyNames(Object.getPrototypeOf(globalThis)).length" to "0",
        )
    )

    /** The pure rules against the current Kotlin: sleep range and millis, wait policy, condition truthiness. */
    @Test
    fun sleepAndWaitRules() {
        val inputs = listOf<Pair<Any?, Any?>>(
            0.0 to null, 1000.0 to null, 1000.0 to 3000.0, 3000.0 to 1000.0, 4000.0 to "1e3", 4000.0 to "\u00b11e3", 100.0 to "500", -5.0 to null,
            1000.0 to -2000.0, 5.0 to Double.POSITIVE_INFINITY, "200" to null,
        )
        inputs.forEach { (min, max) ->
            val current = Global.resolveSleepRange(org.autojs.autojs.util.RhinoUtils.coerceLongNumber(min, 0L), max)
            val range = GlobalValues.sleepRange(min, max)
            assertEquals("$min, $max", current.min to current.max, range.min to range.max)
            listOf(0.0, 0.25, 0.999999).forEach { r ->
                assertEquals("$min, $max, $r", Global.selectSleepMillis(current, r), GlobalValues.sleepMillis(range, r))
            }
        }
        expectError(RANGE) { GlobalValues.sleepRange(0.0, "abc") }
        expectError(TYPE) { GlobalValues.sleepRange(0.0, true) }
        expectError(TYPE) { GlobalValues.sleepRange("abc", null) }
        expectError(TYPE) { GlobalValues.sleepRange(null, 10.0) }
        expectError(RANGE) { GlobalValues.sleepRange(Double.NaN, null) }
        expectError(RANGE) { GlobalValues.sleepRange(Double.POSITIVE_INFINITY, null) }

        val policies = listOf<Pair<Any?, Any?>>(null to null, 3.0 to 0.0, 99.0 to 10.0, 100.0 to 200.0, 5000.0 to 6000.0, "50" to "20", Double.NaN to Double.NaN, 0.0 to null)
        policies.forEach { (limit, interval) ->
            val current = Global.resolveWaitPolicy(limit, interval)
            val policy = GlobalValues.waitPolicy("wait", limit, interval)
            assertEquals("$limit, $interval", listOf(current.times, current.timeout, current.interval), listOf(policy.times, policy.timeout, policy.interval))
        }
        expectError(RANGE) { GlobalValues.waitPolicy("wait", -1.0, null) }
        expectError(RANGE) { GlobalValues.waitPolicy("wait", 1.0, Double.POSITIVE_INFINITY) }
        expectError(RANGE) { GlobalValues.waitPolicy("wait", 1.0, -1.0) }

        listOf(null, false, true, 0.0, -0.0, "", Double.NaN, "x", 1).forEach { checked ->
            assertEquals("$checked", Global.isWaitConditionSatisfied(checked), GlobalValues.isWaitConditionSatisfied(checked))
        }
    }

    /** cX / cY / cYx / cXy: the same results as the current CoordinateScale, the failures as TypeError / RangeError. */
    @Test
    fun coordinateRules() {
        val argLists = listOf(
            arrayOf<Any?>(), arrayOf<Any?>(0.5), arrayOf<Any?>(-0.25), arrayOf<Any?>(360.0), arrayOf<Any?>(720.0), arrayOf<Any?>(1.0), arrayOf<Any?>(0.0), arrayOf<Any?>(1e308),
            arrayOf<Any?>(360.0, 1080.0), arrayOf<Any?>(0.5, true), arrayOf<Any?>(0.5, false), arrayOf<Any?>(360.0, "auto"), arrayOf<Any?>(0.5, null, true), arrayOf<Any?>(500.0, null, false),
            arrayOf<Any?>(0.5, "16:9"), arrayOf<Any?>(0.5, "9:16"), arrayOf<Any?>(0.5, 0.5625), arrayOf<Any?>(0.5, 2.0, true), arrayOf<Any?>(100.0, "16:9", true), arrayOf<Any?>(0.5, "16:9", "auto"),
            arrayOf<Any?>(Double.NaN), arrayOf<Any?>(Double.POSITIVE_INFINITY), arrayOf<Any?>("abc"), arrayOf<Any?>(null), arrayOf<Any?>(360.0, 0.0), arrayOf<Any?>(360.0, 1.5),
            arrayOf<Any?>(360.0, "x"), arrayOf<Any?>(0.5, true, true), arrayOf<Any?>(0.5, null, "x"), arrayOf<Any?>(0.5, "16:9", false), arrayOf<Any?>(0.5, "16:x"), arrayOf<Any?>(0.5, "1:2:3"),
            arrayOf<Any?>(0.5, -1.0, true), arrayOf<Any?>(0.5, Any(), true),
            arrayOf<Any?>(360.0, ConsString("au", "to")), arrayOf<Any?>(0.5, null, ConsString("au", "to")), arrayOf<Any?>(0.5, ConsString("16", ":9")),
        )
        val mismatches = mutableListOf<String>()
        GlobalValues.Axis.entries.forEach { axis ->
            val currentAxis = CurrentCoordinateScale.Axis.valueOf(axis.name)
            argLists.forEach { args ->
                // The current functions unwrapped their arguments (a ConsString to a String) before CoordinateScale.
                val unwrapped = args.map { if (it is ConsString) it.toString() else it }.toTypedArray()
                val current = runCatching { CurrentCoordinateScale.convert(currentAxis, 1080, 2340, Scale(), unwrapped) }
                val actual = runCatching { GlobalValues.convert(axis, 1080, 2340, Scale(), args) }
                val label = "${axis.functionName}(${args.joinToString()})"
                when {
                    current.isSuccess -> if (actual.getOrNull() != current.getOrNull()) mismatches += "$label: ${current.getOrNull()} -> $actual"
                    actual.isSuccess -> mismatches += "$label: ${current.exceptionOrNull()} -> ${actual.getOrNull()}"
                    actual.exceptionOrNull() !is EcmaError -> mismatches += "$label: ${actual.exceptionOrNull()} is not a TypeError / RangeError"
                }
            }
        }
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    /** Cases that need Android at run time (display, toast, clipboard, threads, host services) or run once per script. */
    @Test
    fun deviceOnlyCases() {
        exportForDevice(
            listOf(
                // The global isUiThread is this module's (decision a of P3.2): the ui module on the new layer keeps
                // ui.isUiThread only, so the surplus argument is checked here.
                "isUiThread()" to "false",
                "isUiThread(1)" to "throws TypeError",
                "sleep(0)" to "undefined",
                "sleep(null)" to "throws TypeError",
                "sleep(1, 3)" to "undefined",
                "sleep(5, '2')" to "undefined",
                "sleep(0, 'abc')" to "throws RangeError",
                "sleep(0, true)" to "throws TypeError",
                "sleep('abc')" to "throws TypeError",
                "sleep(NaN)" to "throws RangeError",
                "sleep()" to "throws TypeError",
                "sleep(1, 2, 3)" to "throws TypeError",
                "[isStopped(), isShuttingDown(), notStopped(), isRunning()]" to "[false,false,true,true]",
                "typeof getClip()" to "\"string\"",
                "setClip(getClip())" to "undefined",
                "getClip(1)" to "throws TypeError",
                "setClip()" to "throws TypeError",
                "typeof currentPackage" to "\"function\"",
                "currentPackage('nonsense')" to "throws RangeError",
                "currentActivity({ by: 'nonsense' })" to "throws RangeError",
                "currentComponent({ mode: 'x' })" to "throws RangeError",
                "currentPackage(1, 2)" to "throws TypeError",
                "waitForActivity()" to "throws TypeError",
                "waitForActivity(null, 1)" to "throws TypeError",
                "waitForPackage('no.such.package', 1, 0, { else: function () { return 'no'; } })" to "\"no\"",
                "waitForPackage('no.such.package', -1)" to "throws RangeError",
                "toastLog('AutoJs6 P3.0 global check')" to "undefined",
                "toastVerbose()" to "throws TypeError",
                "toastError('a', false, false, 1)" to "throws TypeError",
                "setScreenMetrics(1080, 2340)" to "undefined",
                "setScreenMetrics('a', 1)" to "throws TypeError",
                "setScreenMetrics(NaN, 1)" to "throws RangeError",
                "setScreenMetrics(1)" to "throws TypeError",
                "requiresApi(1)" to "undefined",
                "requiresApi(100000)" to "throws JavaException ScriptException",
                "requiresApi('x')" to "throws TypeError",
                "requiresAutojsVersion(461)" to "undefined",
                "requiresAutojsVersion('6.0.0')" to "undefined",
                "requiresAutojsVersion(300)" to "throws RangeError",
                "requiresAutojsVersion(99999999)" to "throws JavaException IllegalArgumentException",
                "requiresAutojsVersion('99.0.0')" to "throws JavaException IllegalArgumentException",
                "[typeof WIDTH, WIDTH > 0, typeof HEIGHT, HEIGHT > 0]" to "[\"number\",true,\"number\",true]",
                "String(globalThis.Image) === String(globalThis.ImageWrapper)" to "true",
                "[typeof axios, typeof cheerio, typeof dayjs, typeof i18n, typeof pangu]" to "[\"function\",\"object\",\"function\",\"function\",\"object\"]",
                "new Intent().getClass().getName()" to "\"android.content.Intent\"",
                "Intent.ACTION_VIEW" to "\"android.intent.action.VIEW\"",
                "cX() > 0 && cY() > 0" to "true",
                "cX(1, true) === cX()" to "true",
                "cX(360) === Math.round(cX() / 2)" to "true",
                "cY(0.25) === Math.round(cY() * 0.25)" to "true",
                "cYx(0.5, '1:2') === cX()" to "true",
                "[cYx(100, 'au' + String('to')) === cYx(100, 'auto'), cX(0.5, 'au' + String('to')) === cX(0.5)]" to "[true,true]",
                "cX(NaN)" to "throws RangeError",
                "cX('a')" to "throws TypeError",
                "cX(1, 0)" to "throws RangeError",
                "cX(360, 'x')" to "throws TypeError",
                "cX(0.5, true, true)" to "throws TypeError",
                "cX(0.5, null, 'x')" to "throws TypeError",
                "cYx(0.5, '16:x')" to "throws RangeError",
                "cYx(0.5, '16:9', false)" to "throws TypeError",
                "cYx()" to "throws TypeError",
                "cX(1, 2, 3, 4)" to "throws TypeError",
                "exit(1, 2)" to "throws TypeError",
                "stop(1)" to "throws TypeError",
                // Last: the scale bases can be set once per script.
                "getScaleBases()" to "{\"x\":720,\"y\":1280}",
                "setScaleBaseX(0)" to "throws RangeError",
                "setScaleBaseX('a')" to "throws TypeError",
                "setScaleBases(1080)" to "throws TypeError",
                "(setScaleBases(1080.7, '1920'), [getScaleBaseX(), getScaleBaseY()])" to "[1080,1920]",
                "setScaleBaseX(720)" to "throws JavaException IllegalArgumentException",
                "cX(540) === Math.round(cX() / 2)" to "true",
            )
        )
    }

    private fun expectError(name: String, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("Expected a $name, got $error", error is EcmaError && error.name == name)
    }

    companion object {

        private const val TYPE = "TypeError"
        private const val RANGE = "RangeError"

        /** The runtime fields global reads that the JVM test runtime leaves unset: the script thread and the scale bases. */
        private fun prepare(runtime: ScriptRuntime) {
            listOf("mThread" to Thread.currentThread(), "scale" to Scale(runtime)).forEach { (name, value) ->
                ScriptRuntime::class.java.getDeclaredField(name).apply { isAccessible = true }.set(runtime, value)
            }
        }

        /** global, legacy, isNullish and species in their order: from the new layer, or all from their current augment calls. */
        private fun mountInOrder(global: ScriptableObject, runtime: ScriptRuntime, newLayer: Boolean) {
            val mounter = if (newLayer) ScriptModuleMounter(ScriptModules, runtime, runtime.topLevelScope, global, order = ScriptModuleLayer.ORDER) else null
            ScriptModuleLayer.TOP_LEVEL_ORDER.filter { it in MODULES }.forEach { name ->
                ScriptModuleLayer.mountOr(mounter, name) {
                    when (name) {
                        "global" -> Global(runtime).assignWithRuntime(global, runtime, GlobalClasses)
                        "legacy" -> Legacy(runtime).assignWithRuntime(global, runtime)
                        "isNullish" -> IsNullish.augmentWithRuntime(global, runtime)
                        "species" -> Species.augmentWithRuntime(global, runtime)
                        else -> throw AssertionError(name)
                    }
                }
            }
        }

        private val MODULES = listOf("global", "legacy", "isNullish", "species")

        private val exported = LinkedHashMap<String, String>()

        @Synchronized
        private fun exportForDevice(cases: List<Pair<String, String>>) {
            cases.forEach { (expression, expected) -> exported[expression] = expected }
            val dir = File("build/script-modules-device").apply { mkdirs() }
            // The scale cases set the bases once per script, so they stay last.
            val (scale, others) = exported.entries.partition { it.key.contains("ScaleBase") || it.key == "cX(540) === Math.round(cX() / 2)" }
            val entries = (others + scale).joinToString(",\n") { (expression, expected) ->
                "  {\"expression\": ${json(expression)}, \"js\": null, \"kt\": ${json(expected)}, \"reason\": \"new script module layer (P3.0)\"}"
            }
            File(dir, "GlobalModuleTest.json").writeText("[\n$entries\n]\n", Charsets.UTF_8)
        }

        private fun json(value: String): String = buildString {
            append('"')
            value.forEach { c ->
                when (c) {
                    '"', BACKSLASH -> append(BACKSLASH).append(c)
                    '\n' -> append(BACKSLASH).append('n')
                    else -> append(c)
                }
            }
            append('"')
        }

        private const val BACKSLASH = '\\'

        /** The global members of global / legacy / isNullish / species: order, attributes, kinds; the Java class names. */
        /** The getters of global; reading them needs a display, so the surface takes their attributes from the slots. */
        private val GETTERS = listOf("WIDTH", "HEIGHT", "axios", "cheerio", "dayjs", "i18n", "pangu")

        private val DESCRIBE: String
            get() = """
            (function () {
                var out = [];
                var classNames = [${GlobalJavaClasses.NAMES.joinToString(", ") { "'$it'" }}];
                function attrs(d) { return (d.enumerable ? 'E' : '-') + (d.writable ? 'W' : '-') + (d.configurable ? 'C' : '-') + (d.get || d.set ? 'A' : ''); }
                function tag(v) { return v === null ? 'null' : v === Object.prototype ? 'Object.prototype' : v === Function.prototype ? 'Function.prototype' : Object.prototype.toString.call(v); }
                function kind(v) {
                    if (typeof v === 'function') {
                        var extra = Object.getOwnPropertyNames(v).filter(function (k) { return ['length', 'arity', 'name'].indexOf(k) < 0; });
                        return 'function ' + v.name + (extra.length ? ' [' + extra.join(',') + ']' : '') + ' proto ' + tag(Object.getPrototypeOf(v));
                    }
                    return typeof v;
                }
                var keys = Object.keys(globalThis);
                out.push('keys ' + keys.join(','));
                keys.forEach(function (n) {
                    if ([${GETTERS.joinToString(", ") { "'$it'" }}].indexOf(n) >= 0) return out.push(n + ' getter');
                    var d = Object.getOwnPropertyDescriptor(globalThis, n);
                    out.push(n + ' ' + attrs(d) + ' ' + kind(d.value));
                });
                var proto = Object.getPrototypeOf(globalThis);
                out.push('proto ' + tag(proto) + ' ' + Object.getOwnPropertyNames(proto).length + ' > ' + tag(Object.getPrototypeOf(proto)));
                classNames.forEach(function (n) {
                    out.push(n + ' ' + typeof globalThis[n] + ' ' + String(globalThis[n]) + ' ' + (n in globalThis) + ' ' + (typeof eval(n)));
                });
                out.push('missing ' + typeof globalThis.NoSuchJavaClassName);
                return out.join(';');
            })()
        """.trimIndent()

    }

}
