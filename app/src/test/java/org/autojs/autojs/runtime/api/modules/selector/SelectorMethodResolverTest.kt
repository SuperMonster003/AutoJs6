package org.autojs.autojs.runtime.api.modules.selector

import org.autojs.autojs.core.accessibility.UiSelector
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.TopLevel
import org.mozilla.javascript.regexp.NativeRegExp

class SelectorMethodResolverTest {

    private lateinit var context: Context
    private lateinit var scope: TopLevel

    @Before
    fun enterContext() {
        context = Context.enter()
        scope = context.initStandardObjects()
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun nativeRegExpSelectsTheMatchingOverloadWithoutTryingStringFirst() {
        val stringMethod = UiSelector::class.java.getMethod("idMatches", String::class.java)
        val regexpMethod = UiSelector::class.java.getMethod("idMatches", NativeRegExp::class.java)
        val regexp = context.evaluateString(scope, "/target/i", "<test>", 1, null) as NativeRegExp

        val resolution = SelectorMethodResolver.resolve(listOf(stringMethod, regexpMethod), arrayOf(regexp))

        assertSame(regexpMethod, resolution?.method)
        assertSame(regexp, resolution?.arguments?.single())
    }

    @Test
    fun stringSelectsTheStringOverloadRegardlessOfCandidateOrder() {
        val stringMethod = UiSelector::class.java.getMethod("idMatches", String::class.java)
        val regexpMethod = UiSelector::class.java.getMethod("idMatches", NativeRegExp::class.java)

        val resolution = SelectorMethodResolver.resolve(listOf(regexpMethod, stringMethod), arrayOf("target"))

        assertSame(stringMethod, resolution?.method)
    }

    @Test
    fun resolvedRegexpOverloadIsReadyForDirectInvocation() {
        val stringMethod = Fixture::class.java.getMethod("pick", String::class.java)
        val regexpMethod = Fixture::class.java.getMethod("pick", NativeRegExp::class.java)
        val regexp = context.evaluateString(scope, "/target/", "<test>", 1, null) as NativeRegExp
        val fixture = Fixture()

        val resolution = requireNotNull(
            SelectorMethodResolver.resolve(listOf(stringMethod, regexpMethod), arrayOf(regexp)),
        )
        val result = resolution.method.invoke(fixture, *resolution.arguments)

        assertEquals("regexp", result)
        assertEquals(0, fixture.stringCalls)
        assertEquals(1, fixture.regexpCalls)
    }

    @Test
    fun extraArgumentsAreNotSilentlyDiscarded() {
        val stringMethod = UiSelector::class.java.getMethod("idMatches", String::class.java)

        val resolution = SelectorMethodResolver.resolve(listOf(stringMethod), arrayOf("target", "ignored"))

        assertNull(resolution)
    }

    @Test
    fun numericVarargsKeepTheExistingNumericConversions() {
        val method = Fixture::class.java.getMethod(
            "collect",
            String::class.java,
            IntArray::class.java,
        )

        val resolution = SelectorMethodResolver.resolve(listOf(method), arrayOf<Any?>("values", 1.2, 2.8))

        assertSame(method, resolution?.method)
        assertEquals("values", resolution?.arguments?.get(0))
        assertArrayEquals(intArrayOf(1, 3), resolution?.arguments?.get(1) as IntArray)
    }

    @Test
    fun nullIsRejectedForPrimitivesButAcceptedForObjects() {
        val primitiveMethod = Fixture::class.java.getMethod("boundsInside", Int::class.java)
        val objectMethod = Fixture::class.java.getMethod("pick", String::class.java)

        assertNull(SelectorMethodResolver.resolve(listOf(primitiveMethod), arrayOf<Any?>(null)))

        val resolution = SelectorMethodResolver.resolve(listOf(objectMethod), arrayOf<Any?>(null))
        assertSame(objectMethod, resolution?.method)
        assertNull(resolution?.arguments?.single())
    }

    @Test
    fun jsNumbersAreCoercedToTheDeclaredNumericParameterTypes() {
        val longMethod = Fixture::class.java.getMethod("delay", Long::class.java)
        val floatMethod = Fixture::class.java.getMethod("scale", Float::class.java)
        val doubleMethod = Fixture::class.java.getMethod("ratio", Double::class.java)

        assertEquals(3L, SelectorMethodResolver.resolve(listOf(longMethod), arrayOf<Any?>(2.5))?.arguments?.single())
        assertEquals(1.5f, SelectorMethodResolver.resolve(listOf(floatMethod), arrayOf<Any?>(1.5))?.arguments?.single())
        assertEquals(0.25, SelectorMethodResolver.resolve(listOf(doubleMethod), arrayOf<Any?>(0.25))?.arguments?.single())
    }

    @Test
    fun nonNumericArgumentIsNotCoercedIntoNumericParameter() {
        val longMethod = Fixture::class.java.getMethod("delay", Long::class.java)

        assertNull(SelectorMethodResolver.resolve(listOf(longMethod), arrayOf<Any?>("3")))
    }

    class Fixture {

        var stringCalls = 0
        var regexpCalls = 0

        @Suppress("unused", "UNUSED_PARAMETER")
        fun boundsInside(value: Int) = Unit

        @Suppress("unused", "UNUSED_PARAMETER")
        fun delay(value: Long) = Unit

        @Suppress("unused", "UNUSED_PARAMETER")
        fun scale(value: Float) = Unit

        @Suppress("unused", "UNUSED_PARAMETER")
        fun ratio(value: Double) = Unit

        @Suppress("unused")
        fun pick(value: String): String {
            stringCalls++
            return "string"
        }

        @Suppress("unused")
        fun pick(value: NativeRegExp): String {
            regexpCalls++
            return "regexp"
        }

        @Suppress("unused")
        fun collect(prefix: String, vararg values: Int) = prefix to values
    }
}
