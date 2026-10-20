package org.autojs.autojs.runtime.api.modules.global

import org.autojs.autojs.runtime.RhinoTestRuntime
import org.autojs.autojs.runtime.api.augment.differential.DifferentialCases
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.global.IsNullish
import org.autojs.autojs.runtime.api.augment.global.Species
import org.autojs.autojs.runtime.api.augment.nanoid.NanoID
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * `species`, `isNullish` and `nanoid` (no obsolete JS) of the new script module layer against the current Kotlin modules
 * (roadmap P2.2): the "js" side of the [DifferentialRhino] mounts the current `augment` modules, the "kt" side the new
 * layer as `ScriptRuntime.augment()` does with the switch on. `nanoid` is random, so its cases compare lengths and the
 * alphabet. Error types are not compared (every error of the current modules is an InternalError, D10); the new layer's
 * error types are checked by `PureLogicModulesTest` and, with the exported values, on the device (`--strict-errors`).
 */
class GlobalModulesDifferentialTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        val current = RhinoTestRuntime.create(rhino.jsScope)
        val global = rhino.jsScope.globalThis
        IsNullish.augmentWithRuntime(global, current)
        Species.augmentWithRuntime(global, current)
        NanoID.augmentWithRuntime(global, current)
        rhino.mountNewModules("isNullish", "species", "nanoid")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, tolerateErrorTypes = true, exportDir = "build/script-modules-device", exportAll = true)
    }

    companion object {

        private val VALUES = listOf(
            "null", "undefined", "true", "'a'", "1", "NaN", "[]", "{}", "function () {}", "new Date(0)", "/x/", "new Error('e')", "new TypeError('t')",
            "new Map()", "new Set()", "new WeakMap()", "new WeakSet()", "new ArrayBuffer(1)", "new Int8Array(1)", "new Uint8ClampedArray(1)",
            "new Float64Array(1)", "new DataView(new ArrayBuffer(1))", "Symbol('s')", "Object.create(null)", "arguments", "Math", "JSON",
            "java", "java.lang.String", "new java.lang.Object()", "new java.lang.String('j')", "java.lang.Integer.valueOf(7)",
            "java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 1)", "new java.util.ArrayList()", "new Boolean(true)", "new String('s')",
            "new Number(1)", "Object(1)", "<x/>", "<><x/><y/></>",
        )

        private val PREDICATES = listOf(
            "isArray", "isArrayBuffer", "isBigInt", "isBoolean", "isContinuation", "isDataView", "isDate", "isError", "isFloat32Array", "isFloat64Array",
            "isFunction", "isHTMLDocument", "isInt16Array", "isInt32Array", "isInt8Array", "isJavaObject", "isJavaClass", "isJavaPackage", "isMap",
            "isNamespace", "isNull", "isNumber", "isObject", "isQName", "isRegExp", "isSet", "isString", "isUint16Array", "isUint32Array", "isUint8Array",
            "isUint8ClampedArray", "isUndefined", "isWeakMap", "isWeakSet", "isWindow", "isXML", "isXMLList",
        )

        private val EXPRESSIONS = VALUES.map { "(function () { return species($it); })()" } +
                VALUES.map { "(function () { return species.isObject($it) + ',' + species.isJavaObject($it) + ',' + isNullish($it); })()" } +
                listOf(
                    "(function (s) { return species(s + 'x'); })('a')",
                    "(function (s) { return species.isString(s + 'x'); })('a')",
                    "(function () { var v = [1, 'a', null, {}]; return [" + PREDICATES.joinToString(", ") { "v.map(function (x) { return species.$it(x); }).join()" } + "]; })()",
                    "[isJavaClass(java.lang.String), isJavaPackage(java), isObject({}), isObject(1)]",
                    "isObject === species.isObject",
                    "species()",
                    "species(undefined)",
                    "species(1, 2)",
                    "species.isArray()",
                    "species.isArray([], 1)",
                    "isNullish()",
                    "isNullish(null, 1)",
                    "[1, null, 2].some(isNullish)",
                    "typeof species",
                    "typeof isNullish",
                    "nanoid().length",
                    "nanoid(8).length",
                    "nanoid(0)",
                    "nanoid(8.7).length",
                    "nanoid('5').length",
                    "nanoid(undefined).length",
                    "nanoid(null).length",
                    "/^[A-Za-z0-9_-]{64}$/.test(nanoid(64))",
                    "nanoid(-1)",
                    "nanoid(NaN)",
                    "nanoid('abc')",
                    "nanoid(1, 2)",
                    "nanoid(true).length",
                    "typeof nanoid",
                )

        /** The deviations of the current Kotlin modules changed by the new layer (ROADMAP appendix E, "P2.2"). */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference(
                "(function (s) { return species(s + 'x'); })('a')",
                js = "\"JavaObject\"",
                kt = "\"String\"",
                reason = "a string built at run time is a ConsString, which Context.javaToJS wrapped as a Java object in the current module; " +
                        "its species is String (typeof is \"string\")",
            ),
            DifferentialCases.KnownDifference(
                "(function (s) { return species.isString(s + 'x'); })('a')",
                js = "false",
                kt = "true",
                reason = "as for species: a runtime-built string is a String",
            ),
            DifferentialCases.KnownDifference(
                "(function () { return species(Symbol('s')); })()",
                js = "\"JavaObject\"",
                kt = "\"Symbol\"",
                reason = "documented rule Object.prototype.toString.call(o) gives Symbol; the current module wrapped the symbol as a Java object",
            ),
            DifferentialCases.KnownDifference(
                "(function () { return species.isObject(Symbol('s')) + ',' + species.isJavaObject(Symbol('s')) + ',' + isNullish(Symbol('s')); })()",
                js = "\"false,true,false\"",
                kt = "\"false,false,false\"",
                reason = "as for species: a symbol is not a Java object",
            ),
            DifferentialCases.KnownDifference(
                "isObject === species.isObject",
                js = "false",
                kt = "true",
                reason = "the global function and the module member are one function object on the new layer (as randomInt / Mathx.randomInt, " +
                        "appendix E P2.1); names, attributes and order are unchanged",
            ),
            DifferentialCases.KnownDifference(
                "nanoid(true).length",
                js = "1",
                kt = "throws TypeError",
                reason = "T4: a boolean is not a number for a numeric parameter (documented: size is a number); the current module converted true to 1",
            ),
        )

    }

}
