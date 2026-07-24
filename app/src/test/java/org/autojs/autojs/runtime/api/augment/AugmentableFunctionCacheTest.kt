package org.autojs.autojs.runtime.api.augment

import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

class AugmentableFunctionCacheTest {

    private lateinit var cx: Context

    @Before
    fun enterContext() {
        cx = Context.enter()
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun cachedFunctionMetadataStillInvokesTheCurrentAugmentableInstance() {
        val firstTarget = RhinoUtils.newNativeObject()
        val secondTarget = RhinoUtils.newNativeObject()

        StatefulAugmentable("first").assign(firstTarget)
        StatefulAugmentable("second").assign(secondTarget)

        assertEquals("first:value", call(firstTarget, "echoAlias", "value"))
        assertEquals("second:value", call(secondTarget, "echoAlias", "value"))
        assertEquals("second:again", call(secondTarget, "echoAgain", "again"))
    }

    @Test
    fun classCacheFallsBackWhenAnInstanceDeclaresDifferentFunctions() {
        val alphaTarget = RhinoUtils.newNativeObject()
        val betaTarget = RhinoUtils.newNativeObject()

        VariantAugmentable("alpha").assign(alphaTarget)
        VariantAugmentable("beta").assign(betaTarget)

        assertEquals("alpha", call(alphaTarget, "alpha"))
        assertEquals("beta", call(betaTarget, "beta"))
    }

    @Test
    fun voidReturningMethodsProduceJavaScriptUndefined() {
        val target = RhinoUtils.newNativeObject()
        VoidAugmentable().assign(target)

        assertEquals(Undefined.instance, call(target, "perform"))
    }

    private fun call(target: ScriptableObject, name: String, vararg args: Any?): Any? {
        val function = target.get(name, target) as BaseFunction
        val callArgs = Array<Any?>(args.size) { index -> args[index] }
        return RhinoUtils.callFunction(function, target, target, callArgs)
    }

    class StatefulAugmentable(private val instanceName: String) : Augmentable() {

        override val selfAssignmentFunctions = listOf(
            "echo" to listOf("echoAlias", "echoAgain"),
        )

        @Suppress("unused")
        fun echo(args: Array<out Any?>): String = "$instanceName:${args[0]}"
    }

    class VariantAugmentable(private val selectedFunction: String) : Augmentable() {

        override val selfAssignmentFunctions = listOf(selectedFunction)

        @Suppress("unused", "UNUSED_PARAMETER")
        fun alpha(args: Array<out Any?>): String = "alpha"

        @Suppress("unused", "UNUSED_PARAMETER")
        fun beta(args: Array<out Any?>): String = "beta"
    }

    class VoidAugmentable : Augmentable() {

        override val selfAssignmentFunctions = listOf("perform")

        @Suppress("unused", "UNUSED_PARAMETER")
        fun perform(args: Array<out Any?>) {
            // Intentionally empty. Reflection exposes this Kotlin Unit method as JVM void.
        }
    }
}
