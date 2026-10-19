package org.autojs.autojs.runtime.api.modules.sample

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptAttributes
import org.autojs.autojs.runtime.api.modules.ScriptConstruct
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptJavaClass
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.VarScope
import java.util.concurrent.TimeUnit

/**
 * Exercises every annotation and parameter kind of the script module processor (roadmap P1.1); the generated
 * `SampleModuleBinding` is checked by `ScriptModuleBindingPrototypeTest`.
 */
@ScriptModule(name = "sample", aliases = ["sampleAlias"])
@ScriptJavaClass(name = "TimeUnit", type = TimeUnit::class)
@ScriptJavaClass(name = "SampleTimeUnit", type = TimeUnit::class, global = ScriptGlobal.ONLY)
class SampleModule(private val runtime: ScriptRuntime) {

    private var reads = 0

    @ScriptFunction(aliases = ["plus"])
    fun add(a: Double, b: Double = 1.0): Double = a + b

    @ScriptFunction
    fun sum(vararg values: Double): Double = values.sum()

    @ScriptFunction
    fun join(separator: String, vararg parts: String): String = parts.joinToString(separator)

    @ScriptFunction
    fun pick(o: Scriptable): String = "object:${o.className}"

    @ScriptFunction(name = "pick")
    fun pickNumber(n: Double): String = "number:$n"

    @ScriptFunction(name = "pick")
    fun pickStrings(a: String, b: String): String = "strings:$a$b"

    @ScriptFunction
    fun repeat(s: String, times: Int): String = s.repeat(times)

    @ScriptFunction
    fun describe(cx: Context, scope: VarScope, value: Any?): String = "${cx.languageVersion}:${scope.javaClass.simpleName}:${value?.javaClass?.simpleName}"

    @ScriptFunction
    fun touch(label: String?) {
        lastTouched = label
    }

    @ScriptFunction
    fun size(bytes: ByteArray): Int = bytes.size

    @ScriptFunction
    fun keys(map: Map<String, Any?>): List<String> = map.keys.toList()

    @ScriptFunction
    fun count(items: List<Any?>): Int = items.size

    @ScriptFunction
    fun unit(unit: TimeUnit): String = unit.name

    @ScriptFunction
    fun flag(b: Boolean = false, n: Long = 7): String = "$b:$n"

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun sampleEverywhere(): String = "everywhere"

    @ScriptFunction(global = ScriptGlobal.ONLY, attributes = ScriptAttributes.PERMANENT or ScriptAttributes.DONTENUM)
    fun sampleGlobalOnly(): String = "global"

    @ScriptGetter
    val reading: Int
        get() = ++reads

    @ScriptGetter
    var lastTouched: String? = null

    @ScriptGetter(name = "runtimeName")
    fun runtimeClassName(): String = runtime.javaClass.simpleName

    @ScriptProperty(attributes = ScriptAttributes.PERMANENT or ScriptAttributes.READONLY)
    val VERSION = "1.0"

    @ScriptInvoke
    fun invoke(x: Double): String = "called:$x"

    @ScriptConstruct
    fun construct(cx: Context, scope: VarScope, label: String): Scriptable = (cx.newObject(scope) as Scriptable).also { it.put("label", it, label) }

    @ScriptPrototype
    val prototype: Any = SampleProto()

    /** The Java prototype of the module object, as `ScriptRuntime.util` is for `util`. */
    class SampleProto {
        fun fromProto(): String = "proto"
    }

    @Suppress("unused")
    fun notExposed(array: NativeArray): Int = array.size

}
