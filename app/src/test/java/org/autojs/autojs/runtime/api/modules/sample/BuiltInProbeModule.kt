package org.autojs.autojs.runtime.api.modules.sample

import org.autojs.autojs.runtime.api.modules.ScriptBuiltIn
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptProperty

/**
 * Test fixture of `@ScriptModule(builtIn)` (roadmap P2.1): extends the built-in `Number` with a data property, static
 * functions (one bound only there), prototype functions and a global function; checked by `ScriptBuiltInExtenderTest`.
 */
@ScriptModule(name = "numberProbe", dollarPrefix = false, builtIn = "Number")
object BuiltInProbeModule {

    @ScriptProperty(builtIn = [ScriptBuiltIn.STATIC])
    val ANSWER = 42

    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun twice(x: Double): Double = x * 2

    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC], builtInOnly = true)
    fun negate(x: Double): Double = -x

    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun plus(self: Double, n: Double = 1.0): Double = self + n

    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE, ScriptBuiltIn.STATIC])
    fun describe(self: Any?): String = if (self is Number) "number:$self" else "other"

    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun fail(self: Double): Double = throw IllegalStateException("boom $self")

    @ScriptFunction(builtIn = [ScriptBuiltIn.GLOBAL])
    fun numberProbeGlobal(): String = "global"

}
