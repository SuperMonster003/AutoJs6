package org.autojs.autojs.runtime.api.modules.global

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Scale
import org.autojs.autojs.runtime.api.augment.differential.DifferentialCases
import org.autojs.autojs.runtime.api.augment.differential.DifferentialCases.KnownDifference
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs6.BuildConfig
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Obsolete `__globals__.js` (the global functions and the legacy `isObjectSpecies`) against `global` / `legacy` of the
 * new script module layer (roadmap P3.0), mounted the way `ScriptRuntime.augment()` does with the switch on. Error types
 * are compared (D10). The obsolete module runs with stubs for the host objects it reads (`ui`, `autojs`, `device`, the
 * ScriptRuntime's `isStopped` / `sleep`) and the obsolete `util`; members that need Android on the Kotlin side (sleep's
 * UI thread check, toast, clipboard, current package, display) are skipped here and covered on the device by
 * `GlobalModuleTest`. The differences left are listed in ROADMAP appendix E "P3.0 new deviations".
 *
 * zh-CN: 旧 `__globals__.js` (全局函数与 legacy 的 isObjectSpecies) 对新层 global / legacy (P3.0), 按开关打开时 ScriptRuntime.augment() 的方式挂载, 比较错误类型 (D10).
 * 旧模块以 stub 提供其读取的宿主对象 (ui, autojs, device, ScriptRuntime 的 isStopped / sleep) 与旧 util; Kotlin 一侧需要 Android 的成员 (sleep 的 UI 线程检查,
 * toast, 剪贴板, 当前包名, 屏幕) 在此跳过, 由 GlobalModuleTest 在真机上覆盖. 剩余差异见附录 E "P3.0 新发现的偏差".
 */
class GlobalModuleDifferentialTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        val host = ObsoleteModuleHost(rhino.context, rhino.jsScope)
        host.loadAndDefine("util")
        rhino.evalJs(JS_STUBS)
        host.setRuntimeProperty("isStopped", rhino.evalJs("(function () { return false; })"))
        host.setRuntimeProperty("sleep", rhino.evalJs("(function (millis) { java.lang.Thread.sleep(millis); })"))
        host.load("globals")
        listOf("mThread" to Thread.currentThread(), "scale" to Scale(rhino.ktRuntime)).forEach { (name, value) ->
            ScriptRuntime::class.java.getDeclaredField(name).apply { isAccessible = true }.set(rhino.ktRuntime, value)
        }
        rhino.mountNewModules("global", "legacy", "isNullish", "species")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, exportDir = "build/script-modules-device", exportAll = true)
    }

    companion object {

        /** Host objects the obsolete module reads, as plain stubs (the version of this build for `autojs`). */
        private val JS_STUBS = """
            global.ui = { isUiThread: function () { return false; } };
            global.autojs = { versionCode: ${BuildConfig.VERSION_CODE}, versionName: '${BuildConfig.VERSION_NAME}' };
            global.device = { width: 1080, height: 2340 };
            global.ScriptRuntime = Packages.org.autojs.autojs.runtime.ScriptRuntime;
            global.UiObject = Packages.org.autojs.autojs.core.automator.UiObject;
            global.ScriptInterruptedException = Packages.org.autojs.autojs.runtime.exception.ScriptInterruptedException;
            global.R = { strings: { error_an_operation_is_not_implemented: 0 } };
            global.context = { getString: function () { return 'An operation is not implemented'; } };
        """.trimIndent()

        private const val MESSAGE = "(function (f) { try { f(); return 'no error'; } catch (e) { return String((e.javaException || e).getMessage()); } })"

        private val EXPRESSIONS = listOf(
            "TODO()",
            "TODO('why')",
            "$MESSAGE(function () { TODO('why'); })",
            "$MESSAGE(function () { TODO(42); })",
            "$MESSAGE(function () { TODO({}); })",
            "$MESSAGE(function () { TODO(''); })",
            "$MESSAGE(function () { TODO(null); })",
            "TODO('a', 'b')",
            "[isJavaObject(new java.lang.Object()), isJavaObject(java.lang.String), isJavaObject({}), isJavaObject(null), isJavaObject(1)]",
            "[isInteger(1), isInteger(1.5), isInteger('1'), isInteger(NaN), isInteger(Infinity), isInteger(-0)]",
            "[isBigInt(1n), isBigInt(1), isBigInt('1')]",
            "[isPrimitive(1), isPrimitive('a'), isPrimitive(null), isPrimitive(undefined), isPrimitive({}), isPrimitive(function () {})]",
            "[isReference({}), isReference([]), isReference(function () {}), isReference(1), isReference(null)]",
            "[isEmptyObject({}), isEmptyObject({ a: 1 }), isEmptyObject([]), isEmptyObject(null), isEmptyObject('')]",
            "unwrapJavaObject(1)",
            "String(unwrapJavaObject(new java.lang.StringBuilder('ab')))",
            "isInteger()",
            "isPrimitive(1, 2)",
            "[isObjectSpecies({}), isObjectSpecies([]), isObjectSpecies(new Date(0)), isObjectSpecies(null), isObjectSpecies(function () {}), isObjectSpecies(1)]",
            "isObjectSpecies()",
            "typeof random()",
            "random(5)",
            "random(1, 1)",
            "random(-3, -3)",
            "random(null, null)",
            "random(undefined, 2)",
            "random('2', '2')",
            "typeof random(1, 2, 3)",
            "wait(function () { return 1; })",
            "wait(function () { return 0; }, 1)",
            "wait(function () { return ''; }, 1)",
            "wait(function () { return false; }, 2, 0)",
            "wait(function () { return null; }, 1)",
            "wait(function () { return NaN; }, 1)",
            "(function () { var n = 0; wait(function () { n++; return false; }, 3, 0); return n; })()",
            "(function () { var n = 0; wait(function () { return ++n === 2; }, 5, 0); return n; })()",
            "wait(function () { return 'ok'; }, { then: function (v) { return v + '!'; } })",
            "wait(function () { return false; }, 1, { else: function () { return 'no'; } })",
            "wait(function () { return false; }, 1, 0, { else: function () {} })",
            "wait(function () { return 'x'; }, 1, 0, { then: function (v) { return v; } })",
            "wait(function () { return true; }, 0)",
            "(function () { var n = 0; var r = wait(function () { n++; return true; }, 0, { else: function (v) { return 'else:' + v; } }); return [n, r]; })()",
            "wait(function () { return true; }, '2', '0')",
            "wait(function () { return true; }, -1)",
            "wait(function () { return true; }, 1, Infinity)",
            "wait(function () { return true; }, 1, -1)",
            "wait(function () { return true; }, 1, 0, 5)",
            "wait(function () { return true; }, { then: 1 })",
            "wait(function () { throw new Error('inner'); }, 1)",
            "wait()",
            "wait(function () { return true; }, 1, 0, {}, 5)",
            "[isStopped(), isShuttingDown(), notStopped(), isRunning()]",
            "isStopped(1)",
            "requiresAutojsVersion(${BuildConfig.VERSION_CODE})",
            "requiresAutojsVersion(${BuildConfig.VERSION_CODE + 1})",
            "requiresAutojsVersion(461)",
            "requiresAutojsVersion(300)",
            "requiresAutojsVersion(6)",
            "requiresAutojsVersion('6.0.0')",
            "requiresAutojsVersion('99.0.0')",
            "requiresAutojsVersion('abc')",
            "requiresAutojsVersion(null)",
            "requiresApi('x')",
            "getScaleBases()",
            "[getScaleBaseX(), getScaleBaseY()]",
            "setScaleBaseX(0)",
            "setScaleBaseX(-1.5)",
            "setScaleBaseX('a')",
            "setScaleBases(1080)",
            "(setScaleBases(1080.7, 1920), [getScaleBaseX(), getScaleBaseY()])",
            "setScaleBaseX(720)",
            "[getScaleBases().x, getScaleBases().y]",
            "sleep(0)",
            "sleep(null)",
        )

        private const val ARITY = "Arguments follow the real signature (T4, appendix C Q7): a missing required argument or a surplus one is a TypeError; the obsolete JS ignored surplus arguments and read a missing one as undefined, the current Kotlin rejects both as well."
        private const val JAVA_THROW = "The obsolete JS threw the Java exception object as a JS value (the script catches the Java object itself, without name or javaException); the current Kotlin and the new layer throw it as a Java exception, e.javaException is the NotImplementedError (D10)."
        private const val EMPTY_OBJECT = "Documented: isEmptyObject is true for a JavaScript object without own properties only, as util.isEmptyObject of P2.3; the obsolete JS counted the for-in keys of any value, so null and strings were empty objects."
        private const val SPECIES = "Documented: isObjectSpecies checks a non-null object by typeof (arrays and dates included), as the current Kotlin does; the obsolete JS used species.isObject."
        private const val RANDOM_STRING = "Documented return type number: the obsolete JS concatenated a string bound ('0' + '2'); the bounds are read with JS ToNumber."
        private const val WAIT_CALLBACK = "A callback that is not an object is a TypeError (current Kotlin, appendix C Q12: the obsolete JS ignored it silently)."
        private const val WAIT_RANGE = "An unusable limit or interval is a RangeError (D10); the obsolete JS threw Error, the current Kotlin IllegalArgumentException."
        private const val VERSION = "Kept from the current Kotlin: a version code below 461 (the first AutoJs6 version code) is refused with a RangeError and 2 to 9 stand for the version names \"2.0\" to \"9.0\"; a version name is compared leniently (the obsolete JS required x.y.z); an unmet requirement is an IllegalArgumentException (the obsolete JS threw Error)."
        private const val SCALE = "Documented (6.8.0): the bases are truncated toward zero and must be positive (RangeError, TypeError for non-numbers), each direction can be set once (IllegalArgumentException of the host), setScaleBases validates both before setting either; the obsolete JS checked neither consistently."
        private const val INT_ARG = "Integer arguments follow the current Kotlin (coerceIntNumber): a non-numeric value is a TypeError (D10); the obsolete JS passed it to Java, whose conversion failed with an InternalError."

        private val KNOWN_DIFFERENCES = listOf(
            KnownDifference("TODO()", js = "throws [object JavaObject]", kt = "throws JavaException NotImplementedError", reason = JAVA_THROW),
            KnownDifference("TODO('why')", js = "throws [object JavaObject]", kt = "throws JavaException NotImplementedError", reason = JAVA_THROW),
            KnownDifference("TODO('a', 'b')", js = "throws [object JavaObject]", kt = "throws TypeError", reason = ARITY),
            KnownDifference(
                "[isEmptyObject({}), isEmptyObject({ a: 1 }), isEmptyObject([]), isEmptyObject(null), isEmptyObject('')]",
                js = "[true,false,true,true,true]", kt = "[true,false,true,false,false]", reason = EMPTY_OBJECT,
            ),
            KnownDifference("isInteger()", js = "false", kt = "throws TypeError", reason = ARITY),
            KnownDifference("isPrimitive(1, 2)", js = "true", kt = "throws TypeError", reason = ARITY),
            KnownDifference("isObjectSpecies()", js = "false", kt = "throws TypeError", reason = ARITY),
            KnownDifference(
                "[isObjectSpecies({}), isObjectSpecies([]), isObjectSpecies(new Date(0)), isObjectSpecies(null), isObjectSpecies(function () {}), isObjectSpecies(1)]",
                js = "[true,false,false,false,false,false]", kt = "[true,true,true,false,false,false]", reason = SPECIES,
            ),
            KnownDifference("random('2', '2')", js = "\"02\"", kt = "2", reason = RANDOM_STRING),
            KnownDifference("typeof random(1, 2, 3)", js = "\"number\"", kt = "throws TypeError", reason = ARITY),
            KnownDifference("wait(function () { return true; }, 1, 0, 5)", js = "true", kt = "throws TypeError", reason = WAIT_CALLBACK),
            KnownDifference("wait(function () { return true; }, -1)", js = "throws Error", kt = "throws RangeError", reason = WAIT_RANGE),
            KnownDifference("wait(function () { return true; }, 1, Infinity)", js = "throws Error", kt = "throws RangeError", reason = WAIT_RANGE),
            KnownDifference("wait(function () { return true; }, 1, -1)", js = "throws Error", kt = "throws RangeError", reason = WAIT_RANGE),
            KnownDifference("wait(function () { return true; }, 1, 0, {}, 5)", js = "true", kt = "throws TypeError", reason = ARITY),
            KnownDifference("isStopped(1)", js = "false", kt = "throws TypeError", reason = ARITY),
            KnownDifference("requiresAutojsVersion(300)", js = "undefined", kt = "throws RangeError", reason = VERSION),
            KnownDifference("requiresAutojsVersion(${BuildConfig.VERSION_CODE + 1})", js = "throws Error", kt = "throws JavaException IllegalArgumentException", reason = VERSION),
            KnownDifference("requiresAutojsVersion('99.0.0')", js = "throws Error", kt = "throws JavaException IllegalArgumentException", reason = VERSION),
            KnownDifference("requiresAutojsVersion('abc')", js = "throws Error", kt = "undefined", reason = VERSION),
            KnownDifference("requiresAutojsVersion(null)", js = "throws Error", kt = "throws TypeError", reason = VERSION),
            KnownDifference("requiresApi('x')", js = "throws InternalError", kt = "throws TypeError", reason = INT_ARG),
            KnownDifference("setScaleBaseX('a')", js = "throws RangeError", kt = "throws TypeError", reason = SCALE),
            KnownDifference("setScaleBases(1080)", js = "throws RangeError", kt = "throws TypeError", reason = ARITY),
            KnownDifference("(setScaleBases(1080.7, 1920), [getScaleBaseX(), getScaleBaseY()])", js = "throws RangeError", kt = "[1080,1920]", reason = SCALE),
            KnownDifference("setScaleBaseX(720)", js = "undefined", kt = "throws JavaException IllegalArgumentException", reason = SCALE),
            KnownDifference("[getScaleBases().x, getScaleBases().y]", js = "[720,1280]", kt = "[1080,1920]", reason = SCALE),
        )

    }

}
