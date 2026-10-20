package org.autojs.autojs.runtime.api.modules.sample

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptAttributes
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptJavaClass
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.ScriptToString
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import java.util.concurrent.TimeUnit

/**
 * Test fixture of roadmap P1.2: on-demand mounting with global members, a READONLY module binding, a FUNCTIONAL
 * `toString`, a prototype chain with an emitter, a sub-module ([ProbeChildModule]) and the error model; checked by
 * `ScriptModuleMounterTest`. [created] counts the module instances, so the tests can see when the module is built.
 */
@ScriptModule(name = "probe", aliases = ["probeAlias"], attributes = ScriptAttributes.PERMANENT or ScriptAttributes.READONLY, emitter = true)
@ScriptJavaClass(name = "ProbeUnit", type = TimeUnit::class, global = ScriptGlobal.ONLY)
class ProbeModule(@Suppress("unused") private val runtime: ScriptRuntime) {

    init {
        created++
    }

    @ScriptFunction
    fun ping(value: String = "pong"): String = value

    @ScriptFunction(name = "toString", toStringRole = ScriptToString.FUNCTIONAL)
    fun convert(value: Double): String = "converted:$value"

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun probeGlobal(): String = "global"

    @ScriptProperty(global = ScriptGlobal.ONLY)
    val probeConstant = "constant"

    @ScriptGetter(global = ScriptGlobal.ONLY)
    val probeCreated: Int
        get() = created

    @ScriptFunction
    fun fail(): String = throw IllegalStateException("boom")

    @ScriptFunction
    fun interrupt(): String = throw ScriptInterruptedException()

    @ScriptFunction
    fun callBack(cx: Context, scope: VarScope, callback: Function): Any? = callback.call(cx, scope, Undefined.instance, emptyArray())

    @ScriptPrototype
    val prototypes: List<Any> = listOf(ProtoA(), ProtoB())

    class ProtoA {
        fun fromA(): String = "a"
    }

    class ProtoB {
        fun fromB(): String = "b"
    }

    companion object {
        @JvmStatic
        var created = 0
    }

}

/** A sub-module of [ProbeModule] without `$` names, with a member on the global object. */
@ScriptModule(name = "child", parent = ProbeModule::class, dollarPrefix = false)
object ProbeChildModule {

    @ScriptFunction
    fun hello(): String = "hello"

    @ScriptFunction(global = ScriptGlobal.ONLY)
    fun probeChildGlobal(): String = "child-global"

}

/** An eagerly built module with a LITERAL `toString`. */
@ScriptModule(name = "eagerProbe", eager = true, dollarPrefix = false)
class EagerProbeModule {

    init {
        created++
    }

    @ScriptFunction(name = "toString", toStringRole = ScriptToString.LITERAL)
    fun describe(): String = "[object EagerProbe]"

    companion object {
        @JvmStatic
        var created = 0
    }

}

/** Stands in for `scriptRuntime.events.emitter()` in the emitter prototype. */
class FakeEmitter {
    fun emit(name: String): String = "emitted:$name"
}
